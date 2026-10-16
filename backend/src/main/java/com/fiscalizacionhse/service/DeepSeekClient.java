package com.fiscalizacionhse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscalizacionhse.config.DeepSeekClientConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Cliente HTTP centralizado para DeepSeek API con:
 * - Timeouts configurados (RestTemplate con DeepSeekClientConfig)
 * - Reintentos automáticos (RetryTemplate con RetryConfig)
 * - Manejo de errores unificado
 * - Logging detallado
 * 
 * 🔥 ANTES cada servicio llamaba a DeepSeek por su cuenta sin timeouts ni reintentos.
 * Ahora TODO pasa por este cliente.
 * 
 * 📌 MODELOS REALES DEEPSEEK (2025):
 *    deepseek-chat      → rápido, modelo general ✅
 *    deepseek-reasoner  → razonamiento profundo (DeepSeek-R1/R2) ✅
 * 
 * ❌ deepseek-v4-flash y deepseek-v4-pro NO existen como modelos reales en la API.
 */
@Component
@Slf4j
public class DeepSeekClient {

    private final RestTemplate restTemplate;
    private final RetryTemplate retryTemplate;
    private final ObjectMapper objectMapper;
    private final DeepSeekClientConfig config;

    public DeepSeekClient(
            @Qualifier("deepSeekRestTemplate") RestTemplate restTemplate,
            @Qualifier("deepSeekRetryTemplate") RetryTemplate deepSeekRetryTemplate,
            DeepSeekClientConfig config) {
        this.restTemplate = restTemplate;
        this.retryTemplate = deepSeekRetryTemplate;
        this.config = config;
        this.objectMapper = new ObjectMapper();
        
        log.info("🤖 DeepSeekClient iniciado");
        log.info("   ├─ Modelo principal:   {} (deepseek-reasoner / R2)", config.getDefaultModel());
        log.info("   ├─ Modelo rápido:      {} (deepseek-chat)", config.getFastModel());
        log.info("   ├─ Temperatura:      {}", config.getDefaultTemperature());
        log.info("   ├─ Max tokens:       {}", config.getMaxTokens());
        log.info("   └─ Timeouts:         connect=30s, read=120s, request=150s");
    }

