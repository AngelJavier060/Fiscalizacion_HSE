package com.fiscalizacionhse.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Catálogo exhaustivo de Controles Críticos (CC) escaneando todo el texto del PDF.
 * No depende del LLM: evita omitir CC2/CC3 u otros por límites de contexto del modelo.
 */
@Service
@Slf4j
public class CatalogoControlesCriticosService {

    /** CC en cualquier posición (PDFBox suele partir mal las líneas). */
    private static final Pattern PATRON_CC_FLEXIBLE = Pattern.compile(
            "(?i)\\b(CC)\\s*(\\d{1,2})\\b\\s*[-–—:\\.]?\\s*([^\\n\\r]{2,280})");

    private static final Pattern PATRON_SOLO_CC = Pattern.compile(
            "(?i)\\bCC\\s*(\\d{1,2})\\b");

    private static final Pattern PATRON_INDICE = Pattern.compile(
            "(?i)(?:ÍNDICE|INDICE|CONTENIDO|TABLA\\s+DE\\s+CONTENIDO|SUMARIO)");

    public record CcItem(String codigo, int numero, String titulo, String estandar) {}

    public record CatalogoCc(String tituloDocumento, List<CcItem> items) {
        public int total() {
            return items.size();
        }

        public Map<String, List<CcItem>> porEstandar() {
            Map<String, List<CcItem>> map = new LinkedHashMap<>();
            for (CcItem item : items) {
                String key = item.estandar() != null ? item.estandar() : "Sin estándar asignado";
                map.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
            }
            for (List<CcItem> lista : map.values()) {
                lista.sort(Comparator.comparingInt(CcItem::numero));
            }
            return map;
        }
    }

    public boolean esPreguntaSobreInventarioControlesCriticos(String pregunta) {
        if (pregunta == null || pregunta.isBlank()) {
            return false;
        }
        String n = normalizar(pregunta);
        if (n.contains("control critico") || n.contains("controles criticos")) {
            return true;
        }
        if (n.contains(" cuantos ") || n.startsWith("cuantos ") || n.contains("cuantas ")
                || n.contains("numero de") || n.contains("número de") || n.contains("total de")) {
            return n.contains("control") || n.contains(" cc") || n.contains("critico");
        }
        if (n.contains("listado") && (n.contains("cc") || n.contains("control"))) {
            return true;
        }
        if (n.contains("todos los cc") || n.contains("todas las cc")) {
            return true;
        }
        return n.contains("inventario") && n.contains("control");
    }

    /**
     * Escanea todo el texto y devuelve el catálogo deduplicado (mejor título por CC + estándar).
     */
    public CatalogoCc extraerCatalogo(String tituloDocumento, String textoCompleto) {
        if (textoCompleto == null || textoCompleto.isBlank()) {
            return new CatalogoCc(tituloDocumento, List.of());
        }

        List<EstandarPos> estandares = localizarEstandares(textoCompleto);
        Map<String, CcItem> unicos = new LinkedHashMap<>();

        // Pasada 1: CC con título en la misma coincidencia
        Matcher m = PATRON_CC_FLEXIBLE.matcher(textoCompleto);
        while (m.find()) {
            int pos = m.start();
            String codigo = "CC" + m.group(2);
            int numero = Integer.parseInt(m.group(2));
            String titulo = limpiarTitulo(m.group(3));
            if (!tituloValido(titulo)) {
                titulo = buscarTituloCercano(textoCompleto, pos, codigo);
            }
            String estandar = estandarEnPosicion(estandares, pos);
            registrarCc(unicos, codigo, numero, titulo, estandar);
        }

        // Pasada 2: refuerzo en zona de índice (primeras páginas / bloque ÍNDICE)
        String zonaIndice = extraerZonaIndice(textoCompleto);
        if (!zonaIndice.isBlank()) {
            Matcher mi = PATRON_CC_FLEXIBLE.matcher(zonaIndice);
            while (mi.find()) {
                String codigo = "CC" + mi.group(2);
                int numero = Integer.parseInt(mi.group(2));
                String titulo = limpiarTitulo(mi.group(3));
                if (tituloValido(titulo)) {
                    String estandar = inferirEstandarDesdeContextoIndice(zonaIndice, mi.start(), estandares);
                    registrarCc(unicos, codigo, numero, titulo, estandar);
                }
            }
        }

        // Pasada 3: dentro de cada bloque estándar, CC sueltos sin título → buscar en líneas vecinas
        for (EstandarPos ep : estandares) {
            String bloque = textoCompleto.substring(ep.inicio(), ep.fin());
            Matcher ms = PATRON_SOLO_CC.matcher(bloque);
            while (ms.find()) {
                int absPos = ep.inicio() + ms.start();
                String codigo = "CC" + ms.group(1);
                int numero = Integer.parseInt(ms.group(1));
                String clave = claveUnica(ep.titulo(), codigo);
                if (!unicos.containsKey(clave)) {
                    String titulo = buscarTituloCercano(textoCompleto, absPos, codigo);
                    registrarCc(unicos, codigo, numero, titulo, ep.titulo());
                }
            }
        }

        List<CcItem> ordenados = new ArrayList<>(unicos.values());
        ordenados.sort(Comparator
                .comparing((CcItem i) -> i.estandar() != null ? i.estandar() : "")
                .thenComparingInt(CcItem::numero));

        log.info("📊 Catálogo CC «{}»: {} controles en {} estándares",
                tituloDocumento, ordenados.size(),
                ordenados.stream().map(CcItem::estandar).distinct().count());

        return new CatalogoCc(tituloDocumento, ordenados);
    }

