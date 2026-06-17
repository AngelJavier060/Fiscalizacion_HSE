package com.fiscalizacionhse.service;

import com.fiscalizacionhse.exception.BadRequestException;
import com.fiscalizacionhse.exception.ResourceNotFoundException;
import com.fiscalizacionhse.model.PermisoTrabajo;
import com.fiscalizacionhse.repository.PermisoTrabajoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

@Service
@Slf4j
public class PermisoArchivoService {

    private final PermisoTrabajoRepository repository;
    private final Path uploadDir;

    public record ArchivoPermiso(Resource resource, String nombreArchivo, String tipoMime) {}

    public PermisoArchivoService(PermisoTrabajoRepository repository) {
        this.repository = repository;
        this.uploadDir = Path.of("uploads/permisos");
        try {
            Files.createDirectories(uploadDir);
            log.info("📁 Directorio de uploads de permisos creado: {}", uploadDir.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear el directorio de uploads de permisos", e);
        }
    }

    /**
     * Subir un archivo (PDF/imagen) asociado a un permiso de trabajo.
     * El archivo se guarda como: {permitId}_{nombreOriginal}
     */
    @Transactional
    public String subirArchivo(String permitId, MultipartFile archivo) {
        PermisoTrabajo permiso = repository.findById(permitId)
                .orElseThrow(() -> new ResourceNotFoundException("PermisoTrabajo", permitId));

        if (archivo == null || archivo.isEmpty()) {
            throw new BadRequestException("Debe adjuntar un archivo");
        }

        String nombreOriginal = archivo.getOriginalFilename();
        if (nombreOriginal == null || nombreOriginal.isBlank()) {
            nombreOriginal = "documento.pdf";
        }

        // Nombre: PT_2026_1234_nombreOriginal.pdf
        String nombreSeguro = permitId.replaceAll("[^a-zA-Z0-9_\\-]", "_")
                + "_" + nombreOriginal.replaceAll("[^a-zA-Z0-9_.\\-]", "_");

        Path rutaDestino = uploadDir.resolve(nombreSeguro);

        try {
            Files.copy(archivo.getInputStream(), rutaDestino, StandardCopyOption.REPLACE_EXISTING);
            log.info("✅ Archivo de permiso guardado: {}", rutaDestino);
        } catch (IOException e) {
            log.error("❌ Error al guardar archivo del permiso {}: {}", permitId, e.getMessage());
            throw new RuntimeException("Error al guardar el archivo del permiso", e);
        }

        // Actualizar imagePath con la ruta del servidor
        String rutaServidor = rutaDestino.toString();
        String imagePathActual = permiso.getImagePath();
        if (imagePathActual == null || imagePathActual.isBlank()) {
            permiso.setImagePath(rutaServidor);
        } else {
            // Concatenar si ya hay rutas previas (separador "|")
            permiso.setImagePath(imagePathActual + "|" + rutaServidor);
        }
        repository.save(permiso);

        return rutaServidor;
    }

    /**
     * Obtener un archivo del permiso para descarga/visualización
     */
    public ArchivoPermiso obtenerArchivo(String permitId, String nombreArchivo) {
        repository.findById(permitId)
                .orElseThrow(() -> new ResourceNotFoundException("PermisoTrabajo", permitId));

        // Buscar el archivo en el directorio de uploads
        Path path = uploadDir.resolve(nombreArchivo);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("Archivo del permiso no encontrado en el servidor");
        }

        Resource resource = new FileSystemResource(path.toFile());
        String tipoMime = detectarTipoMime(nombreArchivo);

        return new ArchivoPermiso(resource, nombreArchivo, tipoMime);
    }

    /**
     * Obtener el primer archivo asociado a un permiso
     */
    public ArchivoPermiso obtenerPrimerArchivo(String permitId) {
        PermisoTrabajo permiso = repository.findById(permitId)
                .orElseThrow(() -> new ResourceNotFoundException("PermisoTrabajo", permitId));

        String imagePath = permiso.getImagePath();
        if (imagePath == null || imagePath.isBlank()) {
            throw new ResourceNotFoundException("El permiso no tiene archivos adjuntos");
        }

        // Tomar la primera ruta (antes del |)
        String primeraRuta = imagePath.split("\\|")[0].trim();
        Path path = Path.of(primeraRuta);

        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("Archivo del permiso no encontrado en el servidor: " + primeraRuta);
        }

        Resource resource = new FileSystemResource(path.toFile());
        String nombreArchivo = path.getFileName().toString();
        String tipoMime = detectarTipoMime(nombreArchivo);

        return new ArchivoPermiso(resource, nombreArchivo, tipoMime);
    }

    /**
     * Eliminar archivo físico de un permiso
     */
    public void eliminarArchivo(String rutaArchivo) {
        try {
            Files.deleteIfExists(Path.of(rutaArchivo));
            log.info("🗑️ Archivo de permiso eliminado: {}", rutaArchivo);
        } catch (IOException e) {
            log.warn("⚠️ No se pudo eliminar el archivo: {}", rutaArchivo);
        }
    }

    private String detectarTipoMime(String nombreArchivo) {
        if (nombreArchivo == null) return "application/octet-stream";
        String lower = nombreArchivo.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }
}
