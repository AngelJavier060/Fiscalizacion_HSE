package com.fiscalizacionhse.repository;

import com.fiscalizacionhse.model.SsoTema;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SsoTemaRepository extends JpaRepository<SsoTema, Long> {

    Optional<SsoTema> findByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);

    List<SsoTema> findAllByOrderByNombreAsc();

    List<SsoTema> findByActivoTrueOrderByNombreAsc();
}
