package com.fiscalizacionhse.event;

/**
 * Disparado tras confirmar la transacción de subida; el procesamiento pesado corre en segundo plano.
 */
public record DocumentoSubidoEvent(Long documentoId, Long usuarioId) {
}
