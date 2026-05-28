package com.fiscalizacionhse.repository;

import com.fiscalizacionhse.model.Modulo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ModuloRepository extends JpaRepository<Modulo, String> {
    List<Modulo> findAllByOrderByOrdenAsc();
}
