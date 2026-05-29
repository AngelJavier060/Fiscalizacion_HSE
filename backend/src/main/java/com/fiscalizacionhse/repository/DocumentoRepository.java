package com.fiscalizacionhse.repository;

import com.fiscalizacionhse.model.Documento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentoRepository extends JpaRepository<Documento, Long> {

    Page<Documento> findByEmpresaIdAndActivoTrueOrderByCreatedAtDesc(Long empresaId, Pageable pageable);

    List<Documento> findByEmpresaIdAndActivoTrue(Long empresaId);

    @Query("""
            SELECT d.id AS id, d.titulo AS titulo
            FROM Documento d
            WHERE d.empresa.id = :empresaId AND d.activo = true
            ORDER BY d.titulo ASC
            """)
    List<DocumentoIdTituloView> findActivosIdTituloByEmpresaId(@Param("empresaId") Long empresaId);

    @Query("""
            SELECT d.textoExtraido FROM Documento d
            WHERE d.id = :id AND d.empresa.id = :empresaId AND d.activo = true
            """)
    Optional<String> findTextoExtraidoByIdAndEmpresaId(
            @Param("id") Long id, @Param("empresaId") Long empresaId);

    @Query("SELECT d.titulo FROM Documento d WHERE d.id = :id")
    Optional<String> findTituloById(@Param("id") Long id);

    long countByEmpresaIdAndActivoTrue(Long empresaId);

    @Query(value = """
        SELECT d FROM Documento d
        WHERE d.empresa.id = :empresaId
        AND d.activo = true
        AND (
            LOWER(d.titulo) LIKE LOWER(CONCAT('%', :termino, '%'))
            OR LOWER(d.textoExtraido) LIKE LOWER(CONCAT('%', :termino, '%'))
            OR LOWER(d.textoTraducido) LIKE LOWER(CONCAT('%', :termino, '%'))
            OR LOWER(d.descripcion) LIKE LOWER(CONCAT('%', :termino, '%'))
        )
        ORDER BY d.createdAt DESC
    """)
    Page<Documento> buscarPorTermino(@Param("empresaId") Long empresaId,
                                      @Param("termino") String termino,
                                      Pageable pageable);

    List<Documento> findByEstadoProcesamientoAndActivoTrueAndUpdatedAtBefore(
            String estadoProcesamiento, LocalDateTime updatedAtBefore);

    List<Documento> findByEstadoProcesamientoAndActivoTrueAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            String estadoProcesamiento, LocalDateTime updatedAtBefore, org.springframework.data.domain.Pageable pageable);
}
