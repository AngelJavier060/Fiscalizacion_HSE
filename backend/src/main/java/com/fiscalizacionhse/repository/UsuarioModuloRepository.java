package com.fiscalizacionhse.repository;

import com.fiscalizacionhse.model.UsuarioModulo;
import com.fiscalizacionhse.model.UsuarioModuloId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UsuarioModuloRepository extends JpaRepository<UsuarioModulo, UsuarioModuloId> {
    List<UsuarioModulo> findByUsuarioId(Long usuarioId);
    void deleteByUsuarioId(Long usuarioId);
}
