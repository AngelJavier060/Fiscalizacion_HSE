package com.fiscalizacionhse.repository;

import com.fiscalizacionhse.model.SsoIntento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SsoIntentoRepository extends JpaRepository<SsoIntento, Long> {

    Optional<SsoIntento> findFirstByUsuarioIdAndEstadoOrderByFechaInicioDesc(Long usuarioId, String estado);

    List<SsoIntento> findByUsuarioIdOrderByFechaInicioDesc(Long usuarioId);

    @Query("SELECT i FROM SsoIntento i JOIN FETCH i.usuario WHERE i.estado = :estado")
    List<SsoIntento> findByEstado(@Param("estado") String estado);

    @Query("""
            SELECT i FROM SsoIntento i
            LEFT JOIN FETCH i.tema
            WHERE i.id = :id AND i.usuario.id = :usuarioId
            """)
    Optional<SsoIntento> findByIdAndUsuarioId(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    @Query("""
            SELECT DISTINCT i FROM SsoIntento i
            LEFT JOIN FETCH i.respuestas r
            LEFT JOIN FETCH r.pregunta p
            LEFT JOIN FETCH p.tema
            LEFT JOIN FETCH r.opcionElegida
            WHERE i.id = :id AND i.usuario.id = :usuarioId
            """)
    Optional<SsoIntento> findDetalleByIdAndUsuarioId(@Param("id") Long id, @Param("usuarioId") Long usuarioId);
}
