package com.fiscalizacionhse.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String recurso, Long id) {
        super(recurso + " no encontrado con id: " + id);
    }

    public ResourceNotFoundException(String mensaje) {
        super(mensaje);
    }
}
