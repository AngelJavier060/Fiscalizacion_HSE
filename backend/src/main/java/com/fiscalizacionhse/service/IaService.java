package com.fiscalizacionhse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class IaService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PuntoClaveExtraccionService extraccionService;
    private final CriteriosCalidadExtraccionService criteriosCalidadService;
    private final String apiKey;
    private final String apiUrl;

    /** Chunk por defecto (regeneración con límite de llamadas para no DDOS-ear la API). */
    private static final int CHUNK_SIZE = 16000;

    /** En la primera subida: pocas llamadas síncronas para no bloquear al usuario varios minutos. */
    private static final int CHUNK_SIZE_SUBIDA_RAPIDA = 24000;

    /** Máx. llamadas DeepSeek durante la SUBIDA del documento (resto disponible tras “Regenerar puntos IA”). */
    private static final int MAX_CHUNKS_SUBIDA_RAPIDA = 4;

    /** Máx. llamadas al regenerar manualmente desde el detalle del documento (procesa todo el PDF). */
    private static final int MAX_CHUNKS_REGENERACION = 80;

    private static final int MAX_PUNTOS_POR_CHUNK = 80;

    public IaService(
            PuntoClaveExtraccionService extraccionService,
            CriteriosCalidadExtraccionService criteriosCalidadService,
            @Value("${app.ia.deepseek.api-key:}") String apiKey,
            @Value("${app.ia.deepseek.api-url:https://api.deepseek.com/v1/chat/completions}") String apiUrl) {
        this.extraccionService = extraccionService;
        this.criteriosCalidadService = criteriosCalidadService;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
    }

    /**
     * Extrae puntos clave (visión más completa pero acotada) — usado desde “Regenerar puntos IA”.
     */
    public List<PuntoClaveIa> extraerPuntosClave(String titulo, String textoCompleto) {
        List<PuntoClaveIa> estructurados = combinarExtraccionEstructurada(titulo, textoCompleto);
        if (estructurados.size() >= 2) {
            log.info("✅ Puntos clave estructurados: {} (CC + criterios de calidad)", estructurados.size());
            return estructurados;
        }
        return extraerPuntosClaveInterno(titulo, textoCompleto, CHUNK_SIZE, MAX_CHUNKS_REGENERACION, false);
    }

    /**
     * Primera pasada tras subir archivo: intenta extracción estructurada; si no alcanza, IA acotada.
     */
    public List<PuntoClaveIa> extraerPuntosClaveSubidaRapida(String titulo, String textoCompleto) {
        List<PuntoClaveIa> estructurados = combinarExtraccionEstructurada(titulo, textoCompleto);
        if (estructurados.size() >= 2) {
            return estructurados;
        }
        return extraerPuntosClaveInterno(
                titulo, textoCompleto, CHUNK_SIZE_SUBIDA_RAPIDA, MAX_CHUNKS_SUBIDA_RAPIDA, true);
    }

    private List<PuntoClaveIa> combinarExtraccionEstructurada(String titulo, String textoCompleto) {
        List<PuntoClaveIa> cc = extraccionService.extraerPorTemasYControles(textoCompleto);
        CriteriosCalidadExtraccionService.CatalogoCriterios criterios =
                criteriosCalidadService.extraerCatalogo(titulo, textoCompleto);
        List<PuntoClaveIa> criteriosPuntos = criteriosCalidadService.comoPuntosClave(criterios);
        List<PuntoClaveIa> todos = new ArrayList<>(cc);
        int orden = todos.size();
        for (PuntoClaveIa p : criteriosPuntos) {
            p.setOrden(orden++);
            todos.add(p);
        }
        return todos;
    }

    private List<PuntoClaveIa> extraerPuntosClaveInterno(
            String titulo,
            String textoCompleto,
            int tamChunk,
            int maxChunks,
            boolean esPrimeraSubida) {
        if (textoCompleto == null || textoCompleto.isBlank()) {
            log.warn("⚠️ No hay texto para extraer puntos clave");
            return Collections.emptyList();
        }

        log.info("🤖 Extrayendo puntos clave de «{}» ({} caracteres, chunk≈{}, maxChunks={}, subida rápida={})",
                titulo, textoCompleto.length(), tamChunk, maxChunks, esPrimeraSubida);

        // Si no hay API Key, usar modo simulado
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("⚠️ No hay API Key configurada. Usando extracción simulada.");
            return extraerPuntosSimulado(textoCompleto);
        }

        List<String> todosLosChunks = dividirEnChunks(textoCompleto, tamChunk);
        int limiteChunks = esPrimeraSubida
                ? Math.min(maxChunks, todosLosChunks.size())
                : todosLosChunks.size();
        if (todosLosChunks.size() > limiteChunks) {
            log.warn("⚠️ Documento muy largo para esta etapa: se procesan solo {} de {} chunks ({} caracteres procesados sobre {}). "
                            + "{}",
                    limiteChunks, todosLosChunks.size(),
                    tamañoParteEnCaracteres(todosLosChunks, limiteChunks), textoCompleto.length(),
                    esPrimeraSubida
                            ? "Use «Regenerar puntos IA» en el documento para ampliar análisis (también con límite de seguridad)."
                            : "");
        }

        List<PuntoClaveIa> todosLosPuntos = new ArrayList<>();
        int ordenGlobal = 0;

        for (int i = 0; i < limiteChunks; i++) {
            log.info("📄 Procesando chunk {}/{} (de {} totales en el texto)...",
                    i + 1, limiteChunks, todosLosChunks.size());
            List<PuntoClaveIa> puntosChunk = extraerPuntosDeChunk(
                    titulo, todosLosChunks.get(i), i, limiteChunks, todosLosChunks.size());

            for (PuntoClaveIa punto : puntosChunk) {
                punto.setOrden(ordenGlobal++);
            }

            todosLosPuntos.addAll(puntosChunk);
        }

        if (esPrimeraSubida && todosLosChunks.size() > limiteChunks && !todosLosPuntos.isEmpty()) {
            PuntoClaveIa pie = new PuntoClaveIa();
            pie.setContenido("ℹ️ **Análisis parcial tras la subida** (solo las primeras secciones del texto): "
                    + "el PDF tiene muchísimas páginas o texto muy extenso. "
                    + "Desde **Regenerar puntos IA** se analizan más tramos (~hasta "
                    + MAX_CHUNKS_REGENERACION + " llamadas máximas al modelo). "
                    + "Para el chat/RAG también conviene ejecutar indexación desde FISCALIZA-AI cuando termine.");
            pie.setConfianza(BigDecimal.ONE);
            pie.setOrden(ordenGlobal++);
            todosLosPuntos.add(pie);
        }

        log.info("✅ Extraídos {} puntos clave en total", todosLosPuntos.size());
        return todosLosPuntos;
    }

    private static int tamañoParteEnCaracteres(List<String> chunks, int hastaIndiceExclusive) {
        int s = 0;
        for (int i = 0; i < hastaIndiceExclusive && i < chunks.size(); i++) {
            s += chunks.get(i).length();
        }
        return s;
    }

    /**
     * Extrae puntos clave de un chunk específico usando DeepSeek
     */
    private List<PuntoClaveIa> extraerPuntosDeChunk(
            String titulo,
            String chunk,
            int chunkIndex,
            int chunksProcesadosEnEstaLlamada,
            int chunksTotalesTextoCompleto) {
        try {
            String contextoChunk = chunksTotalesTextoCompleto > 1
                    ? " (Parte enviada " + (chunkIndex + 1) + "/" + chunksProcesadosEnEstaLlamada
                      + "; documento tiene " + chunksTotalesTextoCompleto + " partes reconstruibles)"
                    : "";

            String prompt = String.format("""
                Eres un experto analista de normativas HSE (Health, Safety & Environment).
                
                Documento: %s%s
                
                Extrae los puntos más importantes, relevantes y accionables de este fragmento del documento.
                Presta especial atención a:
                - Cláusulas legales, textos que empiecen por **Certifico**, **Declaro**, **Asumo**
                - **Controles críticos** numerados: CC1, CC2, CC3 … CC10, CC11, etc.
                - **Factores de calidad** numerados (Factor de calidad 1, 2, 3…)
                - Listas de compromisos, obligaciones, plazos, EPP, consecuencias
                
                REGLAS CRÍTICAS PARA LISTAS NUMERADAS (CC, factores de calidad, requisitos):
                - Extrae **CADA ítem por separado** con su numeración original (ej. «CC3: …», «Factor de calidad 5: …»)
                - **NO omitas ítems del medio** ni resumas varios controles en uno solo
                - Incluye la **descripción completa** de cada control/factor, no solo el título
                - Si hay CC1 hasta CC10 (o más), deben aparecer **todos** como puntos distintos
                
                Los puntos deben ser específicos, concretos y accionables.
                
                Para cada punto incluye:
                1. El contenido del punto (con numeración CC/Fc si aplica)
                2. Un nivel de confianza del 0.0 al 1.0
                
                IMPORTANTE: Extrae TODOS los puntos relevantes que encuentres en este fragmento.
                No hay límite — pueden ser 5 o 500 según el contenido.
                
                Texto del documento:
                %s
                """, titulo, contextoChunk,
                    chunk.length() > 28000 ? chunk.substring(0, 28000) + "\n... [texto truncado para este request]" : chunk);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "deepseek-chat");
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content",
                            "Eres un asistente que extrae puntos clave de documentos HSE. "
                            + "Cuando hay controles críticos (CC1, CC2…) o factores de calidad numerados, "
                            + "cada uno debe ser un punto JSON separado con su numeración. "
                            + "Responde ÚNICAMENTE en el siguiente formato JSON, sin explicaciones adicionales:\n"
                            + "{\"puntos\": [{\"contenido\": \"texto del punto\", \"confianza\": 0.95}]}"),
                    Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("temperature", 0.2);
            requestBody.put("max_tokens", 8192);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    JsonNode.class
            );

            String contenido = response.getBody()
                    .get("choices").get(0)
                    .get("message").get("content")
                    .asText();

            return parsearPuntosJson(contenido);

        } catch (Exception e) {
            log.error("❌ Error al extraer puntos con IA en chunk {}: {}", chunkIndex, e.getMessage());
            return extraerPuntosSimulado(chunk);
        }
    }

    /**
     * Parsea la respuesta JSON de la IA
     */
    private List<PuntoClaveIa> parsearPuntosJson(String json) {
        List<PuntoClaveIa> puntos = new ArrayList<>();

        try {
            // Intentar parsear como JSON
            JsonNode root = objectMapper.readTree(json);
            JsonNode puntosNode = root.get("puntos");
            if (puntosNode != null && puntosNode.isArray()) {
                for (JsonNode puntoNode : puntosNode) {
                    PuntoClaveIa punto = new PuntoClaveIa();
                    punto.setContenido(puntoNode.get("contenido").asText());

                    if (puntoNode.has("confianza")) {
                        punto.setConfianza(BigDecimal.valueOf(puntoNode.get("confianza").asDouble()));
                    } else {
                        punto.setConfianza(BigDecimal.valueOf(0.85));
                    }

                    puntos.add(punto);
                }
            }

            // Si no encontró puntos en JSON, intentar extraer con regex
            if (puntos.isEmpty()) {
                puntos = parsearPuntosConRegex(json);
            }

        } catch (Exception e) {
            log.warn("⚠️ No se pudo parsear JSON, usando regex fallback: {}", e.getMessage());
            puntos = parsearPuntosConRegex(json);
        }

        return puntos;
    }

    /**
     * Fallback: parsear puntos usando regex si el JSON no es válido
     */
    private List<PuntoClaveIa> parsearPuntosConRegex(String texto) {
        List<PuntoClaveIa> puntos = new ArrayList<>();
        Pattern pattern = Pattern.compile(
            "(?:^|\\n)\\s*(?:\\d+[.)]\\s*|[-*]\\s*|Punto\\s+\\d+[.:]?\\s*)(.+?)(?=\\n\\s*(?:\\d+[.)]\\s*|[-*]\\s*|Punto\\s+\\d+[.:]?\\s*|$))",
            Pattern.MULTILINE | Pattern.DOTALL
        );

        Matcher matcher = pattern.matcher(texto);
        while (matcher.find()) {
            String contenido = matcher.group(1).trim();
            if (contenido.length() > 20) { // Ignorar fragmentos muy cortos
                PuntoClaveIa punto = new PuntoClaveIa();
                punto.setContenido(contenido);
                punto.setConfianza(BigDecimal.valueOf(0.70));
                puntos.add(punto);
            }
        }

        return puntos;
    }

    /**
     * Extracción simulada cuando no hay API Key
     */
    private List<PuntoClaveIa> extraerPuntosSimulado(String texto) {
        List<PuntoClaveIa> puntos = new ArrayList<>();
        String[] parrafos = texto.split("\\n\\n+");

        log.info("📝 Modo simulado: analizando {} párrafos", parrafos.length);

        int maxPuntos = Math.min(parrafos.length, 20);
        for (int i = 0; i < maxPuntos; i++) {
            String parrafo = parrafos[i].trim();
            if (parrafo.length() > 30) {
                PuntoClaveIa punto = new PuntoClaveIa();
                punto.setContenido(parrafo.substring(0, Math.min(200, parrafo.length())));
                punto.setConfianza(BigDecimal.valueOf(0.50));
                punto.setOrden(i);
                puntos.add(punto);
            }
        }

        // Agregar nota si no hay API Key
        if (maxPuntos > 0) {
            PuntoClaveIa nota = new PuntoClaveIa();
            nota.setContenido("ℹ️ Listado rápido automático — con DeepSeek configurado estos puntos se redactarán mejor a partir del documento.");
            nota.setConfianza(BigDecimal.valueOf(0.0));
            nota.setOrden(maxPuntos);
            puntos.add(nota);
        }

        return puntos;
    }

    /**
     * Divide el texto en chunks para procesamiento
     */
    private List<String> dividirEnChunks(String texto, int tamChunk) {
        List<String> chunks = new ArrayList<>();
        if (texto.length() <= tamChunk) {
            chunks.add(texto);
            return chunks;
        }

        int start = 0;
        while (start < texto.length()) {
            int end = Math.min(start + tamChunk, texto.length());

            // Intentar cortar en un salto de línea
            if (end < texto.length()) {
                int lastBreak = texto.lastIndexOf('\n', end);
                if (lastBreak > start + tamChunk / 2) {
                    end = lastBreak;
                }
            }

            chunks.add(texto.substring(start, end));
            start = end;
        }

        log.info("📄 Documento dividido en {} chunks de tamaño medio ~{} caracteres", chunks.size(), tamChunk);
        return chunks;
    }

    /**
     * Clase interna para representar un punto clave extraído por IA
     */
    public static class PuntoClaveIa {
        private String contenido;
        private String titulo;
        private String tema;
        private String codigo;
        private String tipo;
        private BigDecimal confianza;
        private int orden;

        public String getContenido() { return contenido; }
        public void setContenido(String contenido) { this.contenido = contenido; }

        public String getTitulo() { return titulo; }
        public void setTitulo(String titulo) { this.titulo = titulo; }

        public String getTema() { return tema; }
        public void setTema(String tema) { this.tema = tema; }

        public String getCodigo() { return codigo; }
        public void setCodigo(String codigo) { this.codigo = codigo; }

        public String getTipo() { return tipo; }
        public void setTipo(String tipo) { this.tipo = tipo; }

        public BigDecimal getConfianza() { return confianza; }
        public void setConfianza(BigDecimal confianza) { this.confianza = confianza; }

        public int getOrden() { return orden; }
        public void setOrden(int orden) { this.orden = orden; }
    }
}
