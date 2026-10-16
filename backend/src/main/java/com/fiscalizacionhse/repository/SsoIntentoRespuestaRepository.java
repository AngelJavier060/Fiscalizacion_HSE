package com.fiscalizacionhse.repository;

import com.fiscalizacionhse.model.SsoIntentoRespuesta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SsoIntentoRespuestaRepository extends JpaRepository<SsoIntentoRespuesta, Long> {

    Optional<SsoIntentoRespuesta> findByIntentoIdAndPreguntaId(Long intentoId, Long preguntaId);

    boolean existsByPreguntaId(Long preguntaId);

    boolean existsByOpcionElegidaId(Long opcionId);

    @Query("""
            SELECT r.pregunta.id FROM SsoIntentoRespuesta r
            WHERE r.intento.usuario.id = :usuarioId
              AND r.intento.estado = 'finalizado'
              AND r.intento.modo <> 'falladas'
              AND r.esCorrecta = false
              AND r.intento.fechaFin = (
                    SELECT MAX(i2.fechaFin) FROM SsoIntento i2
                    JOIN i2.respuestas r2
                    WHERE i2.usuario.id = :usuarioId
                      AND i2.estado = 'finalizado'
                      AND i2.modo <> 'falladas'
                      AND r2.pregunta.id = r.pregunta.id
              )
            """)
    List<Long> findPreguntaIdsUltimaIncorrecta(@Param("usuarioId") Long usuarioId);
}
