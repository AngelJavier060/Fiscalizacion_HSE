package com.fiscalizacionhse.service;

import com.fiscalizacionhse.exception.ResourceNotFoundException;
import com.fiscalizacionhse.model.Documento;
import com.fiscalizacionhse.model.PuntoClave;
import com.fiscalizacionhse.repository.DocumentoRepository;
import com.fiscalizacionhse.repository.PuntoClaveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Escrituras cortas en BD durante el procesamiento de PDF.
 * La extracción e IA corren SIN transacción abierta (evita agotar el pool con miles de docs).
 */
@Component
@RequiredArgsConstructor
public class DocumentoProcesamientoPersistence {

    private final DocumentoRepository documentoRepository;
    private final PuntoClaveRepository puntoClaveRepository;
    private final CacheManager cacheManager;

    @Transactional(readOnly = true)
    public Documento cargar(Long documentoId) {
        return documentoRepository.findById(documentoId)
                .orElseThrow(() -> new ResourceNotFoundException("Documento", documentoId));
    }

    @Transactional
    public void marcarProcesando(Long documentoId) {
        Documento d = cargarEnEscritura(documentoId);
        d.setEstadoProcesamiento("PROCESANDO");
        d.setErrorProcesamiento(null);
        documentoRepository.save(d);
    }

    @Transactional
    public void guardarTextoExtraido(
            Long documentoId,
            String textoExtraido,
            String idiomaDetectado,
            boolean requiereTraduccion) {
        Documento d = cargarEnEscritura(documentoId);
        d.setTextoExtraido(textoExtraido);
        d.setIdiomaOriginal(idiomaDetectado);
        d.setIdiomaDetectado(idiomaDetectado);
        d.setRequiereTraduccion(requiereTraduccion);
        documentoRepository.save(d);
        evictTextoCache(documentoId);
    }

    @Transactional
    public void guardarTraduccion(Long documentoId, String textoTraducido) {
        Documento d = cargarEnEscritura(documentoId);
        d.setTextoTraducido(textoTraducido);
        d.setTraducido(true);
        documentoRepository.save(d);
        evictTextoCache(documentoId);
    }

    @Transactional
    public void guardarPuntosIa(Long documentoId, List<PuntoClave> puntos) {
        if (puntos == null || puntos.isEmpty()) {
            return;
        }
        Documento d = cargarEnEscritura(documentoId);
        for (PuntoClave punto : puntos) {
            punto.setDocumento(d);
        }
        puntoClaveRepository.saveAll(puntos);
        d.setPuntosGeneradosIa(true);
        documentoRepository.save(d);
    }

    @Transactional
    public void marcarCompletado(Long documentoId, boolean puntosGeneradosIa) {
        Documento d = cargarEnEscritura(documentoId);
        d.setEstadoProcesamiento("COMPLETADO");
        d.setErrorProcesamiento(null);
        if (puntosGeneradosIa) {
            d.setPuntosGeneradosIa(true);
        }
        documentoRepository.save(d);
        evictTextoCache(documentoId);
    }

    @Transactional
    public void marcarError(Long documentoId, String mensaje) {
        Documento d = cargarEnEscritura(documentoId);
        d.setEstadoProcesamiento("ERROR");
        d.setErrorProcesamiento(mensaje);
        documentoRepository.save(d);
    }

    private Documento cargarEnEscritura(Long documentoId) {
        return documentoRepository.findById(documentoId)
                .orElseThrow(() -> new ResourceNotFoundException("Documento", documentoId));
    }

    private void evictTextoCache(Long documentoId) {
        var cache = cacheManager.getCache("textoCompleto");
        if (cache != null) {
            cache.evict(documentoId);
        }
    }
}
