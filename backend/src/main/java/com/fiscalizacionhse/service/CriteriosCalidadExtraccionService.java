package com.fiscalizacionhse.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrae bloques «Criterios de Calidad» con cada sub-ítem a., b., c., d.… sin omitir ninguno.
 * Pensado para libros ESV/ENAP donde la IA suele resumir y perder ítems.
 */
@Service
@Slf4j
public class CriteriosCalidadExtraccionService {

    private static final Pattern PATRON_TITULO_CRITERIOS = Pattern.compile(
            "(?i)criterios?\\s+de\\s+calidad");

    private static final Pattern PATRON_GRUPO_CASO = Pattern.compile(
            "(?i)^para\\s+el\\s+caso\\s+de\\s+(.+)$");

    private static final Pattern PATRON_ITEM_LETRA = Pattern.compile(
            "(?i)^([a-z])\\.\\s+(.+)$");

    private static final Pattern PATRON_ESTANDAR = Pattern.compile(
            "(?m)^(EST[AÁ]NDAR\\s+DE\\s+.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern PATRON_CC = Pattern.compile(
            "(?i)\\bCC\\s*(\\d{1,2})\\b");

    private static final Pattern PATRON_FIN_BLOQUE_CRITERIOS = Pattern.compile(
            "(?m)^(FACTORES?\\s+ESCALADORES?|CONTROL\\s+CR[IÍ]TICO|EST[AÁ]NDAR\\s+DE\\s+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern PATRON_CC_TITULO = Pattern.compile(
            "(?i)(?:CONTROL\\s+CR[IÍ]TICO[^\\n]*\\()?CC\\s*(\\d{1,2})\\)?");

    private static final Pattern PATRON_FIN_SECCION = Pattern.compile(
            "(?m)^(EST[AÁ]NDAR\\s+DE\\s+|CAP[ÍI]TULO\\s+|SECCI[ÓO]N\\s+\\d|CONTROL\\s+CR[IÍ]TICO\\s*$|FACTORES?\\s+DE\\s+CALIDAD)",
            Pattern.CASE_INSENSITIVE);

    public record CriterioItem(
            String estandar,
            String codigoCc,
            String grupoCaso,
            String letra,
            String texto) {}

    public record CatalogoCriterios(String tituloDocumento, List<CriterioItem> items) {
        public int total() {
            return items.size();
        }
    }

    public boolean esPreguntaSobreCriteriosCalidad(String pregunta) {
        if (pregunta == null || pregunta.isBlank()) {
            return false;
        }
        String n = normalizar(pregunta);
        if ((n.contains("criterio") || n.contains("aspecto")) && n.contains("calidad")) {
            return true;
        }
        if (n.contains("factores de calidad") || n.contains("factor de calidad")) {
            return true;
        }
        if (n.contains("carga pesada") || n.contains("combustible liquido") || n.contains("combustible gaseoso")) {
            return true;
        }
        if (n.contains("transporte de carga") && (n.contains("calidad") || n.contains("criterio") || n.contains("conductor"))) {
            return true;
        }
        return n.contains("sustancia peligrosa") && (n.contains("calidad") || n.contains("criterio"));
    }

    public CatalogoCriterios extraerCatalogo(String tituloDocumento, String textoCompleto) {
        if (textoCompleto == null || textoCompleto.isBlank()) {
            return new CatalogoCriterios(tituloDocumento, List.of());
        }

        List<EstandarPos> estandares = localizarEstandares(textoCompleto);
        List<CriterioItem> todos = new ArrayList<>();
        int idx = 0;

        while (idx < textoCompleto.length()) {
            int start = indexOfIgnoreCase(textoCompleto, "criterios de calidad", idx);
            if (start < 0) {
                break;
            }
            int end = encontrarFinSeccion(textoCompleto, start + 15);
            String bloque = textoCompleto.substring(start, end);
            String estandar = estandarEnPosicion(estandares, start);
            String cc = ccMasCercanoAntes(textoCompleto, start);
            todos.addAll(parsearBloqueCriterios(bloque, estandar, cc));
            idx = end;
        }

        // Pasada extra: grupos «Para el caso de…» con ítems a. b. c. aunque no digan «Criterios de Calidad»
        if (todos.isEmpty()) {
            todos.addAll(buscarGruposParaElCasoConItems(textoCompleto, estandares));
        }

        log.info("📋 Criterios de calidad «{}»: {} ítems (a/b/c/…) detectados",
                tituloDocumento, todos.size());

        return new CatalogoCriterios(tituloDocumento, todos);
    }

