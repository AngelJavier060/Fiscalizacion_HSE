package com.fiscalizacionhse.service;

import com.fiscalizacionhse.dto.response.AnalizarReporteResponse;
import com.fiscalizacionhse.exception.BadRequestException;
import com.fiscalizacionhse.model.Documento;
import com.fiscalizacionhse.repository.DocumentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalizarReporteService {

    private static final int MAX_INFORME_OLLAMA = 7000;
    private static final int MAX_CITAS_OLLAMA = 9000;
    private static final int MAX_COINCIDENCIAS = 10;
    private static final int SNIPPET = 380;
    private static final int MIN_INFORME = 40;

    private static final Set<String> STOPWORDS = Set.of(
            "quiero", "saber", "pregunta", "dime", "hola", "buenas", "este", "esta", "estos", "estas",
            "eso", "esa", "esos", "esas", "para", "por", "con", "sin", "como", "cual", "cuales", "donde",
            "cuando", "quien", "sobre", "desde", "hasta", "toda", "todo", "todos", "todas", "algo", "nada",
            "muy", "mas", "menos", "debe", "puede", "hacer", "tener", "ser", "son", "era", "fue", "han",
            "hay", "les", "del", "las", "los", "unos", "una", "que", "de", "el", "la", "en", "un", "se",
            "al", "lo", "le", "su", "sus", "mi", "me", "nos", "ya", "si", "no", "o", "y", "e", "u",
            "informe", "reporte", "whatsapp", "adjunto", "buenos", "dias", "tardes", "noches", "estimado",
            "saludos", "favor", "gracias", "aqui", "alla", "hoy", "ayer", "turno");

    private final DocumentoRepository documentoRepository;
    private final PdfService pdfService;
    private final DocumentoOcrService ocrService;
    private final OllamaClient ollamaClient;

    public AnalizarReporteResponse analizar(Long empresaId, String textoPegado, MultipartFile archivo) {
        if (empresaId == null || empresaId <= 0) {
            throw new BadRequestException("Indique la empresa.");
        }
        String informe = extraerInforme(textoPegado, archivo);
        if (informe.replaceAll("\\s+", "").length() < MIN_INFORME) {
            throw new BadRequestException(
                    "No hay texto suficiente en el informe. Péguelo o suba un PDF/foto con letras (si es foto, el OCR interno las lee).");
        }

        List<Documento> docs = documentoRepository.findByEmpresaIdAndActivoTrue(empresaId);
        List<Documento> conTexto = docs.stream()
                .filter(d -> d.getTextoExtraido() != null && d.getTextoExtraido().replaceAll("\\s+", "").length() > 40)
                .toList();

        LinkedHashSet<String> terminos = extraerTerminos(informe);
        List<AnalizarReporteResponse.Coincidencia> coincidencias = buscarCoincidencias(conTexto, terminos);
        List<String> huecos = detectarHuecos(informe, coincidencias, conTexto, terminos);

        OllamaClient.EstadoOllama estado = ollamaClient.estado();
        String propuesto;
        String motor;
        if (estado.listo()) {
            try {
                propuesto = redactarConOllama(informe, coincidencias, huecos);
                motor = "ollama";
            } catch (Exception e) {
                log.warn("Ollama falló al redactar, se usa plantilla: {}", e.getMessage());
                propuesto = redactarPlantilla(informe, coincidencias, huecos);
                motor = "extractivo";
                estado = new OllamaClient.EstadoOllama(
                        estado.procesoActivo(), false, estado.modelo(), estado.modelos(),
                        "Ollama no pudo redactar (" + e.getMessage() + "). Se armó el cruce con citas de sus PDF.");
            }
        } else {
            propuesto = redactarPlantilla(informe, coincidencias, huecos);
            motor = "extractivo";
        }

        String nombreArchivo = archivo != null && !archivo.isEmpty() ? archivo.getOriginalFilename() : null;
        return AnalizarReporteResponse.builder()
                .motor(motor)
                .ollamaListo(estado.listo() && "ollama".equals(motor))
                .modelo(estado.modelo())
                .avisoMotor(estado.mensaje())
                .caracteresInforme(informe.length())
                .archivoNombre(nombreArchivo)
                .documentosConsultados(conTexto.size())
                .coincidencias(coincidencias)
                .huecos(huecos)
                .textoPropuesto(propuesto)
                .build();
    }

    private String extraerInforme(String textoPegado, MultipartFile archivo) {
        String pegado = textoPegado != null ? textoPegado.trim() : "";
        if (archivo == null || archivo.isEmpty()) {
            return pegado;
        }
        String extraido = extraerDeArchivo(archivo);
        if (pegado.isBlank()) {
            return extraido;
        }
        if (extraido.isBlank()) {
            return pegado;
        }
        return pegado + "\n\n--- Archivo ---\n" + extraido;
    }

    private String extraerDeArchivo(MultipartFile archivo) {
        String nombre = archivo.getOriginalFilename() != null
                ? archivo.getOriginalFilename().toLowerCase(Locale.ROOT)
                : "";
        String tipo = archivo.getContentType() != null ? archivo.getContentType().toLowerCase(Locale.ROOT) : "";
        try {
            if (nombre.endsWith(".txt") || nombre.endsWith(".md") || tipo.startsWith("text/")) {
                return new String(archivo.getBytes(), StandardCharsets.UTF_8).trim();
            }
            if (nombre.endsWith(".pdf") || tipo.contains("pdf")) {
                Path tmp = Files.createTempFile("hse-reporte-", ".pdf");
                try {
                    Files.copy(archivo.getInputStream(), tmp, StandardCopyOption.REPLACE_EXISTING);
                    String texto = pdfService.extraerTexto(tmp.toAbsolutePath().toString(), null);
                    return texto != null ? texto.trim() : "";
                } finally {
                    Files.deleteIfExists(tmp);
                }
            }
            if (esImagen(nombre, tipo)) {
                BufferedImage img = ImageIO.read(archivo.getInputStream());
                if (img == null) {
                    return "";
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "png", baos);
                return ocrService.reconocerPng(baos.toByteArray());
            }
            throw new BadRequestException("Use PDF, foto (JPG/PNG) o un .txt (por ejemplo un chat exportado de WhatsApp).");
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.warn("No se pudo leer el informe {}: {}", nombre, e.getMessage());
            throw new BadRequestException("No se pudo leer el archivo del informe.");
        }
    }

    private static boolean esImagen(String nombre, String tipo) {
        return tipo.startsWith("image/")
                || nombre.endsWith(".png")
                || nombre.endsWith(".jpg")
                || nombre.endsWith(".jpeg")
                || nombre.endsWith(".webp")
                || nombre.endsWith(".bmp");
    }

    private List<AnalizarReporteResponse.Coincidencia> buscarCoincidencias(
            List<Documento> docs, LinkedHashSet<String> terminos) {
        List<AnalizarReporteResponse.Coincidencia> out = new ArrayList<>();
        if (docs.isEmpty() || terminos.isEmpty()) {
            return out;
        }
        for (Documento doc : docs) {
            String texto = doc.getTextoExtraido();
            String mejor = mejorSnippet(texto, terminos);
            int score = puntuar(texto, terminos);
            if (mejor == null || score < 2) {
                continue;
            }
            out.add(AnalizarReporteResponse.Coincidencia.builder()
                    .documentoId(doc.getId())
                    .documentoTitulo(doc.getTitulo())
                    .cita(mejor)
                    .relevancia(score)
                    .build());
        }
        out.sort(Comparator.comparingInt(AnalizarReporteResponse.Coincidencia::getRelevancia).reversed());
        if (out.size() > MAX_COINCIDENCIAS) {
            return new ArrayList<>(out.subList(0, MAX_COINCIDENCIAS));
        }
        return out;
    }

    private List<String> detectarHuecos(
            String informe,
            List<AnalizarReporteResponse.Coincidencia> coincidencias,
            List<Documento> docs,
            LinkedHashSet<String> terminos) {
        List<String> huecos = new ArrayList<>();
        if (docs.isEmpty()) {
            huecos.add("No hay PDF con texto extraído en esta empresa. Suba las normas a Documentos.");
            return huecos;
        }
        if (coincidencias.isEmpty()) {
            huecos.add("El informe no coincide con el texto extraído de los PDF cargados. Revise si faltan procedimientos o si el PDF es solo imagen.");
        }
        String corpus = docs.stream()
                .map(d -> normalizar(d.getTextoExtraido()))
                .reduce("", (a, b) -> a + "\n" + b);
        for (String term : terminos) {
            if (term.length() < 6) {
                continue;
            }
            if (!corpus.contains(term) && huecos.size() < 8) {
                huecos.add("En el informe aparece «" + term + "» y no consta en los documentos cargados.");
            }
        }
        return huecos;
    }

    private String redactarConOllama(
            String informe,
            List<AnalizarReporteResponse.Coincidencia> coincidencias,
            List<String> huecos) {
        String sistema = """
                Eres un analista HSE interno de esta empresa (FISCALIZA).
                Circuito cerrado: usa SOLO el INFORME y las CITAS de PDF de la empresa.
                Prohibido inventar normas, leyes, ISO o procedimientos que no estén en las CITAS.
                Si algo del informe no está en las citas, dilo como hueco: «No consta en los documentos cargados».
                Redacta en español, tono profesional de fiscalización, Markdown claro.
                Estructura obligatoria:
                ## Dictamen
                (2–4 frases: qué se observó y si hay respaldo normativo interno)
                ## Cruce con la normativa interna
                (viñetas: hallazgo del informe → cita / PDF)
                ## Huecos
                (lo que el informe menciona y no aparece en los PDF)
                ## Texto propuesto
                (párrafo listo para copiar: observaciones, qué verificar, referencia a los PDF citados)
                Cierra con: *Fuente: PDF cargados en la empresa. Motor local Ollama.*
                """;
        String user = "INFORME DE CAMPO:\n" + truncar(informe, MAX_INFORME_OLLAMA)
                + "\n\nCITAS DE LOS PDF DE LA EMPRESA:\n" + armarCitasParaPrompt(coincidencias)
                + "\n\nHUECOS YA DETECTADOS:\n"
                + (huecos.isEmpty() ? "(ninguno automático)\n" : String.join("\n", huecos));
        return ollamaClient.chat(sistema, user);
    }

    private String redactarPlantilla(
            String informe,
            List<AnalizarReporteResponse.Coincidencia> coincidencias,
            List<String> huecos) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Dictamen\n\n");
        if (coincidencias.isEmpty()) {
            sb.append("El informe se recibió, pero **no hay cruce claro** con los PDF de la empresa. ");
            sb.append("Suba el procedimiento correspondiente o reprocese PDFs que sean foto.\n\n");
        } else {
            sb.append("El informe se cruzó con **")
                    .append(coincidencias.size())
                    .append("** coincidencia(s) en la biblioteca HSE. ");
            sb.append("Lo que sigue está tomado de esos documentos; no se inventó normativa externa.\n\n");
        }
        sb.append("## Cruce con la normativa interna\n\n");
        if (coincidencias.isEmpty()) {
            sb.append("- Sin citas recuperadas.\n\n");
        } else {
            for (AnalizarReporteResponse.Coincidencia c : coincidencias) {
                sb.append("- **«").append(c.getDocumentoTitulo()).append("»:** ")
                        .append(c.getCita()).append("\n\n");
            }
        }
        sb.append("## Huecos\n\n");
        if (huecos.isEmpty()) {
            sb.append("- No se detectaron temas del informe ausentes en los PDF (con la búsqueda automática).\n\n");
        } else {
            for (String h : huecos) {
                sb.append("- ").append(h).append("\n");
            }
            sb.append("\n");
        }
        sb.append("## Texto propuesto\n\n");
        sb.append("Se revisó el reporte de campo contra los documentos internos de la empresa. ");
        if (!coincidencias.isEmpty()) {
            sb.append("Los hallazgos se respaldan principalmente en: ");
            List<String> titulos = coincidencias.stream()
                    .map(AnalizarReporteResponse.Coincidencia::getDocumentoTitulo)
                    .distinct()
                    .limit(4)
                    .toList();
            sb.append(String.join("; ", titulos)).append(". ");
        }
        if (!huecos.isEmpty()) {
            sb.append("Quedan puntos del informe que **no constan** en los PDF cargados y deben verificarse o documentarse. ");
        }
        sb.append("Se recomienda contrastar en terreno cada ítem citado y adjuntar evidencia fotográfica.\n\n");
        String muestra = truncar(informe.replaceAll("\\s+", " ").trim(), 420);
        sb.append("> Extracto del informe: ").append(muestra).append("\n\n");
        sb.append("*Fuente: PDF cargados en la empresa. Borrador por plantilla (Ollama no estaba listo).*\n");
        return sb.toString();
    }

    private static String armarCitasParaPrompt(List<AnalizarReporteResponse.Coincidencia> coincidencias) {
        if (coincidencias == null || coincidencias.isEmpty()) {
            return "(No hubo coincidencias en los PDF. No invente requisitos.)\n";
        }
        StringBuilder sb = new StringBuilder();
        for (AnalizarReporteResponse.Coincidencia c : coincidencias) {
            sb.append("- PDF: ").append(c.getDocumentoTitulo()).append("\n  Cita: ").append(c.getCita()).append("\n");
        }
        return truncar(sb.toString(), MAX_CITAS_OLLAMA);
    }

    private static LinkedHashSet<String> extraerTerminos(String texto) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String n = normalizar(texto);
        Matcher m = Pattern.compile("[a-zñ0-9]{4,}").matcher(n);
        while (m.find()) {
            String w = m.group();
            if (!STOPWORDS.contains(w)) {
                terms.add(w);
            }
        }
        return terms;
    }

    private static String mejorSnippet(String texto, LinkedHashSet<String> terminos) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String lower = texto.toLowerCase(Locale.ROOT);
        int best = -1;
        String bestTerm = null;
        for (String t : terminos) {
            if (t.length() < 4) {
                continue;
            }
            int idx = indexOfIgnore(lower, t);
            if (idx >= 0 && (best < 0 || t.length() > bestTerm.length())) {
                best = idx;
                bestTerm = t;
            }
        }
        if (best < 0) {
            return null;
        }
        int s = Math.max(0, best - SNIPPET / 3);
        int e = Math.min(texto.length(), best + SNIPPET);
        String excerpt = texto.substring(s, e).replaceAll("\\s+", " ").trim();
        if (excerpt.length() > 520) {
            excerpt = excerpt.substring(0, 517) + "…";
        }
        return excerpt;
    }

    private static int puntuar(String texto, LinkedHashSet<String> terminos) {
        if (texto == null) {
            return 0;
        }
        String n = normalizar(texto);
        int score = 0;
        for (String t : terminos) {
            if (t.length() >= 4 && n.contains(t)) {
                score += t.length() >= 7 ? 3 : 1;
            }
        }
        return score;
    }

    private static int indexOfIgnore(String haystackLower, String term) {
        return haystackLower.indexOf(term.toLowerCase(Locale.ROOT));
    }

    private static String normalizar(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder();
        for (int cp : n.codePoints().toArray()) {
            int t = Character.getType(cp);
            if (t != Character.NON_SPACING_MARK && t != Character.ENCLOSING_MARK) {
                sb.appendCodePoint(cp);
            }
        }
        return sb.toString();
    }

    private static String truncar(String texto, int max) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= max ? texto : texto.substring(0, max) + "\n…";
    }
}
