package com.fiscalizacionhse.service;

import com.fiscalizacionhse.dto.request.RecordatorioRequest;
import com.fiscalizacionhse.dto.response.RecordatorioResponse;
import com.fiscalizacionhse.exception.BadRequestException;
import com.fiscalizacionhse.exception.ResourceNotFoundException;
import com.fiscalizacionhse.model.*;
import com.fiscalizacionhse.repository.DocumentoRepository;
import com.fiscalizacionhse.repository.EmpresaRepository;
import com.fiscalizacionhse.repository.RecordatorioRepository;
import com.fiscalizacionhse.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecordatorioService {

    private final RecordatorioRepository recordatorioRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final DocumentoRepository documentoRepository;
    private final NotificacionService notificacionService;
    private final AuditoriaService auditoriaService;

    public Page<RecordatorioResponse> listarPorEmpresa(Long empresaId, Pageable pageable) {
        return recordatorioRepository
                .findByEmpresaIdOrderByCreatedAtDesc(empresaId, pageable)
                .map(this::toResponse);
    }

    public RecordatorioResponse obtener(Long id) {
        return toResponse(buscar(id));
    }

    @Transactional
    public RecordatorioResponse crear(RecordatorioRequest request, Long usuarioId) {
        Empresa empresa = empresaRepository.findById(request.getEmpresaId())
                .orElseThrow(() -> new ResourceNotFoundException("Empresa", request.getEmpresaId()));

        Usuario creador = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", usuarioId));

        Documento documento = null;
        if (request.getDocumentoId() != null) {
            documento = documentoRepository.findById(request.getDocumentoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Documento", request.getDocumentoId()));
        }

        // Validar fechas
        if (request.getFechaInicio().isBefore(LocalDate.now())) {
            throw new BadRequestException("La fecha de inicio no puede ser anterior a hoy");
        }
        if (request.getFechaFin() != null && request.getFechaFin().isBefore(request.getFechaInicio())) {
            throw new BadRequestException("La fecha de fin debe ser posterior a la fecha de inicio");
        }

        Recordatorio recordatorio = Recordatorio.builder()
                .titulo(request.getTitulo())
                .descripcion(request.getDescripcion())
                .tipoRecurrencia(request.getTipoRecurrencia() != null ?
                        request.getTipoRecurrencia() : "ONE_TIME")
                .intervaloDias(request.getIntervaloDias() != null ? request.getIntervaloDias() : 1)
                .diaSemana(request.getDiaSemana())
                .diaMes(request.getDiaMes())
                .fechaInicio(request.getFechaInicio())
                .fechaFin(request.getFechaFin())
                .horaRecordatorio(request.getHoraRecordatorio() != null ?
                        request.getHoraRecordatorio() : LocalTime.of(8, 0))
                .incluirAudio(request.getIncluirAudio() != null ? request.getIncluirAudio() : false)
                .mensajePersonalizado(request.getMensajePersonalizado())
                .activo(true)
                .documento(documento)
                .empresa(empresa)
                .creadoPor(creador)
                .build();

        // Calcular próxima ejecución
        recordatorio.setProximaEjecucion(calcularProximaEjecucion(recordatorio));

        // Asignar destinatarios
        Set<Usuario> destinatarios = new HashSet<>();
        if (request.getDestinatarioIds() != null && !request.getDestinatarioIds().isEmpty()) {
            for (Long destId : request.getDestinatarioIds()) {
                Usuario dest = usuarioRepository.findById(destId)
                        .orElseThrow(() -> new ResourceNotFoundException("Usuario destinatario", destId));
                destinatarios.add(dest);
            }
        }
        // Si no hay destinatarios específicos, notificar a todos los usuarios activos de la empresa
        if (destinatarios.isEmpty()) {
            List<Usuario> usuariosEmpresa = usuarioRepository
                    .findByEmpresaIdAndActivoTrue(empresa.getId());
            destinatarios.addAll(usuariosEmpresa);
        }
        recordatorio.setDestinatarios(destinatarios);

        recordatorio = recordatorioRepository.save(recordatorio);

        auditoriaService.registrar(
                creador, empresa, "CREAR_RECORDATORIO", "Recordatorio",
                recordatorio.getId(),
                "Recordatorio creado: " + recordatorio.getTitulo() +
                " (" + recordatorio.getTipoRecurrencia() + ")",
                null);

        return toResponse(recordatorio);
    }

    @Transactional
    public RecordatorioResponse actualizar(Long id, RecordatorioRequest request) {
        Recordatorio recordatorio = buscar(id);

        recordatorio.setTitulo(request.getTitulo());
        recordatorio.setDescripcion(request.getDescripcion());
        recordatorio.setTipoRecurrencia(request.getTipoRecurrencia());
        recordatorio.setIntervaloDias(request.getIntervaloDias());
        recordatorio.setDiaSemana(request.getDiaSemana());
        recordatorio.setDiaMes(request.getDiaMes());
        recordatorio.setFechaInicio(request.getFechaInicio());
        recordatorio.setFechaFin(request.getFechaFin());
        recordatorio.setHoraRecordatorio(request.getHoraRecordatorio());
        recordatorio.setIncluirAudio(request.getIncluirAudio());
        recordatorio.setMensajePersonalizado(request.getMensajePersonalizado());

        // Recalcular próxima ejecución
        recordatorio.setProximaEjecucion(calcularProximaEjecucion(recordatorio));

        // Actualizar destinatarios
        if (request.getDestinatarioIds() != null) {
            Set<Usuario> destinatarios = new HashSet<>();
            for (Long destId : request.getDestinatarioIds()) {
                Usuario dest = usuarioRepository.findById(destId)
                        .orElseThrow(() -> new ResourceNotFoundException("Usuario", destId));
                destinatarios.add(dest);
            }
            recordatorio.setDestinatarios(destinatarios);
        }

        recordatorio = recordatorioRepository.save(recordatorio);
        return toResponse(recordatorio);
    }

    @Transactional
    public void toggleActivo(Long id) {
        Recordatorio recordatorio = buscar(id);
        recordatorio.setActivo(!recordatorio.getActivo());
        if (!recordatorio.getActivo()) {
            recordatorio.setProximaEjecucion(null);
        } else {
            recordatorio.setProximaEjecucion(calcularProximaEjecucion(recordatorio));
        }
        recordatorioRepository.save(recordatorio);
    }

    @Transactional
    public void eliminar(Long id) {
        Recordatorio recordatorio = buscar(id);
        recordatorioRepository.delete(recordatorio);
    }

    /**
     * Calcula cuándo debe ejecutarse por primera vez o la próxima vez
     */
    public LocalDateTime calcularProximaEjecucion(Recordatorio r) {
        if (!r.getActivo()) return null;

        LocalDateTime base = LocalDateTime.of(r.getFechaInicio(), r.getHoraRecordatorio());

        return switch (r.getTipoRecurrencia()) {
            case "ONE_TIME" -> base.isAfter(LocalDateTime.now()) ? base : null;
            case "DAILY" -> calcularProximaDiaria(r);
            case "WEEKLY" -> calcularProximaSemanal(r);
            case "MONTHLY" -> calcularProximaMensual(r);
            case "CUSTOM" -> calcularProximaCustom(r);
            default -> base;
        };
    }

    private LocalDateTime calcularProximaDiaria(Recordatorio r) {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime candidata = LocalDateTime.of(
                r.getUltimaEjecucion() != null
                        ? r.getUltimaEjecucion().toLocalDate().plusDays(r.getIntervaloDias())
                        : r.getFechaInicio(),
                r.getHoraRecordatorio());

        while (candidata.isBefore(ahora)) {
            candidata = candidata.plusDays(r.getIntervaloDias());
        }

        if (r.getFechaFin() != null && candidata.toLocalDate().isAfter(r.getFechaFin())) {
            return null;
        }
        return candidata;
    }

    private LocalDateTime calcularProximaSemanal(Recordatorio r) {
        LocalDateTime ahora = LocalDateTime.now();
        int diaObjetivo = r.getDiaSemana() != null ? r.getDiaSemana() : ahora.getDayOfWeek().getValue() - 1;
        LocalDateTime candidata = LocalDateTime.of(
                r.getUltimaEjecucion() != null
                        ? r.getUltimaEjecucion().toLocalDate().plusDays(1)
                        : r.getFechaInicio(),
                r.getHoraRecordatorio());

        // Avanzar hasta encontrar el día de semana correcto
        while (candidata.getDayOfWeek().getValue() - 1 != diaObjetivo
                || candidata.isBefore(ahora)) {
            candidata = candidata.plusDays(1);
        }

        if (r.getFechaFin() != null && candidata.toLocalDate().isAfter(r.getFechaFin())) {
            return null;
        }
        return candidata;
    }

    private LocalDateTime calcularProximaMensual(Recordatorio r) {
        LocalDateTime ahora = LocalDateTime.now();
        int diaMes = r.getDiaMes() != null ? r.getDiaMes() : ahora.getDayOfMonth();

        LocalDateTime candidata = LocalDateTime.of(
                r.getUltimaEjecucion() != null
                        ? r.getUltimaEjecucion().toLocalDate().plusMonths(1).withDayOfMonth(
                                Math.min(diaMes, r.getUltimaEjecucion().toLocalDate().plusMonths(1)
                                        .lengthOfMonth()))
                        : r.getFechaInicio().withDayOfMonth(
                                Math.min(diaMes, r.getFechaInicio().lengthOfMonth())),
                r.getHoraRecordatorio());

        while (candidata.isBefore(ahora)) {
            candidata = candidata.plusMonths(1);
        }

        if (r.getFechaFin() != null && candidata.toLocalDate().isAfter(r.getFechaFin())) {
            return null;
        }
        return candidata;
    }

    private LocalDateTime calcularProximaCustom(Recordatorio r) {
        return calcularProximaDiaria(r);
    }

    private Recordatorio buscar(Long id) {
        return recordatorioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recordatorio", id));
    }

    private RecordatorioResponse toResponse(Recordatorio r) {
        return RecordatorioResponse.builder()
                .id(r.getId())
                .titulo(r.getTitulo())
                .descripcion(r.getDescripcion())
                .tipoRecurrencia(r.getTipoRecurrencia())
                .intervaloDias(r.getIntervaloDias())
                .diaSemana(r.getDiaSemana())
                .diaMes(r.getDiaMes())
                .fechaInicio(r.getFechaInicio())
                .fechaFin(r.getFechaFin())
                .horaRecordatorio(r.getHoraRecordatorio())
                .proximaEjecucion(r.getProximaEjecucion())
                .ultimaEjecucion(r.getUltimaEjecucion())
                .incluirAudio(r.getIncluirAudio())
                .mensajePersonalizado(r.getMensajePersonalizado())
                .activo(r.getActivo())
                .documentoId(r.getDocumento() != null ? r.getDocumento().getId() : null)
                .documentoTitulo(r.getDocumento() != null ? r.getDocumento().getTitulo() : null)
                .empresaId(r.getEmpresa().getId())
                .empresaNombre(r.getEmpresa().getNombre())
                .creadoPorId(r.getCreadoPor().getId())
                .creadoPorNombre(r.getCreadoPor().getNombre())
                .destinatarios(r.getDestinatarios().stream()
                        .map(u -> new RecordatorioResponse.DestinatarioInfo(u.getId(), u.getNombre(), u.getEmail()))
                        .collect(Collectors.toList()))
                .createdAt(r.getCreatedAt())
                .build();
    }
}