    /**
     * Responde solo con los Criterios de Calidad del estándar indicado en la pregunta.
     */
    public Optional<String> responderCriteriosDeEstandar(String tituloDocumento, String textoCompleto, String pregunta) {
        Optional<String> estandarOpt = EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta);
        if (estandarOpt.isEmpty() || textoCompleto == null || textoCompleto.isBlank()) {
            return Optional.empty();
        }

        String nombreEstandar = estandarOpt.get();
        int inicio = EstandarConsultaHelper.localizarSeccionEstandar(textoCompleto, nombreEstandar);
        if (inicio < 0) {
            log.warn("No se encontró sección del estándar «{}» en el PDF", nombreEstandar);
            return Optional.empty();
        }

        int fin = EstandarConsultaHelper.encontrarFinSeccionEstandar(textoCompleto, inicio, nombreEstandar);
        String seccionEstandar = textoCompleto.substring(inicio, fin);
        Optional<String> ccFiltro = EstandarConsultaHelper.detectarCcEnPregunta(pregunta);

        List<String> bloques = extraerBloquesCriteriosLiterales(seccionEstandar, ccFiltro);
        if (!bloques.isEmpty()) {
            log.info("📋 Criterios de calidad «{}» / estándar «{}» — {} bloque(s) literal(es)",
                    tituloDocumento, nombreEstandar, bloques.size());
            return Optional.of(formatearTranscripcionEstandar(tituloDocumento, nombreEstandar, pregunta, bloques));
        }

