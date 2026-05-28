package com.fiscalizacionhse.repository;

import com.fiscalizacionhse.model.ConsultaIa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConsultaIaRepository extends JpaRepository<ConsultaIa, Long> {

    Page<ConsultaIa> findByUsuarioIdOrderByCreatedAtDesc(Long usuarioId, Pageable pageable);

    Page<ConsultaIa> findByEmpresaIdOrderByCreatedAtDesc(Long empresaId, Pageable pageable);

    List<ConsultaIa> findByUsuarioIdAndCreatedAtAfterOrderByCreatedAtAsc(
            Long usuarioId, java.time.LocalDateTime since);

    long countByEmpresaId(Long empresaId);

    long countByUsuarioId(Long usuarioId);
}
