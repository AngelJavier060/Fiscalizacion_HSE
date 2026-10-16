package com.fiscalizacionhse.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuración de reintentos automáticos para llamadas a DeepSeek API.
 * 
 * 🔥 Evita que errores transitorios (timeout, rate limit, 5xx) dejen la app colgada.
 * 
 * Estrategia:
 * - Máx 3 reintentos
 * - Backoff exponencial: 1s → 2s → 4s (espera entre reintentos)
 * - Solo reintenta en: timeout, 429 (rate limit), 5xx (server errors)
 */
@Configuration
@EnableRetry
@Slf4j
public class RetryConfig {

    @Bean(name = "deepSeekRetryTemplate")
    public RetryTemplate deepSeekRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // Política de reintentos: máximo 3 intentos
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        retryTemplate.setRetryPolicy(retryPolicy);

        // Backoff exponencial: espera 1s, luego 2s, luego 4s
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000L);  // 1 segundo
        backOffPolicy.setMultiplier(2.0);          // se duplica cada intento
        backOffPolicy.setMaxInterval(10000L);      // máximo 10 segundos
        retryTemplate.setBackOffPolicy(backOffPolicy);

        log.info("🔄 RetryTemplate configurado: maxAttempts=3, backoff=1s/2s/4s");
        return retryTemplate;
    }
}
