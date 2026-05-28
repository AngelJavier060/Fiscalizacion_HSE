package com.fiscalizacionhse.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Busca en la transcripción completa del PDF (texto_extraido) y responde con
 * el pasaje literal del libro — sin resumir ni parafrasear con IA.
 */
@Service
@Slf4j
public class TranscripcionLibroConsultaService {

    private static final int MAX_PASAJES = 4;
    private static final int MAX_CHARS_POR_PASAJE = 28_000;
    private static final int VENTANA_BUSQUEDA_ATRAS = 18_000;
    private static final int VENTANA_BUSQUEDA_ADELANTE = 22_000;

    private static final Set<String> STOPWORDS = Set.of(
            "quiero", "saber", "dime", "digame", "hola", "este", "esta", "para", "por", "con", "sin",
            "como", "cual", "cuales", "donde", "cuando", "quien", "sobre", "todo", "todos", "todas",
            "algo", "muy", "mas", "menos", "debe", "puede", "hacer", "tener", "ser", "son", "hay",
            "del", "las", "los", "una", "uno", "que", "dame", "lista", "listado");

    private static final Pattern PATRON_INICIO_SECCION = Pattern.compile(
            "(?m)^(EST[AÁ]NDAR\\s+DE\\s+|CRITERIOS?\\s+DE\\s+CALIDAD|CONTROL\\s+CR[IÍ]TICO|"
                    + "PARA\\s+EL\\s+CASO\\s+DE\\s+|CAP[ÍI]TULO\\s+|SECCI[ÓO]N\\s+\\d|\\bCC\\s*\\d+\\b)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern PATRON_FIN_SECCION = Pattern.compile(
            "(?m)^EST[AÁ]NDAR\\s+DE\\s+",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public record Pasaje(int inicio, int fin, String texto, int coincidencias, String encabezado) {}

    /**
     * Intenta responder citando textualmente el libro. Vacío si no hay pasajes útiles.
     */
    public Optional<String> responderConTranscripcion(String tituloDocumento, String textoCompleto, String pregunta) {
        if (textoCompleto == null || textoCompleto.isBlank() || pregunta == null || pregunta.isBlank()) {
            return Optional.empty();
        }
        if (EstandarConsultaHelper.esConsultaResumenInformal(pregunta)
                && !EstandarConsultaHelper.esConsultaApartadoTematico(pregunta)) {
            return Optional.empty();
        }

        if (EstandarConsultaHelper.esConsultaApartadoTematico(pregunta)) {
            Optional<String> compromiso = transcribirApartadoCompromiso(tituloDocumento, textoCompleto);
            if (compromiso.isPresent()) {
                return compromiso;
            }
        }

        Optional<String> estandarOpt = EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta);
        if (estandarOpt.isPresent()) {
            Optional<String> seccionEstandar = transcribirSeccionEstandar(
                    tituloDocumento, textoCompleto, pregunta, estandarOpt.get());
            if (seccionEstandar.isPresent()) {
                return seccionEstandar;
            }
        }

        List<Pasaje> pasajes = buscarPasajesRelevantes(textoCompleto, pregunta);
        if (pasajes.isEmpty()) {
            return Optional.empty();
        }

        Pasaje mejor = pasajes.get(0);
        if (!esPasajeUtil(mejor, pregunta) || esPasajeIndice(mejor)) {
            return Optional.empty();
        }

        String respuesta = formatearRespuesta(tituloDocumento, pregunta, pasajes);
        log.info("📖 Transcripción directa «{}» — {} pasaje(s), mejor bloque {} chars, {} coincidencias",
                tituloDocumento, pasajes.size(), mejor.texto().length(), mejor.coincidencias());
        return Optional.of(respuesta);
    }

