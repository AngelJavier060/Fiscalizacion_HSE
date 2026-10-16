package com.fiscalizacionhse.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SsoPreguntaRequest {

    @NotNull(message = "El tema es obligatorio")
    private Long temaId;

    @NotBlank(message = "El enunciado es obligatorio")
    private String enunciado;

    private String explicacion;
    private String fuente;
    private Boolean activo;

    @Valid
    @NotNull
    @Size(min = 2, max = 5, message = "Debe haber entre 2 y 5 opciones")
    private List<SsoOpcionRequest> opciones;

    @Data
    public static class SsoOpcionRequest {
        private Long id;
        @NotBlank(message = "El texto de la opción es obligatorio")
        private String texto;
        @NotNull
        private Boolean esCorrecta;
        private String argumento;
    }
}
