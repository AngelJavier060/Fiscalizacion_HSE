package com.fiscalizacionhse.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Cliente HTTP hacia Ollama en el mismo servidor (circuito cerrado, sin nube).
 */
@Configuration
@Slf4j
@Getter
public class OllamaClientConfig {

    @Value("${app.ia.ollama.enabled:true}")
    private boolean enabled;

    @Value("${app.ia.ollama.base-url:http://127.0.0.1:11434}")
    private String baseUrl;

    @Value("${app.ia.ollama.model:llama3.2}")
    private String model;

    @Value("${app.ia.ollama.connect-timeout:8}")
    private int connectTimeout;

    @Value("${app.ia.ollama.read-timeout:180}")
    private int readTimeout;

    @Value("${app.ia.ollama.temperature:0.2}")
    private double temperature;

    @Value("${app.ia.ollama.max-tokens:2048}")
    private int maxTokens;

    @Bean(name = "ollamaRestTemplate")
    public RestTemplate ollamaRestTemplate() {
        log.info("Configurando RestTemplate para Ollama local {} (modelo {})", baseUrl, model);
        return new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(connectTimeout))
                .setReadTimeout(Duration.ofSeconds(readTimeout))
                .requestFactory(() -> {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(connectTimeout * 1000);
                    factory.setReadTimeout(readTimeout * 1000);
                    return factory;
                })
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
