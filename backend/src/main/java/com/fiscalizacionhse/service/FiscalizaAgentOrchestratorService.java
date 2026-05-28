package com.fiscalizacionhse.service;

import com.fiscalizacionhse.dto.response.BusquedaIaResponse;
import com.fiscalizacionhse.dto.response.PasoAgente;
import com.fiscalizacionhse.model.Documento;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orquestador multi-paso del agente FISCALIZA-AI:
 * listar → buscar → leer PDF prioritarios → redactar con DeepSeek.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FiscalizaAgentOrchestratorService {

    private static final int MAX_DOCS_LECTURA = 3;
    private static final int MAX_CHARS_LECTURA_POR_DOC = 6000;
    private static final int MAX_CHARS_LECTURA_RESUMEN = 2800;
    private static final int MAX_PASAJES_RESUMEN = 5;

    private final IaBusquedaService busquedaService;
    private final IaChatService chatService;

    public record ResultadoFlujoAgente(
            String respuesta,
            List<PasoAgente> pasos,
            List<BusquedaIaResponse.ResultadoBusqueda> fragmentos,
            String documentosRef,
            String advertencia
    ) {}

    public ResultadoFlujoAgente ejecutarConsultaContenido(
            String pregunta,
            Long empresaId,
            List<Documento> todosActivos,
            List<BusquedaIaResponse.ResultadoBusqueda> fragmentos) {

        List<PasoAgente> pasos = new ArrayList<>();
        int orden = 1;

        pasos.add(PasoAgente.builder()
                .orden(orden++)
                .herramienta("listar_documentos")
                .titulo("Biblioteca HSE")
                .detalle("Identificados **" + todosActivos.size() + "** PDF activos en la empresa.")
                .estado("ok")
                .build());

        List<BusquedaIaResponse.ResultadoBusqueda> frags =
                fragmentos != null ? fragmentos : List.of();

        Set<Long> docsEnFragmentos = frags.stream()
                .map(BusquedaIaResponse.ResultadoBusqueda::getDocumentoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        pasos.add(PasoAgente.builder()
                .orden(orden++)
                .herramienta("buscar_fragmentos")
                .titulo("Búsqueda semántica")
                .detalle(frags.isEmpty()
                        ? "No se hallaron fragmentos relacionados con la pregunta."
                        : "Recuperados **" + frags.size() + "** fragmentos en **"
                        + docsEnFragmentos.size() + "** documento(s).")
                .estado(frags.isEmpty() ? "omitido" : "ok")
                .build());

        boolean resumenBreve = chatService.esConsultaResumenInformal(pregunta)
                && !chatService.preguntaPideListaCompleta(pregunta);
        boolean preguntaPorEstandar = EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta).isPresent();

        String bloqueLectura = "";
        List<Long> docsLeidos = seleccionarDocumentosParaLectura(pregunta, frags, todosActivos);
        if (!(resumenBreve && preguntaPorEstandar) && !docsLeidos.isEmpty()) {
            bloqueLectura = leerDocumentosPrioritarios(pregunta, docsLeidos, todosActivos);
            pasos.add(PasoAgente.builder()
                    .orden(orden++)
                    .herramienta("leer_documentos")
                    .titulo("Lectura de PDF")
                    .detalle("Revisados en profundidad **" + docsLeidos.size() + "** archivo(s): "
                            + titulosDocs(docsLeidos, todosActivos) + ".")
                    .estado("ok")
                    .build());
        } else {
            pasos.add(PasoAgente.builder()
                    .orden(orden++)
                    .herramienta("leer_documentos")
                    .titulo("Lectura de PDF")
                    .detalle("Sin archivos prioritarios para lectura ampliada.")
                    .estado("omitido")
                    .build());
        }

        String ctxFragmentos = busquedaService.construirContextoFragmentos(frags);

        String bloqueSecciones = "";
        if (!resumenBreve
                && chatService.preguntaRequiereExtraccionTematicaCompleta(pregunta)
                && !frags.isEmpty()) {
            bloqueSecciones = busquedaService.construirSeccionesTematicas(pregunta, frags);
        }

        String bloqueEstandarFocal = "";
        if (resumenBreve) {
            bloqueEstandarFocal = extraerSeccionEstandarParaResumen(pregunta, docsLeidos, todosActivos);
        }
        boolean tieneFocalEstandar = bloqueEstandarFocal != null && !bloqueEstandarFocal.isBlank();

        StringBuilder contextoFinal = new StringBuilder();
        if (tieneFocalEstandar) {
            contextoFinal.append(bloqueEstandarFocal).append("\n\n");
        }
        if (bloqueSecciones != null && !bloqueSecciones.isBlank()) {
            contextoFinal.append(bloqueSecciones).append("\n\n");
        }
        if (!resumenBreve || !tieneFocalEstandar) {
            if (bloqueLectura != null && !bloqueLectura.isBlank()) {
                contextoFinal.append(bloqueLectura).append("\n\n");
            }
        }
        if (ctxFragmentos != null && !ctxFragmentos.isBlank()
                && !(resumenBreve && tieneFocalEstandar)) {
            contextoFinal.append(ctxFragmentos);
        }

        String documentosRef = frags.isEmpty()
                ? "[]"
                : busquedaService.construirReferenciasJson(frags);

        String contextoTrim = contextoFinal.toString().trim();
        String tituloFuente = resolverTituloFuenteEstandar(pregunta, todosActivos);
        String respuesta;
        Optional<String> apartadoLiteral = busquedaService.intentarRespuestaApartadoLiteral(todosActivos, pregunta);
        if (apartadoLiteral.isPresent()) {
            respuesta = apartadoLiteral.get();
        } else if (resumenBreve && tieneFocalEstandar
                && EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta).isPresent()) {
            respuesta = chatService.generarRespuestaConcisaEstandarDocumento(
                    pregunta, tituloFuente, contextoTrim);
        } else {
            respuesta = chatService.generarRespuestaRag(pregunta, contextoTrim, documentosRef);
        }

        pasos.add(PasoAgente.builder()
                .orden(orden)
                .herramienta("redactar_respuesta")
                .titulo(apartadoLiteral.isPresent() ? "Texto literal del apartado"
                        : resumenBreve && tieneFocalEstandar ? "Resumen del estándar" : "Redacción DeepSeek")
                .detalle(chatService.deepseekDisponible()
                        ? "Respuesta generada solo con la sección del estándar pedido."
                        : "DeepSeek no configurado; respuesta limitada.")
                .estado(chatService.deepseekDisponible() ? "ok" : "error")
                .build());

        String advertencia = null;
        if (!chatService.deepseekDisponible()) {
            advertencia = "Motor DeepSeek no configurado: configure DEEPSEEK_API_KEY en el servidor.";
        } else if (frags.isEmpty()) {
            advertencia = "No se encontraron fragmentos relacionados. Reformule la pregunta o elija un documento concreto.";
        }

        log.info("Agente multi-paso empresa {} — {} pasos, {} fragmentos",
                empresaId, pasos.size(), frags.size());

        return new ResultadoFlujoAgente(respuesta, pasos, frags, documentosRef, advertencia);
    }

    public List<PasoAgente> pasosInventario(int totalDocs) {
        return List.of(PasoAgente.builder()
                .orden(1)
                .herramienta("listar_documentos")
                .titulo("Inventario")
                .detalle("Listados **" + totalDocs + "** PDF de la empresa.")
                .estado("ok")
                .build());
    }

    public List<PasoAgente> pasosCapacidades(int totalDocs, boolean deepseek) {
        return List.of(
                PasoAgente.builder()
                        .orden(1)
                        .herramienta("listar_documentos")
                        .titulo("Biblioteca HSE")
                        .detalle(totalDocs + " PDF disponibles.")
                        .estado("ok")
                        .build(),
                PasoAgente.builder()
                        .orden(2)
                        .herramienta("explicar_capacidades")
                        .titulo("Capacidades del agente")
                        .detalle(deepseek ? "DeepSeek conectado." : "DeepSeek no configurado.")
                        .estado("ok")
                        .build()
        );
    }

    private List<Long> seleccionarDocumentosParaLectura(
            String pregunta,
            List<BusquedaIaResponse.ResultadoBusqueda> fragmentos,
            List<Documento> todosActivos) {

        LinkedHashSet<Long> ids = new LinkedHashSet<>();

        Optional<String> estandarPregunta = EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta);
        if (estandarPregunta.isPresent()) {
            String nombreEstandar = estandarPregunta.get();
            for (Documento doc : todosActivos) {
                String texto = busquedaService.obtenerTextoDocumento(doc);
                if (texto == null || texto.replaceAll("\\s+", "").length() < 40) {
                    continue;
                }
                if (EstandarConsultaHelper.localizarSeccionEstandar(texto, nombreEstandar) >= 0) {
                    ids.add(doc.getId());
                    if (ids.size() >= MAX_DOCS_LECTURA) {
                        break;
                    }
                }
            }
        }

        for (BusquedaIaResponse.ResultadoBusqueda f : fragmentos) {
            if (f.getDocumentoId() != null) {
                ids.add(f.getDocumentoId());
                if (ids.size() >= MAX_DOCS_LECTURA) {
                    break;
                }
            }
        }
        if (ids.isEmpty() && !todosActivos.isEmpty()) {
            todosActivos.stream().limit(1).map(Documento::getId).forEach(ids::add);
        }
        return new ArrayList<>(ids);
    }

    private String leerDocumentosPrioritarios(
            String pregunta,
            List<Long> docIds,
            List<Documento> todosActivos) {

        Map<Long, Documento> porId = todosActivos.stream()
                .collect(Collectors.toMap(Documento::getId, d -> d, (a, b) -> a));

        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════════\n");
        sb.append("LECTURA AMPLIADA DE PDF PRIORITARIOS (agente)\n");
        sb.append("Pasajes extraídos del texto completo según la pregunta.\n\n");

        for (Long id : docIds) {
            Documento doc = porId.get(id);
            if (doc == null) {
                continue;
            }
            String texto = busquedaService.obtenerTextoDocumento(doc);
            if (texto == null || texto.replaceAll("\\s+", "").length() < 40) {
                continue;
            }
            String pasajes = extraerPasajesRelevantes(
                    pregunta, texto,
                    chatService.esConsultaResumenInformal(pregunta)
                            ? MAX_CHARS_LECTURA_RESUMEN
                            : MAX_CHARS_LECTURA_POR_DOC);
            sb.append("--- PDF: «").append(doc.getTitulo()).append("» (ID ").append(id).append(") ---\n");
            sb.append(pasajes).append("\n\n");
        }
        sb.append("═══════════════════════════════════════════════════════\n");
        return sb.toString();
    }

    private String extraerPasajesRelevantes(String pregunta, String texto, int maxChars) {
        Set<String> terminos = tokenizar(pregunta);
        if (terminos.isEmpty()) {
            return truncar(texto, maxChars);
        }

        String[] parrafos = texto.split("\\n{2,}|(?<=[.!?])\\s+");
        List<Map.Entry<Integer, String>> puntuados = new ArrayList<>();

        for (String p : parrafos) {
            String limpio = p.replaceAll("\\s+", " ").trim();
            if (limpio.length() < 30) {
                continue;
            }
            int score = 0;
            String norm = normalizar(limpio);
            for (String t : terminos) {
                if (norm.contains(t)) {
                    score++;
                }
            }
            if (score > 0) {
                puntuados.add(Map.entry(score, limpio));
            }
        }

        puntuados.sort((a, b) -> Integer.compare(b.getKey(), a.getKey()));

        StringBuilder sb = new StringBuilder();
        int chars = 0;
        int tomados = 0;
        int maxPasajes = chatService.esConsultaResumenInformal(pregunta)
                ? MAX_PASAJES_RESUMEN
                : 12;
        for (Map.Entry<Integer, String> e : puntuados) {
            if (tomados >= maxPasajes || chars >= maxChars) {
                break;
            }
            sb.append("• ").append(e.getValue()).append("\n\n");
            chars += e.getValue().length();
            tomados++;
        }

        if (sb.length() < 80) {
            return truncar(texto, maxChars);
        }
        return sb.toString().trim();
    }

    private String extraerSeccionEstandarParaResumen(
            String pregunta,
            List<Long> docIds,
            List<Documento> todosActivos) {

        Optional<String> estandarOpt = EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta);
        if (estandarOpt.isEmpty()) {
            return "";
        }
        String nombreEstandar = estandarOpt.get();
        Map<Long, Documento> porId = todosActivos.stream()
                .collect(Collectors.toMap(Documento::getId, d -> d, (a, b) -> a));

        LinkedHashSet<Long> candidatos = new LinkedHashSet<>();
        for (Documento doc : todosActivos) {
            if (doc == null || !Boolean.TRUE.equals(doc.getActivo())) {
                continue;
            }
            if (busquedaService.extraerSeccionEstandarDesdeEditor(doc, pregunta).isPresent()) {
                candidatos.add(doc.getId());
                continue;
            }
            String texto = busquedaService.obtenerTextoDocumento(doc);
            if (texto != null
                    && EstandarConsultaHelper.localizarSeccionEstandar(texto, nombreEstandar) >= 0) {
                candidatos.add(doc.getId());
            }
        }
        if (candidatos.isEmpty()) {
            if (!docIds.isEmpty()) {
                candidatos.addAll(docIds);
            } else {
                todosActivos.stream().map(Documento::getId).limit(2).forEach(candidatos::add);
            }
        }

        StringBuilder bloques = new StringBuilder();
        int agregados = 0;
        for (Long id : candidatos) {
            if (agregados >= MAX_DOCS_LECTURA) {
                break;
            }
            Documento doc = porId.get(id);
            if (doc == null) {
                continue;
            }
            String extracto = null;
            String fuente = "Editor de Contenido";
            Optional<String> desdeEditor = busquedaService.extraerSeccionEstandarDesdeEditor(doc, pregunta);
            if (desdeEditor.isPresent()) {
                extracto = desdeEditor.get();
            } else {
                String texto = busquedaService.textoParaExtraccionSeccionEstandar(doc, pregunta);
                if (texto == null || texto.replaceAll("\\s+", "").length() < 40) {
                    continue;
                }
                int inicio = EstandarConsultaHelper.localizarSeccionEstandar(texto, nombreEstandar);
                if (inicio < 0) {
                    continue;
                }
                int fin = EstandarConsultaHelper.encontrarFinSeccionEstandar(texto, inicio, nombreEstandar);
                extracto = texto.substring(inicio, fin).trim();
                fuente = "texto del documento";
            }
            if (extracto == null || extracto.length() < 80) {
                continue;
            }
            if (extracto.length() > 5500) {
                extracto = extracto.substring(0, 5500) + "\n…";
            }
            bloques.append("""
                    ═══════════════════════════════════════════════════════
                    SECCIÓN FOCAL — %s
                    Estándar: %s | Documento: «%s» (%s)
                    ═══════════════════════════════════════════════════════

                    %s

                    """.formatted(
                    agregados + 1,
                    nombreEstandar.toUpperCase(Locale.ROOT),
                    doc.getTitulo(),
                    fuente,
                    extracto));
            agregados++;
        }
        return bloques.toString().trim();
    }

    private String resolverTituloFuenteEstandar(String pregunta, List<Documento> activos) {
        for (Documento doc : activos) {
            if (doc == null || !Boolean.TRUE.equals(doc.getActivo())) {
                continue;
            }
            if (busquedaService.extraerSeccionEstandarDesdeEditor(doc, pregunta).isPresent()) {
                return doc.getTitulo();
            }
        }
        return activos.isEmpty() ? "Documentos HSE" : activos.get(0).getTitulo();
    }

    private static Set<String> tokenizar(String texto) {
        Set<String> out = new LinkedHashSet<>();
        if (texto == null) {
            return out;
        }
        String n = normalizar(texto);
        for (String w : n.split("\\s+")) {
            if (w.length() >= 4) {
                out.add(w);
            }
        }
        if (n.contains("altura")) {
            out.add("altura");
            out.add("alturas");
            out.add("arnes");
            out.add("andamio");
        }
        if (n.contains("estandar") || n.contains("standard")) {
            out.add("estandar");
        }
        return out;
    }

    private static String titulosDocs(List<Long> ids, List<Documento> docs) {
        Map<Long, String> map = docs.stream()
                .collect(Collectors.toMap(Documento::getId, Documento::getTitulo, (a, b) -> a));
        return ids.stream()
                .map(id -> "«" + map.getOrDefault(id, "ID " + id) + "»")
                .collect(Collectors.joining(", "));
    }

    private static String truncar(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max).trim() + "…";
    }

    private static String normalizar(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }
}
