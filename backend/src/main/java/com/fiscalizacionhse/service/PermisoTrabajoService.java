package com.fiscalizacionhse.service;

import com.fiscalizacionhse.dto.request.PermisoTrabajoRequest;
import com.fiscalizacionhse.dto.response.PermisoTrabajoResponse;
import com.fiscalizacionhse.exception.ResourceNotFoundException;
import com.fiscalizacionhse.exception.ConflictException;
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
    @Transactional(readOnly = true)
    public Page<PermisoTrabajoResponse> listarPorEmpresa(Long empresaId, Pageable pageable) {
        return repository
                .findByEmpresaIdAndActivoTrueOrderByCreatedAtDesc(empresaId, pageable)
                .map(this::toResponse);
    }

    // ── Listar todos los permisos de una empresa (sin paginar) ───────
    @Transactional(readOnly = true)
    public List<PermisoTrabajoResponse> listarTodos(Long empresaId) {
        return repository
                .findByEmpresaIdAndActivoTrueOrderByCreatedAtDesc(empresaId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Listar TODOS los permisos (para SUPER_ADMIN) ─────────────────
    @Transactional(readOnly = true)
    public List<PermisoTrabajoResponse> listarTodosGlobal() {
        return repository
                .findAllByActivoTrueOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Obtener un permiso por ID ─────────────────────────────────────
    @Transactional(readOnly = true)
    public PermisoTrabajoResponse obtener(String id) {
        PermisoTrabajo permiso = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PermisoTrabajo", id));
        return toResponse(permiso);
    }

    // ── Crear nuevo permiso ───────────────────────────────────────────
    @Transactional
    public PermisoTrabajoResponse crear(PermisoTrabajoRequest request, Long usuarioId) {
        System.out.println("🔍 DEBUG: Creando permiso - ID: " + request.getId() + ", usuarioId: " + usuarioId);
        System.out.println("🔍 DEBUG: empresaId: " + request.getEmpresaId() + ", title: " + request.getTitle());
        
        // Si el ID es temporal (TEMP-), generar un ID real
        String finalId = request.getId();
        if (finalId != null && finalId.startsWith("TEMP-")) {
            System.out.println("🔍 DEBUG: ID temporal detectado, generando ID real");
            // Generar ID único con formato PT-{year}-{timestamp}
            int timestamp = (int) (System.currentTimeMillis() % 10000);
            finalId = "PT-" + java.time.Year.now().getValue() + "-" + String.format("%04d", timestamp);
            
            // Asegurar que el ID generado sea único
            while (repository.findById(finalId).isPresent()) {
                timestamp = (timestamp + 1) % 10000;
                finalId = "PT-" + java.time.Year.now().getValue() + "-" + String.format("%04d", timestamp);
            }
            System.out.println("🔍 DEBUG: ID generado: " + finalId);
        } else {
            // Validar que no exista un permiso con el mismo ID
            if (repository.findById(request.getId()).isPresent()) {
                System.out.println("⚠️ DEBUG: ID duplicado: " + request.getId());
                throw new ConflictException(
                    "Ya existe un permiso de trabajo con el ID '" + request.getId() +
                    "'. No es posible crear uno duplicado."
                );
            }
        }

        Empresa empresa = empresaRepository.findById(request.getEmpresaId())
                .orElseThrow(() -> {
                    System.out.println("❌ DEBUG: Empresa no encontrada: " + request.getEmpresaId());
                    return new ResourceNotFoundException("Empresa", request.getEmpresaId());
                });
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> {
                    System.out.println("❌ DEBUG: Usuario no encontrado: " + usuarioId);
                    return new ResourceNotFoundException("Usuario", usuarioId);
                });
        
        System.out.println("✅ DEBUG: Empresa y usuario encontrados, creando permiso");

        PermisoTrabajo permiso = PermisoTrabajo.builder()
                .id(finalId)
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
    @Transactional(readOnly = true)
    public long contarVigentes(Long empresaId) {
        LocalDateTime now = LocalDateTime.now();
        return repository.countByEmpresaIdAndActivoTrueAndStartDateBeforeAndEndDateAfter(
                empresaId, now, now);
    }

    @Transactional(readOnly = true)
    public long contarExpirados(Long empresaId) {
        return repository.countByEmpresaIdAndActivoTrueAndEndDateBefore(
                empresaId, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public long contarTotal(Long empresaId) {
        return repository.countByEmpresaIdAndActivoTrue(empresaId);
    }

    // ── Contar permisos globales (para SUPER_ADMIN) ──────────────────
    @Transactional(readOnly = true)
    public long contarVigentesGlobal() {
        LocalDateTime now = LocalDateTime.now();
        return repository.countAllByActivoTrueAndStartDateBeforeAndEndDateAfter(now, now);
    }

    @Transactional(readOnly = true)
    public long contarExpiradosGlobal() {
        return repository.countAllByActivoTrueAndEndDateBefore(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public long contarTotalGlobal() {
        return repository.countAllByActivoTrue();
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
