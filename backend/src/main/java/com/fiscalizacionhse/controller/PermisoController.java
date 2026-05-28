package com.fiscalizacionhse.controller;

import com.fiscalizacionhse.dto.request.AccesoUsuarioDto;
import com.fiscalizacionhse.dto.request.RolModuloDto;
import com.fiscalizacionhse.dto.response.ModuloResponse;
import com.fiscalizacionhse.service.PermisoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/permisos")
@RequiredArgsConstructor
public class PermisoController {

    private final PermisoService permisoService;

    @GetMapping("/modulos")
    public ResponseEntity<List<ModuloResponse>> listarModulos() {
        return ResponseEntity.ok(permisoService.listarModulos());
    }

    @GetMapping("/matriz")
    public ResponseEntity<List<RolModuloDto>> getMatriz() {
        return ResponseEntity.ok(permisoService.getMatriz());
    }

    @PutMapping("/matriz")
    public ResponseEntity<Void> guardarMatriz(@RequestBody List<RolModuloDto> permisos) {
        permisoService.guardarMatriz(permisos);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/usuario/{id}")
    public ResponseEntity<AccesoUsuarioDto> getAccesoUsuario(@PathVariable Long id) {
        return ResponseEntity.ok(permisoService.getAccesoUsuario(id));
    }

    @PutMapping("/usuario/{id}")
    public ResponseEntity<Void> guardarAccesoUsuario(
            @PathVariable Long id, @RequestBody AccesoUsuarioDto dto) {
        permisoService.guardarAccesoUsuario(id, dto);
        return ResponseEntity.noContent().build();
    }
}
