package com.fiscalizacionhse.service;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detecta el estándar pedido en una pregunta y localiza su sección real en el PDF
 * (evitando el índice/tabla de contenidos al inicio del libro).
 */
final class EstandarConsultaHelper {

    private static final Pattern PATRON_ESTANDAR_EN_PREGUNTA = Pattern.compile(
            "(?i)(?:est[aá]ndar\\s+de\\s+|del\\s+est[aá]ndar\\s+de\\s+)([a-z0-9\\s\\-]{4,}?)"
                    + "(?:\\s*(?:criterio|aspecto|calidad|cc\\s*\\d|$)|\\s*$)");

    /** «10. Estándar de Excavaciones» o «10 excavaciones». */
    private static final Pattern PATRON_ESTANDAR_NUMERADO = Pattern.compile(
            "(?i)(?:\\b\\d{1,2}\\s*[.)]?\\s*)?(?:est[aá]ndar\\s+de\\s+)?([a-z][a-z0-9\\s\\-]{3,})");

    private static final Pattern PATRON_TITULO_SECCION_MAYUS = Pattern.compile(
            "(?m)^\\s*((?:\\d{1,2}\\s*[.)]?\\s*)?[A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑ0-9\\s\\-]{6,})\\s*$");

    private static final Pattern PATRON_FIN_SECCION_MAYUS = Pattern.compile(
            "(?m)^\\s*(?:EST[AÁ]NDAR\\s+DE\\s+|OPERACIONES\\s+DE\\s+|TRABAJO\\s+EN\\s+|TRABAJOS\\s+EN\\s+"
                    + "|DEFINICIONES\\s+GENERALES|CONTROL\\s+DE\\s+TRABAJO|PELIGRO\\s+\\d+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern PATRON_CC_EN_PREGUNTA = Pattern.compile(
            "(?i)\\bCC\\s*(\\d{1,2})\\b");

    private static final Pattern PATRON_LINEA_ESTANDAR = Pattern.compile(
            "(?m)^((?:\\d{1,2}\\s*[.)]?\\s*)?EST[AÁ]NDAR\\s+(?:\\d{1,2}\\s+)?DE\\s+.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern PATRON_INDICE = Pattern.compile(
            "(?i)^EST[AÁ]NDAR\\s+DE\\s+.+\\s+\\d{1,3}\\s*$");

    /** Índice del libro: «10. ESTÁNDAR DE EXCAVACIONES» (sin párrafos). */
    private static final Pattern PATRON_INDICE_NUMERADO = Pattern.compile(
            "(?im)^\\s*(\\d{1,2})\\s*[.)]?\\s*EST[AÁ]NDAR\\s+DE\\s+([A-ZÁÉÍÓÚÑ0-9\\s\\-]{4,}?)(?:\\s+\\d{1,3})?\\s*$");

    public record IndiceEstandar(int numero, String nombre) {}

    /** Índice oficial ESV (saludo y referencia cuando el PDF no trae numeración completa). */
    static final List<IndiceEstandar> INDICE_ESV_OFICIAL = List.of(
            new IndiceEstandar(1, "CONTROL DE TRABAJO"),
            new IndiceEstandar(2, "CONTROL DE ATMÓSFERAS PELIGROSAS"),
            new IndiceEstandar(3, "TRABAJO EN CALIENTE"),
            new IndiceEstandar(4, "TRABAJO EN ESPACIOS CONFINADOS"),
            new IndiceEstandar(5, "CONDUCCIÓN SEGURA"),
            new IndiceEstandar(6, "AISLAMIENTO Y BLOQUEO DE ENERGÍAS"),
            new IndiceEstandar(7, "APERTURA DE LÍNEAS Y EQUIPOS DE PROCESO"),
            new IndiceEstandar(8, "TRABAJOS ELÉCTRICOS EN MEDIA Y ALTA TENSIÓN"),
            new IndiceEstandar(9, "TRABAJO EN ALTURA"),
            new IndiceEstandar(10, "EXCAVACIONES"),
            new IndiceEstandar(11, "OPERACIONES DE LEVANTE"),
            new IndiceEstandar(12, "LIMPIEZA Y PRUEBAS CON PRESIÓN EN LÍNEAS Y EQUIPOS"),
            new IndiceEstandar(13, "TRANSPORTE DE CARGA"),
            new IndiceEstandar(14, "DEFINICIONES GENERALES")
    );

    /** Une índices detectados en los libros cargados; si falta, usa referencia ESV. */
    static List<IndiceEstandar> indiceUnificadoParaSaludo(Collection<List<IndiceEstandar>> porLibro) {
        TreeMap<Integer, IndiceEstandar> map = new TreeMap<>();
        if (porLibro != null) {
            for (List<IndiceEstandar> lista : porLibro) {
                if (lista == null) {
                    continue;
                }
                for (IndiceEstandar item : lista) {
                    if (item.numero() >= 1 && item.numero() <= 20) {
                        map.put(item.numero(), item);
                    }
                }
            }
        }
        if (map.isEmpty()) {
            return new ArrayList<>(INDICE_ESV_OFICIAL);
        }
        if (!map.containsKey(14)) {
            map.put(14, new IndiceEstandar(14, "DEFINICIONES GENERALES"));
        }
        return new ArrayList<>(map.values());
    }