    public List<Pasaje> buscarPasajesRelevantes(String textoCompleto, String pregunta) {
        LinkedHashSet<String> terminos = extraerTerminosConsulta(pregunta);
        if (terminos.isEmpty()) {
            return List.of();
        }

        List<int[]> intervalosRaw = new ArrayList<>();
        Map<String, Integer> conteoPorIntervalo = new HashMap<>();

        for (String term : terminos) {
            if (term.length() < 3) {
                continue;
            }
            try {
                Pattern p = Pattern.compile(Pattern.quote(term), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                Matcher m = p.matcher(textoCompleto);
                while (m.find()) {
                    int center = m.start();
                    int start = expandirInicioSeccion(textoCompleto, center);
                    int end = expandirFinSeccion(textoCompleto, center, start);
                    String clave = start + ":" + end;
                    conteoPorIntervalo.merge(clave, 1, Integer::sum);
                    intervalosRaw.add(new int[]{start, end});
                }
            } catch (Exception ignored) {
                // término inválido como regex
            }
        }

        if (intervalosRaw.isEmpty()) {
            return List.of();
        }

        Map<String, int[]> merged = fusionarIntervalos(intervalosRaw);
        List<Pasaje> pasajes = new ArrayList<>();

        for (int[] r : merged.values()) {
            int start = r[0];
            int end = r[1];
            String clave = start + ":" + end;
            int hits = conteoPorIntervalo.getOrDefault(clave, contarCoincidenciasEnRango(textoCompleto, terminos, start, end));
            String extracto = textoCompleto.substring(start, end).trim();
            if (extracto.length() < 60) {
                continue;
            }
            if (extracto.length() > MAX_CHARS_POR_PASAJE) {
                extracto = extracto.substring(0, MAX_CHARS_POR_PASAJE) + "\n\n… *(continúa en el PDF)*";
            }
            String encabezado = detectarEncabezadoSeccion(extracto);
            pasajes.add(new Pasaje(start, end, extracto, hits, encabezado));
        }

        pasajes.sort((a, b) -> {
            int cmp = Integer.compare(b.coincidencias(), a.coincidencias());
            return cmp != 0 ? cmp : Integer.compare(b.texto().length(), a.texto().length());
        });

        return pasajes.stream().limit(MAX_PASAJES).toList();
    }

    public String formatearRespuesta(String tituloDocumento, String pregunta, List<Pasaje> pasajes) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 📖 Texto del documento — «").append(tituloDocumento).append("»\n\n");
        sb.append("**Consulta:** ").append(pregunta.trim()).append("\n\n");
        sb.append("Lo siguiente es **transcripción literal** del PDF (sin resumen de IA):\n\n");

        int n = 0;
        for (Pasaje p : pasajes) {
            n++;
            if (pasajes.size() > 1) {
                sb.append("### Fragmento ").append(n);
                if (p.encabezado() != null && !p.encabezado().isBlank()) {
                    sb.append(" — ").append(p.encabezado());
                }
                sb.append("\n\n");
            } else if (p.encabezado() != null && !p.encabezado().isBlank()) {
                sb.append("**").append(p.encabezado()).append("**\n\n");
            }

            sb.append("```text\n").append(p.texto()).append("\n```\n\n");
        }

        sb.append("---\n*Fuente: texto extraído del PDF cargado en la plataforma.*\n");
        return sb.toString();
    }

    /** Respuesta multi-documento (modo empresa). Solo transcripción literal explícita, no «háblame de…». */
    public Optional<String> responderConTranscripcionMulti(
            String pregunta, List<Map.Entry<String, String>> documentosTituloTexto) {

        if (documentosTituloTexto == null || documentosTituloTexto.isEmpty()) {
            return Optional.empty();
        }
        if (EstandarConsultaHelper.esConsultaResumenInformal(pregunta)) {
            return Optional.empty();
        }

        Optional<String> estandarOpt = EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta);

        StringBuilder sb = new StringBuilder();
        sb.append("## 📖 Transcripción desde sus documentos\n\n");
        sb.append("**Consulta:** ").append(pregunta.trim()).append("\n\n");

        int docsConHit = 0;
        for (Map.Entry<String, String> entry : documentosTituloTexto) {
            Optional<String> bloqueDoc = Optional.empty();
            if (estandarOpt.isPresent()) {
                bloqueDoc = transcribirSeccionEstandar(
                        entry.getKey(), entry.getValue(), pregunta, estandarOpt.get());
            }
            if (bloqueDoc.isEmpty()) {
                List<Pasaje> pasajes = buscarPasajesRelevantes(entry.getValue(), pregunta);
                if (!pasajes.isEmpty() && esPasajeUtil(pasajes.get(0), pregunta) && !esPasajeIndice(pasajes.get(0))) {
                    bloqueDoc = Optional.of(formatearRespuesta(entry.getKey(), pregunta, pasajes));
                }
            }
            if (bloqueDoc.isEmpty()) {
                continue;
            }
            docsConHit++;
            sb.append("---\n\n");
            sb.append(bloqueDoc.get());
        }

