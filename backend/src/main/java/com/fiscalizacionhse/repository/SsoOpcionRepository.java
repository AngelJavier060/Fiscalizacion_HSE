package com.fiscalizacionhse.repository;

import com.fiscalizacionhse.model.SsoOpcion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SsoOpcionRepository extends JpaRepository<SsoOpcion, Long> {

    List<SsoOpcion> findByPreguntaId(Long preguntaId);
}
