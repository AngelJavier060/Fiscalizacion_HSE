package com.fiscalizacionhse.controller;

import com.fiscalizacionhse.dto.request.RecordatorioRequest;
import com.fiscalizacionhse.dto.response.RecordatorioResponse;
import com.fiscalizacionhse.service.RecordatorioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recordatorios")
@RequiredArgsConstructor
public class RecordatorioController {

    private final RecordatorioService recordatorioService;

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<Page<RecordatorioResponse>> listarPorEmpresa(
            @PathVariable Long empresaId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(recordatorioService.listarPorEmpresa(empresaId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecordatorioResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(recordatorioService.obtener(id));
    }

    @PostMapping
    public ResponseEntity<RecordatorioResponse> crear(
            @Valid @RequestBody RecordatorioRequest request,
            Authentication authentication) {
        Long usuarioId = Long.parseLong(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recordatorioService.crear(request, usuarioId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecordatorioResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody RecordatorioRequest request) {
        return ResponseEntity.ok(recordatorioService.actualizar(id, request));
    }

    @PatchMapping("/{id}/toggle-activo")
    public ResponseEntity<Void> toggleActivo(@PathVariable Long id) {
        recordatorioService.toggleActivo(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        recordatorioService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
