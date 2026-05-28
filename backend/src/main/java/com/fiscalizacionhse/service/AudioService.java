package com.fiscalizacionhse.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Servicio de Text-to-Speech (TTS) para generar recordatorios en audio.
 * Soporta múltiples motores:
 *   1. Google Cloud TTS (primario)
 *   2. DeepSeek Audio API (alternativo)
 *   3. gTTS vía línea de comandos (fallback)
 *   4. Modo simulado (sin API key)
 */
@Service
@Slf4j
public class AudioService {

    private final RestTemplate restTemplate;
    private final Path audioDir;
    private final String googleApiKey;
    private final String deepseekApiKey;
    private final String deepseekApiUrl;

    public AudioService(
            @Value("${app.tts.google-api-key:}") String googleApiKey,
            @Value("${app.tts.deepseek-api-key:}") String deepseekApiKey,
            @Value("${app.tts.deepseek-api-url:https://api.deepseek.com/v1/audio/speech}") String deepseekApiUrl) {
        this.restTemplate = new RestTemplate();
        this.googleApiKey = googleApiKey;
        this.deepseekApiKey = deepseekApiKey;
        this.deepseekApiUrl = deepseekApiUrl;
        this.audioDir = Path.of("uploads/audios");
        try {
            Files.createDirectories(audioDir);
            log.info("📁 Directorio de audios creado: {}", audioDir.toAbsolutePath());
        } catch (IOException e) {
            log.error("❌ No se pudo crear directorio de audios", e);
            throw new RuntimeException("Error al crear directorio de audios", e);
        }
    }

    /**
     * Genera un audio a partir del texto
     * @return AudioResult con la ruta y duración, o null si falla
     */
    public AudioResult generarAudio(String titulo, String texto) {
        if (texto == null || texto.isBlank()) {
            log.warn("⚠️ Texto vacío para generar audio");
            return null;
        }

        String nombreArchivo = UUID.randomUUID() + ".mp3";
        Path rutaAudio = audioDir.resolve(nombreArchivo);

        // 1. Intentar con Google Cloud TTS
        if (googleApiKey != null && !googleApiKey.isBlank()) {
            try {
                return generarConGoogleTTS(titulo, texto, nombreArchivo, rutaAudio);
            } catch (Exception e) {
                log.warn("⚠️ Google TTS falló: {}", e.getMessage());
            }
        }

        // 2. Intentar con DeepSeek Audio API
        if (deepseekApiKey != null && !deepseekApiKey.isBlank()) {
            try {
                return generarConDeepSeek(titulo, texto, nombreArchivo, rutaAudio);
            } catch (Exception e) {
                log.warn("⚠️ DeepSeek Audio falló: {}", e.getMessage());
            }
        }

        // 3. Intentar con gTTS (vía línea de comandos)
        try {
            return generarConGTTS(texto, nombreArchivo, rutaAudio);
        } catch (Exception e) {
            log.warn("⚠️ gTTS falló: {}", e.getMessage());
        }

        // 4. Modo simulado — crear archivo de texto como marcador
        log.info("🔊 Modo simulado: audio generado como texto para '{}'", titulo);
        return generarAudioSimulado(titulo, texto, nombreArchivo, rutaAudio);
    }

