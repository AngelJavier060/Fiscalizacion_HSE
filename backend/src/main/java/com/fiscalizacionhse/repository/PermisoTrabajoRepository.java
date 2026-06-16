package com.fiscalizacionhse.repository;

import com.fiscalizacionhse.model.PermisoTrabajo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PermisoTrabajoRepository extends JpaRepository<PermisoTrabajo, String> {

    Page<PermisoTrabajo> findByEmpresaIdAndActivoTrueOrderByCreatedAtDesc(
            Long empresaId, Pageable pageable);

    List<PermisoTrabajo> findByEmpresaIdAndActivoTrueOrderByCreatedAtDesc(Long empresaId);

    Page<PermisoTrabajo> findByEmpresaIdAndActivoTrueAndTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
            Long empresaId, String title, String description, Pageable pageable);

    long countByEmpresaIdAndActivoTrue(Long empresaId);

    long countByEmpresaIdAndActivoTrueAndEndDateBefore(Long empresaId, LocalDateTime now);

    long countByEmpresaIdAndActivoTrueAndStartDateBeforeAndEndDateAfter(
            Long empresaId, LocalDateTime now1, LocalDateTime now2);
}
