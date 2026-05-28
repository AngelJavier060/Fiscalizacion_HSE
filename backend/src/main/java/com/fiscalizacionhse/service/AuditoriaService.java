package com.fiscalizacionhse.service;

import com.fiscalizacionhse.dto.response.AuditoriaResponse;
import com.fiscalizacionhse.model.Auditoria;
import com.fiscalizacionhse.model.Empresa;
import com.fiscalizacionhse.model.Usuario;
import com.fiscalizacionhse.repository.AuditoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private final AuditoriaRepository auditoriaRepository;

    @Transactional
    public void registrar(Usuario usuario, Empresa empresa, String accion,
                          String entidad, Long entidadId, String detalle, String direccionIp) {
        Auditoria auditoria = Auditoria.builder()
                .usuario(usuario)
                .empresa(empresa)
                .accion(accion)
                .entidad(entidad)
                .entidadId(entidadId)
                .detalle(detalle)
                .direccionIp(direccionIp)
                .build();

        auditoriaRepository.save(auditoria);
    }

    @Transactional(readOnly = true)
    public long contar() {
        return auditoriaRepository.count();
    }

    @Transactional(readOnly = true)
    public Page<AuditoriaResponse> listar(Pageable pageable) {
        Page<Auditoria> page = auditoriaRepository.findAll(pageable);
        return toPageResponse(page, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditoriaResponse> listarPorEmpresa(Long empresaId, Pageable pageable) {
        Page<Auditoria> page = auditoriaRepository.findByEmpresaIdOrderByCreatedAtDesc(empresaId, pageable);
        return toPageResponse(page, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AuditoriaResponse> listarPorUsuario(Long usuarioId, Pageable pageable) {
        Page<Auditoria> page = auditoriaRepository.findByUsuarioIdOrderByCreatedAtDesc(usuarioId, pageable);
        return toPageResponse(page, pageable);
    }

    private Page<AuditoriaResponse> toPageResponse(Page<Auditoria> page, Pageable pageable) {
        List<AuditoriaResponse> contenido = page.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return new PageImpl<>(contenido, pageable, page.getTotalElements());
    }

    private AuditoriaResponse toResponse(Auditoria auditoria) {
        Usuario usuario = auditoria.getUsuario();
        Empresa empresa = auditoria.getEmpresa();
        return AuditoriaResponse.builder()
                .id(auditoria.getId())
                .usuarioId(usuario != null ? usuario.getId() : null)
                .usuarioNombre(usuario != null ? usuario.getNombre() : null)
                .usuarioEmail(usuario != null ? usuario.getEmail() : null)
                .empresaId(empresa != null ? empresa.getId() : null)
                .empresaNombre(empresa != null ? empresa.getNombre() : null)
                .accion(auditoria.getAccion())
                .entidad(auditoria.getEntidad())
                .entidadId(auditoria.getEntidadId())
                .detalle(auditoria.getDetalle())
                .direccionIp(auditoria.getDireccionIp())
                .createdAt(auditoria.getCreatedAt())
                .build();
    }
}
