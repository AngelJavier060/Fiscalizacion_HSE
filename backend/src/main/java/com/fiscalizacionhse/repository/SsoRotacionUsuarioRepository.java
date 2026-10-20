package com.fiscalizacionhse.repository;

import com.fiscalizacionhse.model.SsoRotacionId;
import com.fiscalizacionhse.model.SsoRotacionUsuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SsoRotacionUsuarioRepository extends JpaRepository<SsoRotacionUsuario, SsoRotacionId> {

    List<SsoRotacionUsuario> findByUsuarioIdAndVuelta(Long usuarioId, Integer vuelta);

    long countByUsuarioIdAndVuelta(Long usuarioId, Integer vuelta);

    boolean existsByUsuarioIdAndPreguntaIdAndVuelta(Long usuarioId, Long preguntaId, Integer vuelta);
}
