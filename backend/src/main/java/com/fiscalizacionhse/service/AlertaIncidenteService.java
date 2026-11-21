package com.fiscalizacionhse.service;

import com.fiscalizacionhse.dto.request.AlertaIncidenteRequest;
import com.fiscalizacionhse.dto.response.AlertaIncidenteResponse;
import com.fiscalizacionhse.exception.BadRequestException;
import com.fiscalizacionhse.exception.ConflictException;
import com.fiscalizacionhse.exception.ResourceNotFoundException;
import com.fiscalizacionhse.model.AlertaIncidente;
import com.fiscalizacionhse.model.Usuario;
import com.fiscalizacionhse.repository.AlertaIncidenteRepository;
import com.fiscalizacionhse.repository.UsuarioRepository;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Slf4j
public class AlertaIncidenteService {

    private static final long MAX_BYTES = 25L * 1024 * 1024;
    private static final Set<String> EXTENSIONES = Set.of("pdf", "png", "jpg", "jpeg");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final AlertaIncidenteRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final Path uploadDir;

    public record ArchivoAlerta(Resource resource, String nombre, String tipoMime) {}

    public AlertaIncidenteService(AlertaIncidenteRepository repository, UsuarioRepository usuarioRepository) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.uploadDir = Path.of("uploads/alertas");
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear la carpeta de evidencias de alertas", e);
        }
    }

    @Transactional(readOnly = true)
    public List<AlertaIncidenteResponse> listar() {
        return repository.findAllByOrderByFechaDescCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AlertaIncidenteResponse crear(AlertaIncidenteRequest request, MultipartFile archivo, long usuarioId) {
        String codigo = request.getCodigo().trim();
        if (repository.existsByCodigoIgnoreCase(codigo)) {
            throw new ConflictException("Ya existe una alerta con el código " + codigo);
        }
        String impacto = normalizarImpacto(request.getImpacto());

        Usuario autor = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", usuarioId));

        AlertaIncidente alerta = AlertaIncidente.builder()
                .codigo(codigo)
                .titulo(request.getTitulo().trim())
                .unidadNegocio(request.getUnidad().trim())
                .ambito(request.getAmbito().trim())
                .fecha(request.getFecha())
                .area(request.getArea().trim())
                .duenoArea(request.getDueno().trim())
                .hora(request.getHora())
                .empresaInvolucrada(request.getEmpresa().trim())
                .impacto(impacto)
                .accidenteGrave(request.getAccidenteGrave())
                .iap(request.getIap())
                .eorpv(request.getEorpv().trim())
                .prioridad(Boolean.TRUE.equals(request.getAccidenteGrave()) || Boolean.TRUE.equals(request.getIap())
                        ? "alta" : "media")
                .creadoPor(autor)
                .build();

        if (archivo != null && !archivo.isEmpty()) {
            guardarArchivo(alerta, archivo);
        }

        return toResponse(repository.save(alerta));
    }

    @Transactional(readOnly = true)
    public ArchivoAlerta obtenerArchivo(Long id) {
        AlertaIncidente alerta = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AlertaIncidente", id));
        if (alerta.getArchivoRuta() == null || alerta.getArchivoRuta().isBlank()) {
            throw new ResourceNotFoundException("Archivo de alerta", id);
        }
        Path ruta = Path.of(alerta.getArchivoRuta());
        if (!Files.exists(ruta)) {
            throw new ResourceNotFoundException("Archivo de alerta", id);
        }
        String nombre = alerta.getArchivoNombre() == null ? ruta.getFileName().toString() : alerta.getArchivoNombre();
        return new ArchivoAlerta(new FileSystemResource(ruta), nombre, tipoMime(nombre));
    }

    private void guardarArchivo(AlertaIncidente alerta, MultipartFile archivo) {
        if (archivo.getSize() > MAX_BYTES) {
            throw new BadRequestException("El archivo supera los 25 MB");
        }
        String original = archivo.getOriginalFilename();
        if (original == null || original.isBlank()) {
            original = "evidencia.pdf";
        }
        String limpio = original.replaceAll("[^a-zA-Z0-9_.\\-]", "_");
        String extension = extensionDe(limpio);
        if (!EXTENSIONES.contains(extension)) {
            throw new BadRequestException("Solo se admiten PDF, JPG o PNG");
        }
        String nombre = System.currentTimeMillis() + "_" + limpio;
        Path destino = uploadDir.resolve(nombre);
        try {
            Files.copy(archivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("No se guardó la evidencia de la alerta {}: {}", alerta.getCodigo(), e.getMessage());
            throw new BadRequestException("No se pudo guardar el archivo");
        }
        alerta.setArchivoNombre(original);
        alerta.setArchivoRuta(destino.toString());
    }

    private String normalizarImpacto(String valor) {
        if (valor == null) {
            throw new BadRequestException("El impacto a las personas es obligatorio");
        }
        String texto = valor.trim().toLowerCase(Locale.ROOT);
        if (texto.equals("sin lesion") || texto.equals("sin lesión")) {
            return "Sin lesión";
        }
        if (texto.equals("con lesion") || texto.equals("con lesión")) {
            return "Con lesión";
        }
        throw new BadRequestException("El impacto debe ser Sin lesión o Con lesión");
    }

    private AlertaIncidenteResponse toResponse(AlertaIncidente a) {
        String detalle = a.getImpacto() + " · " + a.getEmpresaInvolucrada();
        return AlertaIncidenteResponse.builder()
                .id(a.getId())
                .codigo(a.getCodigo())
                .titulo(a.getTitulo())
                .unidad(a.getUnidadNegocio())
                .ambito(a.getAmbito())
                .fecha(a.getFecha().toString())
                .area(a.getArea())
                .dueno(a.getDuenoArea())
                .hora(a.getHora().format(HORA))
                .empresa(a.getEmpresaInvolucrada())
                .impacto(a.getImpacto())
                .accidenteGrave(a.getAccidenteGrave())
                .iap(a.getIap())
                .eorpv(a.getEorpv())
                .archivoNombre(a.getArchivoNombre())
                .tieneArchivo(a.getArchivoRuta() != null && !a.getArchivoRuta().isBlank())
                .prioridad(a.getPrioridad())
                .detalle(detalle)
                .build();
    }

    private String extensionDe(String nombre) {
        int punto = nombre.lastIndexOf('.');
        if (punto < 0 || punto == nombre.length() - 1) {
            return "";
        }
        return nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
    }

    private String tipoMime(String nombre) {
        return switch (extensionDe(nombre)) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            default -> "application/pdf";
        };
    }
}