    private AudioResult generarConGoogleTTS(String titulo, String texto,
                                             String nombreArchivo, Path rutaAudio)
            throws IOException {
        String url = "https://texttospeech.googleapis.com/v1/text:synthesize?key=" + googleApiKey;

        String textoLimitado = texto.length() > 2000 ? texto.substring(0, 2000) + "..." : texto;

        Map<String, Object> requestBody = Map.of(
                "input", Map.of("text", textoLimitado),
                "voice", Map.of(
                        "languageCode", "es-ES",
                        "name", "es-ES-Standard-A",
                        "ssmlGender", "FEMALE"
                ),
                "audioConfig", Map.of(
                        "audioEncoding", "MP3",
                        "speakingRate", 0.9,
                        "pitch", 0.0
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST,
                new HttpEntity<>(requestBody, headers),
                Map.class
        );

        String audioContent = (String) response.getBody().get("audioContent");
        byte[] audioBytes = java.util.Base64.getDecoder().decode(audioContent);
        Files.write(rutaAudio, audioBytes);

        int duracion = calcularDuracionEstimada(textoLimitado);
        log.info("✅ Google TTS: audio generado '{}' ({}s)", nombreArchivo, duracion);

        return new AudioResult(rutaAudio.toString(), duracion);
    }

    private AudioResult generarConDeepSeek(String titulo, String texto,
                                            String nombreArchivo, Path rutaAudio)
            throws IOException {
        String textoLimitado = texto.length() > 1500 ? texto.substring(0, 1500) + "..." : texto;

        Map<String, Object> requestBody = Map.of(
                "model", "deepseek-tts",
                "input", textoLimitado,
                "voice", "nova",
                "response_format", "mp3",
                "speed", 0.9
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(deepseekApiKey);

        ResponseEntity<byte[]> response = restTemplate.exchange(
                deepseekApiUrl, HttpMethod.POST,
                new HttpEntity<>(requestBody, headers),
                byte[].class
        );

        Files.write(rutaAudio, response.getBody());

        int duracion = calcularDuracionEstimada(textoLimitado);
        log.info("✅ DeepSeek TTS: audio generado '{}' ({}s)", nombreArchivo, duracion);

        return new AudioResult(rutaAudio.toString(), duracion);
    }

    private AudioResult generarConGTTS(String texto, String nombreArchivo, Path rutaAudio)
            throws IOException, InterruptedException {
        // Crear archivo temporal de texto
        Path tempTxt = audioDir.resolve("temp_" + UUID.randomUUID() + ".txt");
        Files.writeString(tempTxt, texto);

        // Llamar a gTTS: gtts-cli --file temp.txt --output audio.mp3 --lang es
        ProcessBuilder pb = new ProcessBuilder(
                "gtts-cli",
                "--file", tempTxt.toString(),
                "--output", rutaAudio.toString(),
                "--lang", "es"
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();
        int exitCode = process.waitFor();

        Files.deleteIfExists(tempTxt); // limpiar

        if (exitCode == 0 && Files.exists(rutaAudio) && Files.size(rutaAudio) > 0) {
            int duracion = calcularDuracionEstimada(texto);
            log.info("✅ gTTS: audio generado '{}' ({}s, {} bytes)",
                    nombreArchivo, duracion, Files.size(rutaAudio));
            return new AudioResult(rutaAudio.toString(), duracion);
        }

        throw new RuntimeException("gTTS falló con código: " + exitCode);
    }

    private AudioResult generarAudioSimulado(String titulo, String texto,
                                              String nombreArchivo, Path rutaAudio) {
        try {
            // En modo simulado, crear un archivo .txt con el texto del audio
            String nombreTxt = nombreArchivo.replace(".mp3", ".txt");
            Path rutaTxt = audioDir.resolve(nombreTxt);

            String contenido = String.format("""
                ==========================================
                🎯 RECORDATORIO: %s
                📅 Generado: %s
                ==========================================
                
                %s
                
                ==========================================
                🔊 Para escuchar este audio:
                - En el teléfono: usa el reproductor de audio
                - Configura DeepSeek/Google TTS en application.yml
                ==========================================
                """, titulo, java.time.LocalDateTime.now(), texto);

            Files.writeString(rutaTxt, contenido);

            // También copiar como .mp3 simulado (archivo de texto)
            Files.writeString(rutaAudio, contenido);

            int duracion = calcularDuracionEstimada(texto);
            log.info("🔊 Audio simulado: '{}' ({})", nombreArchivo, rutaTxt);

            return new AudioResult(rutaAudio.toString(), duracion);

        } catch (IOException e) {
            log.error("❌ Error generando audio simulado: {}", e.getMessage());
            return null;
        }
    }

    private int calcularDuracionEstimada(String texto) {
        // Aprox: 150 palabras por minuto = 2.5 palabras por segundo
        int palabras = texto.split("\\s+").length;
        return Math.max(5, (int) Math.ceil(palabras / 2.5));
    }

    public byte[] obtenerAudioBytes(String rutaAudio) {
        try {
            Path archivo = Path.of(rutaAudio);
            if (Files.exists(archivo)) {
                return Files.readAllBytes(archivo);
            }
            // Fallback: buscar .txt
            Path txtFile = Path.of(rutaAudio.replace(".mp3", ".txt"));
            if (Files.exists(txtFile)) {
                return Files.readAllBytes(txtFile);
            }
        } catch (IOException e) {
            log.error("❌ Error leyendo audio: {}", e.getMessage());
        }
        return null;
    }

    public record AudioResult(String rutaArchivo, int duracionSegundos) {}
}
