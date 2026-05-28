package com.fiscalizacionhse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscalizacionhse.dto.response.BusquedaAsistidaResponse;
import com.fiscalizacionhse.dto.response.BusquedaIaResponse;
import com.fiscalizacionhse.dto.response.PasoAgente;
import com.fiscalizacionhse.exception.BadRequestException;
import com.fiscalizacionhse.exception.ResourceNotFoundException;
import com.fiscalizacionhse.model.*;
import com.fiscalizacionhse.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class IaBusquedaService {

    private static final int MIN_TEXTO_DOC_IA = 20;

    /** Pool de chunks ordenados por similitud antes de equilibrar entre PDF distintos. */
    private static final int RAG_POOL_TOP_N = 400;

    /** Fragmentos máximos que se envían al modelo en una consulta multi-documento. */
    private static final int RAG_MAX_FRAGMENTOS_TOTAL = 40;

    /** Máximo de fragmentos por documento en primera pasada (evita que un solo PDF monopolice el contexto). */
    private static final int RAG_MAX_FRAGMENTOS_POR_DOCUMENTO = 5;

    /** Fallback SQL texto: suficientes filas para repartir entre varios PDF. */
    private static final int RAG_FALLBACK_SQL_LIMIT = 100;

    /** PDF más relevantes a los que se inyecta sección temática completa (listados CC, factores, etc.). */
    private static final int RAG_MAX_DOCS_SECCION_COMPLETA = 2;

    private static final String MARCADOR_SECCIONES_COMPLETAS = "SECCIONES TEMÁTICAS COMPLETAS";

    private static final String MARCADOR_CATALOGO_EMPRESA = "CATÁLOGO DE PDF DISPONIBLE EN LA EMPRESA";

    /**
     * Cuando el usuario pide inventario / títulos cargados, incluimos el catálogo aunque ya haya chunks RAG,
     * para que la IA cite nombres exactos de archivos.
     */
    private static final List<String> PATRONES_CONSULTA_CATALOGO = List.of(
            "que documentos", "cuales documentos", "cual documento", "listado de document", "lista de document",
            "documentos disponibles", "documentos cargados", "documentos subidos", "documentos tengo",
            "documentos tiene", "documentos hay", "documento sub", "archivos cargados", "archivos subidos",
            "archivos tengo", "archivos tiene", "pdf cargados", "pdf subidos", "que archivos", "que pdf",
            "que libros", "libros disponibles", "titulos de los", "titulos de las", "titulo de los",
            "nombres de los document", "nombre de los document", "inventario de document",
            "documentos de la empresa", "documentos en fiscaliza",             "documentos en la plataforma",
            "informacion tengo", "información tengo",
            "los titulos", "los títulos",
            "what documents", "which documents", "uploaded files", "my documents", "list documents");

    /** Máx. caracteres de descripción en el bloque catálogo (prompt). */
    private static final int DESC_CATALOGO_PROMPT_MAX = 180;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EmbeddingDocumentoRepository embeddingRepository;
    private final DocumentoRepository documentoRepository;
    private final EmpresaRepository empresaRepository;
    private final ConsultaIaRepository consultaIaRepository;
    private final IaEmbeddingService embeddingService;
    private final IaChatService chatService;
    private final UsuarioRepository usuarioRepository;
    private final PuntoClaveRepository puntoClaveRepository;
    private final CatalogoControlesCriticosService catalogoCcService;
    private final CriteriosCalidadExtraccionService criteriosCalidadService;
    private final TranscripcionLibroConsultaService transcripcionLibroService;
    private final ConsultaLibroPrecisaService consultaLibroPrecisaService;
    private final FiscalizaAgentService fiscalizaAgentService;
    private final FiscalizaAgentOrchestratorService agentOrchestrator;
    private final ContenidoEditorConsultaService contenidoEditorService;
    private final DocumentoService documentoService;

    public IaBusquedaService(
            EmbeddingDocumentoRepository embeddingRepository,
            DocumentoRepository documentoRepository,
            EmpresaRepository empresaRepository,
            ConsultaIaRepository consultaIaRepository,
            IaEmbeddingService embeddingService,
            IaChatService chatService,
            UsuarioRepository usuarioRepository,
            PuntoClaveRepository puntoClaveRepository,
            CatalogoControlesCriticosService catalogoCcService,
            CriteriosCalidadExtraccionService criteriosCalidadService,
            TranscripcionLibroConsultaService transcripcionLibroService,
            ConsultaLibroPrecisaService consultaLibroPrecisaService,
            FiscalizaAgentService fiscalizaAgentService,
            @Lazy FiscalizaAgentOrchestratorService agentOrchestrator,
            ContenidoEditorConsultaService contenidoEditorService,
            @Lazy DocumentoService documentoService) {
        this.embeddingRepository = embeddingRepository;
        this.documentoRepository = documentoRepository;
        this.empresaRepository = empresaRepository;
        this.consultaIaRepository = consultaIaRepository;
        this.embeddingService = embeddingService;
        this.chatService = chatService;
        this.usuarioRepository = usuarioRepository;
        this.puntoClaveRepository = puntoClaveRepository;
        this.catalogoCcService = catalogoCcService;
        this.criteriosCalidadService = criteriosCalidadService;
        this.transcripcionLibroService = transcripcionLibroService;
        this.consultaLibroPrecisaService = consultaLibroPrecisaService;
        this.fiscalizaAgentService = fiscalizaAgentService;
        this.agentOrchestrator = agentOrchestrator;
        this.contenidoEditorService = contenidoEditorService;
        this.documentoService = documentoService;
    }

    @Transactional
    public int indexarDocumento(Long documentoId) {
        Documento documento = documentoRepository.findById(documentoId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentoId));
        String texto = textoParaIndexar(documento);
        if (texto == null || texto.isBlank()) { log.warn("Documento {} sin texto", documentoId); return 0; }
        embeddingRepository.deleteByDocumentoId(documentoId);
        List<IaEmbeddingService.ChunkResult> chunks = embeddingService.dividirEnChunks(texto, documento.getTitulo());
        List<IaEmbeddingService.EmbeddingResult> embeddings = embeddingService.generarEmbeddingsParaChunks(chunks, texto);
        List<EmbeddingDocumento> entities = new ArrayList<>();
        for (IaEmbeddingService.EmbeddingResult er : embeddings) {
            entities.add(EmbeddingDocumento.builder()
                .chunkText(er.texto()).chunkOrder(er.orden())
                .embedding(embeddingService.embeddingToString(er.embedding()))
                .tokenCount(er.tokenCount()).documento(documento)
                .empresa(documento.getEmpresa()).build());
        }
        embeddingRepository.saveAll(entities);
        log.info("Documento '{}' indexado: {} chunks", documento.getTitulo(), entities.size());
        return entities.size();
    }

    @Transactional
    public int indexarEmpresa(Long empresaId) {
        List<Documento> documentos = documentoRepository.findByEmpresaIdAndActivoTrue(empresaId);
        int total = 0;
        for (Documento doc : documentos) total += indexarDocumento(doc.getId());
        log.info("Empresa {} indexada: {} chunks", empresaId, total);
        return total;
    }

    public List<BusquedaIaResponse.ResultadoBusqueda> buscar(String consulta, Long empresaId, int limite) {
        List<Double> embeddingConsulta = embeddingService.generarEmbedding(consulta);
        List<EmbeddingDocumento> todos = embeddingRepository.findByEmpresaId(empresaId);
        List<BusquedaIaResponse.ResultadoBusqueda> resultados = new ArrayList<>();
        for (EmbeddingDocumento ed : todos) {
            if (ed.getEmbedding() == null || ed.getEmbedding().isBlank()) continue;
            try {
                List<Double> embDoc = embeddingService.stringToEmbedding(ed.getEmbedding());
                double similitud = embeddingService.calcularSimilitud(embeddingConsulta, embDoc);
                resultados.add(BusquedaIaResponse.ResultadoBusqueda.builder()
                    .chunkId(ed.getId()).chunkText(ed.getChunkText())
                    .chunkOrder(ed.getChunkOrder()).documentoId(ed.getDocumento().getId())
                    .documentoTitulo(ed.getDocumento().getTitulo()).similitud(similitud).build());
            } catch (Exception e) { log.warn("Error chunk {}: {}", ed.getId(), e.getMessage()); }
        }
        resultados.sort((a, b) -> Double.compare(b.getSimilitud(), a.getSimilitud()));
        return resultados.stream().limit(limite)
            .peek(r -> r.setSimilitud(Math.round(r.getSimilitud() * 10000.0) / 10000.0))
            .collect(Collectors.toList());
    }

    public Map<String, Object> estadoEmpresa(Long empresaId) {
        List<Documento> activos = ordenarPorTitulo(
                documentoRepository.findByEmpresaIdAndActivoTrue(empresaId));
        long embeddings = embeddingRepository.countByEmpresaId(empresaId);
        boolean deepseek = chatService.deepseekDisponible();
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("motor", deepseek ? "deepseek-chat" : "sin-configurar");
        estado.put("deepseekActivo", deepseek);
        estado.put("documentosActivos", activos.size());
        estado.put("embeddingsIndexados", embeddings);
        estado.put("listo", deepseek && !activos.isEmpty());
        estado.put("agente", "FISCALIZA-AI");
        estado.put("documentos", activos.stream()
                .map(d -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", d.getId());
                    item.put("titulo", d.getTitulo() != null ? d.getTitulo() : "");
                    String desc = truncarUnaLinea(d.getDescripcion(), 120);
                    item.put("descripcion", desc != null ? desc : "");
                    return item;
                })
                .collect(Collectors.toList()));
        if (!deepseek) {
            estado.put("mensaje", "Configure DEEPSEEK_API_KEY en el servidor para activar respuestas con IA.");
        } else if (activos.isEmpty()) {
            estado.put("mensaje", "Suba documentos PDF en la sección Documentos para poder consultarlos.");
        } else if (embeddings == 0) {
            estado.put("mensaje", "Al hacer la primera pregunta se indexarán sus PDF automáticamente.");
        } else {
            estado.put("mensaje", "Agente listo: responde solo con los documentos cargados en su empresa.");
        }
        return estado;
    }

    public BusquedaIaResponse consultarConRag(String pregunta, Long empresaId, Long usuarioId,
                                             Long documentoIdFiltro) {
        if (empresaId == null || empresaId <= 0) {
            throw new BadRequestException("Debe indicar una empresa válida.");
        }

        if (EstandarConsultaHelper.esConsultaApartadoTematico(pregunta)
                || EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta).isPresent()) {
            return consultarLibroCerradoEnEditor(pregunta, empresaId, usuarioId, documentoIdFiltro);
        }

        List<Documento> todosActivos = ordenarPorTitulo(
                documentoRepository.findByEmpresaIdAndActivoTrue(empresaId));

        if (todosActivos.isEmpty()) {
            return guardarYdevolverRespuesta(
                    pregunta, empresaId, usuarioId,
                    """
                    **No hay documentos cargados** en esta empresa.

                    Suba PDF en la sección **Documentos** y vuelva a preguntar. FISCALIZA-AI solo responde con archivos que usted haya cargado en la plataforma.
                    """,
                    "[]", Collections.emptyList(), Collections.emptyList(),
                    metaConsulta(todosActivos, "Sin documentos activos en la empresa."));
        }

        FiscalizaAgentService.IntencionAgente intencion = fiscalizaAgentService.detectarIntencion(pregunta);
        if (intencion == FiscalizaAgentService.IntencionAgente.SALUDO) {
            Map<String, List<EstandarConsultaHelper.IndiceEstandar>> porLibro =
                    indiceEstandaresPorDocumento(todosActivos);
            String respuesta = fiscalizaAgentService.responderSaludoComparativo(
                    todosActivos, porLibro, chatService.deepseekDisponible());
            Map<String, Object> meta = metaConsulta(todosActivos, null);
            meta.put("mostrarReferencias", false);
            return guardarYdevolverRespuesta(
                    pregunta, empresaId, usuarioId, respuesta,
                    "[]", Collections.emptyList(), Collections.emptyList(), meta);
        }
        if (intencion == FiscalizaAgentService.IntencionAgente.INVENTARIO_DOCUMENTOS) {
            String respuesta = fiscalizaAgentService.responderInventarioDocumentos(todosActivos);
            List<BusquedaIaResponse.CatalogoEmpresaDoc> catalogoUi = fiscalizaAgentService.catalogoUi(todosActivos);
            Map<String, Object> meta = metaConsulta(todosActivos, null);
            meta.put("pasosAgente", agentOrchestrator.pasosInventario(todosActivos.size()));
            return guardarYdevolverRespuesta(
                    pregunta, empresaId, usuarioId, respuesta,
                    construirReferenciasCatalogo(todosActivos),
                    Collections.emptyList(), catalogoUi, meta);
        }
        if (intencion == FiscalizaAgentService.IntencionAgente.CAPACIDADES_AGENTE) {
            String respuesta = fiscalizaAgentService.responderCapacidadesAgente(
                    todosActivos, chatService.deepseekDisponible());
            Map<String, Object> meta = metaConsulta(todosActivos, null);
            meta.put("pasosAgente", agentOrchestrator.pasosCapacidades(
                    todosActivos.size(), chatService.deepseekDisponible()));
            return guardarYdevolverRespuesta(
                    pregunta, empresaId, usuarioId, respuesta,
                    construirReferenciasCatalogo(todosActivos),
                    Collections.emptyList(), fiscalizaAgentService.catalogoUi(todosActivos), meta);
        }

        Optional<RespuestaDirectaDocumento> respuestaDirecta =
                intentarRespuestaDirectaDesdeDocumentos(todosActivos, pregunta);
        if (respuestaDirecta.isPresent()) {
            Map<String, Object> meta = metaConsulta(todosActivos, null);
            meta.put("modo", respuestaDirecta.get().modo());
            meta.put("mostrarReferencias", false);
            return guardarYdevolverRespuesta(
                    pregunta, empresaId, usuarioId, respuestaDirecta.get().texto(), "[]",
                    Collections.emptyList(), Collections.emptyList(), meta);
        }

        if (documentoIdFiltro != null) {
            return consultarSobreDocumentoConcreto(pregunta, empresaId, usuarioId, documentoIdFiltro);
        }

        asegurarIndiceSemanticoEnSegundoPlano(empresaId, documentoIdFiltro, todosActivos);

        List<BusquedaIaResponse.ResultadoBusqueda> pool = buscar(pregunta, empresaId, RAG_POOL_TOP_N);
        if (pool.isEmpty()) {
            pool = buscarFallback(pregunta, empresaId, RAG_FALLBACK_SQL_LIMIT);
        }
        if (pool.isEmpty()) {
            pool = recuperacionAmpliaSinonimosYTextoLibre(pregunta, empresaId);
        }
        List<BusquedaIaResponse.ResultadoBusqueda> chunksRelevantes =
                diversificarFragmentosPorDocumento(pool, RAG_MAX_FRAGMENTOS_TOTAL, RAG_MAX_FRAGMENTOS_POR_DOCUMENTO);
        if (!pool.isEmpty() && chunksRelevantes.isEmpty()) {
            chunksRelevantes.addAll(pool.stream()
                    .limit(Math.min(pool.size(), RAG_MAX_FRAGMENTOS_TOTAL))
                    .collect(Collectors.toList()));
        }

        log.info("Consulta RAG empresa {} — fragmentos recuperados tras equilibrio entre documentos: {} (pool {})",
                empresaId, chunksRelevantes.size(), pool.size());

        if (catalogoCcService.esPreguntaSobreInventarioControlesCriticos(pregunta)) {
            List<CatalogoControlesCriticosService.CatalogoCc> catalogos =
                    construirCatalogosDesdePool(pregunta, pool, todosActivos);
            if (!catalogos.isEmpty() && catalogos.stream().anyMatch(c -> c.total() > 0)) {
                String respuesta = catalogoCcService.formatearRespuestaInventarioMultiDocumento(catalogos, pregunta);
                String documentosRef = construirReferenciasCatalogoCc(catalogos);
                return guardarYdevolverRespuesta(
                        pregunta, empresaId, usuarioId, respuesta, documentosRef,
                        chunksRelevantes, Collections.emptyList());
            }
        }

        Optional<String> consultaPrecisaEmpresa = consultaLibroPrecisaService.responder(
                todosActivos.isEmpty() ? "" : todosActivos.get(0).getTitulo(),
                todosActivos.stream().findFirst().map(this::textoParaIndexar).orElse(""),
                pregunta);
        if (consultaPrecisaEmpresa.isEmpty() && !todosActivos.isEmpty()) {
            for (Documento doc : todosActivos) {
                if (!Boolean.TRUE.equals(doc.getActivo())) {
                    continue;
                }
                String t = textoPlanoParaConsulta(doc, pregunta);
                if (t == null || t.replaceAll("\\s+", "").length() < MIN_TEXTO_DOC_IA) {
                    continue;
                }
                consultaPrecisaEmpresa = consultaLibroPrecisaService.responderOMensaje(
                        doc.getTitulo(), t, pregunta);
                if (consultaPrecisaEmpresa.isPresent()) {
                    break;
                }
            }
        }
        if (consultaPrecisaEmpresa.isPresent()) {
            return guardarYdevolverRespuesta(
                    pregunta, empresaId, usuarioId, consultaPrecisaEmpresa.get(), "[]",
                    chunksRelevantes, Collections.emptyList());
        }

        if (criteriosCalidadService.esPreguntaSobreCriteriosCalidad(pregunta)) {
            String respuestaCriterios = construirRespuestaCriteriosMultiDocumento(todosActivos, pregunta);
            if (respuestaCriterios != null) {
                return guardarYdevolverRespuesta(
                        pregunta, empresaId, usuarioId, respuestaCriterios, "[]",
                        chunksRelevantes, Collections.emptyList());
            }
        }

        if (!chatService.esConsultaResumenInformal(pregunta)) {
            Optional<String> transcripcionEmpresa = construirTranscripcionMultiDocumento(todosActivos, pregunta);
            if (transcripcionEmpresa.isPresent()) {
                return guardarYdevolverRespuesta(
                        pregunta, empresaId, usuarioId, transcripcionEmpresa.get(), "[]",
                        chunksRelevantes, Collections.emptyList());
            }
        }

        FiscalizaAgentOrchestratorService.ResultadoFlujoAgente flujo = agentOrchestrator.ejecutarConsultaContenido(
                pregunta, empresaId, todosActivos, chunksRelevantes);
        boolean respuestaConcisa = chatService.esConsultaResumenInformal(pregunta)
                && !chatService.preguntaPideListaCompleta(pregunta);
        Map<String, Object> meta = metaConsulta(todosActivos, flujo.advertencia());
        meta.put("pasosAgente", flujo.pasos());
        meta.put("mostrarReferencias", !respuestaConcisa);
        List<BusquedaIaResponse.ResultadoBusqueda> resultadosUi = respuestaConcisa
                ? Collections.emptyList()
                : flujo.fragmentos();
        return guardarYdevolverRespuesta(
                pregunta, empresaId, usuarioId, flujo.respuesta(), flujo.documentosRef(),
                resultadosUi, Collections.emptyList(), meta);
    }

    /** Recupera fragmentos RAG con fallbacks (usado por el orquestador si hiciera falta re-buscar). */
    public List<BusquedaIaResponse.ResultadoBusqueda> recuperarFragmentosParaConsulta(
            String pregunta, Long empresaId) {
        List<BusquedaIaResponse.ResultadoBusqueda> pool = buscar(pregunta, empresaId, RAG_POOL_TOP_N);
        if (pool.isEmpty()) {
            pool = buscarFallback(pregunta, empresaId, RAG_FALLBACK_SQL_LIMIT);
        }
        if (pool.isEmpty()) {
            pool = recuperacionAmpliaSinonimosYTextoLibre(pregunta, empresaId);
        }
        List<BusquedaIaResponse.ResultadoBusqueda> chunks =
                diversificarFragmentosPorDocumento(pool, RAG_MAX_FRAGMENTOS_TOTAL, RAG_MAX_FRAGMENTOS_POR_DOCUMENTO);
        if (!pool.isEmpty() && chunks.isEmpty()) {
            chunks.addAll(pool.stream()
                    .limit(Math.min(pool.size(), RAG_MAX_FRAGMENTOS_TOTAL))
                    .collect(Collectors.toList()));
        }
        return chunks;
    }

    public String construirContextoFragmentos(List<BusquedaIaResponse.ResultadoBusqueda> chunks) {
        return construirContexto(chunks);
    }

    public String construirSeccionesTematicas(
            String pregunta, List<BusquedaIaResponse.ResultadoBusqueda> pool) {
        return construirBloqueSeccionesTematicasCompletas(pregunta, pool);
    }

    public String construirReferenciasJson(List<BusquedaIaResponse.ResultadoBusqueda> chunks) {
        return construirReferencias(chunks);
    }

    public String obtenerTextoDocumento(Documento doc) {
        return textoPlanoParaConsulta(doc, null);
    }

    /** Texto para consultas IA: prioriza HTML del Editor de Contenido + puntos clave estructurados. */
    public String textoPlanoParaConsulta(Documento doc, String pregunta) {
        if (pregunta != null && esRespuestaConcisa(pregunta)
                && EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta).isPresent()) {
            return textoFocalParaConsulta(doc, pregunta);
        }
        return construirTextoPlanoDocumento(doc, pregunta, true);
    }

    /** Solo sección del estándar (editor o PDF), sin puntos clave ni texto completo del libro. */
    public String textoFocalParaConsulta(Documento doc, String pregunta) {
        Optional<String> desdeEditor = extraerSeccionEstandarDesdeEditor(doc, pregunta);
        if (desdeEditor.isPresent() && desdeEditor.get().length() >= 80) {
            return "Titulo: " + doc.getTitulo() + "\n\n" + desdeEditor.get();
        }
        String texto = textoParaExtraccionSeccionEstandar(doc, pregunta);
        Optional<String> est = EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta);
        if (est.isEmpty() || texto == null || texto.isBlank()) {
            String bloque = construirBloqueEditorPrioritario(doc, pregunta);
            if (!bloque.isBlank()) {
                return "Titulo: " + doc.getTitulo() + "\n\n" + bloque;
            }
            return "Titulo: " + doc.getTitulo();
        }
        int inicio = EstandarConsultaHelper.localizarSeccionEstandar(texto, est.get());
        if (inicio < 0) {
            String bloque = construirBloqueEditorPrioritario(doc, pregunta);
            return bloque.isBlank()
                    ? "Titulo: " + doc.getTitulo()
                    : "Titulo: " + doc.getTitulo() + "\n\n" + bloque;
        }
        int fin = EstandarConsultaHelper.encontrarFinSeccionEstandar(texto, inicio, est.get());
        String seccion = texto.substring(inicio, fin).trim();
        if (seccion.length() > 8000) {
            seccion = seccion.substring(0, 8000) + "\n…";
        }
        return "Titulo: " + doc.getTitulo() + "\n\n### " + est.get().toUpperCase(Locale.ROOT) + "\n\n" + seccion;
    }

    /** Texto para localizar secciones: solo Editor de Contenido guardado (sin PDF crudo). */
    public String textoParaExtraccionSeccionEstandar(Documento doc, String pregunta) {
        if (!tieneEditorGuardado(doc)) {
            return "";
        }
        String bloque = construirBloqueEditorPrioritario(doc, pregunta);
        if (!bloque.isBlank()) {
            return bloque;
        }
        return limpiarHtmlDocumento(textoCrudoDocumento(doc));
    }

    private record RespuestaDirectaDocumento(String texto, String modo) {}

    /**
     * Ruta ultrarrápida: un solo {@code texto_extraido} desde BD, sin cargar todos los libros ni RAG.
     */
    private BusquedaIaResponse consultarLibroCerradoEnEditor(
            String pregunta, Long empresaId, Long usuarioId, Long documentoIdFiltro) {

        long inicio = System.currentTimeMillis();
        List<com.fiscalizacionhse.repository.DocumentoIdTituloView> cabeceras =
                documentoRepository.findActivosIdTituloByEmpresaId(empresaId);

        if (cabeceras.isEmpty()) {
            return guardarYdevolverRespuesta(
                    pregunta, empresaId, usuarioId,
                    "**No hay documentos activos** en esta empresa.",
                    "[]", Collections.emptyList(), Collections.emptyList(),
                    metaConsulta(List.of(), "Sin documentos activos."));
        }

        Long docId = resolverDocumentoIdApartado(cabeceras, documentoIdFiltro, pregunta);
        String tituloDoc = documentoRepository.findTituloById(docId).orElse("Documento");
        Optional<String> htmlOpt = documentoRepository.findTextoExtraidoByIdAndEmpresaId(docId, empresaId);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("motor", "editor-bd");
        meta.put("deepseekActivo", chatService.deepseekDisponible());
        meta.put("documentosEmpresa", cabeceras.size());
        meta.put("mostrarReferencias", false);

        if (htmlOpt.isEmpty() || htmlOpt.get().isBlank()) {
            meta.put("modo", "apartado_sin_editor");
            return guardarYdevolverRespuesta(
                    pregunta, empresaId, usuarioId,
                    mensajeApartadoSinTextoGuardado(tituloDoc), "[]",
                    Collections.emptyList(), Collections.emptyList(), meta);
        }

        String raw = htmlOpt.get();
        long t0 = System.currentTimeMillis();
        String contenido = documentoService.autoEstructurarDesdeTextoExtraido(raw);
        log.info("Consulta libro doc {} — entrada {} chars → estructurado {} chars ({} ms)",
                docId, raw.length(), contenido.length(), System.currentTimeMillis() - t0);

        meta.put("motor", "libro-editor-cerrado");
        meta.put("deepseekActivo", false);

        Optional<String> respuestaLibro = contenidoEditorService.responderLibroCerrado(
                contenido, pregunta, tituloDoc);
        if (respuestaLibro.isEmpty()) {
            respuestaLibro = contenidoEditorService.responderDesdeTextoPlano(raw, pregunta, tituloDoc);
            if (respuestaLibro.isPresent()) {
                meta.put("modo", "estandar_pdf_plano");
            }
        }

        if (respuestaLibro.isPresent()) {
            boolean esEstandar = EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta).isPresent();
            meta.put("modo", EstandarConsultaHelper.pideResumenExplicito(pregunta)
                    ? (esEstandar ? "estandar_resumen_local" : "apartado_resumen_local")
                    : (esEstandar ? "estandar_literal" : "apartado_literal"));
            log.info("Libro cerrado doc {} («{}») modo={} — {} ms",
                    docId, tituloDoc, meta.get("modo"), System.currentTimeMillis() - inicio);
            return guardarYdevolverRespuesta(
                    pregunta, empresaId, usuarioId, respuestaLibro.get(), "[]",
                    Collections.emptyList(), Collections.emptyList(), meta);
        }

        Optional<String> est = EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta);
        String ventana = est.isPresent()
                ? ContenidoEditorConsultaService.acotarVentanaEstandar(contenido, est.get())
                : ContenidoEditorConsultaService.acotarVentanaCompromiso(contenido);
        int secciones = contenidoEditorService.extraerSeccionesDesdeHtml(ventana).size();
        meta.put("modo", "libro_sin_seccion");
        log.info("Sección no hallada doc {} ({} secciones) — {} ms",
                docId, secciones, System.currentTimeMillis() - inicio);
        return guardarYdevolverRespuesta(
                pregunta, empresaId, usuarioId,
                mensajeSeccionNoEncontrada(tituloDoc, pregunta, est.orElse(null), secciones), "[]",
                Collections.emptyList(), Collections.emptyList(), meta);
    }

    private static String mensajeSeccionNoEncontrada(
            String tituloDoc, String pregunta, String estandar, int seccionesDetectadas) {
        if (estandar != null && !estandar.isBlank()) {
            return """
                    En **«%s»** no encontré el texto del **ESTÁNDAR DE %s** en el PDF guardado.

                    **No hace falta** marcar títulos H2 a mano: el sistema lee títulos del PDF automáticamente.
                    Si el libro es antiguo, abra el documento una vez (vista texto) o **vuelva a subir el PDF** para regenerar la estructura.

                    **Su pregunta:** «%s»
                    """.formatted(tituloDoc, estandar.toUpperCase(Locale.ROOT), pregunta).trim();
        }
        return mensajeApartadoSeccionNoEncontrada(tituloDoc, pregunta, seccionesDetectadas);
    }

    private Long resolverDocumentoIdApartado(
            List<com.fiscalizacionhse.repository.DocumentoIdTituloView> cabeceras,
            Long documentoIdFiltro,
            String pregunta) {

        if (documentoIdFiltro != null && documentoIdFiltro > 0) {
            boolean pertenece = cabeceras.stream()
                    .anyMatch(c -> documentoIdFiltro.equals(c.getId()));
            if (pertenece) {
                return documentoIdFiltro;
            }
        }
        String pn = EstandarConsultaHelper.normalizar(pregunta);
        com.fiscalizacionhse.repository.DocumentoIdTituloView mejor = null;
        int mejorPuntaje = Integer.MIN_VALUE;
        for (com.fiscalizacionhse.repository.DocumentoIdTituloView c : cabeceras) {
            String t = c.getTitulo() != null
                    ? EstandarConsultaHelper.normalizar(c.getTitulo()) : "";
            int puntaje = 0;
            if (t.contains("enap")) {
                puntaje += 25;
            }
            if (t.contains("esv") || t.contains("libro")) {
                puntaje += 12;
            }
            if (pn.contains("enap") && t.contains("enap")) {
                puntaje += 10;
            }
            if (pn.contains("compromiso") && t.contains("enap")) {
                puntaje += 8;
            }
            if (puntaje > mejorPuntaje) {
                mejorPuntaje = puntaje;
                mejor = c;
            }
        }
        return mejor != null ? mejor.getId() : cabeceras.get(0).getId();
    }

    private static String mensajeApartadoSinTextoGuardado(String tituloDoc) {
        return """
                El documento **«%s»** no tiene texto guardado en el Editor.

                Abra **Libro estructurado**, pegue o estructure el contenido, pulse **Guardar** y vuelva a preguntar.
                """.formatted(tituloDoc).trim();
    }

    private static String mensajeApartadoSinHtmlEditor(String tituloDoc) {
        return """
                **«%s»** aún no tiene el **Libro estructurado** guardado (solo PDF).

                En **Documentos** → **Libro estructurado** → título del apartado en **H1** (p. ej. «COMPROMISO DE LA ALTA DIRECCIÓN DE ENAP») → **Guardar**.
                """.formatted(tituloDoc).trim();
    }

    private static String mensajeApartadoSeccionNoEncontrada(
            String tituloDoc, String pregunta, int seccionesDetectadas) {
        return """
                En **«%s»** leí el editor (%d secciones) pero **no encontré** el apartado del Compromiso.

                Verifique un **H1** con el título del Compromiso (no use solo «ii.» del PDF) y pulse **Guardar**.

                **Su pregunta:** «%s»
                """.formatted(tituloDoc, seccionesDetectadas, pregunta).trim();
    }

    /**
     * Respuesta inmediata desde editor/BD (sin RAG ni orquestador).
     */
    public Optional<RespuestaDirectaDocumento> intentarRespuestaDirectaDesdeDocumentos(
            List<Documento> docs, String pregunta) {
        if (docs == null || docs.isEmpty() || pregunta == null || pregunta.isBlank()) {
            return Optional.empty();
        }
        if (EstandarConsultaHelper.esConsultaApartadoTematico(pregunta)) {
            List<Documento> ordenados = ordenarDocumentosParaApartado(docs, pregunta);
            if (EstandarConsultaHelper.pideResumenExplicito(pregunta)) {
                Optional<String> resumen = intentarRespuestaResumenApartado(ordenados, pregunta);
                if (resumen.isPresent()) {
                    return Optional.of(new RespuestaDirectaDocumento(resumen.get(), "apartado_resumen"));
                }
                return Optional.of(new RespuestaDirectaDocumento(
                        mensajeApartadoNoDisponible(ordenados, pregunta), "apartado_sin_editor"));
            }
            if (EstandarConsultaHelper.debeUsarModoLiteralApartado(pregunta)) {
                Optional<String> literal = intentarRespuestaApartadoLiteral(ordenados, pregunta);
                if (literal.isPresent()) {
                    return Optional.of(new RespuestaDirectaDocumento(literal.get(), "apartado_literal"));
                }
                return Optional.of(new RespuestaDirectaDocumento(
                        mensajeApartadoNoDisponible(ordenados, pregunta), "apartado_sin_editor"));
            }
            return Optional.empty();
        }
        Optional<String> estandarOpt = EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta);
        if (estandarOpt.isEmpty()) {
            return Optional.empty();
        }
        for (Documento doc : docs) {
            if (!Boolean.TRUE.equals(doc.getActivo())) {
                continue;
            }
            String focal = textoFocalParaConsulta(doc, pregunta);
            if (focal == null || focal.replaceAll("\\s+", "").length() < 80) {
                continue;
            }
            if (EstandarConsultaHelper.pideTextoLiteral(pregunta)) {
                return Optional.of(new RespuestaDirectaDocumento(
                        formatearSeccionEstandarLiteral(doc.getTitulo(), focal, estandarOpt.get()),
                        "estandar_literal"));
            }
            if (EstandarConsultaHelper.pideResumenExplicito(pregunta)
                    || EstandarConsultaHelper.esConsultaResumenInformal(pregunta)) {
                String resumen = chatService.generarRespuestaConcisaEstandarDocumento(
                        pregunta, doc.getTitulo(), focal);
                return Optional.of(new RespuestaDirectaDocumento(resumen, "estandar_conciso"));
            }
        }
        return Optional.empty();
    }

    private static String formatearSeccionEstandarLiteral(
            String tituloDocumento, String textoFocal, String nombreEstandar) {
        String cuerpo = textoFocal != null ? textoFocal : "";
        if (cuerpo.startsWith("Titulo:")) {
            int sep = cuerpo.indexOf("\n\n");
            if (sep > 0) {
                cuerpo = cuerpo.substring(sep + 2).trim();
            }
        }
        String titulo = nombreEstandar.toUpperCase(Locale.ROOT);
        if (!titulo.startsWith("ESTÁNDAR") && !titulo.startsWith("ESTANDAR")) {
            titulo = "ESTÁNDAR DE " + titulo;
        }
        return "## " + titulo + "\n\n"
                + "*Texto del apartado en «" + tituloDocumento + "»*\n\n"
                + cuerpo;
    }

    /**
     * Resumen breve de un apartado temático (p. ej. Compromiso), sin transcripción completa.
     */
    public Optional<String> intentarRespuestaResumenApartado(List<Documento> docs, String pregunta) {
        if (!EstandarConsultaHelper.esConsultaApartadoTematico(pregunta)
                || !EstandarConsultaHelper.pideResumenExplicito(pregunta)
                || docs == null) {
            return Optional.empty();
        }
        for (Documento doc : docs) {
            if (!Boolean.TRUE.equals(doc.getActivo())) {
                continue;
            }
            Optional<String> cuerpoOpt = extraerCuerpoPlanoApartadoTematico(doc, pregunta);
            if (cuerpoOpt.isEmpty() || cuerpoOpt.get().replaceAll("\\s+", "").length() < 80) {
                continue;
            }
            String tituloApartado = EstandarConsultaHelper.esConsultaApartadoCompromiso(pregunta)
                    ? "Compromiso de la Alta Dirección de ENAP"
                    : "Apartado solicitado";
            String resumen = chatService.generarResumenBreveApartado(
                    pregunta, doc.getTitulo(), tituloApartado, cuerpoOpt.get());
            return Optional.of(resumen);
        }
        return Optional.empty();
    }

    private Optional<String> extraerCuerpoPlanoApartadoTematico(Documento doc, String pregunta) {
        if (!tieneEditorGuardado(doc)) {
            return Optional.empty();
        }
        String raw = textoCrudoDocumento(doc);
        List<ContenidoEditorConsultaService.SeccionEditor> secciones =
                contenidoEditorService.extraerSeccionesDesdeHtml(raw);
        return contenidoEditorService.buscarSeccionPorApartadoTematico(secciones, pregunta)
                .map(ContenidoEditorConsultaService.SeccionEditor::cuerpo)
                .filter(c -> c != null && !c.isBlank());
    }

    /**
     * Apartados temáticos: solo HTML del Editor de Contenido guardado en BD (sin PDF ni RAG).
     */
    public Optional<String> intentarRespuestaApartadoLiteral(List<Documento> docs, String pregunta) {
        if (!EstandarConsultaHelper.debeUsarModoLiteralApartado(pregunta) || docs == null) {
            return Optional.empty();
        }
        for (Documento doc : docs) {
            if (!Boolean.TRUE.equals(doc.getActivo())) {
                continue;
            }
            if (!tieneEditorGuardado(doc)) {
                continue;
            }
            Optional<String> desdeEditor = extraerApartadoTematicoDesdeEditor(doc, pregunta);
            if (desdeEditor.isPresent()
                    && !desdeEditor.get().toUpperCase(Locale.ROOT).contains("INTRODUCCION Y CONTEXTO")) {
                return desdeEditor;
            }
        }
        return Optional.empty();
    }

    private List<Documento> ordenarDocumentosParaApartado(List<Documento> docs, String pregunta) {
        if (docs == null || docs.size() <= 1) {
            return docs != null ? docs : List.of();
        }
        String n = EstandarConsultaHelper.normalizar(pregunta);
        return docs.stream()
                .filter(d -> Boolean.TRUE.equals(d.getActivo()))
                .sorted(Comparator
                        .comparingInt((Documento d) -> scoreDocumentoParaApartado(d, n)).reversed()
                        .thenComparing(d -> d.getTitulo() != null ? d.getTitulo() : "",
                                String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private int scoreDocumentoParaApartado(Documento doc, String preguntaNorm) {
        int score = tieneEditorGuardado(doc) ? 20 : 0;
        String titulo = doc.getTitulo() != null
                ? EstandarConsultaHelper.normalizar(doc.getTitulo()) : "";
        if (titulo.contains("enap")) {
            score += 15;
        }
        if (titulo.contains("esv") || titulo.contains("libro")) {
            score += 10;
        }
        if (preguntaNorm.contains("enap") && titulo.contains("enap")) {
            score += 8;
        }
        return score;
    }

    private String mensajeApartadoNoDisponible(List<Documento> docs, String pregunta) {
        boolean algunoConEditor = docs != null && docs.stream().anyMatch(this::tieneEditorGuardado);
        if (!algunoConEditor) {
            return """
                    No encuentro el **Libro estructurado** guardado en la base de datos.

                    1. Abra el documento en **Documentos** → pestaña **Libro estructurado**.
                    2. Use **H1** como título del apartado (Compromiso) con el texto debajo — no confunda con «ii. INTRODUCCIÓN» del PDF.
                    3. Pulse **Guardar**.
                    4. En este chat, **seleccione ese libro** en el panel lateral (ámbito del documento).

                    FISCALIZA-AI responde este apartado **solo** con el Editor de Contenido guardado, no con el PDF escaneado.
                    """.trim();
        }
        return """
                Tengo libros con editor guardado, pero **no localicé el apartado** pedido en ninguno.

                Revise que exista un **H1** con «COMPROMISO» en el título y pulse **Guardar**.
                En el chat, **seleccione el libro ENAP/ESV** en el selector de documento del panel lateral.

                **Su pregunta:** «%s»
                """.formatted(pregunta).trim();
    }

    public Optional<String> extraerApartadoTematicoDesdeEditor(Documento doc, String pregunta) {
        if (!EstandarConsultaHelper.esConsultaApartadoTematico(pregunta)) {
            return Optional.empty();
        }
        String raw = textoCrudoDocumento(doc);
        if (!contenidoEditorService.esHtmlEditor(raw)) {
            return Optional.empty();
        }
        List<ContenidoEditorConsultaService.SeccionEditor> secciones =
                contenidoEditorService.extraerSeccionesDesdeHtml(raw);
        return contenidoEditorService.buscarSeccionPorApartadoTematico(secciones, pregunta)
                .map(s -> contenidoEditorService.formatearApartadoLiteral(doc.getTitulo(), s))
                .filter(r -> !r.isBlank());
    }

    /** Sección de un estándar tomada del HTML del editor (más fiable que regex sobre PDF crudo). */
    public Optional<String> extraerSeccionEstandarDesdeEditor(Documento doc, String pregunta) {
        Optional<String> estandarOpt = EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta);
        if (estandarOpt.isEmpty()) {
            return Optional.empty();
        }
        String raw = textoCrudoDocumento(doc);
        if (!contenidoEditorService.esHtmlEditor(raw)) {
            return Optional.empty();
        }
        List<ContenidoEditorConsultaService.SeccionEditor> secciones =
                contenidoEditorService.extraerSeccionesDesdeHtml(raw);
        return contenidoEditorService.buscarSeccionPorEstandar(secciones, estandarOpt.get())
                .map(s -> "### " + s.titulo() + "\n\n"
                        + contenidoEditorService.truncarCuerpoParaConsulta(s.cuerpo()));
    }

    /** Bloque listo para el prompt del agente (secciones del editor según la pregunta). */
    public String construirBloqueEditorPrioritario(Documento doc, String pregunta) {
        String raw = textoCrudoDocumento(doc);
        if (!contenidoEditorService.esHtmlEditor(raw)) {
            return "";
        }
        List<ContenidoEditorConsultaService.SeccionEditor> secciones =
                contenidoEditorService.extraerSeccionesDesdeHtml(raw);
        if (secciones.isEmpty()) {
            return "";
        }
        List<ContenidoEditorConsultaService.SeccionEditor> filtradas =
                contenidoEditorService.filtrarPorPregunta(secciones, pregunta);
        boolean huboFiltro = filtradas.size() < secciones.size();
        return contenidoEditorService.formatearBloqueEditorParaPrompt(
                doc.getTitulo(), filtradas, huboFiltro);
    }

    /**
     * No bloquea la respuesta HTTP: la indexación pesada ocurre al guardar el editor.
     * Aquí solo registra si faltan embeddings para consultas RAG genéricas.
     */
    private void asegurarIndiceSemanticoEnSegundoPlano(
            Long empresaId, Long documentoIdFiltro, List<Documento> activos) {
        try {
            if (documentoIdFiltro != null) {
                if (embeddingRepository.countByDocumentoId(documentoIdFiltro) == 0) {
                    log.info("Documento {} sin embeddings; use búsqueda por texto o guarde el editor para reindexar",
                            documentoIdFiltro);
                }
                return;
            }
            if (embeddingRepository.countByEmpresaId(empresaId) == 0 && !activos.isEmpty()) {
                log.info("Empresa {} sin embeddings indexados ({} docs). Indexación al guardar editor o manual.",
                        empresaId, activos.size());
            }
        } catch (Exception e) {
            log.warn("Comprobación índice IA empresa {}: {}", empresaId, e.getMessage());
        }
    }

    private Map<String, Object> metaConsulta(List<Documento> activos, String advertencia) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("motor", chatService.deepseekDisponible() ? "deepseek-chat" : "sin-configurar");
        meta.put("deepseekActivo", chatService.deepseekDisponible());
        meta.put("documentosEmpresa", activos != null ? activos.size() : 0);
        meta.put("advertencia", advertencia);
        return meta;
    }

    private Map<String, Object> metaConsultaConcisa(Documento documento) {
        Map<String, Object> meta = metaConsulta(List.of(documento), null);
        meta.put("mostrarReferencias", false);
        meta.put("modo", "estandar_conciso");
        return meta;
    }

    private BusquedaIaResponse consultarSobreDocumentoConcreto(
            String pregunta, Long empresaId, Long usuarioId, Long documentoId) {

        Documento documento = documentoRepository.findById(documentoId)
                .orElseThrow(() -> new ResourceNotFoundException("Documento", documentoId));

        if (!documento.getEmpresa().getId().equals(empresaId)) {
            throw new BadRequestException("El documento no pertenece a la empresa indicada.");
        }
        if (!Boolean.TRUE.equals(documento.getActivo())) {
            throw new BadRequestException("El documento no está disponible.");
        }

        Optional<RespuestaDirectaDocumento> respuestaDirecta =
                intentarRespuestaDirectaDesdeDocumentos(List.of(documento), pregunta);
        if (respuestaDirecta.isPresent()) {
            Map<String, Object> meta = metaConsultaConcisa(documento);
            meta.put("modo", respuestaDirecta.get().modo());
            return guardarYdevolverRespuesta(
                    pregunta, empresaId, usuarioId, respuestaDirecta.get().texto(), "[]",
                    Collections.emptyList(), Collections.emptyList(), meta);
        }

        String texto = esRespuestaConcisa(pregunta)
                ? textoFocalParaConsulta(documento, pregunta)
                : textoPlanoParaConsulta(documento, pregunta);
        String textoCompacto = texto != null ? texto.replaceAll("\\s+", "") : "";

        if (texto == null || textoCompacto.length() < MIN_TEXTO_DOC_IA) {
            String respuesta = """
                    No hay suficiente **texto extraíble** en este PDF para que la IA analice cláusulas como «Certifico» o «Declaro».

                    Suele ocurrir con **PDF escaneados** (solo imágenes): el motor actual lee texto con PDFBox, **sin OCR**.

                    **Qué hacer:** exporte el documento como PDF con texto seleccionable, o use un escáner con OCR y vuelva a subirlo.
                    """;

            return guardarYdevolverRespuesta(
                    pregunta, empresaId, usuarioId, respuesta, "[]", Collections.emptyList(),
                    Collections.emptyList());
        }

        if (catalogoCcService.esPreguntaSobreInventarioControlesCriticos(pregunta)) {
            CatalogoControlesCriticosService.CatalogoCc catalogo =
                    catalogoCcService.extraerCatalogo(documento.getTitulo(), texto);
            String respuesta = catalogoCcService.formatearRespuestaInventario(catalogo, pregunta);
            return guardarRespuestaDocumentoConCatalogo(
                    pregunta, empresaId, usuarioId, respuesta, documento, texto);
        }

        Optional<String> consultaPrecisa = consultaLibroPrecisaService.responderOMensaje(
                documento.getTitulo(), texto, pregunta);
        if (consultaPrecisa.isPresent()) {
            return guardarRespuestaDocumentoConCatalogo(
                    pregunta, empresaId, usuarioId, consultaPrecisa.get(), documento, texto);
        }

        if (criteriosCalidadService.esPreguntaSobreCriteriosCalidad(pregunta)) {
            Optional<String> criteriosEstandar = criteriosCalidadService.responderCriteriosDeEstandar(
                    documento.getTitulo(), texto, pregunta);
            if (criteriosEstandar.isPresent()) {
                return guardarRespuestaDocumentoConCatalogo(
                        pregunta, empresaId, usuarioId, criteriosEstandar.get(), documento, texto);
            }
            CriteriosCalidadExtraccionService.CatalogoCriterios cat =
                    criteriosCalidadService.extraerCatalogo(documento.getTitulo(), texto);
            CriteriosCalidadExtraccionService.CatalogoCriterios filtrado =
                    criteriosCalidadService.filtrarPorConsulta(cat, pregunta);
            if (filtrado.total() > 0) {
                String respuesta = criteriosCalidadService.formatearRespuesta(filtrado, pregunta);
                return guardarRespuestaDocumentoConCatalogo(
                        pregunta, empresaId, usuarioId, respuesta, documento, texto);
            }
        }

        if (!chatService.esConsultaResumenInformal(pregunta)) {
            Optional<String> transcripcion = transcripcionLibroService.responderConTranscripcion(
                    documento.getTitulo(), texto, pregunta);
            if (transcripcion.isPresent()) {
                return guardarRespuestaDocumentoConCatalogo(
                        pregunta, empresaId, usuarioId, transcripcion.get(), documento, texto);
            }
        }

        String respuesta = chatService.generarRespuestaSobreDocumento(
                pregunta, documento.getTitulo(), texto);

        boolean concisa = esRespuestaConcisa(pregunta);
        List<BusquedaIaResponse.ResultadoBusqueda> resultados = new ArrayList<>();
        if (!concisa) {
            resultados.add(BusquedaIaResponse.ResultadoBusqueda.builder()
                    .chunkId(null)
                    .chunkText(texto.length() > 1000 ? texto.substring(0, 1000) + "…" : texto)
                    .chunkOrder(0)
                    .documentoId(documento.getId())
                    .documentoTitulo(documento.getTitulo())
                    .similitud(1.0)
                    .build());
        }

        String documentosRef;
        try {
            documentosRef = objectMapper.writeValueAsString(List.of(
                    Map.of("id", documento.getId(), "titulo", documento.getTitulo(), "modo", "documento_completo")
            ));
        } catch (Exception e) {
            documentosRef = "[]";
        }

        Map<String, Object> meta = metaConsulta(List.of(documento), null);
        meta.put("mostrarReferencias", !concisa);
        return guardarYdevolverRespuesta(
                pregunta, empresaId, usuarioId, respuesta, documentosRef, resultados, Collections.emptyList(), meta);
    }

    private boolean esRespuestaConcisa(String pregunta) {
        if (EstandarConsultaHelper.pideTextoLiteral(pregunta)) {
            return false;
        }
        return chatService.esConsultaResumenInformal(pregunta)
                && !chatService.preguntaPideListaCompleta(pregunta);
    }

    /** Índice numerado de estándares por cada libro cargado (comparativo en saludo). */
    private Map<String, List<EstandarConsultaHelper.IndiceEstandar>> indiceEstandaresPorDocumento(
            List<Documento> activos) {
        Map<String, List<EstandarConsultaHelper.IndiceEstandar>> porLibro = new LinkedHashMap<>();
        for (Documento doc : activos) {
            if (!Boolean.TRUE.equals(doc.getActivo())) {
                continue;
            }
            List<EstandarConsultaHelper.IndiceEstandar> indice = extraerIndiceDeDocumento(doc);
            if (!indice.isEmpty()) {
                porLibro.put(doc.getTitulo(), indice);
            }
        }
        return porLibro;
    }

    private List<EstandarConsultaHelper.IndiceEstandar> extraerIndiceDeDocumento(Documento doc) {
        String raw = textoCrudoDocumento(doc);
        if (contenidoEditorService.esHtmlEditor(raw)) {
            List<String> titulos = contenidoEditorService.extraerSeccionesDesdeHtml(raw).stream()
                    .map(ContenidoEditorConsultaService.SeccionEditor::titulo)
                    .toList();
            List<EstandarConsultaHelper.IndiceEstandar> desdeEditor =
                    EstandarConsultaHelper.extraerIndiceDesdeTitulosEditor(titulos);
            if (!desdeEditor.isEmpty()) {
                return desdeEditor;
            }
        }
        String texto = limpiarHtmlDocumento(raw);
        if (texto != null && texto.replaceAll("\\s+", "").length() >= MIN_TEXTO_DOC_IA) {
            List<EstandarConsultaHelper.IndiceEstandar> indice =
                    EstandarConsultaHelper.extraerIndiceEstandaresEnTexto(texto);
            if (!indice.isEmpty()) {
                return indice;
            }
        }
        return List.of();
    }

    private static boolean tituloPareceEstandar(String titulo) {
        if (titulo == null) {
            return false;
        }
        String u = titulo.toUpperCase(Locale.ROOT);
        return u.contains("ESTÁNDAR DE") || u.contains("ESTANDAR DE");
    }

    private static String normalizarTituloEstandar(String titulo) {
        String t = titulo.trim().replaceAll("\\s+", " ");
        if (!tituloPareceEstandar(t)) {
            return "ESTÁNDAR DE " + t.toUpperCase(Locale.ROOT);
        }
        if (t.toUpperCase(Locale.ROOT).startsWith("ESTÁNDAR DE")
                || t.toUpperCase(Locale.ROOT).startsWith("ESTANDAR DE")) {
            return t.toUpperCase(Locale.ROOT).replace("ESTANDAR DE", "ESTÁNDAR DE");
        }
        return t;
    }

    /**
     * Fuente para consultas IA: prioriza el HTML del Editor guardado en {@code textoExtraido}.
     * No usar {@code textoTraducido} si el editor tiene contenido estructurado (evita mezclar PDF traducido).
     */
    private String textoCrudoDocumento(Documento doc) {
        String extraido = doc.getTextoExtraido() != null ? doc.getTextoExtraido() : "";
        if (contenidoEditorService.esHtmlEditor(extraido)) {
            return extraido;
        }
        if (doc.getTraducido() && doc.getTextoTraducido() != null
                && !doc.getTextoTraducido().isBlank()
                && !doc.getTextoTraducido().contains("TRADUCCION PENDIENTE")) {
            return doc.getTextoTraducido();
        }
        return extraido;
    }

    private boolean tieneEditorGuardado(Documento doc) {
        return contenidoEditorService.esHtmlEditor(textoCrudoDocumento(doc));
    }

    private BusquedaIaResponse guardarRespuestaDocumentoConCatalogo(
            String pregunta, Long empresaId, Long usuarioId, String respuesta,
            Documento documento, String texto) {

        boolean concisa = esRespuestaConcisa(pregunta);
        List<BusquedaIaResponse.ResultadoBusqueda> resultados = new ArrayList<>();
        if (!concisa) {
            resultados.add(BusquedaIaResponse.ResultadoBusqueda.builder()
                    .chunkId(null)
                    .chunkText(texto.length() > 1000 ? texto.substring(0, 1000) + "…" : texto)
                    .chunkOrder(0)
                    .documentoId(documento.getId())
                    .documentoTitulo(documento.getTitulo())
                    .similitud(1.0)
                    .build());
        }

        String documentosRef;
        try {
            documentosRef = objectMapper.writeValueAsString(List.of(
                    Map.of("id", documento.getId(), "titulo", documento.getTitulo(),
                            "modo", "catalogo_controles_criticos")
            ));
        } catch (Exception e) {
            documentosRef = "[]";
        }

        Map<String, Object> meta = metaConsulta(List.of(documento), null);
        meta.put("mostrarReferencias", !concisa);
        return guardarYdevolverRespuesta(
                pregunta, empresaId, usuarioId, respuesta, documentosRef, resultados, Collections.emptyList(), meta);
    }

    private List<CatalogoControlesCriticosService.CatalogoCc> construirCatalogosDesdePool(
            String pregunta,
            List<BusquedaIaResponse.ResultadoBusqueda> pool,
            List<Documento> todosActivos) {

        LinkedHashSet<Long> docIds = new LinkedHashSet<>();
        // Inventario CC: escanear todos los PDF activos de la empresa (no solo fragmentos RAG)
        if (catalogoCcService.esPreguntaSobreInventarioControlesCriticos(pregunta) && !todosActivos.isEmpty()) {
            todosActivos.forEach(d -> docIds.add(d.getId()));
        } else {
            for (BusquedaIaResponse.ResultadoBusqueda r : pool) {
                if (r.getDocumentoId() != null) {
                    docIds.add(r.getDocumentoId());
                }
            }
            if (docIds.isEmpty() && !todosActivos.isEmpty()) {
                todosActivos.stream().limit(5).map(Documento::getId).forEach(docIds::add);
            }
        }

        List<CatalogoControlesCriticosService.CatalogoCc> catalogos = new ArrayList<>();
        for (Long docId : docIds) {
            Documento doc = documentoRepository.findById(docId).orElse(null);
            if (doc == null || !Boolean.TRUE.equals(doc.getActivo())) {
                continue;
            }
            String texto = textoParaIndexar(doc);
            if (texto == null || texto.replaceAll("\\s+", "").length() < MIN_TEXTO_DOC_IA) {
                continue;
            }
            catalogos.add(catalogoCcService.extraerCatalogo(doc.getTitulo(), texto));
        }
        return catalogos;
    }

    private Optional<String> construirTranscripcionMultiDocumento(List<Documento> docs, String pregunta) {
        if (docs == null || docs.isEmpty()) {
            return Optional.empty();
        }
        List<Map.Entry<String, String>> entradas = new ArrayList<>();
        for (Documento doc : docs) {
            if (!Boolean.TRUE.equals(doc.getActivo())) {
                continue;
            }
            String texto = textoPlanoParaConsulta(doc, pregunta);
            if (texto == null || texto.replaceAll("\\s+", "").length() < MIN_TEXTO_DOC_IA) {
                continue;
            }
            entradas.add(Map.entry(doc.getTitulo(), texto));
        }
        return transcripcionLibroService.responderConTranscripcionMulti(pregunta, entradas);
    }

    private Optional<String> construirRespuestaEstandarCcMultiDocumento(List<Documento> docs, String pregunta) {
        if (docs == null || docs.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder sb = new StringBuilder();
        int hits = 0;
        for (Documento doc : docs) {
            if (!Boolean.TRUE.equals(doc.getActivo())) {
                continue;
            }
            String texto = textoParaIndexar(doc);
            if (texto == null || texto.replaceAll("\\s+", "").length() < MIN_TEXTO_DOC_IA) {
                continue;
            }
            Optional<String> resp = catalogoCcService.responderEstandarConTodosLosCc(
                    doc.getTitulo(), texto, pregunta);
            if (resp.isPresent()) {
                hits++;
                sb.append(resp.get()).append("\n\n");
            }
        }
        return hits > 0 ? Optional.of(sb.toString()) : Optional.empty();
    }

    private String construirRespuestaCriteriosMultiDocumento(List<Documento> docs, String pregunta) {
        if (docs == null || docs.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int totalItems = 0;
        for (Documento doc : docs) {
            if (!Boolean.TRUE.equals(doc.getActivo())) {
                continue;
            }
            String texto = textoParaIndexar(doc);
            if (texto == null || texto.replaceAll("\\s+", "").length() < MIN_TEXTO_DOC_IA) {
                continue;
            }
            Optional<String> especifico = criteriosCalidadService.responderCriteriosDeEstandar(
                    doc.getTitulo(), texto, pregunta);
            if (especifico.isPresent()) {
                sb.append(especifico.get()).append("\n\n");
                totalItems++;
                continue;
            }
            CriteriosCalidadExtraccionService.CatalogoCriterios cat =
                    criteriosCalidadService.extraerCatalogo(doc.getTitulo(), texto);
            CriteriosCalidadExtraccionService.CatalogoCriterios filtrado =
                    criteriosCalidadService.filtrarPorConsulta(cat, pregunta);
            if (filtrado.total() == 0) {
                continue;
            }
            totalItems += filtrado.total();
            sb.append(criteriosCalidadService.formatearRespuesta(filtrado, pregunta)).append("\n\n");
        }
        if (totalItems == 0) {
            return null;
        }
        return sb.toString();
    }

    private static String mensajeSinCriteriosCalidad(String pregunta) {
        String est = EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta).orElse("el estándar indicado");
        return """
                ## Criterios de Calidad

                No se encontraron bloques **«Criterios de Calidad»** para **%s** en el texto extraído del PDF.

                Prueba: *«criterios de calidad de ESTÁNDAR DE EXCAVACIONES»* o *«… TRANSPORTE DE CARGA»*.
                """.formatted(est.toUpperCase(Locale.ROOT));
    }

    private static String mensajeSinContenidoEstandar(String pregunta) {
        String est = EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta).orElse("el estándar indicado");
        return """
                ## Estándar no extraído

                No se pudo leer el contenido completo (CC1, CC2, CC3…) de **%s** en el PDF.

                Verifique que el archivo tenga **texto seleccionable** (no escaneado sin OCR).
                """.formatted(est.toUpperCase(Locale.ROOT));
    }

    private String construirReferenciasCatalogoCc(List<CatalogoControlesCriticosService.CatalogoCc> catalogos) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (CatalogoControlesCriticosService.CatalogoCc c : catalogos) {
            if (c.total() == 0) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("titulo", c.tituloDocumento());
            m.put("total_cc", c.total());
            m.put("origen", "catalogo_cc_automatico");
            list.add(m);
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private BusquedaIaResponse guardarYdevolverRespuesta(
            String pregunta,
            Long empresaId,
            Long usuarioId,
            String respuesta,
            String documentosRef,
            List<BusquedaIaResponse.ResultadoBusqueda> resultados,
            List<BusquedaIaResponse.CatalogoEmpresaDoc> catalogoEmpresa) {
        return guardarYdevolverRespuesta(
                pregunta, empresaId, usuarioId, respuesta, documentosRef, resultados, catalogoEmpresa, null);
    }

    private BusquedaIaResponse guardarYdevolverRespuesta(
            String pregunta,
            Long empresaId,
            Long usuarioId,
            String respuesta,
            String documentosRef,
            List<BusquedaIaResponse.ResultadoBusqueda> resultados,
            List<BusquedaIaResponse.CatalogoEmpresaDoc> catalogoEmpresa,
            Map<String, Object> meta) {

        ConsultaIa consulta = ConsultaIa.builder()
                .pregunta(pregunta).respuesta(respuesta)
                .documentosReferencia(documentosRef).tipo("CONSULTA")
                .empresa(empresaRepository.findById(empresaId)
                        .orElseThrow(() -> new RuntimeException("Empresa no encontrada")))
                .usuario(usuarioRepository.findById(usuarioId)
                        .orElseThrow(() -> new RuntimeException("Usuario no encontrado")))
                .build();
        consultaIaRepository.save(consulta);

        String motor = meta != null && meta.get("motor") != null
                ? meta.get("motor").toString()
                : (chatService.deepseekDisponible() ? "deepseek-chat" : "sin-configurar");
        Boolean deepseekActivo = meta != null && meta.get("deepseekActivo") != null
                ? (Boolean) meta.get("deepseekActivo")
                : chatService.deepseekDisponible();
        Integer documentosEmpresa = meta != null && meta.get("documentosEmpresa") != null
                ? ((Number) meta.get("documentosEmpresa")).intValue()
                : null;
        String advertencia = meta != null && meta.get("advertencia") != null
                ? meta.get("advertencia").toString()
                : null;
        @SuppressWarnings("unchecked")
        List<PasoAgente> pasosAgente = meta != null && meta.get("pasosAgente") instanceof List<?>
                ? (List<PasoAgente>) meta.get("pasosAgente")
                : null;
        Boolean mostrarReferencias = meta != null && meta.get("mostrarReferencias") instanceof Boolean
                ? (Boolean) meta.get("mostrarReferencias")
                : Boolean.FALSE;

        return BusquedaIaResponse.builder()
                .pregunta(pregunta).respuesta(respuesta)
                .documentosReferencia(documentosRef).resultados(resultados)
                .catalogoEmpresa(catalogoEmpresa != null ? catalogoEmpresa : Collections.emptyList())
                .motor(motor)
                .deepseekActivo(deepseekActivo)
                .documentosEmpresa(documentosEmpresa)
                .advertencia(advertencia)
                .pasosAgente(pasosAgente != null ? pasosAgente : Collections.emptyList())
                .mostrarReferencias(mostrarReferencias)
                .build();
    }

    private List<BusquedaIaResponse.ResultadoBusqueda> buscarFallback(String consulta, Long empresaId, int limiteSql) {
        try {
            var resultadosRaw = embeddingRepository.buscarPorTexto(empresaId, consulta, limiteSql);
            List<BusquedaIaResponse.ResultadoBusqueda> resultados = new ArrayList<>();
            for (Object[] row : resultadosRaw) {
                EmbeddingDocumento ed = (EmbeddingDocumento) row[0];
                Double similitud = (Double) row[1];
                resultados.add(BusquedaIaResponse.ResultadoBusqueda.builder()
                    .chunkId(ed.getId()).chunkText(ed.getChunkText())
                    .chunkOrder(ed.getChunkOrder()).documentoId(ed.getDocumento().getId())
                    .documentoTitulo(ed.getDocumento().getTitulo()).similitud(similitud).build());
            }
            return resultados;
        } catch (Exception e) { return Collections.emptyList(); }
    }

    private String construirContexto(List<BusquedaIaResponse.ResultadoBusqueda> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Fragmentos recuperados desde varios PDF de la empresa. ")
          .append("Cada uno indica título exacto del archivo, ID interno y métrica de relevancia a la pregunta. ")
          .append("Usa solo lo relacionado al tema preguntado; descarta contenido HSE genérico o irrelevante para la consulta.\n\n");

        String docTituloActual = "";
        int indiceFragmentoGlobal = 0;
        int indiceDentroDocumento = 0;

        for (BusquedaIaResponse.ResultadoBusqueda chunk : chunks) {
            boolean cambioDoc = docTituloActual.isEmpty()
                    || !safeTitulo(chunk).equals(docTituloActual);

            if (cambioDoc) {
                docTituloActual = safeTitulo(chunk);
                indiceDentroDocumento = 0;
                sb.append("\n════════════════════════════════════════\n");
                sb.append("DOCUMENTO:\n");
                sb.append("- Título: \"").append(docTituloActual).append("\"\n");
                sb.append("- ID archivo: ").append(chunk.getDocumentoId()).append("\n");
                sb.append("════════════════════════════════════════\n");
            }

            indiceFragmentoGlobal++;
            indiceDentroDocumento++;
            double sim = chunk.getSimilitud() != null ? chunk.getSimilitud() : 0.0;
            sb.append(String.format(Locale.ROOT,
                    "[F %d | fragmento #%d de este archivo | relevancia ~%.4f]\n",
                    indiceFragmentoGlobal, indiceDentroDocumento, sim));
            sb.append(safeChunkText(chunk)).append("\n\n");
        }

        return sb.toString();
    }

    private static String safeTitulo(BusquedaIaResponse.ResultadoBusqueda chunk) {
        String t = chunk.getDocumentoTitulo();
        return t != null ? t : "(sin título)";
    }

    private static String safeChunkText(BusquedaIaResponse.ResultadoBusqueda chunk) {
        String t = chunk.getChunkText();
        return t != null ? t : "";
    }

    /**
     * Toma chunks ya ordenados por similitud y reparte cupo entre PDF distintos,
     * para que una consulta transversal revise varios expedientes en lugar de un solo archivo dominante.
     */
    private List<BusquedaIaResponse.ResultadoBusqueda> diversificarFragmentosPorDocumento(
            List<BusquedaIaResponse.ResultadoBusqueda> ordenadosPorSimilitud,
            int maxFragmentosTotales,
            int maxPorDocumentoPasadaUno) {

        if (ordenadosPorSimilitud == null || ordenadosPorSimilitud.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> vistos = new LinkedHashSet<>();
        List<BusquedaIaResponse.ResultadoBusqueda> salida = new ArrayList<>();
        Map<Long, Integer> cuentaPorDoc = new HashMap<>();

        for (BusquedaIaResponse.ResultadoBusqueda r : ordenadosPorSimilitud) {
            if (salida.size() >= maxFragmentosTotales) {
                break;
            }
            String clave = claveUnicaFragmento(r);
            if (vistos.contains(clave)) {
                continue;
            }
            Long docRaw = r.getDocumentoId();
            long docId = docRaw != null ? docRaw : -1L;
            int usadosDoc = cuentaPorDoc.getOrDefault(docId, 0);
            if (usadosDoc >= maxPorDocumentoPasadaUno) {
                continue;
            }
            salida.add(r);
            vistos.add(clave);
            cuentaPorDoc.put(docId, usadosDoc + 1);
        }

        if (salida.size() < maxFragmentosTotales) {
            for (BusquedaIaResponse.ResultadoBusqueda r : ordenadosPorSimilitud) {
                if (salida.size() >= maxFragmentosTotales) {
                    break;
                }
                String clave = claveUnicaFragmento(r);
                if (!vistos.contains(clave)) {
                    salida.add(r);
                    vistos.add(clave);
                }
            }
        }

        salida.replaceAll(rr -> truncarPrecisionSimilitud(rr));
        return salida;
    }

    private static BusquedaIaResponse.ResultadoBusqueda truncarPrecisionSimilitud(
            BusquedaIaResponse.ResultadoBusqueda r) {
        if (r.getSimilitud() != null) {
            double s = Math.round(r.getSimilitud() * 10000.0) / 10000.0;
            r.setSimilitud(s);
        }
        return r;
    }

    private static String claveUnicaFragmento(BusquedaIaResponse.ResultadoBusqueda r) {
        if (r.getChunkId() != null) {
            return "c:" + r.getChunkId();
        }
        long docId = r.getDocumentoId() != null ? r.getDocumentoId() : 0L;
        int ord = r.getChunkOrder() != null ? r.getChunkOrder() : -1;
        String tx = safeChunkText(r);
        int h = tx.isEmpty() ? 0 : Objects.hash(docId, ord, tx.length(), tx.substring(0, Math.min(tx.length(), 80)));
        return "d:" + docId + ":" + ord + ":" + Integer.toHexString(h);
    }

    private String construirReferencias(List<BusquedaIaResponse.ResultadoBusqueda> chunks) {
        Set<Map<String, Object>> docs = new LinkedHashSet<>();
        for (BusquedaIaResponse.ResultadoBusqueda chunk : chunks)
            docs.add(Map.of("id", chunk.getDocumentoId(), "titulo", chunk.getDocumentoTitulo(), "relevancia", chunk.getSimilitud()));
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(docs); }
        catch (Exception e) { return "[]"; }
    }

    /**
     * Búsqueda semántica + fallback en BD + análisis DeepSeek solo con fragmentos internos.
     */
    public BusquedaAsistidaResponse buscarAsistido(String consulta, Long empresaId, int limite) {
        if (consulta == null || consulta.isBlank()) {
            throw new BadRequestException("La consulta no puede estar vacía.");
        }
        String q = consulta.trim();
        limite = Math.min(Math.max(limite, 1), 30);

        List<String> advertencias = new ArrayList<>();
        if (embeddingRepository.countByEmpresaId(empresaId) == 0) {
            advertencias.add("No hay índice semántico (embeddings) para esta empresa: se usará el texto extraído de los PDF cuando haga falta. Puede indexar desde la sección FISCALIZA-AI.");
        }

        List<BusquedaIaResponse.ResultadoBusqueda> resultados = buscar(q, empresaId, limite);
        if (resultados.isEmpty()) {
            resultados = buscarFallback(q, empresaId, RAG_FALLBACK_SQL_LIMIT);
            resultados = resultados.stream().limit(limite).collect(Collectors.toList());
        }
        if (resultados.isEmpty()) {
            resultados = buscarPorTextoPlanoEnDocumentos(q, empresaId, limite);
            if (!resultados.isEmpty()) {
                advertencias.add("No hubo coincidencias en el índice semántico; la búsqueda se hizo sobre título, descripción y texto extraído de los PDF.");
            }
        } else {
            resultados = resultados.stream().limit(limite).collect(Collectors.toList());
        }

        if (!chatService.deepseekDisponible()) {
            advertencias.add("El asistente de redacción (DeepSeek) no está configurado en el servidor; verá solo fragmentos recuperados hasta que una persona técnica añada la clave DEEPSEEK_API_KEY al arrancar.");
        }

        String contexto = resultados.isEmpty()
                ? "No se recuperaron fragmentos: ningún documento activo contiene la consulta en título, descripción o texto extraíble."
                : construirContexto(resultados);

        if (chatService.preguntaRequiereExtraccionTematicaCompleta(q) && !resultados.isEmpty()) {
            String secciones = construirBloqueSeccionesTematicasCompletas(q, resultados);
            if (!secciones.isBlank()) {
                contexto = secciones + "\n\n--- Fragmentos recuperados (complemento) ---\n\n" + contexto;
            }
        }

        String referencias = construirReferencias(resultados);
        String analisis = chatService.generarBusquedaAsistida(q, contexto, referencias);

        if (resultados.isEmpty()) {
            advertencias.add("No se encontraron coincidencias. Si los PDF son escaneados sin texto seleccionable, el buscador no podrá encontrar términos.");
        }

        String advertencia = advertencias.isEmpty() ? null : String.join(" ", advertencias);

        return BusquedaAsistidaResponse.builder()
                .consulta(q)
                .analisis(analisis)
                .resultados(resultados)
                .advertencia(advertencia)
                .build();
    }

    private List<BusquedaIaResponse.ResultadoBusqueda> recuperacionAmpliaSinonimosYTextoLibre(
            String consultaOriginal, Long empresaId) {
        if (consultaOriginal == null || consultaOriginal.isBlank()) {
            return Collections.emptyList();
        }

        List<BusquedaIaResponse.ResultadoBusqueda> textoPlano =
                buscarPorTextoPlanoEnDocumentos(consultaOriginal.trim(), empresaId, 35);
        if (!textoPlano.isEmpty()) {
            log.info("RAG ampliación: empresa {} recuperó fragmentos desde titulo/descripcion/texto (consulta literal).",
                    empresaId);
            return textoPlano;
        }

        List<String> variantes = derivarConsultasAlternativasParaRag(consultaOriginal);
        for (String v : variantes) {
            textoPlano = buscarPorTextoPlanoEnDocumentos(v, empresaId, 22);
            if (!textoPlano.isEmpty()) {
                log.info("RAG ampliación: empresa {} usando variante '{}' (texto libre BD).",
                        empresaId, v.substring(0, Math.min(v.length(), 80)));
                return textoPlano;
            }
        }

        int limEmbedding = Math.min(200, RAG_POOL_TOP_N);
        for (String v : variantes) {
            List<BusquedaIaResponse.ResultadoBusqueda> vec = buscar(v, empresaId, limEmbedding);
            if (!vec.isEmpty()) {
                log.info("RAG ampliación: empresa {} variante '{}' vía embeddings ({} filas ordenadas).",
                        empresaId, v.substring(0, Math.min(v.length(), 60)), vec.size());
                return vec.stream().limit(80).collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }

    /**
     * Reescrituras para cuando el usuario usa marcas/programas inglés pero el PDF sólo español (o viceversa).
     */
    private static List<String> derivarConsultasAlternativasParaRag(String consultaOriginal) {
        LinkedHashSet<String> extras = new LinkedHashSet<>();
        String n = normalizarMinusculaSinAcentos(consultaOriginal);

        /* Life Saving Rules / salvavidas / críticas de proceso */
        if (n.contains("salva vida") || n.contains("salvan vida") || n.contains("salva vid")
                || n.contains("lifesav") || n.contains("lifesaving") || n.contains("life sav")
                || n.contains("saving rule") || n.contains("reglas que salvan")) {
            Collections.addAll(extras,
                    "life saving rule",
                    "life saving rules",
                    "golden rules seguridad proceso",
                    "reglas criticas proceso seguro");
        }

        if (n.contains("estandar") || n.contains("standard") || n.contains("critical rule")
                || n.contains("rule crit")) {
            Collections.addAll(extras,
                    "estandares criticos",
                    "estandares proceso seguro trabajo seguro comportamiento seguridad");
        }

        if (n.contains("enap") || n.contains("golden rule")) {
            extras.add("reglas de vida seguridad");
        }

        if (n.contains("escalon") || (n.contains("altura") && n.contains("trab"))) {
            Collections.addAll(extras,
                    "rotacion trabajo altura tiempo permanencia descanso orden turnos",
                    "orden escalonado trabajo en alturas",
                    "planificacion trabajo en alturas ascenso descenso",
                    "estandar de trabajo en altura",
                    "factores de calidad trabajo en altura");
        }

        if (n.contains("factor") || n.contains("calidad")) {
            Collections.addAll(extras,
                    "factores de calidad",
                    "factor de calidad",
                    "CC1 CC2 CC3 CC4 CC5 CC6 CC7 CC8 CC9 CC10",
                    "control critico controles criticos");
        }

        if (n.contains("altura") || n.contains("alturas")) {
            Collections.addAll(extras,
                    "estandar de trabajo en altura",
                    "trabajo en alturas",
                    "trabajo en altura");
        }

        extras.removeIf(s -> s == null || s.isBlank());
        return extras.stream().limit(14).collect(Collectors.toList());
    }

    private static String normalizarMinusculaSinAcentos(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        String base = Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return base.replaceAll("\\p{M}+", "");
    }

    private List<BusquedaIaResponse.ResultadoBusqueda> buscarPorTextoPlanoEnDocumentos(
            String consulta, Long empresaId, int limite) {
        Pageable pageable = PageRequest.of(0, Math.min(limite, 30));
        Page<Documento> page = documentoRepository.buscarPorTermino(empresaId, consulta, pageable);
        List<BusquedaIaResponse.ResultadoBusqueda> out = new ArrayList<>();
        int order = 0;
        for (Documento doc : page.getContent()) {
            String texto = textoParaIndexar(doc);
            String snippet = extraerSnippetCentrado(texto, consulta, 950);
            if (snippet.isBlank()) {
                snippet = "(Sin texto extraíble en este PDF; si es un escaneo solo imagen, hace falta OCR o un PDF con texto seleccionable.)";
            }
            double sim = Math.max(0.05, 0.45 - (order * 0.02));
            out.add(BusquedaIaResponse.ResultadoBusqueda.builder()
                    .chunkId(null)
                    .chunkText(snippet)
                    .chunkOrder(order++)
                    .documentoId(doc.getId())
                    .documentoTitulo(doc.getTitulo())
                    .similitud(sim)
                    .build());
        }
        return out;
    }

    private static String extraerSnippetCentrado(String texto, String termino, int maxCaracteres) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        if (termino == null || termino.isBlank()) {
            return texto.length() > maxCaracteres ? texto.substring(0, maxCaracteres) + "…" : texto;
        }
        String lower = texto.toLowerCase();
        String t = termino.trim().toLowerCase();
        int idx = lower.indexOf(t);
        if (idx < 0) {
            return texto.length() > maxCaracteres ? texto.substring(0, maxCaracteres) + "…" : texto;
        }
        int mitad = maxCaracteres / 2;
        int start = Math.max(0, idx - mitad);
        int end = Math.min(texto.length(), idx + termino.length() + mitad);
        String snippet = texto.substring(start, end);
        if (start > 0) {
            snippet = "…" + snippet;
        }
        if (end < texto.length()) {
            snippet = snippet + "…";
        }
        return snippet;
    }

    private String textoParaIndexar(Documento doc) {
        return construirTextoPlanoDocumento(doc, null, true);
    }

    private String construirTextoPlanoDocumento(Documento doc, String pregunta, boolean incluirPuntosClave) {
        StringBuilder sb = new StringBuilder();
        sb.append("Titulo: ").append(doc.getTitulo()).append("\n");
        if (doc.getDescripcion() != null) {
            sb.append("Descripcion: ").append(doc.getDescripcion()).append("\n");
        }

        String bloqueEditor = construirBloqueEditorPrioritario(doc, pregunta);
        if (!bloqueEditor.isBlank()) {
            sb.append('\n').append(bloqueEditor).append('\n');
        } else if (tieneEditorGuardado(doc)) {
            sb.append("\n--- LIBRO ESTRUCTURADO (EDITOR) ---\n");
            sb.append(limpiarHtmlDocumento(textoCrudoDocumento(doc))).append('\n');
        }

        boolean consultaConcisa = pregunta != null && esRespuestaConcisa(pregunta);
        boolean preguntaEstandar = pregunta != null
                && EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta).isPresent();
        if (!incluirPuntosClave || (consultaConcisa && preguntaEstandar)) {
            return sb.toString();
        }

        List<PuntoClave> puntos = puntoClaveRepository.findByDocumentoIdOrderByOrdenAsc(doc.getId());
        if (puntos.isEmpty()) {
            return sb.toString();
        }

        List<PuntoClave> filtrados = filtrarPuntosClavePorConsulta(puntos, pregunta);
        if (filtrados.isEmpty()) {
            return sb.toString();
        }

        sb.append("\n\n--- PUNTOS CLAVE ESTRUCTURADOS (complemento editor) ---\n");
        for (PuntoClave p : filtrados) {
            if (p.getTema() != null && !p.getTema().isBlank()) {
                sb.append("\n[").append(p.getTema()).append("]\n");
            }
            if (p.getCodigo() != null && !p.getCodigo().isBlank()) {
                sb.append(p.getCodigo());
                if (p.getTitulo() != null && !p.getTitulo().isBlank()) {
                    sb.append(" — ").append(p.getTitulo());
                }
                sb.append('\n');
            } else if (p.getTitulo() != null && !p.getTitulo().isBlank()) {
                sb.append(p.getTitulo()).append('\n');
            }
            if (p.getContenido() != null) {
                sb.append(limpiarHtmlDocumento(p.getContenido())).append("\n\n");
            }
        }
        return sb.toString();
    }

    private static LinkedHashSet<String> terminosConsultaParaFiltrado(String pregunta) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        if (pregunta == null || pregunta.isBlank()) {
            return terms;
        }
        String n = normalizarMinusculaSinAcentos(pregunta);
        Matcher m = Pattern.compile("[a-z0-9]{4,}").matcher(n);
        while (m.find()) {
            terms.add(m.group());
        }
        if (n.contains("altura")) {
            terms.add("altura");
            terms.add("alturas");
        }
        if (n.contains("calient") || n.contains("fuego")) {
            terms.add("caliente");
        }
        if (n.contains("confin")) {
            terms.add("confin");
        }
        return terms;
    }

    private static List<PuntoClave> filtrarPuntosClavePorConsulta(List<PuntoClave> puntos, String pregunta) {
        if (puntos == null || puntos.isEmpty()) {
            return List.of();
        }
        Optional<String> estandar = EstandarConsultaHelper.detectarEstandarEnPregunta(pregunta);
        if (estandar.isPresent()) {
            String filtro = normalizarMinusculaSinAcentos(estandar.get());
            List<PuntoClave> out = new ArrayList<>();
            for (PuntoClave p : puntos) {
                String blob = normalizarMinusculaSinAcentos(
                        (p.getTema() != null ? p.getTema() : "") + " "
                                + (p.getTitulo() != null ? p.getTitulo() : "") + " "
                                + (p.getContenido() != null ? p.getContenido() : ""));
                if (blob.contains(filtro.replace(" ", "")) || EstandarConsultaHelper.coincideNombreEstandar(blob, filtro)) {
                    out.add(p);
                }
            }
            return out.size() > 15 ? out.subList(0, 15) : out;
        }
        LinkedHashSet<String> terminos = pregunta != null && !pregunta.isBlank()
                ? terminosConsultaParaFiltrado(pregunta)
                : new LinkedHashSet<>();
        if (terminos.isEmpty()) {
            return List.of();
        }
        List<PuntoClave> out = new ArrayList<>();
        for (PuntoClave p : puntos) {
            String blob = normalizarMinusculaSinAcentos(
                    (p.getTema() != null ? p.getTema() : "") + " "
                            + (p.getTitulo() != null ? p.getTitulo() : "") + " "
                            + (p.getContenido() != null ? p.getContenido() : ""));
            boolean match = terminos.stream().anyMatch(t -> t.length() >= 5 && blob.contains(t));
            if (match) {
                out.add(p);
            }
        }
        return out.size() > 20 ? out.subList(0, 20) : out;
    }

    static String limpiarHtmlDocumento(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        String s = texto;
        s = s.replaceAll("(?is)</p>\\s*<p>", "\n\n");
        s = s.replaceAll("(?is)<br\\s*/?>", "\n");
        s = s.replaceAll("(?is)<li>", "\n- ");
        s = s.replaceAll("(?is)</li>", "");
        s = s.replaceAll("(?is)<[^>]+>", "");
        s = s.replace('\u00A0', ' ');
        s = s.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }

    private static List<Documento> ordenarPorTitulo(List<Documento> docs) {
        if (docs == null || docs.isEmpty()) {
            return Collections.emptyList();
        }
        return docs.stream()
                .sorted(Comparator.comparing(d -> textoSeguroTitulo(d.getTitulo()).toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
    }

    private static String textoSeguroTitulo(String titulo) {
        return titulo != null ? titulo : "";
    }

    /**
     * Texto inequívoco para el modelo y para {@link IaChatService} (marcador estable).
     */
    private String formatearCatalogoParaPrompt(List<Documento> docs) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════════\n");
        sb.append(MARCADOR_CATALOGO_EMPRESA).append("\n");
        sb.append("""
                Metadatos reales registrados en la plataforma: título y ID de cada PDF activo.
                Úsalo cuando el usuario pregunte qué documentos, libros o archivos tiene cargados esta empresa.

                Este bloque lista **nombres de archivos**, no contenido normativo del interior del PDF.
                Si más abajo aparece «Fragmentos recuperados…», puedes combinar inventario + hallazgos de texto.""");
        sb.append("\n\nTotal documentos activos: ").append(docs.size()).append("\n\n");
        int n = 1;
        for (Documento d : docs) {
            sb.append(n++).append(". ID ").append(d.getId()).append(" — «").append(textoSeguroTitulo(d.getTitulo())).append('»');
            String desc = truncarUnaLinea(d.getDescripcion(), DESC_CATALOGO_PROMPT_MAX);
            if (desc != null && !desc.isBlank()) {
                sb.append("\n   Descripción registrada al subir: ").append(desc);
            }
            sb.append('\n');
        }
        sb.append("""
                
                Si no hay fragmentos de contenido recuperados más abajo, explica que el inventario es correcto pero la búsqueda \
                no halló texto relacionado (reformular, indexar en FISCALIZA-AI, o PDF escaneado sin OCR).
                """);
        sb.append("═══════════════════════════════════════════════════════\n");
        return sb.toString();
    }

    private String construirReferenciasCatalogo(List<Documento> docs) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Documento d : docs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("titulo", textoSeguroTitulo(d.getTitulo()));
            m.put("relevancia", 0.0);
            m.put("origen", "catalogo");
            list.add(m);
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static boolean esPreguntaSobreCatalogoDocumentos(String pregunta) {
        if (pregunta == null || pregunta.isBlank()) {
            return false;
        }
        String corta = pregunta.length() > 450 ? pregunta.substring(0, 450) : pregunta;
        String n = normalizarMinusculaSinAcentos(corta);
        for (String p : PATRONES_CONSULTA_CATALOGO) {
            if (n.contains(p)) {
                return true;
            }
        }
        return false;
    }

    private static String truncarUnaLinea(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String one = s.replaceAll("\\s+", " ").trim();
        if (one.length() <= max) {
            return one;
        }
        return one.substring(0, Math.max(1, max - 1)).trim() + "…";
    }

    /**
     * Ensambla catálogo + secciones temáticas completas + fragmentos RAG en orden de prioridad.
     */
    private static String ensamblarContextoRag(
            String bloqueCatalogo, String bloqueSeccionesCompletas, String ctxFragmentos) {
        StringBuilder sb = new StringBuilder();
        if (bloqueCatalogo != null && !bloqueCatalogo.isBlank()) {
            sb.append(bloqueCatalogo);
        }
        if (bloqueSeccionesCompletas != null && !bloqueSeccionesCompletas.isBlank()) {
            if (sb.length() > 0) {
                sb.append("\n\n---\n\n");
            }
            sb.append(bloqueSeccionesCompletas);
        }
        if (ctxFragmentos != null && !ctxFragmentos.isBlank()) {
            if (sb.length() > 0) {
                sb.append("\n\n---\n\nFragmentos recuperados relacionados con la consulta:\n\n");
            }
            sb.append(ctxFragmentos);
        }
        return sb.toString();
    }

    /**
     * Para consultas temáticas (factores de calidad, CC, estándar de altura…), carga el texto completo
     * de los PDF más relevantes y extrae la sección íntegra + bloques CC.
     */
    private String construirBloqueSeccionesTematicasCompletas(
            String pregunta, List<BusquedaIaResponse.ResultadoBusqueda> poolOrdenado) {

        if (poolOrdenado == null || poolOrdenado.isEmpty()) {
            return "";
        }

        Map<Long, Double> mejorSimilitudPorDoc = new LinkedHashMap<>();
        Map<Long, String> tituloPorDoc = new HashMap<>();
        for (BusquedaIaResponse.ResultadoBusqueda r : poolOrdenado) {
            if (r.getDocumentoId() == null) {
                continue;
            }
            double sim = r.getSimilitud() != null ? r.getSimilitud() : 0.0;
            mejorSimilitudPorDoc.merge(r.getDocumentoId(), sim, Math::max);
            if (r.getDocumentoTitulo() != null) {
                tituloPorDoc.putIfAbsent(r.getDocumentoId(), r.getDocumentoTitulo());
            }
        }

        List<Long> docIds = mejorSimilitudPorDoc.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(RAG_MAX_DOCS_SECCION_COMPLETA)
                .map(Map.Entry::getKey)
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════════\n");
        sb.append(MARCADOR_SECCIONES_COMPLETAS).append("\n");
        sb.append("""
                Apartados del **Editor de Contenido** guardado en plataforma (prioridad máxima) o del PDF.
                Use estos bloques para listados completos: CC1, CC2… factores de calidad, estándares, etc.

                """);

        int docsConContenido = 0;
        for (Long docId : docIds) {
            Documento doc = documentoRepository.findById(docId).orElse(null);
            if (doc == null || !Boolean.TRUE.equals(doc.getActivo())) {
                continue;
            }
            String bloqueEditor = construirBloqueEditorPrioritario(doc, pregunta);
            String texto = textoPlanoParaConsulta(doc, pregunta);
            if ((bloqueEditor == null || bloqueEditor.isBlank())
                    && (texto == null || texto.replaceAll("\\s+", "").length() < MIN_TEXTO_DOC_IA)) {
                continue;
            }

            if (bloqueEditor != null && !bloqueEditor.isBlank()) {
                docsConContenido++;
                String titulo = doc.getTitulo() != null ? doc.getTitulo()
                        : tituloPorDoc.getOrDefault(docId, "(sin título)");
                sb.append("\n────────────────────────────────────────\n");
                sb.append("DOCUMENTO: «").append(titulo).append("» (ID ").append(docId).append(")\n");
                sb.append("────────────────────────────────────────\n");
                sb.append(bloqueEditor).append("\n");
                continue;
            }

            IaChatService.ContextoTematicoDocumento ctx = chatService.analizarTextoParaConsulta(texto, pregunta);
            if (ctx.seccionCompleta().isBlank() && ctx.bloqueControlesCriticos().isBlank()) {
                continue;
            }

            docsConContenido++;
            String titulo = doc.getTitulo() != null ? doc.getTitulo()
                    : tituloPorDoc.getOrDefault(docId, "(sin título)");
            sb.append("\n────────────────────────────────────────\n");
            sb.append("DOCUMENTO: «").append(titulo).append("» (ID ").append(docId).append(")\n");
            sb.append("────────────────────────────────────────\n");

            if (!ctx.seccionCompleta().isBlank()) {
                sb.append("\n### SECCIÓN TEMÁTICA COMPLETA\n\n");
                sb.append(ctx.seccionCompleta()).append("\n");
            }
            if (!ctx.bloqueControlesCriticos().isBlank()) {
                sb.append("\n### CONTROLES CRÍTICOS / FACTORES DE CALIDAD\n\n");
                sb.append(ctx.bloqueControlesCriticos()).append("\n");
            }
        }

        sb.append("\n═══════════════════════════════════════════════════════\n");
        return docsConContenido > 0 ? sb.toString() : "";
    }
}