    /**
     * Ejecuta una llamada a DeepSeek usando el modelo rápido (deepseek-chat).
     */
    public JsonNode callFlash(String apiKey, String apiUrl, String systemPrompt, String userMessage) {
        return callChatApi(apiKey, apiUrl, Map.of(
            "model", config.getFastModel(),
            "messages", java.util.List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
            ),
            "temperature", config.getDefaultTemperature(),
            "max_tokens", config.getMaxTokens(),
            "stream", false
        ));
    }

    /**
     * Ejecuta una llamada a DeepSeek usando el modelo de razonamiento (deepseek-reasoner / R2).
     * NOTA: reasoner piensa antes de responder, es más lento pero más preciso.
     */
    public JsonNode callPro(String apiKey, String apiUrl, String systemPrompt, String userMessage) {
        return callChatApi(apiKey, apiUrl, Map.of(
            "model", config.getDefaultModel(), // deepseek-reasoner (R2)
            "messages", java.util.List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
            ),
            "temperature", config.getDefaultTemperature(),
            "max_tokens", config.getMaxTokens(),
            "stream", false
        ));
    }

    /**
     * Ejecuta una llamada a DeepSeek Chat API con reintentos automáticos.
     */
    public JsonNode callChatApi(String apiKey, String apiUrl, Map<String, Object> requestBody) {
        String modelo = (String) requestBody.getOrDefault("model", "desconocido");
        return ejecutarConReintentos(() -> {
            HttpHeaders headers = crearHeaders(apiKey);
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    JsonNode.class
            );

            validarRespuesta(response);
            log.debug("✅ DeepSeek respondió OK (modelo={}, status={})", modelo, response.getStatusCode());
            return response.getBody();
        }, "chat/" + modelo);
    }

    /**
     * Ejecuta una llamada a DeepSeek Embedding API con reintentos automáticos.
     */
    public JsonNode callEmbeddingApi(String apiKey, String embeddingUrl, Map<String, Object> requestBody) {
        return ejecutarConReintentos(() -> {
            HttpHeaders headers = crearHeaders(apiKey);
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    embeddingUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    JsonNode.class
            );

            validarRespuesta(response);
            return response.getBody();
        }, "embedding");
    }

    /**
     * Ejecuta una llamada a DeepSeek TTS API con reintentos automáticos.
     */
    public byte[] callTtsApi(String apiKey, String ttsUrl, Map<String, Object> requestBody) {
        return ejecutarConReintentos(() -> {
            HttpHeaders headers = crearHeaders(apiKey);
            headers.setAccept(MediaType.parseMediaTypes("audio/mpeg, application/octet-stream"));

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    ttsUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    byte[].class
            );

            validarRespuestaBytes(response);
            return response.getBody();
        }, "tts");
    }

    /**
     * Extrae el contenido del mensaje de la respuesta de DeepSeek.
     */
    public String extraerContenido(JsonNode responseBody) {
        try {
            return responseBody
                    .get("choices").get(0)
                    .get("message").get("content")
                    .asText();
        } catch (Exception e) {
            log.error("❌ Error parseando respuesta DeepSeek: {}", e.getMessage());
            throw new DeepSeekException("No se pudo extraer el contenido de la respuesta de DeepSeek", e);
        }
    }

    /**
     * Verifica el estado de la API DeepSeek (health check).
     */
    public boolean verificarConexion(String apiKey, String apiUrl) {
        try {
            Map<String, Object> body = Map.of(
                    "model", config.getDefaultModel(),
                    "messages", java.util.List.of(
                            Map.of("role", "user", "content", "ping")
                    ),
                    "max_tokens", 5
            );
            callChatApi(apiKey, apiUrl, body);
            log.info("✅ Conexión con DeepSeek API verificada correctamente");
            return true;
        } catch (Exception e) {
            log.warn("⚠️ No se pudo conectar con DeepSeek API: {}", e.getMessage());
            return false;
        }
    }

    // ========================================================================
    // MÉTODOS PRIVADOS
    // ========================================================================

    private <T> T ejecutarConReintentos(Callable<T> callable, String tipo) {
        try {
            return retryTemplate.execute(context -> {
                int intento = context.getRetryCount() + 1;
                if (intento > 1) {
                    log.warn("🔄 Reintento {} de llamada DeepSeek ({})...", intento, tipo);
                }
                return callable.call();
            });
        } catch (Exception e) {
            log.error("❌ Falló llamada DeepSeek ({}) después de reintentos: {}", tipo, e.getMessage());
            throw new DeepSeekException("Error en DeepSeek " + tipo + ": " + e.getMessage(), e);
        }
    }

    private HttpHeaders crearHeaders(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        headers.set("Accept", "application/json");
        return headers;
    }

    private void validarRespuesta(ResponseEntity<JsonNode> response) {
        if (response.getBody() == null) {
            throw new DeepSeekException("DeepSeek API respondió con cuerpo vacío");
        }
        JsonNode error = response.getBody().get("error");
        if (error != null) {
            String mensaje = error.has("message") ? error.get("message").asText() : "Error desconocido";
            String tipo = error.has("type") ? error.get("type").asText() : "";
            log.error("❌ DeepSeek API error: {} ({})", mensaje, tipo);
            throw new DeepSeekException("DeepSeek API: " + mensaje);
        }
    }

    private void validarRespuestaBytes(ResponseEntity<byte[]> response) {
        if (response.getBody() == null || response.getBody().length == 0) {
            throw new DeepSeekException("DeepSeek TTS respondió con cuerpo vacío");
        }
    }

    /**
     * Excepción personalizada para errores de DeepSeek.
     */
    public static class DeepSeekException extends RuntimeException {
        public DeepSeekException(String message) {
            super(message);
        }

        public DeepSeekException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
