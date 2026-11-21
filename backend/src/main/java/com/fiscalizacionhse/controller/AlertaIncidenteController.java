package com.fiscalizacionhse.controller;

import com.fiscalizacionhse.dto.request.AlertaIncidenteRequest;
import com.fiscalizacionhse.dto.response.AlertaIncidenteResponse;
import com.fiscalizacionhse.service.AlertaIncidenteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/alertas-incidentes")
@RequiredArgsConstructor
public class AlertaIncidenteController {

    private final AlertaIncidenteService service;

    @GetMapping
    public List<AlertaIncidenteResponse> listar() {
        return service.listar();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AlertaIncidenteResponse> crear(
            @RequestPart("datos") @Valid AlertaIncidenteRequest datos,
            @RequestPart(value = "archivo", required = false) MultipartFile archivo,
            org.springframework.security.core.Authentication authentication) {
        long usuarioId = authentication == null
                ? 0L
                : Long.parseLong(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.crear(datos, archivo, usuarioId));
    }

    @GetMapping("/{id}/archivo")
    public ResponseEntity<org.springframework.core.io.Resource> archivo(@PathVariable Long id) {
        AlertaIncidenteService.ArchivoAlerta arch = service.obtenerArchivo(id);
        ContentDisposition cd = ContentDisposition.inline()
                .filename(arch.nombre(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(MediaType.parseMediaType(arch.tipoMime()))
                .body(arch.resource());
    }
}
