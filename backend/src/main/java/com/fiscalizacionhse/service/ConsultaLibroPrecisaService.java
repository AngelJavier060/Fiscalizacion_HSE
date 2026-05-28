package com.fiscalizacionhse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * Responde transcribiendo literalmente solo lo que el usuario pidió:
 * un CC concreto, solo criterios de calidad, o el estándar completo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConsultaLibroPrecisaService {

    private final CatalogoControlesCriticosService catalogoCcService;
    private final CriteriosCalidadExtraccionService criteriosCalidadService;

    /**
     * @return texto transcrito del PDF si la pregunta es sobre un estándar/CC del libro; vacío si no aplica.
     */
    public Optional<String> responder(String tituloDocumento, String textoCompleto, String pregunta) {
        Optional<EstandarConsultaHelper.ConsultaLibro> consulta =
                EstandarConsultaHelper.clasificarConsultaLibro(pregunta);
        if (consulta.isEmpty() || textoCompleto == null || textoCompleto.isBlank()) {
            return Optional.empty();
        }

        EstandarConsultaHelper.ConsultaLibro c = consulta.get();
        log.info("📖 Consulta precisa — tipo={}, estándar={}, cc={}",
                c.tipo(), c.nombreEstandar().orElse("-"), c.codigoCc().orElse("-"));

        return switch (c.tipo()) {
            case CRITERIOS_CALIDAD -> criteriosCalidadService.responderCriteriosDeEstandar(
                    tituloDocumento, textoCompleto, pregunta);
            case CC_ESPECIFICO -> catalogoCcService.responderEstandarConTodosLosCc(
                    tituloDocumento, textoCompleto, pregunta);
            case ESTANDAR_COMPLETO -> {
                if (EstandarConsultaHelper.esConsultaResumenInformal(pregunta)) {
                    yield Optional.empty();
                }
                yield catalogoCcService.responderEstandarConTodosLosCc(
                        tituloDocumento, textoCompleto, pregunta);
            }
        };
    }

    public Optional<String> responderOMensaje(String tituloDocumento, String textoCompleto, String pregunta) {
        Optional<EstandarConsultaHelper.ConsultaLibro> consulta =
                EstandarConsultaHelper.clasificarConsultaLibro(pregunta);
        if (consulta.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> respuesta = responder(tituloDocumento, textoCompleto, pregunta);
        if (respuesta.isPresent()) {
            return respuesta;
        }
        EstandarConsultaHelper.ConsultaLibro c = consulta.get();
        // Pregunta abierta («háblame de…»): no devolver error aquí; el agente RAG redacta con la sección focal.
        if (c.tipo() == EstandarConsultaHelper.TipoConsultaEstandar.ESTANDAR_COMPLETO
                && EstandarConsultaHelper.esConsultaResumenInformal(pregunta)) {
            return Optional.empty();
        }
        if (c.tipo() == EstandarConsultaHelper.TipoConsultaEstandar.CRITERIOS_CALIDAD) {
            return Optional.of(mensajeSinCriterios(c));
        }
        return Optional.of(mensajeSinContenido(c));
    }

    private static String mensajeSinCriterios(EstandarConsultaHelper.ConsultaLibro c) {
        String est = c.nombreEstandar().orElse("el estándar indicado").toUpperCase(Locale.ROOT);
        String cc = c.codigoCc().map(code -> " (" + code + ")").orElse("");
        return "## Criterios de Calidad\n\nNo se encontró ese bloque en el PDF para **" + est + "**" + cc + ".\n";
    }

    private static String mensajeSinContenido(EstandarConsultaHelper.ConsultaLibro c) {
        if (c.codigoCc().isPresent()) {
            String cc = c.codigoCc().get();
            String est = c.nombreEstandar().orElse("").toUpperCase(Locale.ROOT);
            return "## " + cc + "\n\nNo se encontró **" + cc + "**"
                    + (est.isBlank() ? "" : " en ESTÁNDAR DE " + est)
                    + " en el texto extraído del PDF.\n";
        }
        String est = c.nombreEstandar().orElse("el estándar indicado").toUpperCase(Locale.ROOT);
        return "## Estándar\n\nNo se pudo leer **" + est + "** en el PDF (¿texto seleccionable?).\n";
    }
}
