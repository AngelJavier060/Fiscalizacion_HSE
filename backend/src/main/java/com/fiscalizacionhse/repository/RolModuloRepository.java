package com.fiscalizacionhse.repository;

import com.fiscalizacionhse.model.RolModulo;
import com.fiscalizacionhse.model.RolModuloId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RolModuloRepository extends JpaRepository<RolModulo, RolModuloId> {
    List<RolModulo> findByRol(String rol);
}
