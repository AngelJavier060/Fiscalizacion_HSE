package com.fiscalizacionhse.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class AlertaIncidenteRequest {

    @NotBlank(message = "El código SGI es obligatorio")
    @Size(max = 80, message = "El código admite hasta 80 caracteres")
    private String codigo;

    @NotBlank(message = "El título es obligatorio")
    @Size(max = 500, message = "El título admite hasta 500 caracteres")
    private String titulo;

    @NotBlank(message = "La unidad de negocio es obligatoria")
    @Size(max = 255)
    private String unidad;

    @NotBlank(message = "El ámbito de consecuencias es obligatorio")
    @Size(max = 255)
    private String ambito;

    @NotNull(message = "La fecha del incidente es obligatoria")
    private LocalDate fecha;

    @NotBlank(message = "El área afectada es obligatoria")
    @Size(max = 255)
    private String area;

    @NotBlank(message = "El dueño del área es obligatorio")
    @Size(max = 255)
    private String dueno;

    @NotNull(message = "La hora del incidente es obligatoria")
    private LocalTime hora;

    @NotBlank(message = "La empresa involucrada es obligatoria")
    @Size(max = 255)
    private String empresa;

    @NotBlank(message = "El impacto a las personas es obligatorio")
    private String impacto;

    @NotNull(message = "Indique si es accidente grave o crítico")
    private Boolean accidenteGrave;

    @NotNull(message = "Indique si es IAP")
    private Boolean iap;

    @NotBlank(message = "La clasificación EORpV es obligatoria")
    @Size(max = 255)
    private String eorpv;
}
