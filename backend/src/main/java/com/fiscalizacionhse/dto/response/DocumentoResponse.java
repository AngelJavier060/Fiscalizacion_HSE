package com.fiscalizacionhse.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentoResponse {

    private Long id;
    private String titulo;
    private String descripcion;
    private String archivoNombre;
    private String archivoTipo;
    private Long archivoTamano;
    private String idiomaOriginal;
    private String idiomaDetectado;
    private Boolean requiereTraduccion;
    private Boolean traducido;
    private Boolean puntosGeneradosIa;
    private String estadoProcesamiento;
    private String errorProcesamiento;
    private Long cantidadPuntos;
    private Long cantidadPuntosRevisados;
    private Boolean activo;
    private Long empresaId;
    private String empresaNombre;
    private Long subidoPorId;
    private String subidoPorNombre;
    private LocalDateTime createdAt;
}
