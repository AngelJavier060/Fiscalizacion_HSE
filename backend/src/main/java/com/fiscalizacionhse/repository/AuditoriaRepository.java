package com.fiscalizacionhse.repository;

import com.fiscalizacionhse.model.Auditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {

    @EntityGraph(attributePaths = {"usuario", "empresa"})
    Page<Auditoria> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"usuario", "empresa"})
    Page<Auditoria> findByEmpresaIdOrderByCreatedAtDesc(Long empresaId, Pageable pageable);

    @EntityGraph(attributePaths = {"usuario", "empresa"})
    Page<Auditoria> findByUsuarioIdOrderByCreatedAtDesc(Long usuarioId, Pageable pageable);

    List<Auditoria> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime desde, LocalDateTime hasta);

    long countByAccion(String accion);
}
