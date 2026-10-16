package com.fiscalizacionhse.controller;

import com.fiscalizacionhse.dto.request.SsoIniciarExamenRequest;
import com.fiscalizacionhse.dto.request.SsoResponderRequest;
import com.fiscalizacionhse.dto.response.SsoExamenResponse;
import com.fiscalizacionhse.dto.response.SsoHistorialItemResponse;
import com.fiscalizacionhse.dto.response.SsoInicioResponse;
import com.fiscalizacionhse.dto.response.SsoResultadoResponse;
import com.fiscalizacionhse.service.SimuladorExamenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/simulador")
@RequiredArgsConstructor
public class SimuladorExamenController {

    private final SimuladorExamenService examenService;

    @GetMapping("/inicio")
    public ResponseEntity<SsoInicioResponse> inicio(Authentication auth) {
        return ResponseEntity.ok(examenService.inicio(uid(auth)));
    }

    @PostMapping("/examenes")
    public ResponseEntity<SsoExamenResponse> iniciar(
            @Valid @RequestBody SsoIniciarExamenRequest request, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(examenService.iniciar(uid(auth), request));
    }

    @GetMapping("/examenes/{id}")
    public ResponseEntity<SsoExamenResponse> obtener(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(examenService.obtenerExamen(uid(auth), id));
    }

    @PutMapping("/examenes/{id}/respuestas")
    public ResponseEntity<SsoExamenResponse> responder(
            @PathVariable Long id,
            @Valid @RequestBody SsoResponderRequest request,
            Authentication auth) {
        return ResponseEntity.ok(examenService.responder(uid(auth), id, request));
    }

    @PostMapping("/examenes/{id}/finalizar")
    public ResponseEntity<SsoResultadoResponse> finalizar(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(examenService.finalizar(uid(auth), id));
    }

    @GetMapping("/examenes/{id}/resultado")
    public ResponseEntity<SsoResultadoResponse> resultado(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(examenService.resultado(uid(auth), id));
    }

    @GetMapping("/historial")
    public ResponseEntity<List<SsoHistorialItemResponse>> historial(Authentication auth) {
        return ResponseEntity.ok(examenService.historial(uid(auth)));
    }

    private Long uid(Authentication auth) {
        return Long.parseLong(auth.getName());
    }
}
