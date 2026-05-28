package com.fiscalizacionhse.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsea el HTML guardado en el Editor de Contenido (misma fuente que /documentos/{id})
 * y arma bloques para consultas IA sin omitir secciones.
 */
@Service
@Slf4j
public class ContenidoEditorConsultaService {

    private static final Pattern PATRON_ENCABEZADO_HTML = Pattern.compile(
            "(?is)<h([1-4])[^>]*>(.*?)</h\\1>");
    private static final int MAX_CARACTERES_SECCION = 90_000;

    public record SeccionEditor(String nivel, String titulo, String cuerpo) {}

    public boolean esHtmlEditor(String texto) {
        if (texto == null || !texto.contains("<")) {
            return false;
        }
        String t = texto.toLowerCase(Locale.ROOT);
        return t.contains("<h1") || t.contains("<h2") || t.contains("<h3")
                || t.contains("<p") || t.contains("<div") || t.contains("<li");
    }

    /**
     * Extrae secciones h1–h4 + párrafos/listas hasta el siguiente encabezado (como el editor en Angular).
     */
    public List<SeccionEditor> extraerSeccionesDesdeHtml(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        if (html.length() > 25_000) {
            html = acotarVentanaCompromiso(html);
        }
        return extraerSeccionesLiviano(html);
    }

    /**
     * Agente cerrado: lee H1–H4 del editor (Compromiso, estándares ESV, etc.) sin DeepSeek ni RAG.
     */
    public Optional<String> responderLibroCerrado(String html, String pregunta, String tituloDocumento) {
        if (html == null || html.isBlank() || pregunta == null || pregunta.isBlank()) {
            return Optional.empty();
        }
        boolean apartado = EstandarConsultaHelper.esConsultaApartadoTematico(pregunta);
        Optional<String> estandarClave = EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta);
        if (!apartado && estandarClave.isEmpty()) {
            return Optional.empty();
        }

        html = quitarIndiceEmbebidoHtml(html);

        Optional<SeccionEditor> seccion = Optional.empty();

        // Estándar: texto plano primero (evita índice del PDF); luego HTML estructurado.
        if (estandarClave.isPresent()) {
            String clave = estandarClave.get();
            String plano = html.contains("<") ? cuerpoLegible(limpiarHtml(html)) : html;
            Optional<String> bloque = EstandarConsultaHelper.extraerBloqueEstandar(plano, clave);
            if (bloque.isPresent()) {
                String titulo = extraerTituloEstandarEnPosicion(bloque.get(), 0, clave);
                seccion = Optional.of(new SeccionEditor("H2", titulo, truncar(bloque.get())));
            }
            if (seccion.isEmpty()) {
                seccion = buscarSeccionDocSeccionEnHtml(html, clave);
            }
            if (seccion.isEmpty()) {
                seccion = buscarSeccionPorEstandar(extraerSeccionesLiviano(html), clave);
            }
            if (seccion.isPresent() && !esCuerpoEstandarAceptable(seccion.get().cuerpo())) {
                seccion = Optional.empty();
            }
        }

        if (apartado) {
            String ventana = html.length() > 25_000 ? acotarVentanaCompromiso(html) : html;
            seccion = buscarSeccionPorApartadoTematico(extraerSeccionesLiviano(ventana), pregunta);
        }
        if (seccion.isEmpty()) {
            return responderDesdeTextoPlano(html, pregunta, tituloDocumento);
        }