    /** Línea de lista numerada para Markdown: «1. **ESTÁNDAR DE …**». */
    static String lineaIndiceMarkdown(IndiceEstandar item) {
        if (item.numero() == 14) {
            return "14. **DEFINICIONES GENERALES**";
        }
        String nombre = item.nombre() != null ? item.nombre().trim().toUpperCase(Locale.ROOT) : "";
        if (!nombre.startsWith("ESTÁNDAR") && !nombre.startsWith("ESTANDAR")) {
            nombre = "ESTÁNDAR DE " + nombre;
        }
        return item.numero() + ". **" + nombre + "**";
    }

    private static final Map<String, List<String>> ALIAS_ESTANDARES = Map.ofEntries(
            Map.entry("transporte de carga", List.of("transporte de carga", "transporte carga")),
            Map.entry("excavaciones", List.of("excavacion", "excavaciones")),
            Map.entry("trabajo en caliente", List.of("trabajo en caliente", "caliente")),
            Map.entry("espacios confinados", List.of("espacios confinados", "confinado", "confinados")),
            Map.entry("conduccion segura", List.of("conduccion segura", "conduccion")),
            Map.entry("trabajo en altura", List.of("trabajo en altura", "altura")),
            Map.entry("aislamiento y bloqueo", List.of("aislamiento", "bloqueo de energia", "bloqueo")),
            Map.entry("operaciones de levante", List.of("operaciones de levante", "levante")),
            Map.entry("lineas y equipos", List.of("lineas y equipos", "presion en lineas")),
            Map.entry("atmósferas peligrosas", List.of("atmosferas peligrosas", "atmosfera peligrosa")),
            Map.entry("manejo de productos quimicos", List.of("productos quimicos", "quimicos")),
            Map.entry("seguridad de procesos", List.of("seguridad de procesos", "procesos seguros")),
            Map.entry("radiaciones ionizantes", List.of("radiaciones", "radiacion ionizante")),
            Map.entry("fauna silvestre", List.of("fauna silvestre", "medio ambiente")),
            Map.entry("trabajos subacuaticos", List.of("subacuatico", "subacuaticos")),
            Map.entry("perforacion", List.of("perforacion", "perforaciones"))
    );

    /** Referencia ESV ENAP: complementa el índice si la extracción del PDF no trae todos los títulos. */
    private static final List<String> CATALOGO_ESV_ENAP = List.of(
            "ESTÁNDAR DE TRABAJO EN CALIENTE",
            "ESTÁNDAR DE TRABAJO EN ALTURA",
            "ESTÁNDAR DE ESPACIOS CONFINADOS",
            "ESTÁNDAR DE AISLAMIENTO Y BLOQUEO DE ENERGÍAS",
            "ESTÁNDAR DE EXCAVACIONES",
            "ESTÁNDAR DE TRANSPORTE DE CARGA",
            "ESTÁNDAR DE OPERACIONES DE LEVANTE",
            "ESTÁNDAR DE CONDUCCIÓN SEGURA",
            "ESTÁNDAR DE LÍNEAS Y EQUIPOS",
            "ESTÁNDAR DE ATMÓSFERAS PELIGROSAS",
            "ESTÁNDAR DE MANEJO DE PRODUCTOS QUÍMICOS",
            "ESTÁNDAR DE SEGURIDAD DE PROCESOS",
            "ESTÁNDAR DE RADIACIONES IONIZANTES",
            "ESTÁNDAR DE FAUNA SILVESTRE Y MEDIO AMBIENTE",
            "ESTÁNDAR DE TRABAJOS SUBACUÁTICOS",
            "ESTÁNDAR DE PERFORACIÓN"
    );

    private EstandarConsultaHelper() {}

    static Optional<String> detectarEstandarEnPregunta(String pregunta) {
        if (pregunta == null || pregunta.isBlank()) {
            return Optional.empty();
        }
        String n = normalizar(recortarRuidoTituloDocumentoEnPregunta(pregunta));

        for (Map.Entry<String, List<String>> entry : ALIAS_ESTANDARES.entrySet()) {
            for (String alias : entry.getValue()) {
                if (n.contains(normalizar(alias))) {
                    return Optional.of(entry.getKey());
                }
            }
        }

        Matcher m = PATRON_ESTANDAR_EN_PREGUNTA.matcher(n);
        if (m.find()) {
            String candidato = limpiarNombreEstandar(m.group(1));
            if (candidato.length() >= 4) {
                return Optional.of(candidato);
            }
        }

        if (n.matches("(?s).*\\b\\d{1,2}\\s*[.)]?\\s*est[aá]ndar.*")) {
            Matcher num = PATRON_ESTANDAR_NUMERADO.matcher(n);
            while (num.find()) {
                String candidato = limpiarNombreEstandar(num.group(1));
                if (candidato.length() >= 4 && !candidato.matches("(?i)^(de|del|el|la|los|las)$")) {
                    return Optional.of(candidato);
                }
            }
        }

        if (n.contains("carga pesada") || (n.contains("transporte") && n.contains("carga"))) {
            return Optional.of("transporte de carga");
        }

        Matcher plural = Pattern.compile("est[aá]ndares?\\s+(?:de\\s+)?([a-z0-9\\s\\-]{4,})").matcher(n);
        if (plural.find()) {
            String candidato = plural.group(1).trim();
            if (candidato.length() >= 4) {
                return Optional.of(limpiarNombreEstandar(candidato));
            }
        }

        return Optional.empty();
    }