        if (docsConHit == 0) {
            return Optional.empty();
        }
        return Optional.of(sb.toString());
    }

    private Optional<String> transcribirApartadoCompromiso(String tituloDocumento, String textoCompleto) {
        int inicio = EstandarConsultaHelper.localizarApartadoCompromiso(textoCompleto);
        if (inicio < 0) {
            return Optional.empty();
        }
        int fin = EstandarConsultaHelper.encontrarFinApartadoCompromiso(textoCompleto, inicio);
        String extracto = textoCompleto.substring(inicio, fin).trim();
        if (extracto.length() < 120) {
            return Optional.empty();
        }
        Pasaje pasaje = new Pasaje(inicio, fin, extracto, 20, "COMPROMISO DE LA ALTA DIRECCIÓN DE ENAP");
        log.info("📖 Apartado Compromiso «{}» — {} chars", tituloDocumento, extracto.length());
        return Optional.of(formatearRespuesta(tituloDocumento,
                "Compromiso de la Alta Dirección", List.of(pasaje)));
    }

    private Optional<String> transcribirSeccionEstandar(
            String tituloDocumento, String textoCompleto, String pregunta, String nombreEstandar) {

        int inicio = EstandarConsultaHelper.localizarSeccionEstandar(textoCompleto, nombreEstandar);
        if (inicio < 0) {
            return Optional.empty();
        }

        int fin = EstandarConsultaHelper.encontrarFinSeccionEstandar(textoCompleto, inicio, nombreEstandar);
        String extracto = textoCompleto.substring(inicio, fin).trim();

        Pasaje pasaje = new Pasaje(inicio, fin, extracto, 10,
                "ESTÁNDAR DE " + nombreEstandar.toUpperCase(Locale.ROOT));
        if (esPasajeIndice(pasaje)) {
            return Optional.empty();
        }

        log.info("📖 Sección estándar «{}» — {} chars", nombreEstandar, extracto.length());
        return Optional.of(formatearRespuesta(tituloDocumento, pregunta, List.of(pasaje)));
    }

    private static boolean esPasajeIndice(Pasaje pasaje) {
        int lineasIndice = 0;
        int lineasEstandar = 0;
        for (String raw : pasaje.texto().split("\\R")) {
            String linea = raw.trim();
            if (linea.matches("(?i).*EST[AÁ]NDAR\\s+DE\\s+.*")) {
                lineasEstandar++;
                if (linea.matches("(?i)^EST[AÁ]NDAR\\s+DE\\s+.+\\s+\\d{1,3}\\s*$") && linea.length() < 90) {
                    lineasIndice++;
                }
            }
        }
        if (lineasIndice >= 2 && lineasEstandar >= 2) {
            return true;
        }
        String t = pasaje.texto().toLowerCase(Locale.ROOT);
        return pasaje.texto().length() < 500
                && !t.contains("criterios de calidad")
                && !t.contains("control critico")
                && lineasEstandar >= 2;
    }

    private boolean esPasajeUtil(Pasaje pasaje, String pregunta) {
        if (pasaje.texto().length() >= 120 && pasaje.coincidencias() >= 1) {
            return true;
        }
        if (pasaje.coincidencias() >= 2) {
            return true;
        }
        String t = pasaje.texto().toLowerCase(Locale.ROOT);
        if (t.contains("criterios de calidad") || t.matches("(?s).*\\b[a-z]\\.\\s+\\w+.*")) {
            return pasaje.coincidencias() >= 1;
        }
        LinkedHashSet<String> terms = extraerTerminosConsulta(pregunta);
        long matched = terms.stream()
                .filter(term -> term.length() >= 4 && contieneIgnoreCase(pasaje.texto(), term))
                .count();
        return matched >= 1 && pasaje.texto().length() >= 80;
    }

    private static int expandirInicioSeccion(String texto, int posicionMatch) {
        int limiteAtras = Math.max(0, posicionMatch - VENTANA_BUSQUEDA_ATRAS);
        String ventana = texto.substring(limiteAtras, posicionMatch);

        int mejor = limiteAtras;
        Matcher m = PATRON_INICIO_SECCION.matcher(ventana);
        while (m.find()) {
            mejor = limiteAtras + m.start();
        }

        int ultimoDobleSalto = ventana.lastIndexOf("\n\n\n");
        if (ultimoDobleSalto >= 0 && limiteAtras + ultimoDobleSalto > mejor - 500) {
            mejor = Math.max(mejor, limiteAtras + ultimoDobleSalto);
        }
        return mejor;
    }

    private static int expandirFinSeccion(String texto, int posicionMatch, int inicioYa) {
        int limiteFin = Math.min(texto.length(), posicionMatch + VENTANA_BUSQUEDA_ADELANTE);
        String ventana = texto.substring(posicionMatch, limiteFin);

        Matcher m = PATRON_FIN_SECCION.matcher(ventana);
        if (m.find()) {
            int rel = m.start();
            if (rel > 400) {
                limiteFin = posicionMatch + rel;
            }
        }

        limiteFin = Math.min(limiteFin, inicioYa + MAX_CHARS_POR_PASAJE);
        return limiteFin;
    }

    private static Map<String, int[]> fusionarIntervalos(List<int[]> intervalos) {
        intervalos.sort(Comparator.comparingInt(a -> a[0]));
        Map<String, int[]> out = new LinkedHashMap<>();
        for (int[] iv : intervalos) {
            boolean merged = false;
            for (Map.Entry<String, int[]> e : new ArrayList<>(out.entrySet())) {
                int[] ex = e.getValue();
                if (solapan(ex[0], ex[1], iv[0], iv[1])) {
                    ex[0] = Math.min(ex[0], iv[0]);
                    ex[1] = Math.max(ex[1], iv[1]);
                    out.remove(e.getKey());
                    out.put(ex[0] + ":" + ex[1], ex);
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                out.put(iv[0] + ":" + iv[1], new int[]{iv[0], iv[1]});
            }
        }
        return out;
    }

    private static boolean solapan(int a0, int a1, int b0, int b1) {
        return !(a1 < b0 - 200 || b1 < a0 - 200);
    }

    private static String detectarEncabezadoSeccion(String extracto) {
        for (String linea : extracto.split("\\R")) {
            String l = linea.trim();
            if (l.length() < 8) {
                continue;
            }
            if (l.matches("(?i).*EST[AÁ]NDAR\\s+DE\\s+.*")) {
                return l.length() > 120 ? l.substring(0, 117) + "…" : l;
            }
            if (l.matches("(?i).*CRITERIOS?\\s+DE\\s+CALIDAD.*")) {
                return l;
            }
            if (l.matches("(?i).*PARA\\s+EL\\s+CASO\\s+DE\\s+.*")) {
                return l.length() > 100 ? l.substring(0, 97) + "…" : l;
            }
        }
        return null;
    }

    private static int contarCoincidenciasEnRango(
            String texto, Set<String> terminos, int start, int end) {
        String trozo = texto.substring(start, end).toLowerCase(Locale.ROOT);
        int c = 0;
        for (String t : terminos) {
            if (t.length() >= 3 && trozo.contains(t.toLowerCase(Locale.ROOT))) {
                c++;
            }
        }
        return c;
    }

    static LinkedHashSet<String> extraerTerminosConsulta(String pregunta) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String n = normalizar(pregunta);

        Matcher m = Pattern.compile("[a-zñ0-9]{3,}").matcher(n);
        while (m.find()) {
            String w = m.group();
            if (!STOPWORDS.contains(w)) {
                terms.add(w);
                if (w.endsWith("s") && w.length() > 4) {
                    terms.add(w.substring(0, w.length() - 1));
                }
            }
        }

        expandirDominioHse(n, terms);
        terms.removeIf(t -> t.length() < 3);
        return terms;
    }

    private static void expandirDominioHse(String n, LinkedHashSet<String> terms) {
        if (n.contains("transport") || n.contains("carga") || n.contains("camion")) {
            Collections.addAll(terms, "transporte", "carga", "conductor", "camion", "vehiculo");
        }
        if (n.contains("combust") || n.contains("peligros") || n.contains("sustancia")) {
            Collections.addAll(terms, "combustible", "peligrosa", "sustancia", "gaseoso", "liquido");
        }
        if (n.contains("calidad") || n.contains("criterio") || n.contains("factor")) {
            Collections.addAll(terms, "calidad", "criterios", "criterio", "factores");
        }
        if (n.contains("altura") || n.contains("arnes")) {
            Collections.addAll(terms, "altura", "arnes", "andamio", "caida");
        }
        if (n.contains("critico") || n.contains(" cc") || n.contains("control")) {
            Collections.addAll(terms, "critico", "controles", "cc");
        }
        if (n.contains("calient") || n.contains("fuego")) {
            Collections.addAll(terms, "caliente", "fuego", "ignicion");
        }
        if (n.contains("confin")) {
            Collections.addAll(terms, "confinado", "confinados", "espacios");
        }
        if (n.contains("electr")) {
            Collections.addAll(terms, "electrico", "tension", "media", "alta");
        }
    }

    private static boolean contieneIgnoreCase(String texto, String term) {
        return normalizar(texto).contains(normalizar(term));
    }

    private static String normalizar(String s) {
        if (s == null) {
            return "";
        }
        String base = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return base.replaceAll("\\p{M}+", "");
    }
}