        CatalogoCriterios cat = extraerCatalogo(tituloDocumento, textoCompleto);
        CatalogoCriterios filtrado = filtrarPorConsulta(cat, pregunta);
        if (filtrado.total() > 0) {
            return Optional.of(formatearRespuesta(filtrado, pregunta));
        }
        return Optional.empty();
    }

    public CatalogoCriterios filtrarPorConsulta(CatalogoCriterios catalogo, String pregunta) {
        Optional<String> estandarOpt = EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta);
        Optional<String> ccOpt = EstandarConsultaHelper.detectarCcEnPregunta(pregunta);

        if (estandarOpt.isEmpty() && ccOpt.isEmpty()) {
            return catalogo;
        }

        String filtroEst = estandarOpt.map(EstandarConsultaHelper::normalizar).orElse(null);
        String filtroCc = ccOpt.orElse(null);

        List<CriterioItem> filtrados = catalogo.items().stream()
                .filter(item -> {
                    if (filtroCc != null && item.codigoCc() != null && !filtroCc.equalsIgnoreCase(item.codigoCc())) {
                        return false;
                    }
                    if (filtroEst != null && item.estandar() != null) {
                        String estNorm = EstandarConsultaHelper.normalizar(
                                item.estandar().replaceFirst("(?i)^EST[AÁ]NDAR\\s+DE\\s+", ""));
                        return EstandarConsultaHelper.coincideNombreEstandar(estNorm, filtroEst);
                    }
                    return filtroEst == null;
                })
                .toList();

        return new CatalogoCriterios(catalogo.tituloDocumento(), filtrados);
    }

    public String formatearRespuesta(CatalogoCriterios catalogo, String pregunta) {
        CatalogoCriterios aMostrar = filtrarPorConsulta(catalogo, pregunta);

        if (aMostrar.items().isEmpty() && !catalogo.items().isEmpty()
                && EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta).isPresent()) {
            return """
                    No se encontraron **Criterios de Calidad** para el estándar indicado en la consulta.

                    Verifique el nombre del estándar (ej.: «Transporte de carga», «Excavaciones») o indique el CC (ej.: CC9).
                    """;
        }

        if (aMostrar.items().isEmpty()) {
            return """
                    No se detectaron **Criterios de Calidad** con ítems a./b./c. en el texto extraído del PDF.

                    Si el bloque está en una imagen o tabla no seleccionable, hace falta PDF con OCR o texto digital.
                    """;
        }

        StringBuilder sb = new StringBuilder();
        Optional<String> estandarPregunta = EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta);
        sb.append("## Criterios de Calidad — «").append(escapar(aMostrar.tituloDocumento())).append("»\n\n");
        if (estandarPregunta.isPresent()) {
            sb.append("**Estándar:** ").append(estandarPregunta.get()).append("\n\n");
        }
        sb.append("**Total: ").append(aMostrar.total()).append(" ítems** (solo este estándar, sin resumir).\n\n");

        String estandarActual = null;
        String ccActual = null;
        String grupoActual = null;

        for (CriterioItem item : aMostrar.items()) {
            if (!Objects.equals(estandarActual, item.estandar()) || !Objects.equals(ccActual, item.codigoCc())) {
                estandarActual = item.estandar();
                ccActual = item.codigoCc();
                sb.append("---\n\n");
                if (estandarActual != null && !estandarActual.isBlank()) {
                    sb.append("### ").append(estandarActual);
                    if (ccActual != null && !ccActual.isBlank()) {
                        sb.append(" · **").append(ccActual).append("**");
                    }
                    sb.append("\n\n");
                }
                grupoActual = null;
            }
            if (!Objects.equals(grupoActual, item.grupoCaso())) {
                grupoActual = item.grupoCaso();
                if (grupoActual != null && !grupoActual.isBlank()) {
                    sb.append("**Para el caso de ").append(grupoActual).append(":**\n\n");
                }
            }
            sb.append("- **").append(item.letra()).append(".** ").append(item.texto()).append("\n");
        }

        sb.append("\n---\n*Extraído del PDF — solo el estándar consultado.*\n");
        return sb.toString();
    }

    private List<String> extraerBloquesCriteriosLiterales(String seccionEstandar, Optional<String> ccFiltro) {
        List<String> bloques = new ArrayList<>();
        int idx = 0;
        String ccActual = null;
        String tituloCcActual = null;

        while (idx < seccionEstandar.length()) {
            int start = indexOfIgnoreCase(seccionEstandar, "criterios de calidad", idx);
            if (start < 0) {
                break;
            }

            String ventanaPrevia = seccionEstandar.substring(Math.max(0, start - 1500), start);
            ccActual = ccMasCercanoAntes(seccionEstandar, start);
            tituloCcActual = extraerTituloCc(ventanaPrevia);

            if (ccFiltro.isPresent() && ccActual != null && !ccFiltro.get().equalsIgnoreCase(ccActual)) {
                idx = start + 15;
                continue;
            }

            int end = encontrarFinBloqueCriterios(seccionEstandar, start + 10);
            String bloque = seccionEstandar.substring(start, end).trim();

            String encabezado = "";
            if (tituloCcActual != null && !tituloCcActual.isBlank()) {
                encabezado = tituloCcActual;
            } else if (ccActual != null) {
                encabezado = ccActual;
            }

            if (!encabezado.isBlank()) {
                bloques.add("### " + encabezado + "\n\n```text\n" + bloque + "\n```");
            } else {
                bloques.add("```text\n" + bloque + "\n```");
            }
            idx = end;
        }
        return bloques;
    }

    private static int encontrarFinBloqueCriterios(String texto, int desde) {
        Matcher m = PATRON_FIN_BLOQUE_CRITERIOS.matcher(texto.substring(desde));
        if (m.find() && m.start() > 60) {
            return desde + m.start();
        }
        return Math.min(texto.length(), desde + 12_000);
    }

    private static String extraerTituloCc(String ventanaPrevia) {
        String[] lineas = ventanaPrevia.split("\\R");
        StringBuilder titulo = new StringBuilder();
        for (int i = lineas.length - 1; i >= 0 && i >= lineas.length - 8; i--) {
            String l = lineas[i].trim();
            if (l.isEmpty()) {
                continue;
            }
            if (l.matches("(?i).*CONTROL\\s+CR[IÍ]TICO.*") || l.matches("(?i).*\\(CC\\s*\\d+.*")) {
                titulo.insert(0, l + (titulo.isEmpty() ? "" : " — "));
            }
        }
        Matcher m = PATRON_CC_TITULO.matcher(ventanaPrevia);
        String cc = null;
        while (m.find()) {
            cc = "CC" + m.group(1);
        }
        if (cc != null && !titulo.toString().contains(cc)) {
            titulo.append(cc);
        }
        return titulo.toString().trim();
    }

    private static String formatearTranscripcionEstandar(
            String tituloDoc, String nombreEstandar, String pregunta, List<String> bloques) {
        StringBuilder sb = new StringBuilder();
        if (bloques.size() == 1) {
            sb.append("## Criterios de Calidad");
            if (nombreEstandar != null && !nombreEstandar.isBlank()) {
                sb.append(" — ESTÁNDAR DE ").append(nombreEstandar.toUpperCase(Locale.ROOT));
            }
            sb.append("\n\n").append(bloques.get(0)).append("\n");
            sb.append("\n---\n*Transcripción literal del PDF — solo criterios de calidad.*\n");
            return sb.toString();
        }
        sb.append("## Criterios de Calidad — ESTÁNDAR DE ").append(nombreEstandar.toUpperCase(Locale.ROOT)).append("\n\n");
        for (String b : bloques) {
            sb.append(b).append("\n\n");
        }
        sb.append("---\n*Transcripción literal del PDF — solo criterios de calidad.*\n");
        return sb.toString();
    }

    /** Convierte criterios en puntos clave IA para guardar en BD. */
    public List<IaService.PuntoClaveIa> comoPuntosClave(CatalogoCriterios catalogo) {
        List<IaService.PuntoClaveIa> puntos = new ArrayList<>();
        int orden = 0;
        for (CriterioItem c : catalogo.items()) {
            IaService.PuntoClaveIa p = new IaService.PuntoClaveIa();
            String titulo = (c.grupoCaso() != null ? c.grupoCaso() + " — " : "") + c.letra().toUpperCase() + ".";
            p.setTitulo(titulo.length() > 500 ? titulo.substring(0, 497) + "…" : titulo);
            p.setTema(c.estandar());
            p.setCodigo(c.codigoCc());
            p.setTipo("CRITERIO_CALIDAD");
            p.setContenido("**" + c.letra() + ".** " + c.texto());
            p.setOrden(orden++);
            puntos.add(p);
        }
        return puntos;
    }

    private List<CriterioItem> parsearBloqueCriterios(String bloque, String estandar, String cc) {
        List<CriterioItem> items = new ArrayList<>();
        String grupoActual = null;
        CriterioItem ultimo = null;

        for (String rawLine : bloque.split("\\R")) {
            String linea = rawLine.trim();
            if (linea.isEmpty()) {
                continue;
            }
            if (PATRON_TITULO_CRITERIOS.matcher(linea).find() && linea.length() < 40) {
                continue;
            }

            Matcher mg = PATRON_GRUPO_CASO.matcher(linea);
            if (mg.matches()) {
                grupoActual = mg.group(1).replaceAll(":$", "").trim();
                ultimo = null;
                continue;
            }
            if (linea.toLowerCase(Locale.ROOT).startsWith("para el caso de")) {
                grupoActual = linea.replaceFirst("(?i)^para\\s+el\\s+caso\\s+de\\s*", "")
                        .replaceAll(":$", "").trim();
                ultimo = null;
                continue;
            }

            Matcher mi = PATRON_ITEM_LETRA.matcher(linea);
            if (mi.matches()) {
                String letra = mi.group(1).toLowerCase(Locale.ROOT);
                String texto = mi.group(2).trim();
                ultimo = new CriterioItem(estandar, cc, grupoActual, letra, texto);
                items.add(ultimo);
            } else if (ultimo != null && !esInicioDeNuevaSeccion(linea)) {
                ultimo = new CriterioItem(
                        ultimo.estandar(), ultimo.codigoCc(), ultimo.grupoCaso(), ultimo.letra(),
                        ultimo.texto() + " " + linea);
                items.set(items.size() - 1, ultimo);
            }
        }
        return items;
    }

    private List<CriterioItem> buscarGruposParaElCasoConItems(String texto, List<EstandarPos> estandares) {
        List<CriterioItem> items = new ArrayList<>();
        Pattern p = Pattern.compile("(?i)para\\s+el\\s+caso\\s+de\\s+([^:\\n]+):");
        Matcher m = p.matcher(texto);
        while (m.find()) {
            int start = m.start();
            int end = Math.min(texto.length(), start + 4000);
            Matcher fin = PATRON_FIN_SECCION.matcher(texto.substring(start + 10));
            if (fin.find() && fin.start() > 100) {
                end = start + 10 + fin.start();
            }
            String bloque = texto.substring(start, end);
            String estandar = estandarEnPosicion(estandares, start);
            String cc = ccMasCercanoAntes(texto, start);
            List<CriterioItem> parsed = parsearBloqueCriterios(bloque, estandar, cc);
            if (parsed.size() >= 2) {
                items.addAll(parsed);
            }
        }
        return items;
    }

    private static boolean esInicioDeNuevaSeccion(String linea) {
        return PATRON_ESTANDAR.matcher(linea).matches()
                || PATRON_TITULO_CRITERIOS.matcher(linea).find()
                || PATRON_CC.matcher(linea).lookingAt();
    }

    private static int encontrarFinSeccion(String texto, int desde) {
        Matcher m = PATRON_FIN_SECCION.matcher(texto.substring(desde));
        if (m.find() && m.start() > 80) {
            return desde + m.start();
        }
        return Math.min(texto.length(), desde + 25_000);
    }

    private record EstandarPos(int inicio, int fin, String titulo) {}

    private static List<EstandarPos> localizarEstandares(String texto) {
        List<int[]> headers = new ArrayList<>();
        Matcher m = PATRON_ESTANDAR.matcher(texto);
        while (m.find()) {
            headers.add(new int[]{m.start(), m.end()});
        }
        List<EstandarPos> out = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            int start = headers.get(i)[0];
            int end = (i + 1 < headers.size()) ? headers.get(i + 1)[0] : texto.length();
            String titulo = texto.substring(headers.get(i)[0], headers.get(i)[1]).trim().replaceAll("\\s+", " ");
            out.add(new EstandarPos(start, end, titulo));
        }
        return out;
    }

    private static String estandarEnPosicion(List<EstandarPos> estandares, int pos) {
        String ultimo = null;
        for (EstandarPos ep : estandares) {
            if (ep.inicio() <= pos) {
                ultimo = ep.titulo();
            } else {
                break;
            }
        }
        return ultimo;
    }

    private static String ccMasCercanoAntes(String texto, int pos) {
        String ventana = texto.substring(Math.max(0, pos - 8000), pos);
        Matcher m = PATRON_CC.matcher(ventana);
        String ultimo = null;
        while (m.find()) {
            ultimo = "CC" + m.group(1);
        }
        return ultimo;
    }

    private static int indexOfIgnoreCase(String texto, String busqueda, int desde) {
        Matcher m = Pattern.compile("(?i)" + Pattern.quote(busqueda)).matcher(texto);
        m.region(Math.min(desde, texto.length()), texto.length());
        return m.find() ? m.start() : -1;
    }

    private static String escapar(String s) {
        return s != null ? s : "";
    }

    private static String normalizar(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}+", "");
    }
}
