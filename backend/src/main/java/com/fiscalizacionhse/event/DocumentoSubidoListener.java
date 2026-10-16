package com.fiscalizacionhse.event;

import com.fiscalizacionhse.model.Documento;
import com.fiscalizacionhse.service.DocumentoProcesamientoPersistence;
import com.fiscalizacionhse.service.DocumentoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.Executor;

/**
 * Tras guardar el PDF: archivos chicos/medianos extraen letras en el mismo request.
 * Los libros pesados van a cola para no tumbar el proxy.
 */
@Component
@Slf4j
public class DocumentoSubidoListener {

    static final long MAX_BYTES_EXTRACCION_INMEDIATA = 25L * 1024 * 1024;

    private final DocumentoService documentoService;
    private final DocumentoProcesamientoPersistence procesamientoPersistence;
    private final Executor documentoTaskExecutor;

    public DocumentoSubidoListener(
            DocumentoService documentoService,
            DocumentoProcesamientoPersistence procesamientoPersistence,
            @Qualifier("documentoTaskExecutor") Executor documentoTaskExecutor) {
        this.documentoService = documentoService;
        this.procesamientoPersistence = procesamientoPersistence;
        this.documentoTaskExecutor = documentoTaskExecutor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDocumentoSubido(DocumentoSubidoEvent event) {
        Long id = event.documentoId();
        Documento doc = procesamientoPersistence.cargar(id);
        long tamano = doc.getArchivoTamano() != null ? doc.getArchivoTamano() : 0L;
        Runnable trabajo = () -> {
            log.info("⏳ Extracción de letras doc {} ({} bytes)", id, tamano);
            try {
                documentoService.ejecutarProcesamientoPostSubida(id, event.usuarioId());
            } catch (Exception e) {
                log.error("❌ Fallo procesando documento {}: {}", id, e.getMessage(), e);
            }
        };
        if (tamano > 0 && tamano <= MAX_BYTES_EXTRACCION_INMEDIATA) {
            trabajo.run();
        } else {
            log.info("📚 Doc {} es pesado ({} bytes): extracción en cola", id, tamano);
            documentoTaskExecutor.execute(trabajo);
        }
    }
}