    static Optional<String> detectarCcEnPregunta(String pregunta) {
        if (pregunta == null) {
            return Optional.empty();
        }
        Matcher m = PATRON_CC_EN_PREGUNTA.matcher(pregunta);
        if (m.find()) {
            return Optional.of("CC" + m.group(1));
        }
        return Optional.empty();
    }

    /** Qué fragmento del libro debe transcribirse (solo eso, ni más ni menos). */
    enum TipoConsultaEstandar {
        /** Un solo CC: descripción + criterios + factores de ese control. */
        CC_ESPECIFICO,
        /** CC1…CCn del estándar. */
        ESTANDAR_COMPLETO,
        /** Solo bloques «Criterios de Calidad». */
        CRITERIOS_CALIDAD
    }

    record ConsultaLibro(
            TipoConsultaEstandar tipo,
            Optional<String> nombreEstandar,
            Optional<String> codigoCc) {}

    /**
     * Interpreta la pregunta: ¿un CC?, ¿todo el estándar?, ¿solo criterios de calidad?
     */
    static Optional<ConsultaLibro> clasificarConsultaLibro(String pregunta) {
        if (pregunta == null || pregunta.isBlank()) {
            return Optional.empty();
        }
        Optional<String> estandar = detectarEstandarEnPregunta(pregunta);
        Optional<String> cc = detectarCcEnPregunta(pregunta);
        String n = normalizar(pregunta);

        if (estandar.isEmpty() && cc.isEmpty()) {
            return Optional.empty();
        }

        boolean pideCriterios = (n.contains("criterio") || n.contains("aspecto")) && n.contains("calidad");
        if (pideCriterios) {
            return Optional.of(new ConsultaLibro(TipoConsultaEstandar.CRITERIOS_CALIDAD, estandar, cc));
        }
        if (cc.isPresent()) {
            return Optional.of(new ConsultaLibro(TipoConsultaEstandar.CC_ESPECIFICO, estandar, cc));
        }
        if (estandar.isPresent()) {
            return Optional.of(new ConsultaLibro(TipoConsultaEstandar.ESTANDAR_COMPLETO, estandar, Optional.empty()));
        }
        return Optional.empty();
    }

    /** @deprecated usar {@link #clasificarConsultaLibro(String)} */
    static Optional<TipoConsultaEstandar> clasificarConsultaEstandar(String pregunta) {
        return clasificarConsultaLibro(pregunta).map(ConsultaLibro::tipo);
    }

    static boolean pideEstandarCompleto(String pregunta) {
        return clasificarConsultaLibro(pregunta)
                .map(c -> c.tipo() == TipoConsultaEstandar.ESTANDAR_COMPLETO)
                .orElse(false);
    }

    static boolean pideSoloCriteriosCalidad(String pregunta) {
        return clasificarConsultaLibro(pregunta)
                .map(c -> c.tipo() == TipoConsultaEstandar.CRITERIOS_CALIDAD)
                .orElse(false);
    }

    static boolean pideCcEspecifico(String pregunta) {
        return clasificarConsultaLibro(pregunta)
                .map(c -> c.tipo() == TipoConsultaEstandar.CC_ESPECIFICO)
                .orElse(false);
    }

    /** Pide resumen breve (no transcripción literal del apartado). */
    static boolean pideResumenExplicito(String pregunta) {
        if (pregunta == null || pregunta.isBlank()) {
            return false;
        }
        String n = normalizar(pregunta);
        return n.contains("resumen") || n.contains("resume") || n.contains("resumir")
                || n.contains("resumeme") || n.contains("sintesis");
    }

    /** Pide texto literal del apartado o sección (sin pasar por RAG ni resumen largo). */
    static boolean pideTextoLiteral(String pregunta) {
        if (pregunta == null || pregunta.isBlank()) {
            return false;
        }
        if (pideResumenExplicito(pregunta)) {
            return false;
        }
        String n = normalizar(pregunta);
        if (n.contains("texto completo") || n.contains("texto exacto") || n.contains("texto literal")
                || n.contains("texto especifico") || n.contains("completo especifico")
                || n.contains("literal") || n.contains("transcri") || n.contains("copia el")
                || n.contains("dime el texto") || n.contains("que dice exactamente")) {
            return true;
        }
        return n.contains("hablame") || n.contains("habla me") || n.contains("cuentame")
                || n.contains("cuenta me") || n.contains("dime el apartado");
    }

    /** Apartado temático (p. ej. Compromiso): transcripción literal salvo que pida resumen explícito. */
    static boolean debeUsarModoLiteralApartado(String pregunta) {
        if (!esConsultaApartadoTematico(pregunta)) {
            return false;
        }
        return !pideResumenExplicito(pregunta);
    }

