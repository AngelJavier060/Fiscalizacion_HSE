package com.fiscalizacionhse.repository;

import com.fiscalizacionhse.model.EmbeddingDocumento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmbeddingDocumentoRepository extends JpaRepository<EmbeddingDocumento, Long> {

    List<EmbeddingDocumento> findByDocumentoIdOrderByChunkOrderAsc(Long documentoId);

    List<EmbeddingDocumento> findByEmpresaId(Long empresaId);

    long countByDocumentoId(Long documentoId);

    long countByEmpresaId(Long empresaId);

    @Modifying
    void deleteByDocumentoId(Long documentoId);

    /**
     * Búsqueda semántica usando pgvector (cosine similarity)
     * Nota: Para usar esto, PostgreSQL debe tener la extensión pgvector instalada
     */
    @Query(value = """
        SELECT e.*, 1 - (e.embedding <=> CAST(:embedding AS vector)) AS similarity
        FROM ia_embeddings e
        WHERE e.empresa_id = :empresaId
          AND e.embedding IS NOT NULL
        ORDER BY e.embedding <=> CAST(:embedding AS vector)
        LIMIT :limit
    """, nativeQuery = true)
    List<Object[]> buscarPorSimilitud(
            @Param("empresaId") Long empresaId,
            @Param("embedding") String embedding,
            @Param("limit") int limit
    );

    /**
     * Búsqueda híbrida cuando pgvector no está disponible
     * Usa búsqueda por coincidencia de palabras clave
     */
    @Query(value = """
        SELECT e.*, ts_rank(
            to_tsvector('spanish', e.chunk_text),
            plainto_tsquery('spanish', :query)
        ) AS similarity
        FROM ia_embeddings e
        WHERE e.empresa_id = :empresaId
          AND to_tsvector('spanish', e.chunk_text) @@ plainto_tsquery('spanish', :query)
        ORDER BY similarity DESC
        LIMIT :limit
    """, nativeQuery = true)
    List<Object[]> buscarPorTexto(
            @Param("empresaId") Long empresaId,
            @Param("query") String query,
            @Param("limit") int limit
    );
}
