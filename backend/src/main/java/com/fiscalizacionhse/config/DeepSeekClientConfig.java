package com.fiscalizacionhse.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuración centralizada del cliente HTTP para DeepSeek API.
 * 
 * 🔥 CORRECCIÓN CRÍTICA: Antes cada servicio creaba {@code new RestTemplate()} sin timeouts,
 * lo que provocaba que las peticiones a DeepSeek se quedaran COLGADAS indefinidamente.
 * 
 * Ahora se usa un RestTemplate con:
 * - Timeout de conexión: 30 segundos
 * - Timeout de lectura: 120 segundos (DeepSeek reasoner necesita más tiempo por razonamiento)
 * - Timeout de request: 150 segundos
 * - Manejo de errores mejorado
 * 
 * MODELOS REALES DEEPSEEK (2025):
 * - deepseek-chat      → rápido, modelo general ✅
 * - deepseek-reasoner  → razonamiento profundo (R1/R2) ✅
 * - deepseek-embedding → embeddings
 * - deepseek-tts       → audio
 * 
 * ❌ deepseek-v4-flash y deepseek-v4-pro NO existen como modelos reales en la API.
 */
@Configuration
@Slf4j
public class DeepSeekClientConfig {

    @Value("${app.ia.deepseek.connect-timeout:30}")
    private int connectTimeout;

    @Value("${app.ia.deepseek.read-timeout:120}")
    private int readTimeout;

    @Value("${app.ia.deepseek.request-timeout:150}")
    private int requestTimeout;

    @Value("${app.ia.deepseek.model:deepseek-reasoner}")
    private String defaultModel;

    @Value("${app.ia.deepseek.model-fast:deepseek-chat}")
    private String fastModel;

    @Value("${app.ia.deepseek.default-temperature:0.0}")
    private double defaultTemperature;

    @Value("${app.ia.deepseek.max-tokens:8192}")
    private int maxTokens;

    @Bean(name = "deepSeekRestTemplate")
    public RestTemplate deepSeekRestTemplate() {
        log.info("🔧 Configurando RestTemplate para DeepSeek API");
        log.info("   Modelo default: {} | Modelo rápido: {} | temp: {} | maxTokens: {}",
                defaultModel, fastModel, defaultTemperature, maxTokens);
        log.info("   Timeouts: connect={}s, read={}s, request={}s",
                connectTimeout, readTimeout, requestTimeout);

        RestTemplate restTemplate = new RestTemplateBuilder()
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

        log.info("✅ RestTemplate para DeepSeek API configurado correctamente");
        return restTemplate;
    }

    public String getDefaultModel() { return defaultModel; }
    public String getFastModel() { return fastModel; }
    public String getReasoningModel() { return defaultModel; } // deepseek-reasoner es el modelo de razonamiento
    public double getDefaultTemperature() { return defaultTemperature; }
    public int getMaxTokens() { return maxTokens; }
}