        if (EstandarConsultaHelper.pideResumenExplicito(pregunta)) {
            return Optional.of(formatearResumenLocalApartado(tituloDocumento, seccion.get()));
        }
        if (estandarClave.isPresent() && !apartado) {
            List<SeccionEditor> seccionesAnexo =
                    extraerSeccionesLiviano(acotarVentanaAnexoControles(html));
            List<SeccionEditor> anexoCc =
                    buscarBloquesAnexoRelacionados(seccionesAnexo, estandarClave.get(), pregunta);
            return Optional.of(formatearEstandarCompletoVisual(
                    html, tituloDocumento, seccion.get(), estandarClave.get(), anexoCc));
        }
        return Optional.of(formatearApartadoLiteral(tituloDocumento, seccion.get()));
    }

    /**
     * Respaldo directo sobre texto del PDF (sin depender de H2 en el editor).
     * Usa la misma lógica que evita el índice y localiza el estándar real en el cuerpo.
     */
    public Optional<String> responderDesdeTextoPlano(String texto, String pregunta, String tituloDocumento) {
        if (texto == null || texto.isBlank() || pregunta == null) {
            return Optional.empty();
        }
        String plano = texto.contains("<") ? cuerpoLegible(limpiarHtml(texto)) : texto;
        if (plano.length() < 100) {
            return Optional.empty();
        }

        Optional<String> estandarClave = EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta);
        if (estandarClave.isPresent()) {
            String clave = estandarClave.get();
            Optional<String> bloqueOpt = EstandarConsultaHelper.extraerBloqueEstandar(plano, clave);
            if (bloqueOpt.isEmpty()) {
                return Optional.empty();
            }
            String bloque = bloqueOpt.get();
            String tituloEst = extraerTituloEstandarEnPosicion(bloque, 0, clave);
            SeccionEditor sec = new SeccionEditor("H2", tituloEst, truncar(bloque));
            List<SeccionEditor> anexoCc = Collections.emptyList();
            int anexoPos = plano.toUpperCase(Locale.ROOT).indexOf("ANEXO");
            if (anexoPos > 0) {
                anexoCc = buscarBloquesAnexoRelacionados(
                        extraerSeccionesLiviano(acotarVentanaAnexoControles(
                                "<div>" + plano.substring(anexoPos) + "</div>")),
                        clave, pregunta);
            }
            return Optional.of(formatearEstandarCompletoVisual(plano, tituloDocumento, sec, clave, anexoCc));
        }

        if (EstandarConsultaHelper.esConsultaApartadoTematico(pregunta)
                && normalizar(pregunta).contains("compromiso")) {
            String cuerpo = EstandarConsultaHelper.recortarTextoApartadoCompromiso(plano);
            if (cuerpo.length() >= 80) {
                return Optional.of(formatearApartadoLiteral(
                        tituloDocumento,
                        new SeccionEditor("H1", "COMPROMISO DE LA ALTA DIRECCIÓN DE ENAP", cuerpo)));
            }
        }
        return Optional.empty();
    }

    private static String extraerTituloEstandarEnPosicion(String plano, int inicio, String clave) {
        int lineEnd = plano.indexOf('\n', inicio);
        if (lineEnd < 0) {
            lineEnd = Math.min(plano.length(), inicio + 120);
        }
        String linea = plano.substring(inicio, lineEnd).trim();
        if (linea.length() >= 10) {
            return linea;
        }
        return "ESTÁNDAR DE " + clave.toUpperCase(Locale.ROOT);
    }

    /** @deprecated usar {@link #responderLibroCerrado} */
    public Optional<String> responderApartadoLibroCerrado(String html, String pregunta, String tituloDocumento) {
        return responderLibroCerrado(html, pregunta, tituloDocumento);
    }

    private static String acotarVentanaParaPregunta(String html, String pregunta, String estandarClave) {
        if (html.length() <= 25_000) {
            return html;
        }
        if (EstandarConsultaHelper.esConsultaApartadoTematico(pregunta)) {
            return acotarVentanaCompromiso(html);
        }
        if (estandarClave != null && !estandarClave.isBlank()) {
            return acotarVentanaEstandar(html, estandarClave);
        }
        return html.substring(0, Math.min(html.length(), 120_000));
    }

    /** Quita el índice automático del PDF (evita confundir «EXCAVACIONES» del índice con el estándar real). */
    public static String quitarIndiceEmbebidoHtml(String html) {
        if (html == null || !html.contains("doc-indice")) {
            return html != null ? html : "";
        }
        return html.replaceAll("(?is)<section\\s+class=\"doc-indice\"[\\s\\S]*?</section>\\s*", "").trim();
    }

    /** Ventana HTML desde el estándar real (H2 / doc-seccion) hasta el siguiente estándar o anexo. */
    public static String acotarVentanaEstandar(String html, String nombreEstandar) {
        if (html == null || html.isBlank() || nombreEstandar == null) {
            return html != null ? html : "";
        }
        html = quitarIndiceEmbebidoHtml(html);
        String filtro = normalizar(nombreEstandar);
        String nombreRx = regexTokensEstandar(filtro);

        List<int[]> candidatos = new ArrayList<>();

        Pattern h2EnSeccion = Pattern.compile(
                "(?is)<section[^>]*class=\"[^\"]*(?:doc-seccion|doc-apartado)[^\"]*\"[^>]*>\\s*<h[12][^>]*>[^<]*"
                        + nombreRx + "[^<]*</h[12]>");
        Matcher ms = h2EnSeccion.matcher(html);
        while (ms.find()) {
            candidatos.add(new int[]{ms.start(), puntuarVentanaEstandarHtml(html, ms.start(), filtro)});
        }

        Pattern h2Directo = Pattern.compile(
                "(?is)<h2[^>]*>\\s*(?:\\d{1,2}\\s*[.)]?\\s*)?EST[AÁ]NDAR\\s+(?:\\d{1,2}\\s+)?DE\\s+[^<]*"
                        + nombreRx + "[^<]*</h2>");
        Matcher mh = h2Directo.matcher(html);
        while (mh.find()) {
            candidatos.add(new int[]{mh.start(), puntuarVentanaEstandarHtml(html, mh.start(), filtro)});
        }

        String plano = limpiarHtml(html);
        int posPlano = EstandarConsultaHelper.localizarSeccionEstandar(plano, nombreEstandar);
        if (posPlano >= 0) {
            int posHtml = localizarPosicionEnHtmlDesdePlano(html, plano, posPlano);
            if (posHtml >= 0) {
                candidatos.add(new int[]{posHtml, puntuarVentanaEstandarHtml(html, posHtml, filtro) + 40});
            }
        }

        if (candidatos.isEmpty()) {
            return html.length() > 120_000 ? html.substring(0, 120_000) : html;
        }

        candidatos.sort((a, b) -> Integer.compare(b[1], a[1]));
        int inicio = candidatos.get(0)[0];
        int start = Math.max(0, inicio - 2_000);
        int minFin = inicio + 500;
        int mejorFin = html.length();

        Pattern siguienteSeccion = Pattern.compile(
                "(?is)<section[^>]*class=\"[^\"]*(?:doc-seccion|doc-apartado)[^\"]*\"[^>]*>\\s*<h[12][^>]*>");
        Matcher nsec = siguienteSeccion.matcher(html);
        while (nsec.find(minFin)) {
            String frag = html.substring(nsec.start(), Math.min(nsec.end() + 120, html.length()));
            if (!normalizar(frag).contains(filtro)) {
                mejorFin = nsec.start();
                break;
            }
        }

        Pattern siguienteEstandar = Pattern.compile(
                "(?i)EST[AÁ]NDAR\\s+(?:\\d{1,2}\\s+)?DE\\s+(?!\\s*" + Pattern.quote(filtro) + "\\b)");
        Matcher sig = siguienteEstandar.matcher(html);
        while (sig.find(minFin)) {
            String otro = normalizar(sig.group().replaceFirst("(?i)^est[aá]ndar\\s+(?:\\d{1,2}\\s+)?de\\s+", ""));
            if (!EstandarConsultaHelper.coincideNombreEstandar(otro, filtro)) {
                mejorFin = Math.min(mejorFin, sig.start());
                break;
            }
        }

        String upper = html.toUpperCase(Locale.ROOT);
        int anexo = upper.indexOf("ANEXO", minFin);
        if (anexo > minFin) {
            mejorFin = Math.min(mejorFin, anexo);
        }
        return html.substring(start, Math.min(mejorFin, html.length()));
    }

    /**
     * Localiza el bloque {@code section.doc-seccion} del estándar (formato del Libro estructurado ENAP).
     */
    private Optional<SeccionEditor> buscarSeccionDocSeccionEnHtml(String html, String filtro) {
        if (html == null || html.isBlank()) {
            return Optional.empty();
        }
        Pattern p = Pattern.compile(
                "(?is)<section\\s+[^>]*class=\"[^\"]*(?:doc-seccion|doc-apartado)[^\"]*\"[^>]*>"
                        + "\\s*<h([12])[^>]*>(.*?)</h\\1>(.*?)</section>");
        Matcher m = p.matcher(html);
        SeccionEditor mejor = null;
        int mejorLen = 0;
        while (m.find()) {
            String titulo = limpiarHtml(m.group(2)).trim();
            if (!tituloCoincideEstandar(titulo, filtro)) {
                continue;
            }
            List<SeccionEditor> partes = extraerSeccionesLiviano(m.group(0));
            Optional<SeccionEditor> fusionada = partes.isEmpty()
                    ? Optional.of(new SeccionEditor("H2", titulo, limpiarHtml(m.group(3)).trim()))
                    : buscarSeccionPorEstandar(partes, filtro);
            if (fusionada.isEmpty() || fusionada.get().cuerpo().length() < 40) {
                String cuerpo = limpiarHtml(m.group(3)).trim();
                if (cuerpo.length() >= 40) {
                    fusionada = Optional.of(new SeccionEditor("H2", titulo, truncar(cuerpo)));
                }
            }
            if (fusionada.isPresent()
                    && esCuerpoEstandarAceptable(fusionada.get().cuerpo())
                    && fusionada.get().cuerpo().length() > mejorLen) {
                mejorLen = fusionada.get().cuerpo().length();
                mejor = fusionada.get();
            }
        }
        return Optional.ofNullable(mejor);
    }

    private static boolean esCuerpoEstandarAceptable(String cuerpo) {
        if (cuerpo == null || cuerpo.length() < 200) {
            return false;
        }
        return EstandarConsultaHelper.contieneCuerpoEstandarReal(
                cuerpo.substring(0, Math.min(cuerpo.length(), 12_000)));
    }

    private static String regexTokensEstandar(String filtroNorm) {
        String[] tokens = filtroNorm.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String t : tokens) {
            if (t.length() < 4) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(".*");
            }
            sb.append(Pattern.quote(t));
        }
        if (sb.isEmpty()) {
            sb.append(Pattern.quote(filtroNorm));
        }
        return sb.toString();
    }

    private static int puntuarVentanaEstandarHtml(String html, int pos, String filtro) {
        String ventana = limpiarHtml(html.substring(pos, Math.min(html.length(), pos + 8_000)));
        int score = ventana.length();
        if (ventana.toLowerCase(Locale.ROOT).contains("objetivo")) {
            score += 200;
        }
        if (ventana.toLowerCase(Locale.ROOT).contains("alcance")) {
            score += 150;
        }
        if (ventana.toLowerCase(Locale.ROOT).contains("controles")) {
            score += 100;
        }
        if (normalizar(ventana).contains(filtro)) {
            score += 80;
        }
        return score;
    }

    private static int localizarPosicionEnHtmlDesdePlano(String html, String plano, int posPlano) {
        int lineStart = plano.lastIndexOf('\n', Math.max(0, posPlano - 1));
        String linea = plano.substring(lineStart + 1, Math.min(plano.length(), posPlano + 100)).trim();
        if (linea.length() < 8) {
            return -1;
        }
        String needle = linea.length() > 55 ? linea.substring(0, 55) : linea;
        int p = html.toUpperCase(Locale.ROOT).indexOf(needle.toUpperCase(Locale.ROOT));
        if (p >= 0) {
            return p;
        }
        String corto = needle.length() > 28 ? needle.substring(0, 28) : needle;
        return html.toUpperCase(Locale.ROOT).indexOf(corto.toUpperCase(Locale.ROOT));
    }

    /** Bloque final del libro: anexo de controles críticos (CC1, CC2…). */
    public static String acotarVentanaAnexoControles(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String upper = html.toUpperCase(Locale.ROOT);
        int anexo = upper.indexOf("ANEXO");
        if (anexo < 0) {
            anexo = upper.indexOf("CONTROLES CRITICOS");
        }
        if (anexo < 0) {
            anexo = upper.indexOf("CONTROL CRITICO");
        }
        if (anexo < 0) {
            return "";
        }
        int start = Math.max(0, anexo - 500);
        return html.substring(start, Math.min(html.length(), anexo + 220_000));
    }

    public static String extraerTituloLibroDesdeHtml(String html) {
        if (html == null) {
            return "";
        }
        Matcher hm = PATRON_ENCABEZADO_HTML.matcher(html);
        while (hm.find()) {
            if ("1".equals(hm.group(1))) {
                String t = limpiarHtml(hm.group(2)).trim();
                if (t.length() >= 8 && !normalizar(t).contains("compromiso")) {
                    return t;
                }
            }
        }
        return "";
    }

    /** Escaneo por índices (O(n)); evita {@code .*?} sobre HTML de 270 KB que bloqueaba 45+ s. */
    private List<SeccionEditor> extraerSeccionesLiviano(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        record HeaderPos(int headerStart, int bodyStart, String nivel, String titulo) {}
        List<HeaderPos> headers = new ArrayList<>();
        int pos = 0;
        while (pos < html.length()) {
            int h = buscarSiguienteEncabezado(html, pos);
            if (h < 0) {
                break;
            }
            char nivelChar = Character.toLowerCase(html.charAt(h + 2));
            if (nivelChar < '1' || nivelChar > '4') {
                pos = h + 3;
                continue;
            }
            int gt = html.indexOf('>', h);
            if (gt < 0) {
                break;
            }
            int close = html.indexOf("</h", gt);
            if (close < 0) {
                break;
            }
            int closeGt = html.indexOf('>', close);
            if (closeGt < 0) {
                break;
            }
            String titulo = limpiarHtml(html.substring(gt + 1, close)).trim();
            if (titulo.length() >= 3) {
                headers.add(new HeaderPos(h, closeGt + 1, "H" + nivelChar, titulo));
            }
            pos = closeGt + 1;
        }

        if (headers.isEmpty()) {
            String plano = limpiarHtml(html);
            if (plano.length() < 40) {
                return List.of();
            }
            return List.of(new SeccionEditor("DOC", "Contenido del documento", truncar(plano)));
        }

        List<SeccionEditor> out = new ArrayList<>();
        StringBuilder cuerpoPendiente = new StringBuilder();
        for (int i = 0; i < headers.size(); i++) {
            HeaderPos h = headers.get(i);
            int bodyEnd = (i + 1 < headers.size()) ? headers.get(i + 1).headerStart() : html.length();
            String cuerpo = limpiarHtml(html.substring(h.bodyStart(), bodyEnd)).trim();
            if (esLineaIndicePdf(h.titulo())) {
                if (!cuerpo.isBlank()) {
                    if (cuerpoPendiente.length() > 0) {
                        cuerpoPendiente.append("\n\n");
                    }
                    cuerpoPendiente.append(cuerpo);
                }
                continue;
            }
            if (cuerpoPendiente.length() > 0) {
                cuerpo = cuerpoPendiente + (cuerpo.isBlank() ? "" : "\n\n" + cuerpo);
                cuerpoPendiente.setLength(0);
            }
            if (cuerpo.length() < 10 && h.titulo().length() < 6) {
                continue;
            }
            out.add(new SeccionEditor(h.nivel(), h.titulo(), truncar(cuerpo)));
        }
        return out;
    }

    private static int buscarSiguienteEncabezado(String html, int desde) {
        int mejor = -1;
        for (String tag : new String[] {"<h1", "<h2", "<h3", "<h4", "<H1", "<H2", "<H3", "<H4"}) {
            int p = html.indexOf(tag, desde);
            if (p >= 0 && (mejor < 0 || p < mejor)) {
                mejor = p;
            }
        }
        return mejor;
    }

    private String formatearResumenLocalApartado(String tituloDocumento, SeccionEditor sec) {
        String cuerpo = sec.cuerpo() != null ? sec.cuerpo() : "";
        String intro = cuerpo.replaceAll("\\s+", " ").trim();
        if (intro.length() > 700) {
            intro = intro.substring(0, 700).trim() + "…";
        }
        return """
                ## Resumen: %s
                
                %s
                
                - Fuente: **Editor de Contenido** (sin llamada externa a IA).
                - Para el **texto completo** del apartado: *«Háblame del %s»*.
                
                *Documento: «%s»*
                """.formatted(
                sec.titulo(),
                intro,
                sec.titulo(),
                tituloDocumento != null ? tituloDocumento : "").trim();
    }

    /** Secciones relevantes a la pregunta; con estándar detectado solo devuelve ese apartado. */
    public List<SeccionEditor> filtrarPorPregunta(List<SeccionEditor> secciones, String pregunta) {
        if (secciones == null || secciones.isEmpty()) {
            return List.of();
        }
        if (pregunta == null || pregunta.isBlank()) {
            return secciones;
        }
        boolean consultaConcisa = EstandarConsultaHelper.esConsultaResumenInformal(pregunta);
        Optional<String> estandar = EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta);

        if (estandar.isPresent()) {
            List<SeccionEditor> focal = new ArrayList<>();
            String est = normalizar(estandar.get());
            for (SeccionEditor s : secciones) {
                String tit = normalizar(s.titulo().replaceFirst("(?i)^est[aá]ndar\\s+de\\s+", ""));
                if (EstandarConsultaHelper.coincideNombreEstandar(tit, est)) {
                    focal.add(s);
                }
            }
            if (!focal.isEmpty()) {
                return focal;
            }
            Optional<SeccionEditor> mejor = buscarSeccionPorEstandar(secciones, estandar.get());
            if (mejor.isPresent()) {
                return List.of(mejor.get());
            }
            return List.of();
        }

        LinkedHashSet<String> terminos = terminosPregunta(pregunta);
        List<SeccionEditor> coinciden = new ArrayList<>();
        for (SeccionEditor s : secciones) {
            String blob = normalizar(s.titulo() + " " + s.cuerpo());
            for (String t : terminos) {
                if (t.length() >= 4 && blob.contains(t)) {
                    coinciden.add(s);
                    break;
                }
            }
        }
        if (coinciden.isEmpty() && consultaConcisa) {
            return List.of();
        }
        return coinciden.isEmpty() ? secciones : coinciden;
    }

    /** Apartado temático (Compromiso, introducción…) según la pregunta. */
    public Optional<SeccionEditor> buscarSeccionPorApartadoTematico(
            List<SeccionEditor> secciones, String pregunta) {
        if (secciones == null || secciones.isEmpty() || pregunta == null || pregunta.isBlank()) {
            return Optional.empty();
        }
        if (!EstandarConsultaHelper.esConsultaApartadoTematico(pregunta)) {
            return Optional.empty();
        }

        String n = normalizar(pregunta);
        boolean pideCompromiso = n.contains("compromiso");
        LinkedHashSet<String> terminos = terminosPregunta(pregunta);

        SeccionEditor mejor = null;
        int mejorScore = 0;
        for (SeccionEditor s : secciones) {
            String tit = normalizar(s.titulo());
            if (pideCompromiso && !tit.contains("compromiso")) {
                continue;
            }
            // «ii. INTRODUCCIÓN» del PDF no es el Compromiso aunque venga como encabezado suelto
            if (pideCompromiso && tit.matches("^ii\\s*\\.?\\s*(introduccion|estructura|alcance).*")
                    && !tit.contains("compromiso")) {
                continue;
            }
            int score = 0;
            if ("H1".equalsIgnoreCase(s.nivel())) {
                score += 10;
            } else if ("H2".equalsIgnoreCase(s.nivel())) {
                score += 5;
            }
            if (pideCompromiso && tit.contains("compromiso")) {
                score += 12;
            }
            if (pideCompromiso && tit.contains("alta direccion")) {
                score += 10;
            }
            if (tit.contains("compromiso") && tit.contains("enap")) {
                score += 6;
            }
            for (String t : terminos) {
                if (t.length() >= 5 && tit.contains(t)) {
                    score += 3;
                }
            }
            int largoCuerpo = s.cuerpo() != null ? s.cuerpo().length() : 0;
            if (largoCuerpo < 80) {
                score -= 5;
            }
            if (score > mejorScore) {
                mejorScore = score;
                mejor = s;
            }
        }
        if (mejor == null || mejorScore < 5) {
            if (pideCompromiso) {
                for (SeccionEditor s : secciones) {
                    String tit = normalizar(s.titulo());
                    if (tit.contains("compromiso")) {
                        mejor = s;
                        mejorScore = 10;
                        break;
                    }
                }
            }
        }
        if (mejor != null && mejorScore >= 5) {
            if (pideCompromiso) {
                String cuerpoRecortado = EstandarConsultaHelper.recortarTextoApartadoCompromiso(
                        cuerpoLegible(mejor.cuerpo()));
                if (cuerpoRecortado.length() >= 80) {
                    mejor = new SeccionEditor(mejor.nivel(), mejor.titulo(), cuerpoRecortado);
                }
            }
            return Optional.of(mejor);
        }
        return Optional.empty();
    }

    /**
     * Compromiso ENAP sin recorrer todo el libro con regex de encabezados (evita bloqueos de 30+ s).
     */
    public Optional<String> respuestaLiteralCompromisoDesdeHtml(String html, String tituloDocumento) {
        return responderLibroCerrado(html, "compromiso alta direccion enap", tituloDocumento);
    }

    /** Título real del apartado (H1 prioritario en el editor; no el «ii.» del PDF). */
    public static String detectarTituloCompromisoEnHtml(String htmlVentana) {
        if (htmlVentana == null || htmlVentana.isBlank()) {
            return "COMPROMISO DE LA ALTA DIRECCIÓN DE ENAP";
        }
        Matcher hm = PATRON_ENCABEZADO_HTML.matcher(htmlVentana);
        while (hm.find()) {
            String nivel = "H" + hm.group(1);
            String titulo = limpiarHtml(hm.group(2)).trim();
            String titNorm = normalizar(titulo);
            if (titNorm.contains("compromiso")) {
                return titulo;
            }
            if ("H1".equalsIgnoreCase(nivel) && titNorm.contains("alta direccion")) {
                return titulo;
            }
        }
        return "i. COMPROMISO DE LA ALTA DIRECCIÓN DE ENAP";
    }

    /** Recorta el HTML al bloque Compromiso → Introducción (máx. ~80 KB). */
    public static String acotarVentanaCompromiso(String html) {
        if (html == null) {
            return "";
        }
        if (html.length() <= 25_000) {
            return html;
        }
        String upper = html.toUpperCase(Locale.ROOT);
        int inicio = upper.indexOf("COMPROMISO");
        if (inicio < 0) {
            return html.substring(0, Math.min(html.length(), 100_000));
        }
        int start = Math.max(0, inicio - 4_000);
        int finIntro = upper.indexOf("INTRODUCCION", inicio + 30);
        int fin = finIntro > 0 ? finIntro + 800 : Math.min(html.length(), inicio + 80_000);
        fin = Math.min(fin, html.length());
        return html.substring(start, fin);
    }

    /**
     * Respuesta visual del estándar: título del libro, estándar numerado, cuerpo completo y anexo CC relacionado.
     */
    public String formatearEstandarCompletoVisual(
            String htmlLibro,
            String tituloDocumento,
            SeccionEditor estandar,
            String claveEstandar,
            List<SeccionEditor> bloquesAnexo) {

        String tituloLibro = extraerTituloLibroDesdeHtml(htmlLibro);
        if (tituloLibro.isBlank()) {
            tituloLibro = inferirNombreLibro(tituloDocumento);
        }

        String tituloEst = estandar.titulo() != null ? estandar.titulo().trim() : claveEstandar;
        tituloEst = tituloEst.toUpperCase(Locale.ROOT);
        if (!tituloEst.contains("ESTÁNDAR") && !tituloEst.contains("ESTANDAR")) {
            tituloEst = "ESTÁNDAR DE " + claveEstandar.toUpperCase(Locale.ROOT);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(tituloLibro).append("\n\n");
        sb.append("### ").append(tituloEst).append("\n\n");
        sb.append(cuerpoEstandarLegible(estandar.cuerpo()));

        if (bloquesAnexo != null && !bloquesAnexo.isEmpty()) {
            sb.append("\n\n---\n\n");
            sb.append("### ANEXO CONTROLES CRÍTICOS\n\n");
            for (SeccionEditor cc : bloquesAnexo) {
                String titCc = cc.titulo() != null ? cc.titulo().trim() : "Control crítico";
                sb.append("#### ").append(titCc).append("\n\n");
                sb.append(cuerpoEstandarLegible(cc.cuerpo())).append("\n\n");
            }
        }

        sb.append("\n---\n*Fuente: «")
                .append(tituloDocumento != null ? tituloDocumento : "")
                .append("» — Editor de Contenido guardado en la plataforma.*");
        return sb.toString().trim();
    }

    private List<SeccionEditor> buscarBloquesAnexoRelacionados(
            List<SeccionEditor> seccionesAnexo, String filtroEstandar, String pregunta) {

        if (seccionesAnexo == null || seccionesAnexo.isEmpty()) {
            return List.of();
        }
        String fn = normalizar(filtroEstandar);
        String raiz = fn.endsWith("s") && fn.length() > 5 ? fn.substring(0, fn.length() - 1) : fn;

        List<SeccionEditor> out = new ArrayList<>();
        for (SeccionEditor s : seccionesAnexo) {
            String tit = normalizar(s.titulo());
            String cuerpo = normalizar(s.cuerpo() != null ? s.cuerpo() : "");
            boolean esCc = tit.contains("control critico") || tit.matches(".*\\bcc\\s*\\d+.*")
                    || tit.contains("criterios de calidad") || tit.contains("factor");
            if (!esCc) {
                continue;
            }
            if (cuerpo.contains(fn) || tit.contains(fn) || cuerpo.contains(raiz) || tit.contains(raiz)) {
                out.add(s);
            }
        }
        return out;
    }

    private static String inferirNombreLibro(String tituloDocumento) {
        if (tituloDocumento == null || tituloDocumento.isBlank()) {
            return "Libro estándares que salvan vidas";
        }
        if (tituloDocumento.toUpperCase(Locale.ROOT).contains("ESV")) {
            return "Libro estándares que salvan vidas";
        }
        return tituloDocumento;
    }

    private static String formatearSubtituloEstandar(String titulo) {
        if (titulo == null || titulo.isBlank()) {
            return "";
        }
        if (titulo.matches("(?i)^\\d+\\.?\\s+.+") || titulo.matches("(?i)^\\d+\\.\\d+.*")) {
            return "**" + titulo + "**";
        }
        return "### " + titulo;
    }

    /** Conserva numeración 1. OBJETIVO, 3.1.1, listas a/b/c legibles en el chat. */
    private static String cuerpoEstandarLegible(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        String s = texto.replace("\r\n", "\n").replace('\r', '\n');
        s = s.replaceAll("(?m)^(\\d+\\.\\s+[A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑ\\s]{2,})$", "\n\n**$1**\n");
        s = s.replaceAll("(?m)^(\\d+\\.\\d+\\.?\\s+)", "\n\n**$1** ");
        s = s.replaceAll("(?m)^([a-z]\\.\\s+)", "\n$1 ");
        s = s.replaceAll("[ \\t]+", " ");
        s = s.replaceAll(" *(\\n) *", "$1");
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }

    /** Respuesta literal del apartado (sin resumen IA). */
    public String formatearApartadoLiteral(String tituloDocumento, SeccionEditor sec) {
        String titulo = sec.titulo() != null ? sec.titulo().trim() : "Apartado";
        String cuerpo = cuerpoEstandarLegible(cuerpoLegible(sec.cuerpo()));
        if (titulo.toUpperCase(Locale.ROOT).contains("COMPROMISO")) {
            cuerpo = EstandarConsultaHelper.recortarTextoApartadoCompromiso(cuerpo);
        }
        if (cuerpo.isBlank()) {
            return "";
        }
        return ("## " + titulo + "\n\n" + cuerpo + "\n\n---\n*Texto del documento «"
                + (tituloDocumento != null ? tituloDocumento : "")
                + "» (Editor de Contenido).*").trim();
    }

    public Optional<SeccionEditor> buscarSeccionPorEstandar(List<SeccionEditor> secciones, String nombreEstandar) {
        if (secciones == null || nombreEstandar == null || nombreEstandar.isBlank()) {
            return Optional.empty();
        }
        String filtro = normalizar(nombreEstandar);

        SeccionEditor mejor = null;
        int mejorLen = 0;
        for (int i = 0; i < secciones.size(); i++) {
            if (!tituloCoincideEstandar(secciones.get(i).titulo(), filtro)) {
                continue;
            }
            Optional<SeccionEditor> bloque = fusionarSeccionEstandarDesde(secciones, i, filtro);
            if (bloque.isPresent() && bloque.get().cuerpo().length() > mejorLen) {
                mejorLen = bloque.get().cuerpo().length();
                mejor = bloque.get();
            }
        }
        return Optional.ofNullable(mejor);
    }

    private Optional<SeccionEditor> fusionarSeccionEstandarDesde(
            List<SeccionEditor> secciones, int indiceInicio, String filtro) {

        String tituloPrincipal = secciones.get(indiceInicio).titulo();
        String nivel = secciones.get(indiceInicio).nivel();
        StringBuilder cuerpoCompleto = new StringBuilder();

        for (int i = indiceInicio; i < secciones.size(); i++) {
            SeccionEditor s = secciones.get(i);
            String titNorm = normalizar(s.titulo());
            if (i > indiceInicio && (esInicioDeOtroEstandar(s.titulo(), filtro)
                    || titNorm.contains("anexo") || titNorm.contains("controles criticos"))) {
                break;
            }
            String cuerpo = s.cuerpo() != null ? s.cuerpo().trim() : "";
            if (cuerpo.isBlank()) {
                continue;
            }
            if (i > indiceInicio && tituloCoincideEstandar(s.titulo(), filtro) && cuerpo.length() < 150) {
                continue;
            }
            if (cuerpoCompleto.length() > 0) {
                cuerpoCompleto.append("\n\n");
            }
            if (i > indiceInicio && s.titulo() != null && !s.titulo().isBlank()) {
                cuerpoCompleto.append("\n\n").append(formatearSubtituloEstandar(s.titulo().trim())).append("\n\n");
            }
            cuerpoCompleto.append(cuerpoEstandarLegible(cuerpo));
        }

        if (cuerpoCompleto.length() < 50) {
            return Optional.empty();
        }
        return Optional.of(new SeccionEditor(
                nivel,
                tituloPrincipal,
                truncar(cuerpoCompleto.toString())));
    }

    private static boolean tituloCoincideEstandar(String titulo, String filtro) {
        if (titulo == null || filtro == null) {
            return false;
        }
        String tituloNorm = normalizar(titulo);
        String tit = normalizar(titulo.replaceFirst("(?i)^est[aá]ndar\\s+de\\s+", ""));
        String sinNumero = tituloNorm.replaceFirst("^\\d{1,2}\\s*[.)]?\\s*", "");
        return EstandarConsultaHelper.coincideNombreEstandar(tit, filtro)
                || EstandarConsultaHelper.coincideNombreEstandar(tituloNorm, filtro)
                || EstandarConsultaHelper.coincideNombreEstandar(sinNumero, filtro)
                || tituloNorm.contains(filtro)
                || sinNumero.contains(filtro);
    }

    private static boolean esInicioDeOtroEstandar(String titulo, String filtroActual) {
        if (titulo == null) {
            return false;
        }
        String t = normalizar(titulo);
        if (!t.contains("estandar de")) {
            return false;
        }
        return !tituloCoincideEstandar(titulo, filtroActual);
    }

    private static final int MAX_CUERPO_SECCION_CONSULTA = 5_500;

    public String truncarCuerpoParaConsulta(String cuerpo) {
        if (cuerpo == null || cuerpo.length() <= MAX_CUERPO_SECCION_CONSULTA) {
            return cuerpo != null ? cuerpo : "";
        }
        return cuerpo.substring(0, MAX_CUERPO_SECCION_CONSULTA).trim()
                + "\n\n… *(sección acortada para la consulta; el documento completo está en el Editor de Contenido.)*";
    }

    public String formatearBloqueEditorParaPrompt(
            String tituloDocumento, List<SeccionEditor> secciones, boolean soloRelevantes) {

        if (secciones == null || secciones.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════════\n");
        sb.append("CONTENIDO EDITOR ESTRUCTURADO (fuente prioritaria — HTML guardado en plataforma)\n");
        sb.append("Documento: «").append(tituloDocumento != null ? tituloDocumento : "").append("»\n");
        if (soloRelevantes) {
            sb.append("(Secciones filtradas según la pregunta; el resto del documento también está indexado.)\n");
        }
        sb.append("═══════════════════════════════════════════════════════\n\n");

        for (SeccionEditor s : secciones) {
            sb.append("### ").append(s.titulo()).append("\n\n");
            sb.append(s.cuerpo()).append("\n\n");
        }
        sb.append("═══════════════════════════════════════════════════════\n");
        return sb.toString();
    }

    private static boolean esLineaIndicePdf(String titulo) {
        if (titulo == null) {
            return false;
        }
        String t = titulo.trim();
        return t.matches("(?i)^\\d+\\.?\\s*EST[AÁ]NDAR\\s+DE\\s+.+\\s+\\d{1,3}$") && t.length() < 95;
    }

    private static LinkedHashSet<String> terminosPregunta(String pregunta) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String n = normalizar(pregunta);
        Matcher m = Pattern.compile("[a-z0-9]{4,}").matcher(n);
        while (m.find()) {
            terms.add(m.group());
        }
        return terms;
    }

    private static String limpiarHtml(String html) {
        return cuerpoLegible(html);
    }

    /** Conserva párrafos (no colapsa todo en una línea). */
    private static String cuerpoLegible(String html) {
        if (html == null) {
            return "";
        }
        String s = html;
        s = s.replaceAll("(?is)</p>\\s*<p>", "\n\n");
        s = s.replaceAll("(?is)<br\\s*/?>", "\n");
        s = s.replaceAll("(?is)<li>", "\n- ");
        s = s.replaceAll("(?is)</li>", "");
        s = s.replaceAll("(?is)<[^>]+>", "");
        s = s.replace("&nbsp;", " ").replace("&amp;", "&");
        s = s.replaceAll("[ \\t]+", " ");
        s = s.replaceAll(" *(\\n) *", "$1");
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }

    private static String truncar(String s) {
        if (s == null || s.length() <= MAX_CARACTERES_SECCION) {
            return s != null ? s : "";
        }
        return s.substring(0, MAX_CARACTERES_SECCION) + "\n… *(sección truncada por límite técnico)*";
    }

    private static String normalizar(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }
}
