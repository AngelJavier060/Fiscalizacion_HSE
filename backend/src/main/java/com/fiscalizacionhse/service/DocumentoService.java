package com.fiscalizacionhse.service;

import com.fiscalizacionhse.dto.request.DocumentoRequest;
import com.fiscalizacionhse.dto.response.DocumentoResponse;
import com.fiscalizacionhse.dto.response.PuntoClaveResponse;
import com.fiscalizacionhse.event.DocumentoSubidoEvent;
import com.fiscalizacionhse.exception.BadRequestException;
import com.fiscalizacionhse.exception.ResourceNotFoundException;
import com.fiscalizacionhse.model.Documento;
import com.fiscalizacionhse.model.Empresa;
import com.fiscalizacionhse.model.PuntoClave;
import com.fiscalizacionhse.model.Usuario;
import com.fiscalizacionhse.repository.DocumentoRepository;
import com.fiscalizacionhse.repository.EmpresaRepository;
import com.fiscalizacionhse.repository.PuntoClaveRepository;
import com.fiscalizacionhse.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.math.BigDecimal;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentoService {

    /** Archivo listo para servir con GET (inline PDF). */
    public record PdfArchivo(Resource resource, String nombreArchivo, String tipoMime) {}

    private final DocumentoRepository documentoRepository;
    private final PuntoClaveRepository puntoClaveRepository;
    private final EmpresaRepository empresaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PdfService pdfService;
    private final IdiomaService idiomaService;
    private final TraduccionService traduccionService;
    private final IaService iaService;
    private final AuditoriaService auditoriaService;
    private final IaBusquedaService iaBusquedaService;
    private final ApplicationEventPublisher eventPublisher;
    private final org.springframework.cache.CacheManager cacheManager;
    private final DocumentoProcesamientoPersistence procesamientoPersistence;

    private static final int MINUTOS_PROCESAMIENTO_ACTIVO = 3;

    /**
     * Archivo PDF en disco para previsualización / descarga (debe existir ruta_archivo).
     */
    public PdfArchivo obtenerArchivoPdf(Long documentoId) {
        Documento d = buscar(documentoId);
        if (!Boolean.TRUE.equals(d.getActivo())) {
            throw new BadRequestException("El documento no está disponible");
        }
        Path path = Path.of(d.getRutaArchivo());
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("Archivo PDF del documento no encontrado en el servidor");
        }
        Resource resource = new FileSystemResource(path.toFile());
        String tipo = d.getArchivoTipo() != null && !d.getArchivoTipo().isBlank()
                ? d.getArchivoTipo()
                : "application/pdf";
        return new PdfArchivo(resource, d.getArchivoNombre(), tipo);
    }

    /**
     * Vista rápida de una página como imagen PNG (diagramas/tablas dentro del mismo PDF que no viajan como texto para la IA).
     */
    public byte[] obtenerPreviewPaginaPng(Long documentoId, int paginaNumeradaDesdeUno) {
        Documento d = buscar(documentoId);
        if (!Boolean.TRUE.equals(d.getActivo())) {
            throw new BadRequestException("El documento no está disponible");
        }
        Path path = Path.of(d.getRutaArchivo());
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("Archivo PDF del documento no encontrado en el servidor");
        }

        try {
            return pdfService.renderizarPaginaComoPng(path.toFile(), paginaNumeradaDesdeUno, 104f);
        } catch (FileNotFoundException e) {
            throw new ResourceNotFoundException("Archivo PDF del documento no encontrado en el servidor");
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage());
        } catch (IOException ex) {
            log.warn("No se pudo rasterizar página {} del doc {}: {}", paginaNumeradaDesdeUno, documentoId, ex.getMessage());
            throw new BadRequestException("No se pudo generar la vista previa de la página del PDF.");
        }
    }

    /**
     * Listar documentos de una empresa
     */
    public Page<DocumentoResponse> listarPorEmpresa(Long empresaId, Pageable pageable) {
        return documentoRepository
                .findByEmpresaIdAndActivoTrueOrderByCreatedAtDesc(empresaId, pageable)
                .map(this::toResponse);
    }

    /**
     * Buscar documentos por término
     */
    public Page<DocumentoResponse> buscar(Long empresaId, String termino, Pageable pageable) {
        if (termino == null || termino.isBlank()) {
            return listarPorEmpresa(empresaId, pageable);
        }
        return documentoRepository
                .buscarPorTermino(empresaId, termino, pageable)
                .map(this::toResponse);
    }

    /**
     * Obtener un documento por ID
     */
    public DocumentoResponse obtener(Long id) {
        return toResponse(buscar(id));
    }

    /**
     * Subir PDF: guarda el archivo y responde de inmediato; el resto se procesa en segundo plano
     * (evita 504 del proxy en subidas con extracción + IA).
     */
    @Transactional
    public DocumentoResponse subirDocumento(DocumentoRequest request, MultipartFile archivo, Long usuarioId) {
        if (archivo == null || archivo.isEmpty()) {
            throw new BadRequestException("Debe adjuntar un archivo PDF");
        }
        if (!esPdfValido(archivo)) {
            throw new BadRequestException("Solo se permiten archivos PDF");
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", usuarioId));
        Empresa empresa = empresaRepository.findById(request.getEmpresaId())
                .orElseThrow(() -> new ResourceNotFoundException("Empresa", request.getEmpresaId()));

        String rutaArchivo = pdfService.guardarArchivo(archivo);
        log.info("📁 Archivo guardado (respuesta inmediata al cliente): {}", rutaArchivo);

        Documento documento = Documento.builder()
                .titulo(request.getTitulo())
                .descripcion(request.getDescripcion())
                .archivoNombre(archivo.getOriginalFilename())
                .archivoTipo(archivo.getContentType())
                .archivoTamano(archivo.getSize())
                .rutaArchivo(rutaArchivo)
                .estadoProcesamiento("PROCESANDO")
                .requiereTraduccion(false)
                .traducido(false)
                .puntosGeneradosIa(false)
                .empresa(empresa)
                .subidoPor(usuario)
                .build();

        documento = documentoRepository.save(documento);

        auditoriaService.registrar(
                usuario, empresa, "SUBIR_DOCUMENTO", "Documento",
                documento.getId(),
                "Documento subido (procesamiento en curso): " + documento.getTitulo(),
                null);

        eventPublisher.publishEvent(new DocumentoSubidoEvent(documento.getId(), usuarioId));

        return toResponse(documento);
    }

    /**
     * Reprocesar PDF (ERROR o atascado). No bloquea la petición HTTP.
     */
    @Transactional
    public DocumentoResponse solicitarReprocesamiento(Long documentoId, Long usuarioId) {
        Documento documento = buscar(documentoId);
        if ("PROCESANDO".equals(documento.getEstadoProcesamiento())
                && documento.getUpdatedAt().isAfter(LocalDateTime.now().minusMinutes(MINUTOS_PROCESAMIENTO_ACTIVO))) {
            throw new BadRequestException("El documento se está procesando. Espere unos minutos e intente de nuevo.");
        }
        documento.setEstadoProcesamiento("PROCESANDO");
        documento.setErrorProcesamiento(null);
        documentoRepository.save(documento);
        evictTextoCompletoCache(documentoId);
        eventPublisher.publishEvent(new DocumentoSubidoEvent(documentoId, usuarioId));
        return toResponse(documento);
    }

    private void evictTextoCompletoCache(Long documentoId) {
        var cache = cacheManager.getCache("textoCompleto");
        if (cache != null) {
            cache.evict(documentoId);
        }
    }

    private boolean debeOmitirProcesamientoDuplicado(Documento documento) {
        if ("COMPLETADO".equals(documento.getEstadoProcesamiento())
                && documento.getTextoExtraido() != null
                && !documento.getTextoExtraido().isBlank()) {
            log.info("Documento {} ya procesado, omitiendo", documento.getId());
            return true;
        }
        if ("PROCESANDO".equals(documento.getEstadoProcesamiento())
                && (documento.getTextoExtraido() == null || documento.getTextoExtraido().isBlank())
                && documento.getUpdatedAt().isAfter(LocalDateTime.now().minusMinutes(MINUTOS_PROCESAMIENTO_ACTIVO))) {
            log.info("Documento {} en procesamiento activo ({}), omitiendo duplicado",
                    documento.getId(), documento.getUpdatedAt());
            return true;
        }
        return false;
    }

    private static boolean esPdfValido(MultipartFile archivo) {
        String tipo = archivo.getContentType();
        if ("application/pdf".equals(tipo)) {
            return true;
        }
        String nombre = archivo.getOriginalFilename();
        return nombre != null && nombre.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    /**
     * Pasos 2–5 tras la subida: extracción, idioma, traducción e IA.
     * Sin @Transactional global: PDF + IA pueden tardar minutos sin retener conexiones JDBC.
     */
    public void ejecutarProcesamientoPostSubida(Long documentoId, Long usuarioId) {
        Documento documento = procesamientoPersistence.cargar(documentoId);
        if (debeOmitirProcesamientoDuplicado(documento)) {
            return;
        }
        procesamientoPersistence.marcarProcesando(documentoId);

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", usuarioId));
        Empresa empresa = documento.getEmpresa();
        String titulo = documento.getTitulo();
        String rutaArchivo = documento.getRutaArchivo();

        try {
            log.info("📝 Paso 2/5 - Extrayendo texto de documento {}", documentoId);
            String textoPlano = prepararTextoConSaltos(pdfService.extraerTexto(rutaArchivo));
            textoPlano = quitarArtefactosPagina(textoPlano);
            String textoExtraido = autoEstructurarDesdeTextoExtraido(textoPlano);
            log.info("📝 Texto extraído: {} chars → HTML: {} chars", textoPlano.length(), textoExtraido.length());

            String idiomaDetectado = idiomaService.detectar(textoExtraido);
            boolean requiereTraduccion = !"es".equals(idiomaDetectado) && !"desconocido".equals(idiomaDetectado);
            log.info("🌐 Paso 3/5 - Idioma: {} (traducción: {})", idiomaDetectado, requiereTraduccion);

            procesamientoPersistence.guardarTextoExtraido(
                    documentoId, textoExtraido, idiomaDetectado, requiereTraduccion);

            String textoTraducido = null;
            if (requiereTraduccion) {
                log.info("🌍 Paso 4/5 - Traducción...");
                try {
                    textoTraducido = traduccionService.traducirAIngles(textoExtraido, idiomaDetectado);
                    procesamientoPersistence.guardarTraduccion(documentoId, textoTraducido);
                    log.info("✅ Traducción completada");
                } catch (Exception e) {
                    log.error("❌ Error en traducción: {}", e.getMessage());
                }
            }

            log.info("🤖 Paso 5/5 - Extrayendo puntos clave con IA...");
            boolean puntosGenerados = false;
            try {
                String textoParaAnalisis = (textoTraducido != null && !textoTraducido.isBlank())
                        ? textoTraducido
                        : textoExtraido;

                List<IaService.PuntoClaveIa> puntosIa = iaService.extraerPuntosClaveSubidaRapida(
                        titulo, textoParaAnalisis);

                if (!puntosIa.isEmpty()) {
                    List<PuntoClave> puntosGuardados = new ArrayList<>();
                    for (IaService.PuntoClaveIa puntoIa : puntosIa) {
                        puntosGuardados.add(PuntoClave.builder()
                                .contenido(puntoIa.getContenido())
                                .titulo(puntoIa.getTitulo())
                                .tema(puntoIa.getTema())
                                .codigo(puntoIa.getCodigo())
                                .tipo(puntoIa.getTipo() != null ? puntoIa.getTipo() : "GENERAL")
                                .orden(puntoIa.getOrden())
                                .esIa(true)
                                .confianzaIa(puntoIa.getConfianza() != null
                                        ? puntoIa.getConfianza()
                                        : BigDecimal.valueOf(0.85))
                                .revisado(false)
                                .build());
                    }
                    procesamientoPersistence.guardarPuntosIa(documentoId, puntosGuardados);
                    puntosGenerados = true;
                    log.info("✅ {} puntos clave extraídos", puntosGuardados.size());
                } else {
                    log.warn("⚠️ No se extrajeron puntos clave");
                }
            } catch (Exception e) {
                log.error("❌ Error al extraer puntos clave con IA: {}", e.getMessage());
            }

            procesamientoPersistence.marcarCompletado(documentoId, puntosGenerados);

            auditoriaService.registrar(
                    usuario, empresa, "PROCESAR_DOCUMENTO", "Documento",
                    documentoId,
                    "Procesamiento completado: " + titulo +
                            " (" + idiomaDetectado + ", " + textoExtraido.length() + " caracteres)",
                    null);

        } catch (Exception e) {
            log.error("❌ Error procesando documento {}: {}", documentoId, e.getMessage(), e);
            procesamientoPersistence.marcarError(
                    documentoId,
                    e.getMessage() != null ? e.getMessage() : "Error desconocido al procesar el PDF");
        }
    }

    /**
     * Actualizar metadata del documento
     */
    @org.springframework.cache.annotation.CacheEvict(value = "textoCompleto", key = "#id")
    @Transactional
    public DocumentoResponse actualizar(Long id, DocumentoRequest request, Long actorUsuarioId) {
        Documento documento = buscar(id);
        documento.setTitulo(request.getTitulo());
        documento.setDescripcion(request.getDescripcion());
        documento = documentoRepository.save(documento);

        Usuario actor = usuarioActor(actorUsuarioId);
        auditoriaService.registrar(
                actor, documento.getEmpresa(), "ACTUALIZAR_DOCUMENTO", "Documento",
                documento.getId(), "Documento actualizado: " + documento.getTitulo(), null);

        return toResponse(documento);
    }

    /**
     * Eliminar documento (borrado lógico)
     */
    @Transactional
    public void eliminar(Long id, Long actorUsuarioId) {
        Documento documento = buscar(id);
        documento.setActivo(false);
        documentoRepository.save(documento);

        Usuario actor = usuarioActor(actorUsuarioId);
        auditoriaService.registrar(
                actor, documento.getEmpresa(), "ELIMINAR_DOCUMENTO", "Documento",
                documento.getId(), "Documento eliminado: " + documento.getTitulo(), null);

        // Eliminar archivo físico después de confirmar la auditoría en la misma transacción
        pdfService.eliminarArchivo(documento.getRutaArchivo());
    }

    /**
     * Regenerar puntos clave con IA para un documento existente
     */
    @Transactional
    public List<PuntoClaveResponse> regenerarPuntosIa(Long documentoId) {
        Documento documento = buscar(documentoId);

        // Eliminar puntos IA anteriores
        List<PuntoClave> puntosAnteriores = puntoClaveRepository
                .findByDocumentoIdAndEsIaTrueOrderByOrdenAsc(documentoId);
        puntoClaveRepository.deleteAll(puntosAnteriores);

        // Extraer nuevos puntos
        String textoParaAnalisis = textoTraducidoValido(documento)
                ? documento.getTextoTraducido()
                : documento.getTextoExtraido();

        List<IaService.PuntoClaveIa> nuevosPuntos = iaService.extraerPuntosClave(
                documento.getTitulo(), textoParaAnalisis);

        List<PuntoClave> puntosGuardados = new ArrayList<>();
        for (IaService.PuntoClaveIa puntoIa : nuevosPuntos) {
            PuntoClave punto = PuntoClave.builder()
                    .contenido(puntoIa.getContenido())
                    .titulo(puntoIa.getTitulo())
                    .tema(puntoIa.getTema())
                    .codigo(puntoIa.getCodigo())
                    .tipo(puntoIa.getTipo() != null ? puntoIa.getTipo() : "GENERAL")
                    .orden(puntoIa.getOrden())
                    .esIa(true)
                    .confianzaIa(puntoIa.getConfianza() != null
                            ? puntoIa.getConfianza()
                            : BigDecimal.valueOf(0.85))
                    .revisado(false)
                    .documento(documento)
                    .build();
            puntosGuardados.add(punto);
        }
        puntoClaveRepository.saveAll(puntosGuardados);
        documento.setPuntosGeneradosIa(true);
        documentoRepository.save(documento);

        log.info("✅ {} puntos clave regenerados con IA", puntosGuardados.size());

        return puntosGuardados.stream()
                .map(this::toPuntoClaveResponse)
                .collect(Collectors.toList());
    }

    /**
     * Detecta títulos del PDF (ESTÁNDAR DE …, numeración ENAP) y genera HTML con secciones.
     * No requiere que el usuario marque H2 en el editor.
     */
    public String autoEstructurarDesdeTextoExtraido(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        if (esContenidoHtml(texto) && htmlTieneEstructuraUtilParaConsulta(texto)) {
            return quitarIndiceEmbebido(texto);
        }

        String plano = esContenidoHtml(texto) ? htmlATextoPlano(texto) : texto;
        String textoConSaltos = prepararTextoConSaltos(plano);
        textoConSaltos = quitarArtefactosPagina(textoConSaltos);

        List<com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada> indiceCompleto =
                detectarEncabezados(textoConSaltos);
        List<com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada> seccionesCuerpo =
                filtrarSeccionesCuerpo(textoConSaltos, indiceCompleto);
        String html = generarTextoEstructuradoHtml(textoConSaltos, indiceCompleto, seccionesCuerpo);
        if (html == null || html.isBlank()) {
            return parrafosHtml(textoConSaltos);
        }
        return html;
    }

    /**
     * Obtener el texto completo extraído de un documento con sus secciones detectadas.
     * Los datos se cachean 30 minutos para acceso rápido en modo lectura.
     */
    @org.springframework.cache.annotation.Cacheable(
            value = "textoCompleto",
            key = "#documentoId",
            unless = "#result.textoCompleto == null || #result.textoCompleto.isBlank()")
    public com.fiscalizacionhse.dto.response.TextoCompletoResponse obtenerTextoCompleto(Long documentoId) {
        Documento d = buscar(documentoId);
        String texto = textoTraducidoValido(d) ? d.getTextoTraducido() : d.getTextoExtraido();
        if (texto == null || texto.isBlank()) {
            return com.fiscalizacionhse.dto.response.TextoCompletoResponse.builder()
                    .id(d.getId())
                    .titulo(d.getTitulo())
                    .textoCompleto("")
                    .textoEstructurado("")
                    .textoEditor("")
                    .indice(List.of())
                    .secciones(List.of())
                    .idioma(d.getIdiomaDetectado())
                    .build();
        }

        // HTML ya guardado en BD: devolver tal cual (sin re-extraer ni re-estructurar)
        if (esContenidoHtml(texto) && htmlTieneEstructura(texto)) {
            String textoPlano = htmlATextoPlano(texto);
            List<com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada> indice =
                    detectarEncabezados(prepararTextoConSaltos(textoPlano));
            String htmlLimpio = quitarIndiceEmbebido(texto);
            return com.fiscalizacionhse.dto.response.TextoCompletoResponse.builder()
                    .id(d.getId())
                    .titulo(d.getTitulo())
                    .textoCompleto(textoPlano)
                    .textoEstructurado(htmlLimpio)
                    .textoEditor(htmlLimpio)
                    .indice(indice)
                    .secciones(filtrarSeccionesNavegacion(indice))
                    .idioma(d.getIdiomaDetectado())
                    .build();
        }

        String textoEstructurado = autoEstructurarDesdeTextoExtraido(texto);
        String textoPlano = esContenidoHtml(texto) ? htmlATextoPlano(texto) : texto;
        String textoConSaltos = prepararTextoConSaltos(textoPlano);
        textoConSaltos = quitarArtefactosPagina(textoConSaltos);

        List<com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada> indiceCompleto =
                detectarEncabezados(textoConSaltos);
        List<com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada> seccionesNav =
                filtrarSeccionesNavegacion(indiceCompleto);

        String textoLimpio = textoConSaltos;
        String textoEditor = textoEstructurado;

        return com.fiscalizacionhse.dto.response.TextoCompletoResponse.builder()
                .id(d.getId())
                .titulo(d.getTitulo())
                .textoCompleto(textoLimpio)
                .textoEstructurado(textoEstructurado)
                .textoEditor(textoEditor)
                .indice(indiceCompleto)
                .secciones(seccionesNav)
                .idioma(d.getIdiomaDetectado())
                .build();
    }

    /**
     * Inserta saltos de línea antes de encabezados típicos ENAP/HSE cuando el PDF
     * entrega todo el texto en un solo bloque continuo.
     */
    private String prepararTextoConSaltos(String texto) {
        if (texto == null || texto.isBlank()) return texto == null ? "" : texto;

        String t = texto.replace("\r\n", "\n").replace('\r', '\n');
        t = t.replaceAll("(?m)([\\p{L}])-\\s*\\n\\s*([\\p{L}])", "$1$2");

        // Índice: "1. ESTÁNDAR DE ..." "2. ESTÁNDAR DE ..."
        t = t.replaceAll("(?i)(?<=\\S)\\s+(\\d+\\.\\s+EST[ÁA]NDAR\\s+DE\\s+)", "\n\n$1");
        // Estándares sin número al inicio
        t = t.replaceAll("(?i)(?<=[.;!?])\\s+(EST[ÁA]NDAR\\s+DE\\s+)", "\n\n$1");
        t = t.replaceAll("(?i)(?<=[^\\n])\\s+(EST[ÁA]NDAR\\s+DE\\s+)", "\n\n$1");
        // Secciones numeradas 4.4, 4.4.1
        t = t.replaceAll("(?<![\\d.])(\\d+\\.\\d+(?:\\.\\d+)*)\\s+(?=[A-ZÁÉÍÓÚÑ\"(])", "\n\n$1 ");
        // Capítulos
        t = t.replaceAll("(?i)\\s+((?:CAP[ÍI]TULO|SECCI[ÓO]N)\\s+[\\dIVXLC]+)", "\n\n$1");
        // Párrafos: punto + espacio + mayúscula (oración nueva en documentos formales)
        t = t.replaceAll("(?<=[.;!?])\\s+(?=[A-ZÁÉÍÓÚÑ\"(])", "\n");

        t = t.replaceAll("[ \\t]+", " ");
        t = t.replaceAll("(?m)^\\s+", "");
        t = t.replaceAll("\\n{3,}", "\n\n");
        return t.trim();
    }

    private String quitarArtefactosPagina(String texto) {
        if (texto == null || texto.isBlank()) return texto == null ? "" : texto;
        String limpio = texto;
        limpio = limpio.replaceAll("(?m)^\\s*\\d{1,4}\\s*$", "");
        limpio = limpio.replaceAll("(?mi)^\\s*(?:p[áa]gina|page)\\s+\\d+\\s*(?:de|of)\\s*\\d+\\s*$", "");
        limpio = limpio.replaceAll("(?mi)^\\s*-\\s*\\d+\\s*-\\s*$", "");
        limpio = limpio.replaceAll("\\n{3,}", "\n\n");
        return limpio.trim();
    }

    /**
     * Detecta todos los encabezados extraíbles para índice y estructura.
     */
    private List<com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada> detectarEncabezados(String texto) {
        if (texto == null || texto.isBlank()) return List.of();

        List<EncabezadoMatch> encs = new ArrayList<>();

        Pattern patronIndiceEstandar = Pattern.compile(
                "(?im)^\\s*(\\d+)\\.\\s+(EST[ÁA]NDAR\\s+DE\\s+.+?)(?:\\s+\\d{1,4})?\\s*$",
                Pattern.UNICODE_CHARACTER_CLASS);
        Pattern patronTituloDoc = Pattern.compile(
                "(?im)^\\s*(ESTÁNDARES\\s+QUE\\s+SALVAN\\s+VIDAS)\\s*$",
                Pattern.UNICODE_CHARACTER_CLASS);
        Pattern patronEstandar = Pattern.compile(
                "(?im)^\\s*(EST[ÁA]NDAR\\s+DE\\s+.+?)(?:\\s+\\d{1,4})?\\s*$",
                Pattern.UNICODE_CHARACTER_CLASS);
        Pattern patronNumerado = Pattern.compile(
                "(?m)^\\s*(\\d+(?:\\.\\d+){1,3})\\s+(.{3,180})\\s*$",
                Pattern.UNICODE_CHARACTER_CLASS);
        Pattern patronCapitulo = Pattern.compile(
                "(?im)^\\s*((?:CAP[ÍI]TULO|SECCI[ÓO]N)\\s+[\\dIVXLC]+\\.?\\s*.{3,180})\\s*$",
                Pattern.UNICODE_CHARACTER_CLASS);
        Pattern patronMayusculas = Pattern.compile(
                "(?m)^\\s*(.{18,120})\\s*$",
                Pattern.UNICODE_CHARACTER_CLASS);

        Matcher mIdx = patronIndiceEstandar.matcher(texto);
        while (mIdx.find()) {
            String titulo = mIdx.group(1).trim() + ". " + limpiarPaginaFinal(mIdx.group(2).trim());
            encs.add(new EncabezadoMatch(titulo, mIdx.start(), mIdx.end(), "idx", 0));
        }
        Matcher mDoc = patronTituloDoc.matcher(texto);
        while (mDoc.find()) {
            encs.add(new EncabezadoMatch(mDoc.group(1).trim(), mDoc.start(), mDoc.end(), "doc", 0));
        }
        Matcher mEst = patronEstandar.matcher(texto);
        while (mEst.find()) {
            encs.add(new EncabezadoMatch(limpiarPaginaFinal(mEst.group(1).trim()), mEst.start(), mEst.end(), "estandar", 0));
        }
        Matcher mNum = patronNumerado.matcher(texto);
        while (mNum.find()) {
            String num = mNum.group(1).trim();
            String titulo = num + " " + mNum.group(2).trim();
            int profundidad = num.split("\\.").length - 1;
            encs.add(new EncabezadoMatch(titulo, mNum.start(), mNum.end(), "numerado", profundidad));
        }
        Matcher mCap = patronCapitulo.matcher(texto);
        while (mCap.find()) {
            encs.add(new EncabezadoMatch(mCap.group(1).trim(), mCap.start(), mCap.end(), "capitulo", 0));
        }

        if (encs.size() < 8) {
            Matcher mMay = patronMayusculas.matcher(texto);
            while (mMay.find()) {
                String linea = mMay.group(1).trim();
                if (linea.length() < 18) continue;
                long letras = linea.codePoints().filter(Character::isLetter).count();
                if (letras < 12) continue;
                long mayus = linea.codePoints()
                        .filter(ch -> Character.isLetter(ch) && Character.isUpperCase(ch)).count();
                if (mayus * 100 / letras < 72) continue;
                boolean duplicado = encs.stream()
                        .anyMatch(e -> Math.abs(e.posInicio - mMay.start()) < 40);
                if (!duplicado) {
                    encs.add(new EncabezadoMatch(linea, mMay.start(), mMay.end(), "mayuscula", 0));
                }
            }
        }

        encs.sort((a, b) -> Integer.compare(a.posInicio, b.posInicio));
        encs = deduplicarEncabezados(encs);
        return construirSeccionesDesdeEncabezados(texto, encs);
    }

    private List<EncabezadoMatch> deduplicarEncabezados(List<EncabezadoMatch> encs) {
        List<EncabezadoMatch> out = new ArrayList<>();
        for (EncabezadoMatch e : encs) {
            if ("estandar".equals(e.tipo)) {
                String core = normalizarTitulo(e.titulo);
                boolean dupIdx = encs.stream()
                        .anyMatch(o -> "idx".equals(o.tipo) && normalizarTitulo(o.titulo).equals(core));
                if (dupIdx) continue;
            }
            boolean dup = out.stream().anyMatch(o ->
                    Math.abs(o.posInicio - e.posInicio) < 35
                            || o.titulo.equalsIgnoreCase(e.titulo));
            if (!dup) out.add(e);
        }
        return out;
    }

    private String normalizarTitulo(String titulo) {
        if (titulo == null) return "";
        return titulo.replaceAll("^\\d+\\.\\s+", "")
                .replaceAll("\\s+\\d{1,4}\\s*$", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
    }

    private String limpiarPaginaFinal(String titulo) {
        if (titulo == null) return "";
        return titulo.replaceAll("\\s+\\d{1,4}\\s*$", "").trim();
    }

    private String limpiarCuerpoSeccion(String cuerpo, String titulo) {
        if (cuerpo == null || cuerpo.isBlank()) return cuerpo == null ? "" : cuerpo;
        String core = normalizarTitulo(titulo);
        String[] lines = cuerpo.split("\\n");
        int start = 0;
        while (start < lines.length) {
            String line = lines[start].trim();
            if (line.isEmpty()) {
                start++;
                continue;
            }
            String lineCore = normalizarTitulo(line);
            if (lineCore.equals(core) || core.contains(lineCore) || lineCore.contains(core)) {
                start++;
                continue;
            }
            break;
        }
        if (start >= lines.length) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            if (i > start) sb.append('\n');
            sb.append(lines[i]);
        }
        return sb.toString().trim();
    }

    private String generarIntroHtml(String intro) {
        if (intro == null || intro.isBlank()) return "";
        String[] lineas = intro.split("\\n+");
        StringBuilder sb = new StringBuilder();
        for (String raw : lineas) {
            String linea = raw.trim();
            if (linea.isEmpty()) continue;
            if (linea.toUpperCase().startsWith("ESTÁNDARES ") && linea.length() < 80) {
                sb.append("<h1>").append(escapeHtml(linea)).append("</h1>\n");
            } else if (linea.matches("(?i)^\\d+\\.\\s+ESTÁNDAR.*")) {
                sb.append("<h1>").append(escapeHtml(limpiarPaginaFinal(linea))).append("</h1>\n");
            } else {
                sb.append("<p>").append(escapeHtml(linea)).append("</p>\n");
            }
        }
        return sb.toString().trim();
    }

    private List<com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada> construirSeccionesDesdeEncabezados(
            String texto, List<EncabezadoMatch> encs) {
        List<com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada> resultado = new ArrayList<>();
        for (int i = 0; i < encs.size(); i++) {
            EncabezadoMatch actual = encs.get(i);
            int fin = (i + 1 < encs.size()) ? encs.get(i + 1).posInicio : texto.length();
            String nivel = switch (actual.tipo) {
                case "idx" -> "IDX";
                case "doc" -> "DOC";
                case "numerado" -> actual.titulo.split("\\s+")[0];
                case "estandar" -> "EST";
                case "capitulo" -> "CAP";
                default -> "SEC";
            };
            resultado.add(com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada.builder()
                    .nivel(nivel)
                    .titulo(actual.titulo)
                    .indiceInicio(actual.posInicio)
                    .indiceFin(fin)
                    .profundidad(actual.profundidad)
                    .build());
        }
        return resultado;
    }

    private List<com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada> filtrarSeccionesNavegacion(
            List<com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada> todas) {
        return todas.stream()
                .filter(s -> {
                    String n = s.getNivel() != null ? s.getNivel() : "";
                    if ("IDX".equals(n) || "EST".equals(n) || "CAP".equals(n)) return true;
                    if (s.getTitulo() != null && s.getTitulo().toUpperCase().contains("ESTÁNDAR DE")) return true;
                    return s.getProfundidad() <= 2 && (s.getIndiceFin() - s.getIndiceInicio()) > 120;
                })
                .limit(120)
                .collect(Collectors.toList());
    }

    /**
     * Secciones con cuerpo real del estándar (no entradas del índice al inicio del libro).
     * Si el mismo título aparece en índice y en el capítulo, conserva la de mayor extensión y con «1. OBJETIVO».
     */
    private List<com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada> filtrarSeccionesCuerpo(
            String texto, List<com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada> todas) {

        java.util.Map<String, com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada> mejor =
                new java.util.LinkedHashMap<>();

        for (com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada s : todas) {
            String titulo = s.getTitulo() != null ? s.getTitulo() : "";
            String tituloUp = titulo.toUpperCase(Locale.ROOT);
            if (!tituloUp.contains("ESTÁNDAR") && !"EST".equalsIgnoreCase(s.getNivel())) {
                continue;
            }
            if ("IDX".equalsIgnoreCase(s.getNivel())) {
                continue;
            }

            int ini = Math.max(0, s.getIndiceInicio());
            int fin = Math.min(texto.length(), Math.max(s.getIndiceFin(), ini + 1));
            String slice = texto.substring(ini, fin);
            if (!sliceTieneCuerpoEstandar(slice)) {
                continue;
            }

            String key = normalizarTitulo(titulo);
            com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada prev = mejor.get(key);
            int largo = fin - ini;
            if (prev == null || largo > (prev.getIndiceFin() - prev.getIndiceInicio())) {
                mejor.put(key, s);
            }
        }

        List<com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada> out =
                new java.util.ArrayList<>(mejor.values());
        if (out.size() >= 2) {
            return out;
        }

        return todas.stream()
                .filter(s -> {
                    String titulo = s.getTitulo() != null ? s.getTitulo() : "";
                    boolean esEstandar = titulo.toUpperCase(Locale.ROOT).contains("ESTÁNDAR DE");
                    int largo = Math.max(0, s.getIndiceFin() - s.getIndiceInicio());
                    if (!esEstandar || largo < 800) {
                        return false;
                    }
                    return sliceTieneCuerpoEstandar(texto.substring(
                            s.getIndiceInicio(),
                            Math.min(texto.length(), s.getIndiceFin())));
                })
                .collect(Collectors.toList());
    }

    private static boolean sliceTieneCuerpoEstandar(String slice) {
        if (slice == null || slice.length() < 400) {
            return false;
        }
        return EstandarConsultaHelper.contieneCuerpoEstandarReal(
                slice.substring(0, Math.min(slice.length(), 12_000)));
    }

    private List<com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada> deduplicarSeccionesPorTitulo(
            List<com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada> secciones) {
        java.util.Set<String> vistos = new java.util.HashSet<>();
        List<com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada> out = new java.util.ArrayList<>();
        secciones.stream()
                .sorted(java.util.Comparator.comparingInt(
                        com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada::getIndiceInicio))
                .forEach(s -> {
                    String key = normalizarTitulo(s.getTitulo());
                    if (key.isEmpty() || vistos.contains(key)) return;
                    vistos.add(key);
                    out.add(s);
                });
        return out;
    }

    /** @deprecated use detectarEncabezados */
    private List<com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada> detectarSecciones(String texto) {
        return detectarEncabezados(texto);
    }

    /** Clase interna para acumular matches de encabezados */
    private static class EncabezadoMatch {
        final String titulo;
        final int posInicio;
        final int posFin;
        final String tipo; // "numerado", "estandar", "capitulo", "mayuscula"
        final int profundidad;

        EncabezadoMatch(String titulo, int posInicio, int posFin, String tipo, int profundidad) {
            this.titulo = titulo;
            this.posInicio = posInicio;
            this.posFin = posFin;
            this.tipo = tipo;
            this.profundidad = profundidad;
        }
    }

    private boolean htmlTieneEstructura(String html) {
        if (html == null || html.isBlank()) return false;
        String h = html.toLowerCase();
        int heads = h.split("<h[1-4]").length - 1;
        return heads >= 2;
    }

    /** HTML útil para el chat: secciones con cuerpo, no solo índice o títulos sueltos. */
    private boolean htmlTieneEstructuraUtilParaConsulta(String html) {
        if (html == null || html.isBlank()) {
            return false;
        }
        if (html.contains("doc-seccion")) {
            int secciones = html.split("doc-seccion").length - 1;
            return secciones >= 2;
        }
        if (!htmlTieneEstructura(html)) {
            return false;
        }
        String plano = htmlATextoPlano(html);
        String preparado = prepararTextoConSaltos(plano);
        List<com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada> cuerpo =
                filtrarSeccionesCuerpo(preparado, detectarEncabezados(preparado));
        return cuerpo.size() >= 2;
    }

    private boolean esContenidoHtml(String texto) {
        if (texto == null || !texto.contains("<")) return false;
        String t = texto.toLowerCase();
        return t.contains("<p") || t.contains("<h1") || t.contains("<h2") || t.contains("<h3")
                || t.contains("<div") || t.contains("<ul") || t.contains("<ol") || t.contains("<br");
    }

    private String htmlATextoPlano(String html) {
        if (html == null || html.isBlank()) return "";
        return html
                .replaceAll("(?i)</h[1-6]>", "\n\n")
                .replaceAll("(?i)</p>", "\n\n")
                .replaceAll("(?i)</li>", "\n")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    /**
     * Convierte texto plano en HTML con índice completo + cuerpo estructurado.
     */
    private String generarTextoEstructuradoHtml(
            String texto,
            List<com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada> indiceCompleto,
            List<com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada> seccionesCuerpo) {
        if (texto == null || texto.isBlank()) return "";

        StringBuilder html = new StringBuilder();

        List<com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada> cuerpo =
                (seccionesCuerpo == null || seccionesCuerpo.isEmpty())
                        ? indiceCompleto
                        : seccionesCuerpo;

        if (cuerpo == null || cuerpo.isEmpty()) {
            html.append(parrafosHtml(texto));
            return html.toString().trim();
        }

        int inicioDoc = cuerpo.get(0).getIndiceInicio();
        if (inicioDoc > 0) {
            html.append("<section class=\"doc-intro\">\n");
            html.append(generarIntroHtml(texto.substring(0, inicioDoc)));
            html.append("</section>\n\n");
        }

        for (int i = 0; i < cuerpo.size(); i++) {
            com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada sec = cuerpo.get(i);
            String tag = etiquetaHtmlSeccion(sec);
            html.append("<section class=\"doc-seccion\">\n");
            html.append('<').append(tag).append('>')
                    .append(escapeHtml(limpiarPaginaFinal(sec.getTitulo())))
                    .append("</").append(tag).append(">\n");

            int cuerpoInicio = finDeLinea(texto, sec.getIndiceInicio());
            int nextStart = (i + 1 < cuerpo.size())
                    ? cuerpo.get(i + 1).getIndiceInicio()
                    : texto.length();
            int cuerpoFin = Math.min(nextStart, texto.length());
            if (cuerpoInicio < cuerpoFin) {
                String cuerpoTexto = limpiarCuerpoSeccion(
                        texto.substring(cuerpoInicio, cuerpoFin).trim(), sec.getTitulo());
                html.append(parrafosHtml(cuerpoTexto));
            }
            html.append("</section>\n\n");
        }
        return html.toString().trim();
    }

    private String quitarIndiceEmbebido(String html) {
        if (html == null || !html.contains("doc-indice")) return html == null ? "" : html;
        return html.replaceAll("(?is)<section\\s+class=\"doc-indice\"[\\s\\S]*?</section>\\s*", "").trim();
    }

    private String generarIndiceHtml(
            List<com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada> indice) {
        if (indice == null || indice.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<section class=\"doc-indice\">\n");
        sb.append("<h1>Índice del documento</h1>\n");
        sb.append("<p class=\"doc-indice-nota\">Generado automáticamente desde el texto extraído del PDF (")
                .append(indice.size())
                .append(" entradas detectadas).</p>\n");
        sb.append("<ol class=\"doc-indice-lista\">\n");
        for (com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada s : indice) {
            int nivel = Math.min(Math.max(s.getProfundidad(), 0), 3);
            sb.append("<li class=\"indice-nivel-").append(nivel).append("\">")
                    .append(escapeHtml(s.getTitulo()))
                    .append("</li>\n");
        }
        sb.append("</ol>\n</section>\n\n");
        return sb.toString();
    }

    private String etiquetaHtmlSeccion(
            com.fiscalizacionhse.dto.response.TextoCompletoResponse.SeccionDetectada sec) {
        String nivel = sec.getNivel() != null ? sec.getNivel().toLowerCase() : "";
        String titulo = sec.getTitulo() != null ? sec.getTitulo().toUpperCase() : "";
        if ("doc".equals(nivel) || titulo.contains("ESTÁNDARES QUE SALVAN")) {
            return "h1";
        }
        if ("est".equals(nivel) || "idx".equals(nivel) || "cap".equals(nivel)
                || titulo.contains("ESTÁNDAR DE") || titulo.matches("^\\d+\\.\\s+ESTÁNDAR.*")) {
            return "h2";
        }
        if (titulo.matches("^(OBJETIVO|ALCANCE|REQUISITOS|CONTROLES|CAPACITACIÓN).*")) {
            return "h3";
        }
        if (sec.getProfundidad() <= 0) return "h2";
        if (sec.getProfundidad() == 1) return "h3";
        return "h4";
    }

    private int finDeLinea(String texto, int pos) {
        if (pos < 0) pos = 0;
        if (pos >= texto.length()) return texto.length();
        int idx = texto.indexOf('\n', pos);
        return idx >= 0 ? idx + 1 : texto.length();
    }

    private String parrafosHtml(String texto) {
        if (texto == null || texto.isBlank()) return "";

        String[] bloques;
        if (texto.contains("\n\n")) {
            bloques = texto.split("\\n\\n+");
        } else if (texto.contains("\n")) {
            bloques = texto.split("\\n+");
        } else {
            bloques = texto.split("(?<=[.;!?])\\s+(?=[A-ZÁÉÍÓÚÑ\"(])");
        }

        StringBuilder sb = new StringBuilder();
        StringBuilder acumulador = new StringBuilder();

        for (String bloque : bloques) {
            String linea = bloque.trim().replaceAll("\\s*\\n\\s*", " ");
            if (linea.isBlank()) continue;

            if (linea.length() < 140 && !linea.endsWith(".") && !linea.endsWith(":")) {
                if (acumulador.length() > 0) {
                    sb.append("<p>").append(escapeHtml(acumulador.toString().trim())).append("</p>\n");
                    acumulador.setLength(0);
                }
                sb.append("<p>").append(escapeHtml(linea)).append("</p>\n");
                continue;
            }

            if (acumulador.length() > 0) acumulador.append(' ');
            acumulador.append(linea);

            if (acumulador.length() >= 420) {
                sb.append("<p>").append(escapeHtml(acumulador.toString().trim())).append("</p>\n");
                acumulador.setLength(0);
            }
        }

        if (acumulador.length() > 0) {
            sb.append("<p>").append(escapeHtml(acumulador.toString().trim())).append("</p>\n");
        }
        return sb.toString();
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * Actualiza el texto extraído del documento.
     * Invalida la caché para que el texto-completo se refresque.
     */
    @org.springframework.cache.annotation.CacheEvict(value = "textoCompleto", key = "#documentoId")
    @Transactional
    public DocumentoResponse actualizarTextoExtraido(Long documentoId, String nuevoTexto, Long actorUsuarioId) {
        Documento d = buscar(documentoId);
        d.setTextoExtraido(nuevoTexto);
        d = documentoRepository.save(d);

        Usuario actor = usuarioActor(actorUsuarioId);
        auditoriaService.registrar(
                actor, d.getEmpresa(), "EDITAR_TEXTO", "Documento",
                d.getId(), "Texto extraído editado manualmente", null);

        log.info("📝 Texto extraído del documento {} actualizado ({} caracteres)", documentoId, nuevoTexto.length());

        // No indexar aquí: bloqueaba Guardar 30–60 s (embeddings). El chat de estándares usa texto_extraido directo.
        // Para búsqueda semántica genérica: POST /api/ia/indexar/{documentoId} cuando el usuario lo necesite.

        return toResponse(d);
    }

    private boolean textoTraducidoValido(Documento doc) {
        return doc.getTraducido()
                && doc.getTextoTraducido() != null
                && !doc.getTextoTraducido().isBlank()
                && !doc.getTextoTraducido().contains("TRADUCCIÓN PENDIENTE");
    }

    private Documento buscar(Long id) {
        return documentoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Documento", id));
    }

    private Usuario usuarioActor(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", usuarioId));
    }

    private DocumentoResponse toResponse(Documento d) {
        long cantPuntos = puntoClaveRepository.countByDocumentoId(d.getId());
        long cantPuntosRevisados = puntoClaveRepository.countByDocumentoIdAndRevisadoTrue(d.getId());

        return DocumentoResponse.builder()
                .id(d.getId())
                .titulo(d.getTitulo())
                .descripcion(d.getDescripcion())
                .archivoNombre(d.getArchivoNombre())
                .archivoTipo(d.getArchivoTipo())
                .archivoTamano(d.getArchivoTamano())
                .idiomaOriginal(d.getIdiomaOriginal())
                .idiomaDetectado(d.getIdiomaDetectado())
                .requiereTraduccion(d.getRequiereTraduccion())
                .traducido(d.getTraducido())
                .puntosGeneradosIa(d.getPuntosGeneradosIa())
                .estadoProcesamiento(d.getEstadoProcesamiento())
                .errorProcesamiento(d.getErrorProcesamiento())
                .cantidadPuntos(cantPuntos)
                .cantidadPuntosRevisados(cantPuntosRevisados)
                .empresaId(d.getEmpresa().getId())
                .empresaNombre(d.getEmpresa().getNombre())
                .subidoPorId(d.getSubidoPor().getId())
                .subidoPorNombre(d.getSubidoPor().getNombre())
                .createdAt(d.getCreatedAt())
                .build();
    }

    private PuntoClaveResponse toPuntoClaveResponse(PuntoClave p) {
        return PuntoClaveResponse.builder()
                .id(p.getId())
                .contenido(p.getContenido())
                .titulo(p.getTitulo())
                .tema(p.getTema())
                .codigo(p.getCodigo())
                .tipo(p.getTipo())
                .orden(p.getOrden())
                .esIa(p.getEsIa())
                .confianzaIa(p.getConfianzaIa())
                .revisado(p.getRevisado())
                .documentoId(p.getDocumento().getId())
                .creadoPorId(p.getCreadoPor() != null ? p.getCreadoPor().getId() : null)
                .creadoPorNombre(p.getCreadoPor() != null ? p.getCreadoPor().getNombre() : "IA")
                .createdAt(p.getCreatedAt())
                .build();
    }
}
