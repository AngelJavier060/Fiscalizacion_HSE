package com.fiscalizacionhse.repository;

import com.fiscalizacionhse.model.SsoPregunta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SsoPreguntaRepository extends JpaRepository<SsoPregunta, Long> {

    @Query("""
            SELECT DISTINCT p FROM SsoPregunta p
            JOIN FETCH p.tema
            LEFT JOIN FETCH p.opciones
            WHERE p.id = :id
            """)
    Optional<SsoPregunta> findDetalleById(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT p FROM SsoPregunta p
            JOIN FETCH p.tema
            LEFT JOIN FETCH p.opciones
            WHERE (:temaId IS NULL OR p.tema.id = :temaId)
              AND (:activo IS NULL OR p.activo = :activo)
              AND (:q = '' OR LOWER(p.enunciado) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR (p.fuente IS NOT NULL AND LOWER(p.fuente) LIKE LOWER(CONCAT('%', :q, '%')))
                   OR (p.explicacion IS NOT NULL AND LOWER(p.explicacion) LIKE LOWER(CONCAT('%', :q, '%'))))
            ORDER BY p.id DESC
            """)
    List<SsoPregunta> buscar(
            @Param("q") String q,
            @Param("temaId") Long temaId,
            @Param("activo") Boolean activo);

    List<SsoPregunta> findByActivoTrue();

    @Query("""
            SELECT DISTINCT p FROM SsoPregunta p
            JOIN FETCH p.tema
            LEFT JOIN FETCH p.opciones
            WHERE p.activo = true
            ORDER BY p.id ASC
            """)
    List<SsoPregunta> findActivasConDetalle();

    List<SsoPregunta> findByActivoTrueAndTemaId(Long temaId);

    boolean existsByEnunciadoIgnoreCase(String enunciado);

    long countByTemaId(Long temaId);

    long countByTemaIdAndActivoTrue(Long temaId);

    long countByActivoTrue();
}
