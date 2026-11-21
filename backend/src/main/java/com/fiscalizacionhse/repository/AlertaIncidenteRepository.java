package com.fiscalizacionhse.repository;

import com.fiscalizacionhse.model.AlertaIncidente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertaIncidenteRepository extends JpaRepository<AlertaIncidente, Long> {

    boolean existsByCodigoIgnoreCase(String codigo);

    List<AlertaIncidente> findAllByOrderByFechaDescCreatedAtDesc();
}
