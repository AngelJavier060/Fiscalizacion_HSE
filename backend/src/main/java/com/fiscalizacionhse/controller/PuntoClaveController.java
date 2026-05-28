package com.fiscalizacionhse.controller;

import com.fiscalizacionhse.dto.request.PuntoClaveRequest;
import com.fiscalizacionhse.dto.response.PuntoClaveResponse;
import com.fiscalizacionhse.service.PuntoClaveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/puntos-clave")
@RequiredArgsConstructor
public class PuntoClaveController {

    private final PuntoClaveService puntoClaveService;

    /**
     * Listar puntos clave de un documento
     */
    @GetMapping("/documento/{documentoId}")
    public ResponseEntity<List<PuntoClaveResponse>> listarPorDocumento(@PathVariable Long documentoId) {
        return ResponseEntity.ok(puntoClaveService.listarPorDocumento(documentoId));
    }

    /**
     * Obtener un punto clave por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<PuntoClaveResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(puntoClaveService.obtener(id));
    }

    /**
     * Crear punto clave manualmente
     */
    @PostMapping
    public ResponseEntity<PuntoClaveResponse> crear(
            @Valid @RequestBody PuntoClaveRequest request,
            Authentication authentication) {
        Long usuarioId = Long.parseLong(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(puntoClaveService.crearManual(request, usuarioId));
    }

    /**
     * Crear múltiples puntos clave manualmente (batch)
     */
    @PostMapping("/batch")
    public ResponseEntity<List<PuntoClaveResponse>> crearMasivo(
            @Valid @RequestBody List<PuntoClaveRequest> requests,
            Authentication authentication) {
        Long usuarioId = Long.parseLong(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(puntoClaveService.crearMasivo(requests, usuarioId));
    }

    /**
     * Actualizar un punto clave
     */
    @PutMapping("/{id}")
    public ResponseEntity<PuntoClaveResponse> actualizar(
            @PathVariable Long id, @Valid @RequestBody PuntoClaveRequest request) {
        return ResponseEntity.ok(puntoClaveService.actualizar(id, request));
    }

    /**
     * Marcar punto clave como revisado
     */
    @PatchMapping("/{id}/revisado")
    public ResponseEntity<PuntoClaveResponse> marcarRevisado(
            @PathVariable Long id, Authentication authentication) {
        Long usuarioId = authentication != null ? Long.parseLong(authentication.getName()) : null;
        return ResponseEntity.ok(puntoClaveService.marcarRevisado(id, usuarioId));
    }

    /**
     * Marcar todos los puntos IA de un documento como revisados
     */
    @PostMapping("/documento/{documentoId}/revisar-todos")
    public ResponseEntity<Integer> marcarTodosRevisados(
            @PathVariable Long documentoId, Authentication authentication) {
        Long usuarioId = authentication != null ? Long.parseLong(authentication.getName()) : null;
        return ResponseEntity.ok(puntoClaveService.marcarTodosRevisados(documentoId, usuarioId));
    }

    /**
     * Eliminar un punto clave
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        puntoClaveService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