    /**
     * Consultas que pueden responder desde editor/BD sin indexar ni búsqueda RAG.
     */
    static boolean consultaDirectaSinRag(String pregunta) {
        if (pregunta == null || pregunta.isBlank()) {
            return false;
        }
        if (esConsultaApartadoTematico(pregunta)) {
            return debeUsarModoLiteralApartado(pregunta) || pideResumenExplicito(pregunta);
        }
        if (detectarEstandarEnPregunta(pregunta).isPresent()) {
            return pideTextoLiteral(pregunta) || pideResumenExplicito(pregunta)
                    || esConsultaResumenInformal(pregunta);
        }
        return false;
    }

    /**
     * Pregunta abierta tipo «háblame de…», «qué es…» sin pedir listado completo ni CC literal.
     */
    static boolean esConsultaResumenInformal(String pregunta) {
        if (pregunta == null || pregunta.isBlank()) {
            return false;
        }
        String n = normalizar(pregunta);
        if (esConsultaApartadoTematico(pregunta)) {
            if (pideTextoLiteral(pregunta)) {
                return false;
            }
            return pideResumenExplicito(pregunta)
                    || n.contains("explicame") || n.contains("explica me");
        }
        if (pideTextoLiteral(pregunta) && detectarEstandarEnPregunta(pregunta).isPresent()) {
            return false;
        }
        if (n.contains("todos") || n.contains("todas") || n.contains("completo") || n.contains("completa")
                || n.contains("listado") || n.contains("enumera") || n.contains("cc1") || n.contains("cc 1")
                || n.contains("cada uno") || n.contains("uno por uno") || n.contains("transcri")
                || n.contains("literal") || n.contains("texto exacto") || n.contains("texto completo")) {
            return false;
        }
        return n.contains("hablame") || n.contains("habla me") || n.contains("cuentame") || n.contains("cuenta me")
                || n.contains("explicame") || n.contains("explica me") || n.contains("que es ")
                || n.contains("que hay ") || n.contains("informacion sobre") || n.contains("informame")
                || n.contains("resumen") || n.contains("resume") || n.contains("dime sobre")
                || n.contains("que dice") || n.contains("de que trata") || n.contains("que trata")
                || n.contains("hablar de") || n.contains("sobre el estandar") || n.contains("sobre el standard")
                || n.contains("este estandar") || n.contains("este standard")
                || n.contains("de este estandar") || n.contains("de este standard");
    }

    /**
     * Posición del inicio del contenido real del estándar, o -1 si no se encuentra.
     */
    static int localizarSeccionEstandar(String textoCompleto, String nombreEstandar) {
        if (textoCompleto == null || nombreEstandar == null || nombreEstandar.isBlank()) {
            return -1;
        }

        String filtroNorm = normalizar(nombreEstandar);
        List<int[]> candidatos = new ArrayList<>();

        Matcher m = PATRON_LINEA_ESTANDAR.matcher(textoCompleto);
        while (m.find()) {
            String tituloLinea = normalizar(m.group(1).replaceFirst("(?i)^est[aá]ndar\\s+de\\s+", ""));
            if (!coincideNombreEstandar(tituloLinea, filtroNorm)) {
                continue;
            }
            int pos = m.start();
            String ventana = textoCompleto.substring(pos, Math.min(textoCompleto.length(), pos + 4000));
            if (esBloqueIndice(ventana)) {
                continue;
            }
            int score = puntuarSeccion(ventana);
            candidatos.add(new int[]{pos, score});
        }

        if (candidatos.isEmpty()) {
            candidatos.addAll(buscarSeccionEstandarMultilinea(textoCompleto, filtroNorm));
        }
        if (candidatos.isEmpty()) {
            candidatos.addAll(buscarSeccionPorTituloMayusculas(textoCompleto, filtroNorm));
        }

        if (candidatos.isEmpty()) {
            return -1;
        }

        candidatos.sort((a, b) -> Integer.compare(b[1], a[1]));
        return candidatos.get(0)[0];
    }

