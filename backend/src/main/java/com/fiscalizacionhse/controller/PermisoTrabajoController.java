package com.fiscalizacionhse.controller;

import com.fiscalizacionhse.dto.request.PermisoTrabajoRequest;
import com.fiscalizacionhse.dto.response.PermisoTrabajoResponse;
import com.fiscalizacionhse.service.PermisoTrabajoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/permisos-trabajo")
@RequiredArgsConstructor
public class PermisoTrabajoController {

    private final PermisoTrabajoService service;

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<Page<PermisoTrabajoResponse>> listar(
            @PathVariable Long empresaId, Pageable pageable) {
        return ResponseEntity.ok(service.listarPorEmpresa(empresaId, pageable));
    }

    @GetMapping("/empresa/{empresaId}/todos")
    public ResponseEntity<List<PermisoTrabajoResponse>> listarTodos(
            @PathVariable Long empresaId) {
        return ResponseEntity.ok(service.listarTodos(empresaId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PermisoTrabajoResponse> obtener(@PathVariable String id) {
        return ResponseEntity.ok(service.obtener(id));
    }

    @PostMapping
    public ResponseEntity<PermisoTrabajoResponse> crear(
            @Valid @RequestBody PermisoTrabajoRequest request,
            Authentication authentication) {
        long usuarioId = Long.parseLong(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.crear(request, usuarioId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PermisoTrabajoResponse> actualizar(
            @PathVariable String id,
            @Valid @RequestBody PermisoTrabajoRequest request) {
        return ResponseEntity.ok(service.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/empresa/{empresaId}/contar")
    public ResponseEntity<ContadoresResponse> contar(@PathVariable Long empresaId) {
        return ResponseEntity.ok(ContadoresResponse.builder()
                .total(service.contarTotal(empresaId))
                .vigentes(service.contarVigentes(empresaId))
                .expirados(service.contarExpirados(empresaId))
                .build());
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ContadoresResponse {
        private long total;
        private long vigentes;
        private long expirados;
    }
}