    private static final Pattern PATRON_INICIO_BLOQUE_CC = Pattern.compile(
            "(?im)^CONTROL\\s+CR[IÍ]TICO\\s*$");

    private static final Pattern PATRON_CC_EN_BLOQUE = Pattern.compile(
            "(?i)\\(CC\\s*(\\d{1,2})\\)");

    public boolean esPreguntaSobreEstandarConContenido(String pregunta) {
        return EstandarConsultaHelper.pideEstandarCompleto(pregunta);
    }

    /**
     * Transcripción literal de CC1…CCn del estándar indicado en la pregunta.
     */
    public Optional<String> responderEstandarConTodosLosCc(
            String tituloDocumento, String textoCompleto, String pregunta) {

        Optional<String> estandarOpt = EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta);
        Optional<String> ccFiltro = EstandarConsultaHelper.detectarCcEnPregunta(pregunta);
        if (estandarOpt.isEmpty() && ccFiltro.isEmpty()) {
            return Optional.empty();
        }
        if (textoCompleto == null || textoCompleto.isBlank()) {
            return Optional.empty();
        }

        List<BloqueCcLiteral> bloques;
        String nombreEstandar = estandarOpt.orElse("");

        if (estandarOpt.isPresent()) {
            nombreEstandar = estandarOpt.get();
            int inicio = EstandarConsultaHelper.localizarSeccionEstandar(textoCompleto, nombreEstandar);
            if (inicio < 0) {
                return Optional.empty();
            }
            int fin = EstandarConsultaHelper.encontrarFinSeccionEstandar(textoCompleto, inicio, nombreEstandar);
            bloques = extraerBloquesCcLiterales(textoCompleto.substring(inicio, fin));
        } else {
            bloques = extraerBloquesCcLiterales(textoCompleto);
        }

        if (bloques.isEmpty()) {
            return Optional.empty();
        }

        if (ccFiltro.isPresent()) {
            bloques = bloques.stream()
                    .filter(b -> ccFiltro.get().equalsIgnoreCase(b.codigo()))
                    .toList();
            if (bloques.isEmpty()) {
                return Optional.empty();
            }
        }

