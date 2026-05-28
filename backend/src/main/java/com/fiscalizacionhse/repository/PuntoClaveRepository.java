package com.fiscalizacionhse.repository;

import com.fiscalizacionhse.model.PuntoClave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PuntoClaveRepository extends JpaRepository<PuntoClave, Long> {

    List<PuntoClave> findByDocumentoIdOrderByOrdenAsc(Long documentoId);

    List<PuntoClave> findByDocumentoIdAndRevisadoFalseOrderByOrdenAsc(Long documentoId);

    List<PuntoClave> findByDocumentoIdAndEsIaTrueOrderByOrdenAsc(Long documentoId);

    void deleteByDocumentoId(Long documentoId);

    long countByDocumentoId(Long documentoId);

    long countByDocumentoIdAndRevisadoTrue(Long documentoId);
}
