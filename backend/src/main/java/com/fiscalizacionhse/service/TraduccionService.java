package com.fiscalizacionhse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class TraduccionService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiUrl;

    public TraduccionService(
            @Value("${app.ia.deepseek.api-key:}") String apiKey,
            @Value("${app.ia.deepseek.api-url:https://api.deepseek.com/v1/chat/completions}") String apiUrl) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
    }

    /**
     * Traduce texto de inglés a español usando DeepSeek API
     */
    public String traducirAIngles(String texto, String idiomaOrigen) {
        if (texto == null || texto.isBlank()) return "";
        if ("es".equalsIgnoreCase(idiomaOrigen)) return texto; // Ya está en español

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("⚠️ No hay API Key configurada para traducción. Usando modo simulado.");
            return traducirSimulado(texto);
        }

        try {
            log.info("🌐 Traduciendo {} caracteres de {} a español...", texto.length(), idiomaOrigen);

            Map<String, Object> requestBody = Map.of(
                    "model", "deepseek-chat",
                    "messages", List.of(
                            Map.of("role", "system", "content",
                                    "Eres un traductor experto en normativas HSE (Health, Safety & Environment). " +
                                            "Traduce el siguiente texto del " + idiomaOrigen + " al español. " +
                                            "Mantén el formato, los números, las referencias legales y los términos técnicos. " +
                                            "Responde SOLO con la traducción, sin explicaciones."),
                            Map.of("role", "user", "content", texto)
                    ),
                    "temperature", 0.3,
                    "max_tokens", 4096
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    apiUrl, HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    JsonNode.class
            );

            String traduccion = response.getBody()
                    .get("choices").get(0)
                    .get("message").get("content")
                    .asText();

            log.info("✅ Traducción completada: {} caracteres", traduccion.length());
            return traduccion;

        } catch (Exception e) {
            log.error("❌ Error en traducción con IA: {}. Usando modo simulado.", e.getMessage());
            return traducirSimulado(texto);
        }
    }

    /**
     * Traducción simulada cuando no hay API Key
     */
    private String traducirSimulado(String texto) {
        log.info("📝 Traducción simulada: {} caracteres", texto.length());
        return "[TRADUCCIÓN PENDIENTE - Configurar API Key]\n\n" +
               "Texto original (" + texto.length() + " caracteres):\n" +
               texto.substring(0, Math.min(500, texto.length())) +
               (texto.length() > 500 ? "\n\n... [texto truncado]" : "");
    }
}
