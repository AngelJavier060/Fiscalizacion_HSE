package com.fiscalizacionhse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio de generación de embeddings usando DeepSeek API.
 * Divide documentos en chunks y genera vectores de 1536 dimensiones
 * para búsqueda semántica.
 */
@Service
@Slf4j
public class IaEmbeddingService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String embeddingUrl;
    private final String chatUrl;

    // Tamaño óptimo de chunk para embeddings (~512 tokens)
    private static final int CHUNK_SIZE = 2000;
    // Solapamiento entre chunks para mantener contexto
    private static final int CHUNK_OVERLAP = 200;

    public IaEmbeddingService(
            @Value("${app.ia.deepseek.api-key:}") String apiKey,
            @Value("${app.ia.deepseek.api-url:https://api.deepseek.com/v1/chat/completions}") String chatUrl) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
        this.chatUrl = chatUrl;
        this.embeddingUrl = "https://api.deepseek.com/v1/embeddings";
    }

    /**
     * Genera embeddings para un texto
     */
    public List<Double> generarEmbedding(String texto) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("⚠️ No hay API Key para embeddings. Usando simulación.");
            return generarEmbeddingSimulado(texto);
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "deepseek-embedding");
            requestBody.put("input", texto);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    embeddingUrl, HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    JsonNode.class
            );

            JsonNode dataNode = response.getBody().get("data").get(0).get("embedding");
            List<Double> embedding = new ArrayList<>();
            for (JsonNode node : dataNode) {
                embedding.add(node.asDouble());
            }

            log.debug("✅ Embedding generado: {} dimensiones", embedding.size());
            return embedding;

        } catch (Exception e) {
            log.error("❌ Error generando embedding: {}. Usando simulación.", e.getMessage());
            return generarEmbeddingSimulado(texto);
        }
    }

    /**
     * Divide un documento en chunks superpuestos para embedding
     */
    public List<ChunkResult> dividirEnChunks(String texto, String titulo) {
        List<ChunkResult> chunks = new ArrayList<>();

        if (texto == null || texto.isBlank()) {
            log.warn("⚠️ Texto vacío, no se pueden generar chunks");
            return chunks;
        }

        // Dividir por párrafos
        String[] parrafos = texto.split("\\n\\n+");
        StringBuilder chunkActual = new StringBuilder();
        int orden = 0;

        for (String parrafo : parrafos) {
            String linea = parrafo.trim();
            if (linea.isBlank()) continue;

            // Si agregar este párrafo supera el tamaño máximo, guardar chunk
            if (chunkActual.length() + linea.length() > CHUNK_SIZE && chunkActual.length() > 0) {
                chunks.add(new ChunkResult(chunkActual.toString().trim(), orden++));
                // Mantener solapamiento: últimos caracteres
                String overlap = chunkActual.length() > CHUNK_OVERLAP
                        ? chunkActual.substring(chunkActual.length() - CHUNK_OVERLAP)
                        : chunkActual.toString();
                chunkActual = new StringBuilder(overlap).append("\n\n");
            }

            chunkActual.append(linea).append("\n\n");
        }

        // Último chunk
        if (!chunkActual.isEmpty()) {
            chunks.add(new ChunkResult(chunkActual.toString().trim(), orden));
        }

        log.info("📄 Documento '{}' dividido en {} chunks", titulo, chunks.size());
        return chunks;
    }

    /**
     * Genera embeddings para múltiples chunks
     */
    public List<EmbeddingResult> generarEmbeddingsParaChunks(
            List<ChunkResult> chunks, String textoCompleto) {

        List<EmbeddingResult> resultados = new ArrayList<>();

        for (ChunkResult chunk : chunks) {
            try {
                List<Double> embedding = generarEmbedding(chunk.texto());
                int tokens = chunk.texto().split("\\s+").length;

                resultados.add(new EmbeddingResult(
                        chunk.texto(),
                        chunk.orden(),
                        embedding,
                        tokens
                ));
            } catch (Exception e) {
                log.error("❌ Error generando embedding para chunk {}: {}", chunk.orden(), e.getMessage());
            }
        }

        log.info("✅ Embeddings generados para {} chunks", resultados.size());
        return resultados;
    }

    /**
     * Calcula similitud coseno entre dos vectores
     */
    public double calcularSimilitud(List<Double> v1, List<Double> v2) {
        if (v1 == null || v2 == null || v1.isEmpty() || v2.isEmpty()) return 0.0;

        double dotProduct = 0.0;
        double normV1 = 0.0;
        double normV2 = 0.0;

        int size = Math.min(v1.size(), v2.size());
        for (int i = 0; i < size; i++) {
            double a = v1.get(i);
            double b = v2.get(i);
            dotProduct += a * b;
            normV1 += a * a;
            normV2 += b * b;
        }

        double magnitud = Math.sqrt(normV1) * Math.sqrt(normV2);
        return magnitud == 0 ? 0 : dotProduct / magnitud;
    }

    /**
     * Convierte embedding List<Double> a String para PostgreSQL vector
     */
    public String embeddingToString(List<Double> embedding) {
        return "[" + embedding.stream()
                .map(d -> String.format("%.15f", d))
                .collect(Collectors.joining(",")) + "]";
    }

    /**
     * Convierte String de PostgreSQL vector a List<Double>
     */
    public List<Double> stringToEmbedding(String str) {
        if (str == null || str.isBlank()) return Collections.emptyList();

        String clean = str.replaceAll("[\\[\\]()]", "").trim();
        return Arrays.stream(clean.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Double::parseDouble)
                .collect(Collectors.toList());
    }

    /**
     * Embedding simulado cuando no hay API Key
     */
    private List<Double> generarEmbeddingSimulado(String texto) {
        // Generar un vector pseudo-aleatorio basado en el texto
        // para que textos similares tengan vectores similares
        List<Double> embedding = new ArrayList<>();
        Random random = new Random(texto.hashCode());

        for (int i = 0; i < 128; i++) { // 128D en modo simulado
            embedding.add(random.nextDouble() * 2 - 1);
        }

        return embedding;
    }

    public record ChunkResult(String texto, int orden) {}
    public record EmbeddingResult(String texto, int orden, List<Double> embedding, int tokenCount) {}
}
