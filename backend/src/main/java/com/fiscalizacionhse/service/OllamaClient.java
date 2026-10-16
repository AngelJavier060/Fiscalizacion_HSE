package com.fiscalizacionhse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fiscalizacionhse.config.OllamaClientConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Habla solo con Ollama en localhost. Nunca llama APIs de pago ni de internet.
 */
@Component
@Slf4j
public class OllamaClient {

    private final RestTemplate restTemplate;
    private final OllamaClientConfig config;

    public OllamaClient(
            @Qualifier("ollamaRestTemplate") RestTemplate restTemplate,
            OllamaClientConfig config) {
        this.restTemplate = restTemplate;
        this.config = config;
        log.info("Ollama local: enabled={} url={} model={}",
                config.isEnabled(), config.getBaseUrl(), config.getModel());
    }

    public boolean enabled() {
        return config.isEnabled();
    }

    public String modeloConfigurado() {
        return config.getModel();
    }

    public String baseUrl() {
        return config.getBaseUrl();
    }

    public EstadoOllama estado() {
        if (!config.isEnabled()) {
            return EstadoOllama.apagado("Ollama está desactivado en la configuración.");
        }
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    config.getBaseUrl() + "/api/tags",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    JsonNode.class);
            JsonNode body = response.getBody();
            List<String> modelos = new ArrayList<>();
            if (body != null && body.has("models")) {
                for (JsonNode m : body.get("models")) {
                    if (m.hasNonNull("name")) {
                        modelos.add(m.get("name").asText());
                    }
                }
            }
            boolean hayModelo = tieneModelo(modelos, config.getModel());
            if (modelos.isEmpty()) {
                return new EstadoOllama(true, false, config.getModel(), modelos,
                        "Ollama está en este servidor, pero aún no hay modelo. En una terminal: ollama pull "
                                + config.getModel());
            }
            if (!hayModelo) {
                return new EstadoOllama(true, false, config.getModel(), modelos,
                        "Ollama corre, pero falta el modelo «" + config.getModel()
                                + "». En una terminal: ollama pull " + config.getModel()
                                + ". Modelos instalados: " + String.join(", ", modelos));
            }
            return new EstadoOllama(true, true, config.getModel(), modelos,
                    "Ollama listo en este servidor («" + config.getModel() + "»). El texto no sale de la máquina.");
        } catch (Exception e) {
            log.warn("Ollama no responde en {}: {}", config.getBaseUrl(), e.getMessage());
            return EstadoOllama.apagado(
                    "Ollama no está en marcha. Instálelo, deje el servicio activo y ejecute: ollama pull "
                            + config.getModel());
        }
    }

    public Optional<String> chatSiListo(String systemPrompt, String userMessage) {
        if (!estado().listo()) {
            return Optional.empty();
        }
        try {
            return Optional.of(chat(systemPrompt, userMessage));
        } catch (Exception e) {
            log.warn("Ollama no redactó, se usa el texto de los PDF: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public String chat(String systemPrompt, String userMessage) {
        EstadoOllama st = estado();
        if (!st.listo()) {
            throw new IllegalStateException(st.mensaje());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel());
        body.put("stream", false);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
        ));
        body.put("options", Map.of(
                "temperature", config.getTemperature(),
                "num_predict", config.getMaxTokens()
        ));
        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    config.getBaseUrl() + "/api/chat",
                    HttpMethod.POST,
                    new HttpEntity<>(body),
                    JsonNode.class);
            JsonNode root = response.getBody();
            if (root == null) {
                throw new IllegalStateException("Ollama respondió vacío.");
            }
            JsonNode message = root.path("message").path("content");
            if (message.isMissingNode() || message.asText().isBlank()) {
                throw new IllegalStateException("Ollama no devolvió texto.");
            }
            return message.asText().trim();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error llamando a Ollama: {}", e.getMessage());
            throw new IllegalStateException("Ollama no pudo redactar: " + e.getMessage(), e);
        }
    }

    private static boolean tieneModelo(List<String> instalados, String configurado) {
        if (configurado == null || configurado.isBlank()) {
            return false;
        }
        String wanted = configurado.trim().toLowerCase();
        for (String name : instalados) {
            String n = name.toLowerCase();
            if (n.equals(wanted) || n.startsWith(wanted + ":") || n.equals(wanted + ":latest")) {
                return true;
            }
        }
        return false;
    }

    public record EstadoOllama(
            boolean procesoActivo,
            boolean listo,
            String modelo,
            List<String> modelos,
            String mensaje
    ) {
        static EstadoOllama apagado(String mensaje) {
            return new EstadoOllama(false, false, "", List.of(), mensaje);
        }
    }
}
