package com.fiscalizacionhse.controller;

import com.fiscalizacionhse.dto.request.EmpresaRequest;
import com.fiscalizacionhse.dto.response.EmpresaResponse;
import com.fiscalizacionhse.security.AuthPrincipalIds;
import com.fiscalizacionhse.service.EmpresaService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/empresas")
public class EmpresaController {

    private final EmpresaService empresaService;

    public EmpresaController(EmpresaService empresaService) {
        this.empresaService = empresaService;
    }

    @GetMapping
    public ResponseEntity<Page<EmpresaResponse>> listar(
            @PageableDefault(sort = "nombre", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(empresaService.listar(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmpresaResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(empresaService.obtener(id));
    }

    @PostMapping
    public ResponseEntity<EmpresaResponse> crear(
            @Valid @RequestBody EmpresaRequest request,
            Authentication authentication) {
        long usuarioId = AuthPrincipalIds.usuarioId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(empresaService.crear(request, usuarioId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmpresaResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody EmpresaRequest request,
            Authentication authentication) {
        long usuarioId = AuthPrincipalIds.usuarioId(authentication);
        return ResponseEntity.ok(empresaService.actualizar(id, request, usuarioId));
    }

    @PatchMapping("/{id}/toggle-activo")
    public ResponseEntity<EmpresaResponse> toggleActivo(
            @PathVariable Long id, Authentication authentication) {
        long usuarioId = AuthPrincipalIds.usuarioId(authentication);
        return ResponseEntity.ok(empresaService.toggleActivo(id, usuarioId));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id, Authentication authentication) {
        long usuarioId = AuthPrincipalIds.usuarioId(authentication);
        empresaService.eliminar(id, usuarioId);
    }
}
