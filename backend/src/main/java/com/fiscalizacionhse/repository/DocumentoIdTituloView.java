package com.fiscalizacionhse.repository;

/**
 * Proyección liviana: solo id y título (sin cargar texto_extraido de todos los PDF).
 */
public interface DocumentoIdTituloView {
    Long getId();
    String getTitulo();
}
