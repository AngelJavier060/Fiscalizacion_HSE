package com.fiscalizacionhse.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
public class PdfService {

    private static final int TIMEOUT_PAGINA_SEG = 12;
    private static final int MIN_CHARS_PAGINA = 50;
    private static final float DPI_OCR = 200f;

    private static final ExecutorService PAGE_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "pdf-extract-page");
        t.setDaemon(true);
        return t;
    });

    private final DocumentoExtraccionProgresoStore progreso;
    private final DocumentoOcrService ocr;
    private final Path uploadDir;

    public PdfService(DocumentoExtraccionProgresoStore progreso, DocumentoOcrService ocr) {
        this.progreso = progreso;
        this.ocr = ocr;
        this.uploadDir = Path.of("uploads/documentos");
        try {
            Files.createDirectories(uploadDir);
            log.info("📁 Directorio de uploads creado: {}", uploadDir.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear el directorio de uploads", e);
        }
    }

    public String guardarArchivo(MultipartFile archivo) {
        try {
            String nombreUnico = UUID.randomUUID() + "_" + archivo.getOriginalFilename();
            Path rutaDestino = uploadDir.resolve(nombreUnico);
            Files.copy(archivo.getInputStream(), rutaDestino, StandardCopyOption.REPLACE_EXISTING);
            log.info("✅ PDF guardado: {}", rutaDestino);
            return rutaDestino.toString();
        } catch (IOException e) {
            log.error("❌ Error al guardar PDF: {}", e.getMessage());
            throw new RuntimeException("Error al guardar el archivo PDF", e);
        }
    }

    public String extraerTexto(String rutaArchivo) {
        return extraerTexto(rutaArchivo, null);
    }

    /**
     * Extrae letras página a página. Primera pasada rápida; si la página trae poco texto
     * (infográfico/tabla) reintenta ordenando por posición.
     */
    public String extraerTexto(String rutaArchivo, Long documentoId) {
        File file = new File(rutaArchivo);
        if (!file.exists()) {
            log.warn("⚠️ Archivo no encontrado: {}", rutaArchivo);
            return "";
        }

        if (documentoId != null) {
            progreso.abriendoPdf(documentoId);
        }

        try (RandomAccessReadBufferedFile access = new RandomAccessReadBufferedFile(file);
             PDDocument document = Loader.loadPDF(access)) {
            int paginas = document.getNumberOfPages();
            log.info("📝 Extrayendo texto: {} ({} páginas)", file.getName(), paginas);
            if (documentoId != null) {
                progreso.pagina(documentoId, 0, paginas);
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= paginas; i++) {
                if (documentoId != null) {
                    progreso.pagina(documentoId, i, paginas);
                }
                String pagina = extraerPagina(document, i, false);
                if (pagina.trim().length() < MIN_CHARS_PAGINA) {
                    String lenta = extraerPagina(document, i, true);
                    if (lenta.trim().length() > pagina.trim().length()) {
                        pagina = lenta;
                    }
                }
                if (pagina.trim().length() < MIN_CHARS_PAGINA) {
                    String leido = extraerPaginaPorOcr(document, i, paginas, documentoId);
                    if (leido.trim().length() > pagina.trim().length()) {
                        pagina = leido;
                    }
                }
                sb.append(pagina).append('\n');
            }

            String texto = sb.toString();
            if (texto.isBlank()) {
                log.warn("⚠️ El PDF no contiene capa de texto (parece imagen): {}", rutaArchivo);
                return "";
            }
            log.info("✅ Texto extraído: {} caracteres ({} páginas)", texto.length(), paginas);
            return texto;

        } catch (IOException e) {
            log.error("❌ Error al extraer texto del PDF: {}", e.getMessage());
            throw new RuntimeException("Error al extraer texto del PDF", e);
        }
    }

    private String extraerPaginaPorOcr(PDDocument document, int pagina, int paginasTotal, Long documentoId) {
        if (!ocr.disponible()) {
            return "";
        }
        if (documentoId != null) {
            progreso.ocr(documentoId, pagina, paginasTotal);
        }
        try {
            byte[] png = renderizarPagina(document, pagina, DPI_OCR);
            String leido = ocr.reconocerPng(png);
            if (!leido.isBlank()) {
                log.info("🔎 OCR página {}: {} caracteres", pagina, leido.length());
            }
            return leido;
        } catch (Exception e) {
            log.warn("⚠️ OCR página {} omitido: {}", pagina, e.getMessage());
            return "";
        }
    }

    private String extraerPagina(PDDocument document, int pagina, boolean sortByPosition) {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(sortByPosition);
        stripper.setStartPage(pagina);
        stripper.setEndPage(pagina);
        Future<String> futuro = PAGE_EXECUTOR.submit(() -> stripper.getText(document));
        try {
            return futuro.get(TIMEOUT_PAGINA_SEG, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            futuro.cancel(true);
            log.warn("⚠️ Página {} agotó {}s (sort={})", pagina, TIMEOUT_PAGINA_SEG, sortByPosition);
            return "";
        } catch (Exception e) {
            log.warn("⚠️ Página {} omitida: {}", pagina, e.getMessage());
            return "";
        }
    }

    public byte[] renderizarPaginaComoPng(File archivoPdf, int paginaDesdeUno, float dpi) throws IOException {
        if (!archivoPdf.exists()) {
            throw new FileNotFoundException("Archivo no encontrado");
        }
        try (RandomAccessReadBufferedFile access = new RandomAccessReadBufferedFile(archivoPdf);
             PDDocument document = Loader.loadPDF(access)) {
            int total = document.getNumberOfPages();
            if (paginaDesdeUno < 1 || paginaDesdeUno > total) {
                throw new IllegalArgumentException(
                        "La página debe estar entre 1 y " + total + " (consultada: " + paginaDesdeUno + ")");
            }
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImageWithDPI(paginaDesdeUno - 1, dpi, ImageType.RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        }
    }

    private byte[] renderizarPagina(PDDocument document, int paginaDesdeUno, float dpi) throws IOException {
        PDFRenderer renderer = new PDFRenderer(document);
        BufferedImage image = renderer.renderImageWithDPI(paginaDesdeUno - 1, dpi, ImageType.RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    public void eliminarArchivo(String rutaArchivo) {
        try {
            Files.deleteIfExists(Path.of(rutaArchivo));
            log.info("🗑️ PDF eliminado: {}", rutaArchivo);
        } catch (IOException e) {
            log.warn("⚠️ No se pudo eliminar el archivo: {}", rutaArchivo);
        }
    }

    public Path getUploadDir() {
        return uploadDir;
    }
}
