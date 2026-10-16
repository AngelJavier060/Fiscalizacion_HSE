package com.fiscalizacionhse.service;

import com.fiscalizacionhse.event.DocumentoSubidoEvent;
import com.fiscalizacionhse.model.Documento;
import com.fiscalizacionhse.repository.DocumentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Cierra documentos que ya tienen letras y reencola solo los que no.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentoProcesamientoRecoveryJob {

    private static final int MINUTOS_ATASCADO = 2;
    private static final int LOTE_RECUPERACION = 20;

    private final DocumentoRepository documentoRepository;
    private final DocumentoProcesamientoPersistence procesamientoPersistence;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void recuperarAtascados() {
        LocalDateTime limite = LocalDateTime.now().minusMinutes(MINUTOS_ATASCADO);
        Pageable lote = PageRequest.of(0, LOTE_RECUPERACION);
        List<Documento> atascados = documentoRepository
                .findByEstadoProcesamientoAndActivoTrueAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                        "PROCESANDO", limite, lote);
        if (atascados.isEmpty()) {
            return;
        }
        log.warn("♻️ Revisando {} documento(s) en PROCESANDO", atascados.size());
        for (Documento doc : atascados) {
            if (doc.getTextoExtraido() != null && !doc.getTextoExtraido().isBlank()) {
                procesamientoPersistence.marcarCompletado(
                        doc.getId(), Boolean.TRUE.equals(doc.getPuntosGeneradosIa()));
                log.info("Documento {} ya tenía letras: se marca COMPLETADO", doc.getId());
                continue;
            }
            Long usuarioId = doc.getSubidoPor() != null ? doc.getSubidoPor().getId() : null;
            if (usuarioId == null) {
                continue;
            }
            eventPublisher.publishEvent(new DocumentoSubidoEvent(doc.getId(), usuarioId));
        }
    }
}
