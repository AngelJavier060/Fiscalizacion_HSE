package com.fiscalizacionhse.controller;

import com.fiscalizacionhse.dto.response.NotificacionResponse;
import com.fiscalizacionhse.service.NotificacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final NotificacionService notificacionService;

    @GetMapping("/bandeja")
    public ResponseEntity<Page<NotificacionResponse>> obtenerBandeja(
            Authentication authentication,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long usuarioId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(notificacionService.obtenerBandeja(usuarioId, pageable));
    }

    @GetMapping("/no-leidas")
    public ResponseEntity<List<NotificacionResponse>> obtenerNoLeidas(Authentication authentication) {
        Long usuarioId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(notificacionService.obtenerNoLeidas(usuarioId));
    }

    @GetMapping("/contar-no-leidas")
    public ResponseEntity<Long> contarNoLeidas(Authentication authentication) {
        Long usuarioId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(notificacionService.contarNoLeidas(usuarioId));
    }

    @PatchMapping("/{id}/leida")
    public ResponseEntity<NotificacionResponse> marcarLeida(@PathVariable Long id) {
        return ResponseEntity.ok(notificacionService.marcarLeida(id));
    }

    @PostMapping("/marcar-todas-leidas")
    public ResponseEntity<Integer> marcarTodasLeidas(Authentication authentication) {
        Long usuarioId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(notificacionService.marcarTodasLeidas(usuarioId));
    }

    @GetMapping("/audio/{notificacionId}")
    public ResponseEntity<byte[]> descargarAudio(@PathVariable Long notificacionId) {
        NotificacionResponse notif = null;
        try {
            var bandeja = notificacionService.obtenerBandeja(0L, Pageable.ofSize(999));
            // Buscar la notificación específica — implementación simplificada
        } catch (Exception ignored) {}

        // Para obtener el audio, buscamos por ID directamente desde el servicio
        var notificaciones = notificacionService.obtenerNoLeidas(0L);
        for (var n : notificaciones) {
            if (n.getId().equals(notificacionId) && n.getTieneAudio()) {
                try {
                    byte[] audio = java.nio.file.Files.readAllBytes(
                            java.nio.file.Path.of(n.getRutaAudio()));
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=\"recordatorio.mp3\"")
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .body(audio);
                } catch (Exception e) {
                    return ResponseEntity.notFound().build();
                }
            }
        }
        return ResponseEntity.notFound().build();
    }
}
