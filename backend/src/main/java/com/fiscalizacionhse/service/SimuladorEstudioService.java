package com.fiscalizacionhse.service;

import com.fiscalizacionhse.dto.response.SsoEstudioPreguntaResponse;
import com.fiscalizacionhse.dto.response.SsoTemaResponse;
import com.fiscalizacionhse.model.SsoPregunta;
import com.fiscalizacionhse.repository.SsoPreguntaRepository;
import com.fiscalizacionhse.repository.SsoTemaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SimuladorEstudioService {

    private final SsoPreguntaRepository preguntaRepository;
    private final SsoTemaRepository temaRepository;

    @Transactional(readOnly = true)
    public List<SsoTemaResponse> listarTemas() {
        return temaRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(t -> SsoTemaResponse.builder()
                        .id(t.getId())
                        .nombre(t.getNombre())
                        .activo(true)
                        .totalPreguntas(preguntaRepository.countByTemaIdAndActivoTrue(t.getId()))
                        .preguntasActivas(preguntaRepository.countByTemaIdAndActivoTrue(t.getId()))
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SsoEstudioPreguntaResponse> listarPreguntas(String q, Long temaId) {
        String query = q == null ? "" : q.trim();
        return preguntaRepository.buscar(query, temaId, true).stream()
                .sorted(Comparator
                        .comparing((SsoPregunta p) -> p.getTema().getNombre(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(SsoPregunta::getId))
                .map(this::toEstudio)
                .toList();
    }

    private SsoEstudioPreguntaResponse toEstudio(SsoPregunta p) {
        return SsoEstudioPreguntaResponse.builder()
                .id(p.getId())
                .temaId(p.getTema().getId())
                .temaNombre(p.getTema().getNombre())
                .enunciado(p.getEnunciado())
                .explicacion(p.getExplicacion())
                .fuente(p.getFuente())
                .tieneSustento(p.getSustentoRuta() != null && !p.getSustentoRuta().isBlank())
                .sustentoNombre(p.getSustentoNombre())
                .sustentoTipo(p.getSustentoTipo())
                .build();
    }
}