        log.info("📊 Estándar «{}» — {} bloque(s) CC literal(es)", nombreEstandar, bloques.size());
        return Optional.of(formatearRespuestaEstandarCc(
                tituloDocumento, nombreEstandar, pregunta, bloques));
    }

    private record BloqueCcLiteral(String codigo, int numero, String titulo, String texto) {}

    private List<BloqueCcLiteral> extraerBloquesCcLiterales(String seccionEstandar) {
        List<Integer> inicios = new ArrayList<>();
        Matcher m = PATRON_INICIO_BLOQUE_CC.matcher(seccionEstandar);
        while (m.find()) {
            inicios.add(m.start());
        }

        if (inicios.isEmpty()) {
            Matcher mc = PATRON_CC_EN_BLOQUE.matcher(seccionEstandar);
            while (mc.find()) {
                int pos = mc.start();
                String antes = seccionEstandar.substring(Math.max(0, pos - 400), pos);
                if (antes.toLowerCase(Locale.ROOT).contains("control")) {
                    int inicioBloque = Math.max(0, pos - 400);
                    int idxControl = antes.toLowerCase(Locale.ROOT).lastIndexOf("control");
                    if (idxControl >= 0) {
                        inicioBloque = pos - (antes.length() - idxControl);
                    }
                    if (!inicios.contains(inicioBloque)) {
                        inicios.add(inicioBloque);
                    }
                }
            }
            inicios.sort(Integer::compareTo);
        }

        if (inicios.isEmpty()) {
            return List.of();
        }

        List<BloqueCcLiteral> out = new ArrayList<>();
        for (int i = 0; i < inicios.size(); i++) {
            int start = inicios.get(i);
            int end = (i + 1 < inicios.size()) ? inicios.get(i + 1) : seccionEstandar.length();
            String bloque = seccionEstandar.substring(start, end).trim();
            if (bloque.length() < 80) {
                continue;
            }
            String codigo = "CC?";
            int numero = i + 1;
            String titulo = "";
            Matcher mcc = PATRON_CC_EN_BLOQUE.matcher(bloque);
            if (mcc.find()) {
                numero = Integer.parseInt(mcc.group(1));
                codigo = "CC" + numero;
                titulo = extraerTituloDesdeBloque(bloque, mcc.start());
            }
            out.add(new BloqueCcLiteral(codigo, numero, titulo, bloque));
        }

        out.sort(Comparator.comparingInt(BloqueCcLiteral::numero));
        return out;
    }

    private static String extraerTituloDesdeBloque(String bloque, int posCc) {
        String ventana = bloque.substring(0, Math.min(bloque.length(), posCc + 120));
        String[] lineas = ventana.split("\\R");
        for (int i = lineas.length - 1; i >= 0; i--) {
            String l = lineas[i].trim();
            if (l.matches("(?i).*\\(CC\\s*\\d+\\).*")) {
                return l.replaceAll("(?i)\\(CC\\s*\\d+\\)", "").trim();
            }
            if (l.length() > 5 && !l.matches("(?i)CONTROL\\s+CR[IÍ]TICO")) {
                return l.length() > 100 ? l.substring(0, 97) + "…" : l;
            }
        }
        return "";
    }

    private String formatearRespuestaEstandarCc(
            String tituloDoc, String nombreEstandar, String pregunta, List<BloqueCcLiteral> bloques) {

        if (bloques.size() == 1) {
            return bloques.get(0).texto().trim() + "\n";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bloques.size(); i++) {
            if (i > 0) {
                sb.append("\n\n");
            }
            sb.append(bloques.get(i).texto().trim());
        }
        return sb.toString().trim() + "\n";
    }

    public String formatearRespuestaInventario(CatalogoCc catalogo, String pregunta) {
        if (catalogo.items().isEmpty()) {
            return """
                    No se detectaron **controles críticos (CC)** en el texto extraído de este PDF.

                    Puede deberse a un PDF escaneado sin texto seleccionable, o a un formato distinto al habitual (CC1, CC2…).
                    Pruebe abrir el PDF en el visor y verificar que el texto sea seleccionable; si no, suba una versión con OCR.
                    """;
        }

        Map<String, List<CcItem>> porEstandar = catalogo.porEstandar();
        int totalCc = catalogo.total();
        int totalEstandares = porEstandar.size();

        StringBuilder sb = new StringBuilder();
        sb.append("## Controles críticos — «").append(escapar(catalogo.tituloDocumento())).append("»\n\n");

        if (esPreguntaConteo(pregunta)) {
            sb.append("**Total: ").append(totalCc).append(" controles críticos** repartidos en **")
                    .append(totalEstandares).append(" estándares**.\n\n");
        } else {
            sb.append("Listado completo extraído del documento (**").append(totalCc)
                    .append(" CC** en **").append(totalEstandares).append(" estándares**).\n\n");
        }

        int nEst = 0;
        for (Map.Entry<String, List<CcItem>> entry : porEstandar.entrySet()) {
            nEst++;
            List<CcItem> ccs = entry.getValue();
            sb.append("### ").append(nEst).append(". ").append(entry.getKey());
            sb.append(" (").append(ccs.size()).append(" CC)\n\n");
            for (CcItem cc : ccs) {
                sb.append("- **").append(cc.codigo()).append("**");
                if (cc.titulo() != null && !cc.titulo().isBlank()) {
                    sb.append(": ").append(cc.titulo());
                }
                sb.append('\n');
            }
            sb.append('\n');
        }

        sb.append("---\n");
        sb.append("*Inventario generado escaneando **todo el texto** del PDF (detección automática CC + estándar). ");
        sb.append("Si falta algún CC, puede estar en una imagen/tablas no extraíble como texto.*\n");

        return sb.toString();
    }

    /** Respuesta agregada multi-documento (RAG general). */
    public String formatearRespuestaInventarioMultiDocumento(List<CatalogoCc> catalogos, String pregunta) {
        if (catalogos == null || catalogos.isEmpty()) {
            return "No se encontraron controles críticos (CC) en los documentos analizados.";
        }

        int totalGlobal = catalogos.stream().mapToInt(CatalogoCc::total).sum();
        StringBuilder sb = new StringBuilder();
        sb.append("## Inventario de controles críticos (CC)\n\n");
        sb.append("**Total general: ").append(totalGlobal).append(" CC** en **")
                .append(catalogos.size()).append(" documento(s)**.\n\n");

        for (CatalogoCc cat : catalogos) {
            if (cat.items().isEmpty()) {
                continue;
            }
            sb.append("---\n\n");
            sb.append(formatearRespuestaInventario(cat, pregunta));
        }
        return sb.toString();
    }

    private static void registrarCc(
            Map<String, CcItem> unicos, String codigo, int numero, String titulo, String estandar) {
        String est = estandar != null && !estandar.isBlank() ? estandar.trim() : "Sin estándar asignado";
        String clave = claveUnica(est, codigo);
        CcItem nuevo = new CcItem(codigo, numero, titulo != null ? titulo : "", est);
        CcItem prev = unicos.get(clave);
        if (prev == null || scoreTitulo(nuevo.titulo()) > scoreTitulo(prev.titulo())) {
            unicos.put(clave, nuevo);
        }
    }

    private static int scoreTitulo(String t) {
        if (t == null || t.isBlank()) {
            return 0;
        }
        return t.length();
    }

    private static String claveUnica(String estandar, String codigo) {
        return normalizar(estandar) + "|" + codigo.toUpperCase(Locale.ROOT);
    }

    private record EstandarPos(int inicio, int fin, String titulo) {}

    private static List<EstandarPos> localizarEstandares(String texto) {
        List<int[]> headers = new ArrayList<>();
        Pattern linea = Pattern.compile("(?m)^(EST[AÁ]NDAR\\s+DE\\s+.+)$", Pattern.CASE_INSENSITIVE);
        Matcher m = linea.matcher(texto);
        while (m.find()) {
            headers.add(new int[]{m.start(), m.end()});
        }
        if (headers.isEmpty()) {
            return List.of();
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

    private static String inferirEstandarDesdeContextoIndice(
            String zonaIndice, int posEnZona, List<EstandarPos> estandaresGlobales) {
        String antes = zonaIndice.substring(0, Math.max(0, posEnZona));
        Matcher m = Pattern.compile("(?m)(EST[AÁ]NDAR\\s+DE\\s+.+)$", Pattern.CASE_INSENSITIVE).matcher(antes);
        String ultimo = null;
        while (m.find()) {
            ultimo = m.group(1).trim().replaceAll("\\s+", " ");
        }
        return ultimo;
    }

    private static String extraerZonaIndice(String texto) {
        Matcher mi = PATRON_INDICE.matcher(texto);
        if (mi.find()) {
            int start = mi.start();
            int end = Math.min(texto.length(), start + 80_000);
            return texto.substring(start, end);
        }
        int limite = Math.min(texto.length(), (int) (texto.length() * 0.22));
        if (limite > 5000) {
            return texto.substring(0, limite);
        }
        return "";
    }

    private static String buscarTituloCercano(String texto, int posCc, String codigo) {
        int start = Math.max(0, posCc - 30);
        int end = Math.min(texto.length(), posCc + 350);
        String ventana = texto.substring(start, end);
        Pattern p = Pattern.compile(
                "(?i)\\b" + Pattern.quote(codigo) + "\\b\\s*[-–—:\\.]?\\s*([^\\n\\r]{3,200})");
        Matcher m = p.matcher(ventana);
        if (m.find()) {
            return limpiarTitulo(m.group(1));
        }
        return "";
    }

    private static String limpiarTitulo(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("(?i)^control\\s+critico\\s*[-–—:\\.]?\\s*", "")
                .replaceAll("[\\s\\-–—]+$", "")
                .trim();
        if (t.length() > 220) {
            t = t.substring(0, 217).trim() + "…";
        }
        return t;
    }

    private static boolean tituloValido(String titulo) {
        if (titulo == null || titulo.length() < 4) {
            return false;
        }
        long letras = titulo.chars().filter(Character::isLetter).count();
        return letras >= 4 && !titulo.matches("(?i)^(?:de|la|el|en|y|o|a|del|los|las)\\s.*");
    }

    private static boolean esPreguntaConteo(String pregunta) {
        String n = normalizar(pregunta);
        return n.contains("cuantos") || n.contains("cuantas") || n.contains("total")
                || n.contains("numero") || n.contains("cantidad");
    }

    private static String escapar(String s) {
        return s != null ? s : "";
    }

    private static String normalizar(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}+", "").replaceAll("\\s+", " ").trim();
    }
}
