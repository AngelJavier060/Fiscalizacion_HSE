package com.fiscalizacionhse.event;

import com.fiscalizacionhse.service.DocumentoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentoSubidoListener {

    private final DocumentoService documentoService;

    @Async("documentoTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentoSubido(DocumentoSubidoEvent event) {
        log.info("⏳ Procesamiento en segundo plano del documento {}", event.documentoId());
        try {
            documentoService.ejecutarProcesamientoPostSubida(event.documentoId(), event.usuarioId());
        } catch (Exception e) {
            log.error("❌ Fallo procesando documento {}: {}", event.documentoId(), e.getMessage(), e);
        }
    }
}
