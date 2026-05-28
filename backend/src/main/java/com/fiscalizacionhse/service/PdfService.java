package com.fiscalizacionhse.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
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

@Service
@Slf4j
public class PdfService {

    private final Path uploadDir;

    public PdfService() {
        this.uploadDir = Path.of("uploads/documentos");
        try {
            Files.createDirectories(uploadDir);
            log.info("📁 Directorio de uploads creado: {}", uploadDir.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear el directorio de uploads", e);
        }
    }

    /**
     * Guarda el archivo PDF en el sistema de archivos
     */
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

    /**
     * Extrae el texto completo de un PDF
     */
    public String extraerTexto(String rutaArchivo) {
        File file = new File(rutaArchivo);
        if (!file.exists()) {
            log.warn("⚠️ Archivo no encontrado: {}", rutaArchivo);
            return "";
        }

        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String texto = stripper.getText(document);

            if (texto.isBlank()) {
                log.warn("⚠️ El PDF no contiene texto extraíble: {}", rutaArchivo);
                return "";
            }

            log.info("✅ Texto extraído: {} caracteres", texto.length());
            return texto;

        } catch (IOException e) {
            log.error("❌ Error al extraer texto del PDF: {}", e.getMessage());
            throw new RuntimeException("Error al extraer texto del PDF", e);
        }
    }

    /**
     * Renderiza una página del PDF como PNG (uso: vista rápida en UI; DPI moderado por tamaño).
     *
     * @param paginaDesdeUno número de página 1-based
     * @param dpi típicamente 72–132
     */
    public byte[] renderizarPaginaComoPng(File archivoPdf, int paginaDesdeUno, float dpi) throws IOException {
        if (!archivoPdf.exists()) {
            throw new FileNotFoundException("Archivo no encontrado");
        }
        try (PDDocument document = Loader.loadPDF(archivoPdf)) {
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

    /**
     * Elimina un archivo PDF del sistema
     */
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
