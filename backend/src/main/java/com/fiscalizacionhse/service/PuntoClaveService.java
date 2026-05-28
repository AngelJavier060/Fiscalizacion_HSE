package com.fiscalizacionhse.service;

import com.fiscalizacionhse.dto.request.PuntoClaveRequest;
import com.fiscalizacionhse.dto.response.PuntoClaveResponse;
import com.fiscalizacionhse.exception.BadRequestException;
import com.fiscalizacionhse.exception.ResourceNotFoundException;
import com.fiscalizacionhse.model.Documento;
import com.fiscalizacionhse.model.PuntoClave;
import com.fiscalizacionhse.model.Usuario;
import com.fiscalizacionhse.repository.DocumentoRepository;
import com.fiscalizacionhse.repository.PuntoClaveRepository;
import com.fiscalizacionhse.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PuntoClaveService {

    private final PuntoClaveRepository puntoClaveRepository;
    private final DocumentoRepository documentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    /**
     * Listar puntos clave de un documento
     */
    public List<PuntoClaveResponse> listarPorDocumento(Long documentoId) {
        return puntoClaveRepository.findByDocumentoIdOrderByOrdenAsc(documentoId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Obtener un punto clave por ID
     */
    public PuntoClaveResponse obtener(Long id) {
        return toResponse(buscar(id));
    }

    /**
     * Crear un punto clave manualmente
     */
    @Transactional
    public PuntoClaveResponse crearManual(PuntoClaveRequest request, Long usuarioId) {
        Documento documento = documentoRepository.findById(request.getDocumentoId())
                .orElseThrow(() -> new ResourceNotFoundException("Documento", request.getDocumentoId()));

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", usuarioId));

        if (request.getContenido() == null || request.getContenido().isBlank()) {
            throw new BadRequestException("El contenido del punto clave no puede estar vacío");
        }

        PuntoClave punto = PuntoClave.builder()
                .contenido(request.getContenido())
                .titulo(request.getTitulo())
                .tema(request.getTema())
                .codigo(request.getCodigo())
                .tipo(request.getTipo() != null ? request.getTipo() : "MANUAL")
                .orden(request.getOrden() != null ? request.getOrden() : obtenerProximoOrden(documento.getId()))
                .esIa(false)
                .revisado(true) // Manual siempre está revisado
                .documento(documento)
                .creadoPor(usuario)
                .build();

        punto = puntoClaveRepository.save(punto);

        auditoriaService.registrar(
                usuario, documento.getEmpresa(), "CREAR_PUNTO_CLAVE", "PuntoClave",
                punto.getId(), "Punto clave creado manualmente en documento: " + documento.getTitulo(), null);

        return toResponse(punto);
    }

    /**
     * Crear múltiples puntos clave manualmente (batch)
     */
    @Transactional
    public List<PuntoClaveResponse> crearMasivo(List<PuntoClaveRequest> requests, Long usuarioId) {
        return requests.stream()
                .map(req -> crearManual(req, usuarioId))
                .collect(Collectors.toList());
    }

    /**
     * Actualizar un punto clave
     */
    @Transactional
    public PuntoClaveResponse actualizar(Long id, PuntoClaveRequest request) {
        PuntoClave punto = buscar(id);

        if (request.getContenido() != null) {
            punto.setContenido(request.getContenido());
        }
        if (request.getTitulo() != null) {
            punto.setTitulo(request.getTitulo());
        }
        if (request.getTema() != null) {
            punto.setTema(request.getTema());
        }
        if (request.getCodigo() != null) {
            punto.setCodigo(request.getCodigo());
        }
        if (request.getTipo() != null) {
            punto.setTipo(request.getTipo());
        }
        if (request.getOrden() != null) {
            punto.setOrden(request.getOrden());
        }

        punto = puntoClaveRepository.save(punto);

        return toResponse(punto);
    }

    /**
     * Marcar un punto clave como revisado (para puntos generados por IA)
     */
    @Transactional
    public PuntoClaveResponse marcarRevisado(Long id, Long usuarioId) {
        PuntoClave punto = buscar(id);
        punto.setRevisado(true);

        if (usuarioId != null) {
            Usuario usuario = usuarioRepository.findById(usuarioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario", usuarioId));
            punto.setCreadoPor(usuario);
        }

        punto = puntoClaveRepository.save(punto);
        return toResponse(punto);
    }

    /**
     * Marcar todos los puntos IA de un documento como revisados
     */
    @Transactional
    public int marcarTodosRevisados(Long documentoId, Long usuarioId) {
        List<PuntoClave> puntosIa = puntoClaveRepository
                .findByDocumentoIdAndEsIaTrueOrderByOrdenAsc(documentoId);

        Usuario usuario = usuarioId != null
                ? usuarioRepository.findById(usuarioId).orElse(null)
                : null;

        for (PuntoClave punto : puntosIa) {
            punto.setRevisado(true);
            if (usuario != null) {
                punto.setCreadoPor(usuario);
            }
        }

        puntoClaveRepository.saveAll(puntosIa);
        log.info("✅ {} puntos IA marcados como revisados", puntosIa.size());
        return puntosIa.size();
    }

    /**
     * Eliminar un punto clave
     */
    @Transactional
    public void eliminar(Long id) {
        PuntoClave punto = buscar(id);
        puntoClaveRepository.delete(punto);

        auditoriaService.registrar(
                null, punto.getDocumento().getEmpresa(), "ELIMINAR_PUNTO_CLAVE", "PuntoClave",
                id, "Punto clave eliminado del documento: " + punto.getDocumento().getTitulo(), null);
    }

    private PuntoClave buscar(Long id) {
        return puntoClaveRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PuntoClave", id));
    }

    private int obtenerProximoOrden(Long documentoId) {
        List<PuntoClave> existentes = puntoClaveRepository
                .findByDocumentoIdOrderByOrdenAsc(documentoId);
        return existentes.stream()
                .mapToInt(PuntoClave::getOrden)
                .max()
                .orElse(-1) + 1;
    }

    private PuntoClaveResponse toResponse(PuntoClave p) {
        return PuntoClaveResponse.builder()
                .id(p.getId())
                .contenido(p.getContenido())
                .titulo(p.getTitulo())
                .tema(p.getTema())
                .codigo(p.getCodigo())
                .tipo(p.getTipo())
                .orden(p.getOrden())
                .esIa(p.getEsIa())
                .confianzaIa(p.getConfianzaIa())
                .revisado(p.getRevisado())
                .documentoId(p.getDocumento().getId())
                .creadoPorId(p.getCreadoPor() != null ? p.getCreadoPor().getId() : null)
                .creadoPorNombre(p.getCreadoPor() != null ? p.getCreadoPor().getNombre() : "IA")
                .createdAt(p.getCreatedAt())
                .build();
    }
}
