package com.fiscalizacionhse.service;

import com.fiscalizacionhse.dto.request.PermisoTrabajoRequest;
import com.fiscalizacionhse.dto.response.PermisoTrabajoResponse;
import com.fiscalizacionhse.exception.ResourceNotFoundException;
import com.fiscalizacionhse.model.Empresa;
import com.fiscalizacionhse.model.PermisoTrabajo;
import com.fiscalizacionhse.model.Usuario;
import com.fiscalizacionhse.repository.EmpresaRepository;
import com.fiscalizacionhse.repository.PermisoTrabajoRepository;
import com.fiscalizacionhse.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermisoTrabajoService {

    private final PermisoTrabajoRepository repository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;

    // ── Listar permisos por empresa (paginado) ────────────────────────
    public Page<PermisoTrabajoResponse> listarPorEmpresa(Long empresaId, Pageable pageable) {
        return repository
                .findByEmpresaIdAndActivoTrueOrderByCreatedAtDesc(empresaId, pageable)
                .map(this::toResponse);
    }

    // ── Listar todos los permisos de una empresa (sin paginar) ───────
    public List<PermisoTrabajoResponse> listarTodos(Long empresaId) {
        return repository
                .findByEmpresaIdAndActivoTrueOrderByCreatedAtDesc(empresaId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Obtener un permiso por ID ─────────────────────────────────────
    public PermisoTrabajoResponse obtener(String id) {
        PermisoTrabajo permiso = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PermisoTrabajo", id));
        return toResponse(permiso);
    }

    // ── Crear nuevo permiso ───────────────────────────────────────────
    @Transactional
    public PermisoTrabajoResponse crear(PermisoTrabajoRequest request, Long usuarioId) {
        Empresa empresa = empresaRepository.findById(request.getEmpresaId())
                .orElseThrow(() -> new ResourceNotFoundException("Empresa", request.getEmpresaId()));
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", usuarioId));

        PermisoTrabajo permiso = PermisoTrabajo.builder()
                .id(request.getId())
                .title(request.getTitle())
                .area(request.getArea() != null ? request.getArea() : "Sin asignar")
                .responsible(request.getResponsible() != null ? request.getResponsible() : "Sin asignar")
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .imagePath(request.getImagePath())
                .criticalTask(request.getCriticalTask())
                .description(request.getDescription())
                .emisor(request.getEmisor())
                .ejecutante(request.getEjecutante())
                .empresaEjecutante(request.getEmpresaEjecutante())
                .nota(request.getNota())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .empresa(empresa)
                .creadoPor(usuario)
                .activo(true)
                .build();

        permiso = repository.save(permiso);
        return toResponse(permiso);
    }

    // ── Actualizar permiso existente ──────────────────────────────────
    @Transactional
    public PermisoTrabajoResponse actualizar(String id, PermisoTrabajoRequest request) {
        PermisoTrabajo permiso = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PermisoTrabajo", id));

        permiso.setTitle(request.getTitle());
        permiso.setArea(request.getArea() != null ? request.getArea() : "Sin asignar");
        permiso.setResponsible(request.getResponsible() != null ? request.getResponsible() : "Sin asignar");
        permiso.setStartDate(request.getStartDate());
        permiso.setEndDate(request.getEndDate());
        permiso.setImagePath(request.getImagePath());
        permiso.setCriticalTask(request.getCriticalTask());
        permiso.setDescription(request.getDescription());
        permiso.setEmisor(request.getEmisor());
        permiso.setEjecutante(request.getEjecutante());
        permiso.setEmpresaEjecutante(request.getEmpresaEjecutante());
        permiso.setNota(request.getNota());
        permiso.setStartTime(request.getStartTime());
        permiso.setEndTime(request.getEndTime());

        permiso = repository.save(permiso);
        return toResponse(permiso);
    }

    // ── Eliminar permiso (borrado lógico) ─────────────────────────────
    @Transactional
    public void eliminar(String id) {
        PermisoTrabajo permiso = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PermisoTrabajo", id));
        permiso.setActivo(false);
        repository.save(permiso);
    }

    // ── Contar permisos por estado ────────────────────────────────────
    public long contarVigentes(Long empresaId) {
        LocalDateTime now = LocalDateTime.now();
        return repository.countByEmpresaIdAndActivoTrueAndStartDateBeforeAndEndDateAfter(
                empresaId, now, now);
    }

    public long contarExpirados(Long empresaId) {
        return repository.countByEmpresaIdAndActivoTrueAndEndDateBefore(
                empresaId, LocalDateTime.now());
    }

    public long contarTotal(Long empresaId) {
        return repository.countByEmpresaIdAndActivoTrue(empresaId);
    }

    // ── Mapeo a Response ──────────────────────────────────────────────
    private PermisoTrabajoResponse toResponse(PermisoTrabajo p) {
        LocalDateTime now = LocalDateTime.now();
        String status;
        if (now.isAfter(p.getEndDate())) {
            status = "expired";
        } else {
            long total = java.time.Duration.between(p.getStartDate(), p.getEndDate()).toDays();
            long remaining = java.time.Duration.between(now, p.getEndDate()).toDays();
            if (total > 0 && (double) remaining / total < 0.30) {
                status = "warning";
            } else {
                status = "active";
            }
        }

        int remainingDays = (int) java.time.Duration.between(now, p.getEndDate()).toDays();

        return PermisoTrabajoResponse.builder()
                .id(p.getId())
                .title(p.getTitle())
                .area(p.getArea())
                .responsible(p.getResponsible())
                .startDate(p.getStartDate())
                .endDate(p.getEndDate())
                .imagePath(p.getImagePath())
                .criticalTask(p.getCriticalTask())
                .description(p.getDescription())
                .emisor(p.getEmisor())
                .ejecutante(p.getEjecutante())
                .empresaEjecutante(p.getEmpresaEjecutante())
                .nota(p.getNota())
                .startTime(p.getStartTime())
                .endTime(p.getEndTime())
                .activo(p.getActivo())
                .empresaId(p.getEmpresa().getId())
                .empresaNombre(p.getEmpresa().getNombre())
                .creadoPorId(p.getCreadoPor().getId())
                .creadoPorNombre(p.getCreadoPor().getNombre())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .status(status)
                .remainingDays(remainingDays)
                .build();
    }
}
