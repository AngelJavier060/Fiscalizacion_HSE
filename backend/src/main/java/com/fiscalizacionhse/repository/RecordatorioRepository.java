package com.fiscalizacionhse.repository;

import com.fiscalizacionhse.model.Recordatorio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RecordatorioRepository extends JpaRepository<Recordatorio, Long> {

    Page<Recordatorio> findByEmpresaIdOrderByCreatedAtDesc(Long empresaId, Pageable pageable);

    List<Recordatorio> findByEmpresaIdAndActivoTrue(Long empresaId);

    @Query("SELECT r FROM Recordatorio r WHERE r.activo = true " +
           "AND r.proximaEjecucion IS NOT NULL " +
           "AND r.proximaEjecucion <= :hasta " +
           "ORDER BY r.proximaEjecucion ASC")
    List<Recordatorio> findVencidos(@Param("hasta") LocalDateTime hasta);

    @Query("SELECT r FROM Recordatorio r WHERE r.activo = true " +
           "AND r.proximaEjecucion IS NULL " +
           "AND r.fechaInicio <= CURRENT_DATE " +
           "ORDER BY r.createdAt ASC")
    List<Recordatorio> findPendientesIniciar();

    long countByEmpresaIdAndActivoTrue(Long empresaId);

    long countByEmpresaIdAndActivoTrueAndFechaInicioGreaterThanEqual(
            Long empresaId, java.time.LocalDate fecha);
}
