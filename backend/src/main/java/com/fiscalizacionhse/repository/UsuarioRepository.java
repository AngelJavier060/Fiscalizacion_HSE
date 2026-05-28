package com.fiscalizacionhse.repository;

import com.fiscalizacionhse.model.Usuario;
import com.fiscalizacionhse.model.enums.RolUsuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    Optional<Usuario> findByEmailAndActivoTrue(String email);

    Optional<Usuario> findByIdAndActivoTrue(Long id);

    Page<Usuario> findByEmpresaId(Long empresaId, Pageable pageable);

    long countByRol(RolUsuario rol);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    List<Usuario> findByEmpresaIdAndActivoTrue(Long empresaId);
}
