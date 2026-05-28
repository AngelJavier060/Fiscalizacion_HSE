package com.fiscalizacionhse.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
@Slf4j
public class IdiomaService {

    // Patrones para detectar idioma basado en palabras comunes
    private static final Pattern PATRON_ESPANOL = Pattern.compile(
            "\\b(los|las|del|para|con|por|que|más|entre|sobre|según|como|este|esta|ello|ello|dicha|dicho|norma|ley|reglamento|artículo|capítulo|título|disposición|obligación|derecho|sanción|infracción|inspección|fiscalización|hse|seguridad|salud|ambiente|trabajo|riesgo|accidente|prevención)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern PATRON_INGLES = Pattern.compile(
            "\\b(the|and|for|with|from|that|this|shall|must|should|pursuant|whereas|hereby|thereof|therein|thereto|health|safety|environment|regulation|standard|compliance|requirement|obligation|inspection|violation|penalty|employer|employee|workplace|hazard|risk|accident|prevention|protection)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final int UMBRAL_MINIMO = 3; // Mínimo de palabras coincidentes

    /**
     * Detecta el idioma del texto extraído
     * @return "es" para español, "en" para inglés, "desconocido" si no se puede determinar
     */
    public String detectar(String texto) {
        if (texto == null || texto.isBlank()) {
            return "desconocido";
        }

        String textoLower = texto.toLowerCase();

        // Contar coincidencias en español
        var matcherEs = PATRON_ESPANOL.matcher(textoLower);
        int countEs = 0;
        while (matcherEs.find()) countEs++;

        // Contar coincidencias en inglés
        var matcherEn = PATRON_INGLES.matcher(textoLower);
        int countEn = 0;
        while (matcherEn.find()) countEn++;

        log.debug("🔍 Detección de idioma - Español: {}, Inglés: {}", countEs, countEn);

        if (countEs >= UMBRAL_MINIMO && countEs > countEn) {
            return "es";
        } else if (countEn >= UMBRAL_MINIMO && countEn > countEs) {
            return "en";
        } else if (countEs > 0 && countEs == countEn) {
            // Empate - revisar primeras palabras
            String inicio = textoLower.substring(0, Math.min(200, textoLower.length()));
            if (inicio.contains("the ") || inicio.contains("this ") || inicio.contains("shall ")) {
                return "en";
            }
            return "es";
        }

        return "desconocido";
    }
}
