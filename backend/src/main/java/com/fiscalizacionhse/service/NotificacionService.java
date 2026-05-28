package com.fiscalizacionhse.service;

import com.fiscalizacionhse.dto.response.NotificacionResponse;
import com.fiscalizacionhse.exception.ResourceNotFoundException;
import com.fiscalizacionhse.model.*;
import com.fiscalizacionhse.repository.NotificacionRepository;
import com.fiscalizacionhse.repository.RecordatorioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificacionService {

    private final NotificacionRepository notificacionRepository;
    private final RecordatorioRepository recordatorioRepository;
    private final AudioService audioService;

    /**
     * Obtener bandeja de notificaciones del usuario
     */
    public Page<NotificacionResponse> obtenerBandeja(Long usuarioId, Pageable pageable) {
        return notificacionRepository
                .findByUsuarioIdOrderByCreatedAtDesc(usuarioId, pageable)
                .map(this::toResponse);
    }

    /**
     * Obtener no leídas
     */
    public List<NotificacionResponse> obtenerNoLeidas(Long usuarioId) {
        return notificacionRepository
                .findByUsuarioIdAndLeidaFalseOrderByCreatedAtDesc(usuarioId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Contar no leídas
     */
    public long contarNoLeidas(Long usuarioId) {
        return notificacionRepository.countByUsuarioIdAndLeidaFalse(usuarioId);
    }

    /**
     * Marcar una notificación como leída
     */
    @Transactional
    public NotificacionResponse marcarLeida(Long id) {
        Notificacion notificacion = notificacionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notificacion", id));
        notificacion.setLeida(true);
        notificacion.setFechaLectura(LocalDateTime.now());
        notificacion = notificacionRepository.save(notificacion);
        return toResponse(notificacion);
    }

    /**
     * Marcar todas como leídas
     */
    @Transactional
    public int marcarTodasLeidas(Long usuarioId) {
        return notificacionRepository.marcarTodasLeidas(usuarioId, LocalDateTime.now());
    }

    /**
     * Crear notificación para un recordatorio (con o sin audio)
     */
    @Transactional
    public Notificacion crearDesdeRecordatorio(Recordatorio recordatorio, Usuario usuario) {
        // Construir mensaje
        String titulo = recordatorio.getTitulo();
        String mensaje = recordatorio.getMensajePersonalizado();

        if (mensaje == null || mensaje.isBlank()) {
            mensaje = construirMensajePorDefecto(recordatorio);
        }

        Notificacion.NotificacionBuilder builder = Notificacion.builder()
                .titulo("🔔 " + titulo)
                .mensaje(mensaje)
                .tipo("RECORDATORIO")
                .leida(false)
                .usuario(usuario)
                .recordatorio(recordatorio)
                .documento(recordatorio.getDocumento());

        // Generar audio si está habilitado
        if (recordatorio.getIncluirAudio()) {
            try {
                String textoAudio = String.format(
                    "Recordatorio: %s. %s",
                    recordatorio.getTitulo(),
                    mensaje.length() > 500 ? mensaje.substring(0, 500) : mensaje
                );

                AudioService.AudioResult audio = audioService.generarAudio(
                        recordatorio.getTitulo(), textoAudio);

                if (audio != null) {
                    builder.tieneAudio(true);
                    builder.rutaAudio(audio.rutaArchivo());
                    builder.duracionAudio(audio.duracionSegundos());
                }
            } catch (Exception e) {
                log.warn("⚠️ No se pudo generar audio para notificación: {}", e.getMessage());
            }
        }

        Notificacion notificacion = builder.build();
        notificacion = notificacionRepository.save(notificacion);

        log.info("🔔 Notificación creada para usuario {}: '{}' [audio: {}]",
                usuario.getEmail(), titulo, notificacion.getTieneAudio());

        return notificacion;
    }

    /**
     * Crea notificaciones para todos los destinatarios de un recordatorio
     */
    @Transactional
    public int notificarDestinatarios(Recordatorio recordatorio) {
        int contador = 0;
        for (Usuario usuario : recordatorio.getDestinatarios()) {
            try {
                crearDesdeRecordatorio(recordatorio, usuario);
                contador++;
            } catch (Exception e) {
                log.error("❌ Error notificando a usuario {}: {}",
                        usuario.getEmail(), e.getMessage());
            }
        }
        return contador;
    }

    private String construirMensajePorDefecto(Recordatorio r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tienes un recordatorio programado: ").append(r.getTitulo());

        if (r.getDescripcion() != null && !r.getDescripcion().isBlank()) {
            sb.append(". ").append(r.getDescripcion());
        }

        if (r.getDocumento() != null) {
            sb.append(". Documento relacionado: ").append(r.getDocumento().getTitulo());
        }

        switch (r.getTipoRecurrencia()) {
            case "DAILY" -> sb.append(". Este recordatorio se repite diariamente.");
            case "WEEKLY" -> sb.append(". Este recordatorio se repite semanalmente.");
            case "MONTHLY" -> sb.append(". Este recordatorio se repite mensualmente.");
            default -> {}
        }

        return sb.toString();
    }

    private NotificacionResponse toResponse(Notificacion n) {
        return NotificacionResponse.builder()
                .id(n.getId())
                .titulo(n.getTitulo())
                .mensaje(n.getMensaje())
                .tipo(n.getTipo())
                .leida(n.getLeida())
                .fechaLectura(n.getFechaLectura())
                .tieneAudio(n.getTieneAudio())
                .rutaAudio(n.getRutaAudio())
                .duracionAudio(n.getDuracionAudio())
                .usuarioId(n.getUsuario().getId())
                .usuarioNombre(n.getUsuario().getNombre())
                .recordatorioId(n.getRecordatorio() != null ? n.getRecordatorio().getId() : null)
                .recordatorioTitulo(n.getRecordatorio() != null ? n.getRecordatorio().getTitulo() : null)
                .documentoId(n.getDocumento() != null ? n.getDocumento().getId() : null)
                .documentoTitulo(n.getDocumento() != null ? n.getDocumento().getTitulo() : null)
                .createdAt(n.getCreatedAt())
                .build();
    }
}
