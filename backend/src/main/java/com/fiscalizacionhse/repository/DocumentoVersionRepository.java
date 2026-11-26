package com.fiscalizacionhse.repository;

import com.fiscalizacionhse.model.DocumentoVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentoVersionRepository extends JpaRepository<DocumentoVersion, Long> {

    List<DocumentoVersion> findByDocumentoIdOrderByNumeroVersionDesc(Long documentoId);

    Optional<DocumentoVersion> findByIdAndDocumentoId(Long id, Long documentoId);

    long countByDocumentoId(Long documentoId);
}
