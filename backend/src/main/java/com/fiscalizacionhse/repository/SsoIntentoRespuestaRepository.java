package com.fiscalizacionhse.repository;

import com.fiscalizacionhse.model.SsoIntentoRespuesta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SsoIntentoRespuestaRepository extends JpaRepository<SsoIntentoRespuesta, Long> {

    Optional<SsoIntentoRespuesta> findByIntentoIdAndPreguntaId(Long intentoId, Long preguntaId);

    boolean existsByPreguntaId(Long preguntaId);

    @Query("SELECT DISTINCT r.pregunta.id FROM SsoIntentoRespuesta r")
    List<Long> findPreguntaIdsUsadas();

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

    @Query("""
            SELECT r.pregunta.id, COUNT(r),
                   SUM(CASE WHEN r.esCorrecta = true THEN 1 ELSE 0 END)
            FROM SsoIntentoRespuesta r
            WHERE r.intento.estado = 'finalizado'
              AND r.intento.modo <> 'falladas'
              AND r.opcionElegida IS NOT NULL
              AND r.intento.fechaFin >= :desde
            GROUP BY r.pregunta.id
            """)
    List<Object[]> statsAciertoPorPregunta(@Param("desde") LocalDateTime desde);

    @Query("""
            SELECT r.pregunta.id, COUNT(r)
            FROM SsoIntentoRespuesta r
            WHERE r.intento.estado = 'finalizado'
              AND r.intento.modo <> 'falladas'
              AND r.intento.fechaFin >= :desde
            GROUP BY r.pregunta.id
            """)
    List<Object[]> aparicionesPorPregunta(@Param("desde") LocalDateTime desde);

    @Query("""
            SELECT r.pregunta.id, r.opcionElegida.id, COUNT(r)
            FROM SsoIntentoRespuesta r
            WHERE r.intento.estado = 'finalizado'
              AND r.intento.modo <> 'falladas'
              AND r.opcionElegida IS NOT NULL
              AND r.intento.fechaFin >= :desde
            GROUP BY r.pregunta.id, r.opcionElegida.id
            """)
    List<Object[]> votosPorOpcion(@Param("desde") LocalDateTime desde);
}
