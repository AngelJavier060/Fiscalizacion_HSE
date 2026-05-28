package com.fiscalizacionhse.controller;

import com.fiscalizacionhse.dto.response.AuditoriaResponse;
import com.fiscalizacionhse.service.AuditoriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> contar() {
        return ResponseEntity.ok(Map.of("total", auditoriaService.contar()));
    }

    @GetMapping
    public ResponseEntity<Page<AuditoriaResponse>> listar(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(auditoriaService.listar(pageable));
    }

    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<Page<AuditoriaResponse>> listarPorEmpresa(
            @PathVariable Long empresaId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(auditoriaService.listarPorEmpresa(empresaId, pageable));
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<Page<AuditoriaResponse>> listarPorUsuario(
            @PathVariable Long usuarioId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(auditoriaService.listarPorUsuario(usuarioId, pageable));
    }
}
