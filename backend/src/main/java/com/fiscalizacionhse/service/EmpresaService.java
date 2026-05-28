package com.fiscalizacionhse.service;

import com.fiscalizacionhse.dto.request.EmpresaRequest;
import com.fiscalizacionhse.dto.response.EmpresaResponse;
import com.fiscalizacionhse.exception.ResourceNotFoundException;
import com.fiscalizacionhse.model.Empresa;
import com.fiscalizacionhse.model.Usuario;
import com.fiscalizacionhse.repository.EmpresaRepository;
import com.fiscalizacionhse.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmpresaService {

    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    public Page<EmpresaResponse> listar(Pageable pageable) {
        Page<Empresa> empresas = empresaRepository.findAll(pageable);
        return empresas.map(this::toResponse);
    }

    public EmpresaResponse obtener(Long id) {
        return toResponse(buscar(id));
    }

    @Transactional
    public EmpresaResponse crear(EmpresaRequest request, Long actorUsuarioId) {
        Empresa empresa = Empresa.builder()
                .nombre(request.getNombre())
                .ruc(request.getRuc())
                .direccion(request.getDireccion())
                .email(request.getEmail())
                .telefono(request.getTelefono())
                .vigenciaDesde(request.getVigenciaDesde())
                .vigenciaHasta(request.getVigenciaHasta())
                .build();

        empresa = empresaRepository.save(empresa);

        Usuario actor = usuarioActor(actorUsuarioId);
        auditoriaService.registrar(
                actor, null, "CREAR_EMPRESA", "Empresa",
                empresa.getId(), "Empresa creada: " + empresa.getNombre(), null);

        return toResponse(empresa);
    }

    @Transactional
    public EmpresaResponse actualizar(Long id, EmpresaRequest request, Long actorUsuarioId) {
        Empresa empresa = buscar(id);
        empresa.setNombre(request.getNombre());
        empresa.setRuc(request.getRuc());
        empresa.setDireccion(request.getDireccion());
        empresa.setEmail(request.getEmail());
        empresa.setTelefono(request.getTelefono());
        empresa.setVigenciaDesde(request.getVigenciaDesde());
        empresa.setVigenciaHasta(request.getVigenciaHasta());

        empresa = empresaRepository.save(empresa);

        Usuario actor = usuarioActor(actorUsuarioId);
        auditoriaService.registrar(
                actor, empresa, "ACTUALIZAR_EMPRESA", "Empresa",
                empresa.getId(), "Empresa actualizada: " + empresa.getNombre(), null);

        return toResponse(empresa);
    }

    @Transactional
    public EmpresaResponse toggleActivo(Long id, Long actorUsuarioId) {
        Empresa empresa = buscar(id);
        empresa.setActiva(!empresa.getActiva());
        empresa = empresaRepository.save(empresa);

        String accion = empresa.getActiva() ? "ACTIVAR_EMPRESA" : "SUSPENDER_EMPRESA";
        String detalle = "Empresa " + (empresa.getActiva() ? "activada" : "suspendida") +
                         ": " + empresa.getNombre();

        Usuario actor = usuarioActor(actorUsuarioId);
        auditoriaService.registrar(
                actor, empresa, accion, "Empresa",
                empresa.getId(), detalle, null);

        return toResponse(empresa);
    }

    @Transactional
    public void eliminar(Long id, Long actorUsuarioId) {
        Empresa empresa = buscar(id);
        String nombre = empresa.getNombre();
        empresaRepository.delete(empresa);

        Usuario actor = usuarioActor(actorUsuarioId);
        auditoriaService.registrar(
                actor, null, "ELIMINAR_EMPRESA", "Empresa",
                id, "Empresa eliminada: " + nombre, null);
    }

    private Usuario usuarioActor(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", usuarioId));
    }

    private Empresa buscar(Long id) {
        return empresaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Empresa", id));
    }

    private EmpresaResponse toResponse(Empresa e) {
        long cantidadUsuarios = usuarioRepository.findByEmpresaId(e.getId(), Pageable.ofSize(1))
                .getTotalElements();

        return EmpresaResponse.builder()
                .id(e.getId())
                .nombre(e.getNombre())
                .ruc(e.getRuc())
                .direccion(e.getDireccion())
                .email(e.getEmail())
                .telefono(e.getTelefono())
                .activa(e.getActiva())
                .cantidadUsuarios(cantidadUsuarios)
                .vigenciaDesde(e.getVigenciaDesde())
                .vigenciaHasta(e.getVigenciaHasta())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