    private static List<int[]> buscarSeccionEstandarMultilinea(String texto, String filtroNorm) {
        List<int[]> out = new ArrayList<>();
        String token = tokensSignificativos(filtroNorm).stream().findFirst().orElse(filtroNorm);
        if (token.length() < 4) {
            return out;
        }
        Pattern flex = Pattern.compile(
                "(?is)EST[AÁ]NDAR\\s+DE\\s+(?:[^\\n]{0,80}\\n\\s*){0,4}[^\\n]*"
                        + Pattern.quote(token.substring(0, Math.min(token.length(), 8))),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher fm = flex.matcher(texto);
        while (fm.find()) {
            int pos = fm.start();
            String ventana = texto.substring(pos, Math.min(texto.length(), pos + 4000));
            if (esBloqueIndice(ventana)) {
                continue;
            }
            out.add(new int[]{pos, puntuarSeccion(ventana)});
        }
        return out;
    }

    static int encontrarFinSeccionEstandar(String textoCompleto, int inicio, String nombreEstandar) {
        String filtroNorm = normalizar(nombreEstandar);

        Pattern sigNum = Pattern.compile(
                "(?im)^\\s*(\\d{1,2})\\s*[.)]?\\s+EST[AÁ]NDAR\\s+(?:\\d{1,2}\\s+)?DE\\s+.+$",
                Pattern.UNICODE_CASE);
        Matcher sn = sigNum.matcher(textoCompleto);
        while (sn.find()) {
            if (sn.start() <= inicio + 150) {
                continue;
            }
            String linea = sn.group().trim();
            String tituloLinea = normalizar(linea.replaceFirst("(?i)^\\d{1,2}\\s*[.)]?\\s*", "")
                    .replaceFirst("(?i)^est[aá]ndar\\s+(?:\\d{1,2}\\s+)?de\\s+", ""));
            if (!coincideNombreEstandar(tituloLinea, filtroNorm)) {
                return sn.start();
            }
        }

        Matcher m = PATRON_LINEA_ESTANDAR.matcher(textoCompleto);
        while (m.find()) {
            if (m.start() <= inicio + 200) {
                continue;
            }
            String linea = m.group(1).trim();
            if (PATRON_INDICE.matcher(linea).matches()) {
                continue;
            }
            String tituloLinea = normalizar(linea.replaceFirst("(?i)^est[aá]ndar\\s+(?:\\d{1,2}\\s+)?de\\s+", ""));
            if (coincideNombreEstandar(tituloLinea, filtroNorm)) {
                continue;
            }
            return m.start();
        }
        int finMayus = encontrarFinPorTituloMayusculas(textoCompleto, inicio, filtroNorm);
        if (finMayus > inicio + 400) {
            return finMayus;
        }
        return textoCompleto.length();
    }

    private static List<int[]> buscarSeccionPorTituloMayusculas(String texto, String filtroNorm) {
        List<int[]> out = new ArrayList<>();
        Matcher tm = PATRON_TITULO_SECCION_MAYUS.matcher(texto);
        while (tm.find()) {
            String titulo = normalizar(tm.group(1).replaceFirst("(?i)^\\d{1,2}\\s*[.)]?\\s*", ""));
            if (!coincideNombreEstandar(titulo, filtroNorm)) {
                continue;
            }
            int pos = tm.start();
            String ventana = texto.substring(pos, Math.min(texto.length(), pos + 4000));
            if (esBloqueIndice(ventana)) {
                continue;
            }
            out.add(new int[]{pos, puntuarSeccion(ventana) + 25});
        }
        return out;
    }

    private static int encontrarFinPorTituloMayusculas(String texto, int inicio, String filtroNorm) {
        Matcher tm = PATRON_TITULO_SECCION_MAYUS.matcher(texto);
        while (tm.find()) {
            if (tm.start() <= inicio + 300) {
                continue;
            }
            String titulo = normalizar(tm.group(1).replaceFirst("(?i)^\\d{1,2}\\s*[.)]?\\s*", ""));
            if (coincideNombreEstandar(titulo, filtroNorm)) {
                continue;
            }
            if (titulo.length() >= 6) {
                return tm.start();
            }
        }
        Matcher fm = PATRON_FIN_SECCION_MAYUS.matcher(texto);
        while (fm.find()) {
            if (fm.start() > inicio + 500) {
                return fm.start();
            }
        }
        return -1;
    }

    static boolean coincideNombreEstandar(String tituloNorm, String filtroNorm) {
        if (tituloNorm == null || filtroNorm == null) {
            return false;
        }
        String t = tituloNorm.replaceAll("\\s+\\d{1,3}$", "").trim();
        String f = filtroNorm.trim();
        if (t.contains(f) || f.contains(t)) {
            return true;
        }
        Set<String> tokensF = tokensSignificativos(f);
        Set<String> tokensT = tokensSignificativos(t);
        if (tokensF.isEmpty() || tokensT.isEmpty()) {
            return false;
        }
        long comunes = tokensF.stream().filter(tokensT::contains).count();
        return comunes >= Math.max(1, Math.min(tokensF.size(), tokensT.size()) - 1);
    }

    private static boolean esBloqueIndice(String ventana) {
        if (ventana == null || ventana.isBlank()) {
            return true;
        }
        if (contieneCuerpoEstandarReal(ventana)) {
            return false;
        }
        int lineasEstandar = 0;
        int lineasIndice = 0;
        for (String raw : ventana.split("\\R")) {
            String linea = raw.trim();
            if (linea.matches("(?i).*(?:\\d+\\.\\s*)?EST[AÁ]NDAR\\s+(?:\\d+\\s+)?DE\\s+.*")) {
                lineasEstandar++;
                if (linea.length() < 95 && !linea.toLowerCase(Locale.ROOT).contains("objetivo")) {
                    lineasIndice++;
                }
            }
        }
        if (lineasEstandar >= 2 && lineasIndice >= 2) {
            return true;
        }
        if (lineasEstandar >= 2 && !ventana.toLowerCase(Locale.ROOT).contains("objetivo")) {
            return true;
        }
        String compacto = ventana.replaceAll("\\s+", "");
        return lineasEstandar >= 3
                && !ventana.toLowerCase(Locale.ROOT).contains("criterios de calidad")
                && !ventana.toLowerCase(Locale.ROOT).contains("control critico")
                && compacto.length() < 800;
    }

    /** Capítulo real del estándar (no lista del índice ni introducción del libro). */
    static boolean contieneCuerpoEstandarReal(String ventana) {
        if (ventana == null || ventana.isBlank()) {
            return false;
        }
        String v = ventana.toLowerCase(Locale.ROOT);
        if (v.contains("compromiso de la alta direccion")) {
            return false;
        }
        if (v.contains("introduccion y contexto") && !v.contains("3.1")) {
            return false;
        }
        boolean tieneObjetivo = v.contains("1. objetivo") || v.contains("1 objetivo");
        boolean tieneControles = v.contains("3. controles") || v.contains("3.1.")
                || v.contains("3.1.1");
        boolean tieneAlcance = v.contains("2. alcance") || v.contains("2 alcance");
        return (tieneObjetivo && (tieneControles || tieneAlcance || ventana.length() > 2500))
                || (tieneControles && ventana.length() > 1500);
    }

    /**
     * Extrae el bloque del estándar en texto plano (evita índice y elige la ocurrencia con cuerpo real).
     */
    static Optional<String> extraerBloqueEstandar(String textoPlano, String nombreEstandar) {
        if (textoPlano == null || textoPlano.isBlank() || nombreEstandar == null) {
            return Optional.empty();
        }
        String filtro = normalizar(nombreEstandar);
        List<int[]> candidatos = new ArrayList<>();

        Pattern patronCapitulo = Pattern.compile(
                "(?im)^\\s*(?:\\d{1,2}\\s*[.)]?\\s*)?EST[AÁ]NDAR\\s+(?:\\d{1,2}\\s+)?DE\\s+[^\\n]+$",
                Pattern.UNICODE_CASE);
        Matcher m = patronCapitulo.matcher(textoPlano);
        while (m.find()) {
            String linea = m.group().trim();
            String tituloNorm = normalizar(linea.replaceFirst("(?i)^\\d{1,2}\\s*[.)]?\\s*", "")
                    .replaceFirst("(?i)^est[aá]ndar\\s+(?:\\d{1,2}\\s+)?de\\s+", ""));
            if (!coincideNombreEstandar(tituloNorm, filtro)
                    && !normalizar(linea).contains(filtro)) {
                continue;
            }
            int pos = m.start();
            int fin = encontrarFinSeccionEstandar(textoPlano, pos, nombreEstandar);
            if (fin <= pos + 100) {
                fin = Math.min(textoPlano.length(), pos + 120_000);
            }
            String bloque = textoPlano.substring(pos, fin).trim();
            if (bloque.length() < 200 || esBloqueIndice(bloque.substring(0, Math.min(bloque.length(), 5000)))) {
                continue;
            }
            if (!contieneCuerpoEstandarReal(bloque.substring(0, Math.min(bloque.length(), 12_000)))) {
                continue;
            }
            candidatos.add(new int[]{pos, fin, puntuarSeccion(bloque.substring(0, Math.min(bloque.length(), 8000)))});
        }

        if (candidatos.isEmpty()) {
            int pos = localizarSeccionEstandar(textoPlano, nombreEstandar);
            if (pos < 0) {
                return Optional.empty();
            }
            int fin = encontrarFinSeccionEstandar(textoPlano, pos, nombreEstandar);
            String bloque = textoPlano.substring(pos, Math.min(fin > pos ? fin : textoPlano.length(), textoPlano.length())).trim();
            if (bloque.length() >= 200 && contieneCuerpoEstandarReal(bloque.substring(0, Math.min(bloque.length(), 12_000)))) {
                return Optional.of(bloque);
            }
            return Optional.empty();
        }

        candidatos.sort((a, b) -> Integer.compare(b[2], a[2]));
        int[] mejor = candidatos.get(0);
        return Optional.of(textoPlano.substring(mejor[0], mejor[1]).trim());
    }

    private static int puntuarSeccion(String ventana) {
        String v = ventana.toLowerCase(Locale.ROOT);
        int score = 0;
        if (v.contains("1. objetivo") || v.contains("1 objetivo")) {
            score += 200;
        }
        if (v.contains("2. alcance")) {
            score += 120;
        }
        if (v.contains("3. controles") || v.contains("3.1.")) {
            score += 150;
        }
        if (v.contains("criterios de calidad")) {
            score += 50;
        }
        if (v.contains("control critico") || v.contains("control crítico")) {
            score += 40;
        }
        if (v.contains("descripcion del control")) {
            score += 30;
        }
        if (v.contains("factores escaladores")) {
            score += 20;
        }
        if (v.contains("compromiso de la alta direccion")) {
            score -= 300;
        }
        if (v.contains("introduccion y contexto") && !v.contains("3.1")) {
            score -= 150;
        }
        score += Math.min(ventana.length() / 400, 80);
        return score;
    }

    /** Quita título de PDF pegado en la pregunta («…caliente ENAP - Libro1 ESV…»). */
    static String recortarRuidoTituloDocumentoEnPregunta(String pregunta) {
        if (pregunta == null) {
            return "";
        }
        String p = pregunta;
        String lower = p.toLowerCase(Locale.ROOT);
        String[] cortes = {
                " enap", " libro", " libros", " v3 ", " v2 ", " v1 ",
                " estandares que salvan", " estándares que salvan",
                " documento ", " pdf ", " - enap", " — enap"
        };
        int corte = p.length();
        for (String marca : cortes) {
            int idx = lower.indexOf(marca);
            if (idx > 12 && idx < corte) {
                corte = idx;
            }
        }
        return p.substring(0, corte).trim();
    }

    private static String limpiarNombreEstandar(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replaceAll("\\s+", " ").trim();
        String lower = s.toLowerCase(Locale.ROOT);
        String[] cortes = {" enap", " libro", " v3", " v2", " estandares", " estándares", " esv"};
        for (String marca : cortes) {
            int idx = lower.indexOf(marca);
            if (idx > 4) {
                s = s.substring(0, idx).trim();
                lower = s.toLowerCase(Locale.ROOT);
            }
        }
        return s;
    }

    private static Set<String> tokensSignificativos(String s) {
        Set<String> stop = Set.of("de", "del", "la", "el", "en", "y", "o", "a", "con", "para", "estandar");
        Set<String> out = new LinkedHashSet<>();
        for (String t : s.split("\\s+")) {
            if (t.length() >= 3 && !stop.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    static String normalizar(String s) {
        if (s == null) {
            return "";
        }
        String base = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return base.replaceAll("\\p{M}+", "");
    }

    /**
     * Escanea texto de PDF y devuelve títulos «ESTÁNDAR DE …» (sin líneas de índice con solo número de página).
     */
    static List<String> extraerTitulosEstandaresEnTexto(String textoCompleto) {
        if (textoCompleto == null || textoCompleto.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> unicos = new LinkedHashSet<>();
        List<IndiceEstandar> indice = extraerIndiceEstandaresEnTexto(textoCompleto);
        for (IndiceEstandar item : indice) {
            unicos.add("ESTÁNDAR DE " + item.nombre().toUpperCase(Locale.ROOT));
        }
        if (!unicos.isEmpty()) {
            return new ArrayList<>(unicos);
        }

        Matcher m = PATRON_LINEA_ESTANDAR.matcher(textoCompleto);
        while (m.find()) {
            String line = m.group(1).trim().replaceAll("\\s+", " ");
            if (!esTituloEstandarValido(line)) {
                continue;
            }
            String cleaned = line.replaceAll("(?i)^est[aá]ndar\\s+de\\s+", "")
                    .replaceAll("\\s+\\d{1,3}\\s*$", "")
                    .trim();
            unicos.add("ESTÁNDAR DE " + cleaned.toUpperCase(Locale.ROOT));
        }
        return new ArrayList<>(unicos);
    }

    /**
     * Solo entradas del índice numerado del libro (1…15), sin párrafos ni menciones sueltas.
     */
    static List<IndiceEstandar> extraerIndiceEstandaresEnTexto(String textoCompleto) {
        if (textoCompleto == null || textoCompleto.isBlank()) {
            return List.of();
        }
        Map<Integer, IndiceEstandar> porNumero = new TreeMap<>();
        Matcher m = PATRON_INDICE_NUMERADO.matcher(textoCompleto);
        while (m.find()) {
            int num = Integer.parseInt(m.group(1));
            String nombre = limpiarNombreIndice(m.group(2));
            if (nombre.length() < 4) {
                continue;
            }
            porNumero.putIfAbsent(num, new IndiceEstandar(num, nombre));
        }
        return new ArrayList<>(porNumero.values());
    }

    static List<IndiceEstandar> extraerIndiceDesdeTitulosEditor(Collection<String> titulosSeccion) {
        if (titulosSeccion == null) {
            return List.of();
        }
        Map<Integer, IndiceEstandar> porNumero = new TreeMap<>();
        Pattern p = Pattern.compile(
                "(?i)^\\s*(\\d{1,2})\\s*[.)]?\\s*EST[AÁ]NDAR\\s+DE\\s+(.+)$");
        for (String titulo : titulosSeccion) {
            if (titulo == null) {
                continue;
            }
            Matcher m = p.matcher(titulo.trim());
            if (!m.find()) {
                continue;
            }
            int num = Integer.parseInt(m.group(1));
            String nombre = limpiarNombreIndice(m.group(2));
            if (nombre.length() >= 4) {
                porNumero.putIfAbsent(num, new IndiceEstandar(num, nombre));
            }
        }
        return new ArrayList<>(porNumero.values());
    }

    static boolean esTituloEstandarValido(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String t = line.trim().replaceAll("\\s+", " ");
        if (t.length() < 12 || t.length() > 85) {
            return false;
        }
        if (!t.matches("(?i).*EST[AÁ]NDAR\\s+DE\\s+.*")) {
            return false;
        }
        String low = t.toLowerCase(Locale.ROOT);
        if (low.contains("el cual") || low.contains("establece") || low.contains("debera")
                || low.contains("deberá") || low.contains("complementa")) {
            return false;
        }
        if (t.contains(",") && t.indexOf(',') < 40) {
            return false;
        }
        return !PATRON_INDICE.matcher(t).matches();
    }

    private static String limpiarNombreIndice(String raw) {
        return raw.trim()
                .replaceAll("\\s+\\d{1,3}\\s*$", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * @deprecated usar índice por documento; no mezclar catálogo ESV externo en saludos.
     */
    static List<String> listarEstandaresParaSaludo(Collection<String> detectadosEnPdf) {
        return detectadosEnPdf != null
                ? detectadosEnPdf.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().replaceAll("\\s+", " "))
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList()
                : List.of();
    }

    /**
     * Apartados del libro ESV que no son «ESTÁNDAR DE …» (p. ej. Compromiso de la Alta Dirección).
     * Deben transcribirse literalmente, no resumirse con IA.
     */
    static boolean esConsultaApartadoTematico(String pregunta) {
        if (pregunta == null || pregunta.isBlank()) {
            return false;
        }
        String n = normalizar(pregunta);
        if (n.contains("compromiso") && (n.contains("alta direccion")
                || n.contains("direccion enap") || n.contains("direccion de enap"))) {
            return true;
        }
        if (n.matches("(?s).*\\b[i1]\\s*[.)]?\\s*compromiso.*")) {
            return true;
        }
        if (n.contains("compromiso de la alta")) {
            return true;
        }
        return false;
    }

    /** Posición del bloque «Compromiso de la Alta Dirección» en texto plano/PDF. */
    static int localizarApartadoCompromiso(String texto) {
        if (texto == null || texto.isBlank()) {
            return -1;
        }
        Pattern p = Pattern.compile(
                "(?is)(?:^|\\n)\\s*(?:i\\s*[.)]?\\s*)?COMPROMISO\\s+DE\\s+LA\\s+ALTA\\s+DIRECCION",
                Pattern.UNICODE_CASE);
        Matcher m = p.matcher(texto);
        if (m.find()) {
            return m.start();
        }
        Pattern flex = Pattern.compile(
                "(?is)COMPROMISO\\s+DE\\s+LA\\s+ALTA\\s+DIRECCION\\s+DE\\s+ENAP",
                Pattern.UNICODE_CASE);
        m = flex.matcher(texto);
        return m.find() ? m.start() : -1;
    }

    /**
     * Fin del apartado Compromiso: antes de Introducción / Alcance del libro, no en «estándares que salvan vidas» del párrafo.
     */
    static int encontrarFinApartadoCompromiso(String texto, int inicio) {
        if (texto == null || inicio < 0 || inicio >= texto.length()) {
            return texto != null ? texto.length() : 0;
        }
        int minPos = inicio + 80;
        int mejorFin = texto.length();

        Pattern[] marcadoresFin = {
                Pattern.compile("(?is)(?:^|\\n)\\s*ii\\s*[.)]?\\s*(?:\\n+\\s*)?INTRODUCCION", Pattern.UNICODE_CASE),
                Pattern.compile("(?is)(?:^|\\n)\\s*INTRODUCCION\\s+(?:Y\\s+)?CONTEXTO", Pattern.UNICODE_CASE),
                Pattern.compile("(?is)(?:^|\\n)\\s*ii\\s*[.)]?\\s*(?:\\n|$)", Pattern.UNICODE_CASE),
                Pattern.compile("(?is)(?:^|\\n)\\s*ESTRUCTURA\\s+DEL\\s+DOCUMENTO", Pattern.UNICODE_CASE),
                Pattern.compile("(?is)(?:^|\\n)\\s*VIGENCIA\\s+DEL\\s+DOCUMENTO", Pattern.UNICODE_CASE),
                Pattern.compile("(?is)(?:^|\\n)\\s*MANEJO\\s+DE\\s+EXCEPCIONES", Pattern.UNICODE_CASE),
                Pattern.compile("(?im)^\\s*ALCANCE\\s*$", Pattern.UNICODE_CASE),
                Pattern.compile("(?im)^\\s*1\\s*[.)]?\\s*EST[AÁ]NDAR\\s+DE\\s+", Pattern.UNICODE_CASE),
                Pattern.compile("(?im)^\\s*EST[AÁ]NDAR\\s+DE\\s+CONTROL\\s+DE\\s+TRABAJO", Pattern.UNICODE_CASE),
                Pattern.compile("(?im)^PELIGRO\\s*0?1", Pattern.UNICODE_CASE),
        };

        for (Pattern p : marcadoresFin) {
            Matcher m = p.matcher(texto);
            while (m.find(minPos)) {
                if (m.start() < mejorFin) {
                    mejorFin = m.start();
                }
            }
        }
        return mejorFin;
    }

    /** Solo el bloque Compromiso (sin Introducción ni estándares posteriores). */
    static String recortarTextoApartadoCompromiso(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        int inicio = localizarApartadoCompromiso(texto);
        if (inicio < 0) {
            inicio = 0;
        }
        int fin = encontrarFinApartadoCompromiso(texto, inicio);
        String extracto = texto.substring(inicio, fin).trim();
        if (extracto.length() > 4500) {
            fin = encontrarFinApartadoCompromiso(extracto, 0);
            extracto = extracto.substring(0, fin).trim();
        }
        return extracto;
    }

    static boolean esConsultaApartadoCompromiso(String pregunta) {
        if (pregunta == null) {
            return false;
        }
        String n = normalizar(pregunta);
        return n.contains("compromiso");
    }

    /** Nombre corto para viñetas (sin prefijo «ESTÁNDAR DE»). */
    static String tituloCortoParaLista(String tituloCompleto) {
        if (tituloCompleto == null) {
            return "";
        }
        return tituloCompleto
                .replaceFirst("(?i)^est[aá]ndar\\s+de\\s+", "")
                .trim();
    }
}
