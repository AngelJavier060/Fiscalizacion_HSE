package com.fiscalizacionhse.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    /**
     * Orígenes permitidos, separados por coma.
     * Por defecto: Angular (4200) y Flutter web (puerto aleatorio en localhost).
     * En producción defina CORS_ALLOWED_ORIGINS con su dominio real.
     */
    @Value("${app.cors.allowed-origins:http://localhost:4200,http://localhost:8100,http://localhost:8081,http://localhost}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // allowedOriginPatterns (no allowedOrigins) permite credenciales y
        // el puerto aleatorio de Flutter web (localhost:56341, etc.).
        ArrayList<String> patterns = new ArrayList<>(parseOrigins(allowedOrigins));
        patterns.add("http://localhost:*");
        patterns.add("http://127.0.0.1:*");
        config.setAllowedOriginPatterns(patterns);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static List<String> parseOrigins(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
