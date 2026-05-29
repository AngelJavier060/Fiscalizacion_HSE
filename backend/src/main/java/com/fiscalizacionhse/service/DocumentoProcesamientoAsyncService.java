package com.fiscalizacionhse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentoProcesamientoAsyncService {

    private final DocumentoService documentoService;

    @Async
    public void procesarDocumento(Long documentoId, Long usuarioId) {
        log.info("⏳ Iniciando procesamiento en segundo plano del documento {}", documentoId);
        try {
            documentoService.ejecutarProcesamientoPostSubida(documentoId, usuarioId);
        } catch (Exception e) {
            log.error("❌ Fallo no controlado procesando documento {}: {}", documentoId, e.getMessage(), e);
        }
    }
}
