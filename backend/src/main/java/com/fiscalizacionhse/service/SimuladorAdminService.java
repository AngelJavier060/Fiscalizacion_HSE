package com.fiscalizacionhse.service;

import com.fiscalizacionhse.dto.request.SsoConfiguracionRequest;
import com.fiscalizacionhse.dto.request.SsoPreguntaRequest;
import com.fiscalizacionhse.dto.request.SsoTemaRequest;
import com.fiscalizacionhse.dto.response.SsoCoberturaResponse;
import com.fiscalizacionhse.dto.response.SsoConfiguracionResponse;
import com.fiscalizacionhse.dto.response.SsoEstadisticasResponse;
import com.fiscalizacionhse.dto.response.SsoImportPreviewResponse;
import com.fiscalizacionhse.dto.response.SsoPreguntaAdminResponse;
import com.fiscalizacionhse.dto.response.SsoTemaResponse;
import com.fiscalizacionhse.exception.BadRequestException;
import com.fiscalizacionhse.exception.ConflictException;
import com.fiscalizacionhse.exception.ResourceNotFoundException;
import com.fiscalizacionhse.model.SsoConfiguracion;
import com.fiscalizacionhse.model.SsoIntento;
import com.fiscalizacionhse.model.SsoOpcion;
import com.fiscalizacionhse.model.SsoPregunta;
import com.fiscalizacionhse.model.SsoTema;
import com.fiscalizacionhse.model.Usuario;
import com.fiscalizacionhse.model.enums.RolUsuario;
import com.fiscalizacionhse.repository.SsoConfiguracionRepository;
import com.fiscalizacionhse.repository.SsoIntentoRepository;
import com.fiscalizacionhse.repository.SsoIntentoRespuestaRepository;
import com.fiscalizacionhse.repository.SsoOpcionRepository;
import com.fiscalizacionhse.repository.SsoPreguntaRepository;
import com.fiscalizacionhse.repository.SsoRotacionUsuarioRepository;
import com.fiscalizacionhse.repository.SsoTemaRepository;
import com.fiscalizacionhse.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SimuladorAdminService {

    private final SsoTemaRepository temaRepository;
    private final SsoPreguntaRepository preguntaRepository;
    private final SsoOpcionRepository opcionRepository;
    private final SsoIntentoRespuestaRepository intentoRespuestaRepository;
    private final SsoIntentoRepository intentoRepository;
    private final SsoRotacionUsuarioRepository rotacionRepository;
    private final UsuarioRepository usuarioRepository;
    private final SsoConfiguracionRepository configuracionRepository;

    private static final List<Integer> TIEMPOS_PERMITIDOS = SsoBancoReglas.TIEMPOS;
    private static final List<Integer> NOTAS_PERMITIDAS = List.of(80, 90);
    private static final List<Integer> BANCOS_PERMITIDOS = SsoBancoReglas.BANCOS;
    private static final long SUSTENTO_MAX_BYTES = 15L * 1024 * 1024;
    private static final Set<String> SUSTENTO_EXT = Set.of(".pdf", ".jpg", ".jpeg", ".png", ".webp", ".gif");
    private static final Set<String> SUSTENTO_MIME = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    public record SustentoArchivo(String nombre, String tipoMime, Resource resource) {}

    public SsoConfiguracionResponse obtenerConfiguracion() {
        int banco = SsoBancoReglas.normalizarBanco(entero("preguntas_por_banco", 20));
        int minutos = SsoBancoReglas.tiempoMin(banco);
        return SsoConfiguracionResponse.builder()
                .tiempoExamenMin(minutos)
                .notaMinima(entero("nota_minima", 90))
                .preguntasPorBanco(banco)
                .tiemposPermitidos(TIEMPOS_PERMITIDOS)
                .notasPermitidas(NOTAS_PERMITIDAS)
                .bancosPermitidos(BANCOS_PERMITIDOS)
                .build();
    }

    @Transactional
    public SsoConfiguracionResponse guardarConfiguracion(SsoConfiguracionRequest request) {
        int banco = request.getPreguntasPorBanco() == null
                ? SsoBancoReglas.normalizarBanco(entero("preguntas_por_banco", 20))
                : SsoBancoReglas.normalizarBanco(request.getPreguntasPorBanco());
        if (!BANCOS_PERMITIDOS.contains(banco)) {
            throw new BadRequestException("El banco debe ser de 20, 40, 60 preguntas o todas");
        }
        int minutos = SsoBancoReglas.tiempoMin(banco);
        guardarClave("preguntas_por_banco", String.valueOf(banco));
        guardarClave("tiempo_examen_min", String.valueOf(minutos));
        guardarClave("tiempo_20_min", String.valueOf(minutos));
        guardarClave("tiempo_40_min", String.valueOf(minutos));
        if (request.getNotaMinima() != null) {
            if (!NOTAS_PERMITIDAS.contains(request.getNotaMinima())) {
                throw new BadRequestException("La nota para avanzar debe ser 80 o 90");
            }
            guardarClave("nota_minima", String.valueOf(request.getNotaMinima()));
        }
        return obtenerConfiguracion();
    }

    @Transactional(readOnly = true)
    public SsoCoberturaResponse cobertura() {
        List<SsoPregunta> activas = preguntaRepository.findByActivoTrue();
        Map<Long, Long> apariciones = aLongMap(intentoRespuestaRepository.aparicionesPorPregunta(LocalDateTime.of(2000, 1, 1, 0, 0)));
        List<SsoCoberturaResponse.PreguntaSinUso> sinUso = activas.stream()
                .filter(p -> apariciones.getOrDefault(p.getId(), 0L) == 0)
                .map(p -> SsoCoberturaResponse.PreguntaSinUso.builder()
                        .id(p.getId())
                        .enunciado(p.getEnunciado())
                        .temaNombre(p.getTema() == null ? "—" : p.getTema().getNombre())
                        .vecesSalio(0)
                        .build())
                .toList();
        long repetidas = apariciones.values().stream().filter(v -> v > 1).count();
        int banco = SsoBancoReglas.normalizarBanco(entero("preguntas_por_banco", 20));

        List<SsoCoberturaResponse.UsuarioCobertura> usuarios = new ArrayList<>();
        for (Usuario u : usuarioRepository.findByRolAndActivoTrueOrderByNombreAsc(RolUsuario.USUARIO)) {
            int vuelta = u.getVueltaActual() == null ? 1 : u.getVueltaActual();
            long cumplidas = rotacionRepository.countByUsuarioIdAndVuelta(u.getId(), vuelta);
            long total = activas.size();
            int pct = total == 0 ? 0 : (int) Math.min(100, Math.round((cumplidas * 100.0) / total));
            SsoIntento ultimo = intentoRepository.findByUsuarioIdOrderByFechaInicioDesc(u.getId()).stream()
                    .filter(i -> "finalizado".equals(i.getEstado()) && !"falladas".equals(i.getModo()))
                    .findFirst().orElse(null);
            boolean debeRepetir = ultimo != null && !Boolean.TRUE.equals(ultimo.getAprobado());
            usuarios.add(SsoCoberturaResponse.UsuarioCobertura.builder()
                    .id(u.getId())
                    .nombre(u.getNombre())
                    .email(u.getEmail())
                    .empresaNombre(u.getEmpresa() == null ? "—" : u.getEmpresa().getNombre())
                    .vuelta(vuelta)
                    .cumplidas(cumplidas)
                    .total(total)
                    .porcentaje(pct)
                    .debeRepetir(debeRepetir)
                    .ultimaNota(ultimo == null ? null : ultimo.getNota())
                    .ultimoAlcance(ultimo == null ? null
                            : (ultimo.getTema() == null ? "General" : ultimo.getTema().getNombre()))
                    .build());
        }

        return SsoCoberturaResponse.builder()
                .totalPreguntasActivas(activas.size())
                .preguntasNuncaSalieron(sinUso.size())
                .preguntasEnAlMenosUnExamen(activas.size() - sinUso.size())
                .preguntasRepetidas(repetidas)
                .notaAvance(entero("nota_minima", 90))
                .preguntasPorBanco(banco)
                .tiempoExamenMin(SsoBancoReglas.tiempoMin(banco))
                .usuarios(usuarios)
                .preguntasSinUso(sinUso)
                .build();
    }

    @Transactional(readOnly = true)
    public SsoEstadisticasResponse estadisticas(Integer dias) {
        int ventana = dias == null ? 0 : Math.max(0, dias);
        LocalDateTime desde = ventana > 0
                ? LocalDateTime.now().minusDays(ventana)
                : LocalDateTime.of(2000, 1, 1, 0, 0);

        List<SsoPregunta> activas = preguntaRepository.findActivasConDetalle();
        Map<Long, long[]> acierto = new HashMap<>();
        long respuestas = 0;
        long aciertosTotales = 0;
        for (Object[] row : intentoRespuestaRepository.statsAciertoPorPregunta(desde)) {
            Long id = ((Number) row[0]).longValue();
            long intentos = ((Number) row[1]).longValue();
            long ok = row[2] == null ? 0 : ((Number) row[2]).longValue();
            acierto.put(id, new long[]{intentos, ok});
            respuestas += intentos;
            aciertosTotales += ok;
        }
        Map<Long, Long> apariciones = aLongMap(intentoRespuestaRepository.aparicionesPorPregunta(desde));
        Map<Long, Map<Long, Long>> votos = new HashMap<>();
        for (Object[] row : intentoRespuestaRepository.votosPorOpcion(desde)) {
            Long pid = ((Number) row[0]).longValue();
            Long oid = ((Number) row[1]).longValue();
            long n = ((Number) row[2]).longValue();
            votos.computeIfAbsent(pid, k -> new HashMap<>()).put(oid, n);
        }

        List<SsoEstadisticasResponse.PreguntaStat> stats = new ArrayList<>();
        Map<Long, long[]> porTema = new HashMap<>();
        Map<Long, String> nombreTema = new HashMap<>();
        int faciles = 0;
        int intermedias = 0;
        int criticas = 0;
        long evaluadas = 0;
        long nunca = 0;
        long repetidas = 0;
        SsoEstadisticasResponse.PreguntaStat masReprobada = null;

        for (SsoPregunta p : activas) {
            long[] acc = acierto.getOrDefault(p.getId(), new long[]{0, 0});
            long intentos = acc[0];
            long ok = acc[1];
            int pct = intentos == 0 ? 0 : (int) Math.round((ok * 100.0) / intentos);
            long veces = apariciones.getOrDefault(p.getId(), 0L);
            boolean sinUso = veces == 0;
            if (sinUso) {
                nunca += 1;
            } else {
                evaluadas += 1;
                if (pct >= 75) {
                    faciles += 1;
                } else if (pct >= 50) {
                    intermedias += 1;
                } else {
                    criticas += 1;
                }
            }
            if (veces > 1) {
                repetidas += 1;
            }

            List<SsoOpcion> ops = new ArrayList<>(p.getOpciones() == null ? List.of() : p.getOpciones());
            ops.sort(Comparator.comparing(SsoOpcion::getId));
            Map<Long, Long> votosPreg = votos.getOrDefault(p.getId(), Map.of());
            List<SsoEstadisticasResponse.OpcionStat> opStats = new ArrayList<>();
            String distractor = null;
            int distractorPct = 0;
            long maxWrong = 0;
            for (SsoOpcion o : ops) {
                long v = votosPreg.getOrDefault(o.getId(), 0L);
                int opPct = intentos == 0 ? 0 : (int) Math.round((v * 100.0) / intentos);
                opStats.add(SsoEstadisticasResponse.OpcionStat.builder()
                        .id(o.getId())
                        .texto(o.getTexto())
                        .correcta(Boolean.TRUE.equals(o.getEsCorrecta()))
                        .votos(v)
                        .pct(opPct)
                        .build());
                if (!Boolean.TRUE.equals(o.getEsCorrecta()) && v > maxWrong) {
                    maxWrong = v;
                    distractor = o.getTexto();
                    distractorPct = opPct;
                }
            }

            Long temaId = p.getTema() == null ? null : p.getTema().getId();
            String temaNombre = p.getTema() == null ? "—" : p.getTema().getNombre();
            nombreTema.put(temaId, temaNombre);
            long[] tAcc = porTema.computeIfAbsent(temaId, k -> new long[]{0, 0});
            tAcc[0] += intentos;
            tAcc[1] += ok;

            SsoEstadisticasResponse.PreguntaStat st = SsoEstadisticasResponse.PreguntaStat.builder()
                    .id(p.getId())
                    .enunciado(p.getEnunciado())
                    .temaId(temaId)
                    .temaNombre(temaNombre)
                    .intentos(intentos)
                    .aciertos(ok)
                    .aciertoPct(pct)
                    .vecesSalio(veces)
                    .nuncaSalio(sinUso)
                    .repetida(veces > 1)
                    .distractorTexto(distractor)
                    .distractorPct(distractorPct)
                    .opciones(opStats)
                    .build();
            stats.add(st);
            if (!sinUso && intentos > 0 && (masReprobada == null || pct < masReprobada.getAciertoPct()
                    || (pct == masReprobada.getAciertoPct() && intentos > masReprobada.getIntentos()))) {
                masReprobada = st;
            }
        }

        stats.sort(Comparator
                .comparing(SsoEstadisticasResponse.PreguntaStat::isNuncaSalio)
                .thenComparingInt(SsoEstadisticasResponse.PreguntaStat::getAciertoPct)
                .thenComparing(SsoEstadisticasResponse.PreguntaStat::getIntentos, Comparator.reverseOrder()));

        List<SsoEstadisticasResponse.TemaStat> temas = porTema.entrySet().stream()
                .map(e -> {
                    long tot = e.getValue()[0];
                    long ok = e.getValue()[1];
                    return SsoEstadisticasResponse.TemaStat.builder()
                            .id(e.getKey())
                            .nombre(nombreTema.getOrDefault(e.getKey(), "—"))
                            .intentos(tot)
                            .aciertoPct(tot == 0 ? 0 : (int) Math.round((ok * 100.0) / tot))
                            .build();
                })
                .sorted(Comparator.comparingInt(SsoEstadisticasResponse.TemaStat::getAciertoPct).reversed())
                .toList();

        int coberturaPct = activas.isEmpty() ? 0 : (int) Math.round((evaluadas * 100.0) / activas.size());
        return SsoEstadisticasResponse.builder()
                .dias(ventana)
                .tasaGlobalAcierto(respuestas == 0 ? 0 : (int) Math.round((aciertosTotales * 100.0) / respuestas))
                .respuestasContadas(respuestas)
                .preguntasActivas(activas.size())
                .preguntasEvaluadas(evaluadas)
                .preguntasNuncaSalieron(nunca)
                .preguntasRepetidas(repetidas)
                .preguntasCriticas(criticas)
                .coberturaPct(coberturaPct)
                .masReprobadaId(masReprobada == null ? null : masReprobada.getId())
                .masReprobadaEnunciado(masReprobada == null ? null : masReprobada.getEnunciado())
                .masReprobadaTema(masReprobada == null ? null : masReprobada.getTemaNombre())
                .masReprobadaAcierto(masReprobada == null ? 0 : masReprobada.getAciertoPct())
                .masReprobadaIntentos(masReprobada == null ? 0 : masReprobada.getIntentos())
                .faciles(faciles)
                .intermedias(intermedias)
                .criticas(criticas)
                .porTema(temas)
                .preguntas(stats)
                .build();
    }

    private Map<Long, Long> aLongMap(List<Object[]> rows) {
        Map<Long, Long> out = new HashMap<>();
        for (Object[] row : rows) {
            out.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return out;
    }

    public List<SsoTemaResponse> listarTemas() {
        return temaRepository.findAllByOrderByNombreAsc().stream()
                .map(this::toTema)
                .toList();
    }

    @Transactional
    public SsoTemaResponse crearTema(SsoTemaRequest request) {
        String nombre = request.getNombre().trim();
        if (temaRepository.existsByNombreIgnoreCase(nombre)) {
            throw new ConflictException("Ya existe un tema con ese nombre");
        }
        SsoTema tema = SsoTema.builder()
                .nombre(nombre)
                .activo(request.getActivo() == null || request.getActivo())
                .build();
        return toTema(temaRepository.save(tema));
    }

    @Transactional
    public SsoTemaResponse actualizarTema(Long id, SsoTemaRequest request) {
        SsoTema tema = temaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tema no encontrado"));
        String nombre = request.getNombre().trim();
        if (temaRepository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw new ConflictException("Ya existe un tema con ese nombre");
        }
        tema.setNombre(nombre);
        if (request.getActivo() != null) {
            tema.setActivo(request.getActivo());
        }
        return toTema(tema);
    }

    public List<SsoPreguntaAdminResponse> listarPreguntas(String q, Long temaId, Boolean activo) {
        String query = (q == null || q.isBlank()) ? "" : q.trim();
        return preguntaRepository.buscar(query, temaId, activo).stream()
                .map(this::toAdmin)
                .toList();
    }

    public SsoPreguntaAdminResponse obtenerPregunta(Long id) {
        return toAdmin(preguntaRepository.findDetalleById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pregunta no encontrada")));
    }

    @Transactional
    public SsoPreguntaAdminResponse crearPregunta(SsoPreguntaRequest request, Long usuarioId) {
        validarOpciones(request.getOpciones());
        validarExplicacion(request.getExplicacion());
        SsoTema tema = getTema(request.getTemaId());
        Usuario autor = usuarioRepository.findById(usuarioId).orElse(null);

        SsoPregunta pregunta = SsoPregunta.builder()
                .tema(tema)
                .enunciado(request.getEnunciado().trim())
                .explicacion(blankToNull(request.getExplicacion()))
                .fuente(blankToNull(request.getFuente()))
                .sustentoTexto(blankToNull(request.getSustentoTexto()))
                .activo(request.getActivo() == null || request.getActivo())
                .creadoPor(autor)
                .fechaCreacion(LocalDateTime.now())
                .fechaEdicion(LocalDateTime.now())
                .build();

        for (SsoPreguntaRequest.SsoOpcionRequest op : request.getOpciones()) {
            pregunta.getOpciones().add(buildOpcion(pregunta, op));
        }
        return toAdmin(preguntaRepository.save(pregunta));
    }

    @Transactional
    public SsoPreguntaAdminResponse actualizarPregunta(Long id, SsoPreguntaRequest request) {
        validarOpciones(request.getOpciones());
        validarExplicacion(request.getExplicacion());
        SsoPregunta pregunta = preguntaRepository.findDetalleById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pregunta no encontrada"));
        pregunta.setTema(getTema(request.getTemaId()));
        pregunta.setEnunciado(request.getEnunciado().trim());
        pregunta.setExplicacion(blankToNull(request.getExplicacion()));
        pregunta.setFuente(blankToNull(request.getFuente()));
        pregunta.setSustentoTexto(blankToNull(request.getSustentoTexto()));
        if (request.getActivo() != null) {
            pregunta.setActivo(request.getActivo());
        }
        pregunta.setFechaEdicion(LocalDateTime.now());

        List<SsoOpcion> actuales = new ArrayList<>(pregunta.getOpciones());
        List<Long> idsKeep = request.getOpciones().stream()
                .map(SsoPreguntaRequest.SsoOpcionRequest::getId)
                .filter(Objects::nonNull)
                .toList();

        for (SsoOpcion existente : actuales) {
            if (!idsKeep.contains(existente.getId())) {
                if (intentoRespuestaRepository.existsByOpcionElegidaId(existente.getId())) {
                    throw new ConflictException(
                            "No se puede quitar una opción que ya fue usada en un examen. Desactive la pregunta.");
                }
                pregunta.getOpciones().remove(existente);
            }
        }

        for (SsoPreguntaRequest.SsoOpcionRequest op : request.getOpciones()) {
            if (op.getId() == null) {
                pregunta.getOpciones().add(buildOpcion(pregunta, op));
            } else {
                SsoOpcion existente = actuales.stream()
                        .filter(o -> o.getId().equals(op.getId()))
                        .findFirst()
                        .orElseThrow(() -> new BadRequestException("Opción no pertenece a la pregunta"));
                existente.setTexto(op.getTexto().trim());
                existente.setEsCorrecta(Boolean.TRUE.equals(op.getEsCorrecta()));
                existente.setArgumento(null);
            }
        }
        return toAdmin(pregunta);
    }

    @Transactional
    public SsoPreguntaAdminResponse guardarSustento(Long id, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Seleccione un PDF o una imagen como evidencia");
        }
        if (file.getSize() > SUSTENTO_MAX_BYTES) {
            throw new BadRequestException("El archivo no puede superar 15 MB");
        }
        String original = file.getOriginalFilename() == null ? "sustento" : file.getOriginalFilename();
        String nombre = sanitizarNombre(original);
        String lower = nombre.toLowerCase(Locale.ROOT);
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        boolean extOk = SUSTENTO_EXT.stream().anyMatch(lower::endsWith);
        boolean mimeOk = SUSTENTO_MIME.contains(mime);
        if (!extOk && !mimeOk) {
            throw new BadRequestException("Solo se aceptan PDF, JPG, PNG, WEBP o GIF");
        }
        if (mime.isBlank()) {
            mime = lower.endsWith(".pdf") ? "application/pdf" : "application/octet-stream";
        }

        SsoPregunta pregunta = preguntaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pregunta no encontrada"));
        borrarArchivoFisico(pregunta.getSustentoRuta());
        try {
            Path dest = dirSustento().resolve(UUID.randomUUID() + "_" + nombre);
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
            pregunta.setSustentoNombre(recortar(nombre, 255));
            pregunta.setSustentoTipo(recortar(mime, 120));
            pregunta.setSustentoRuta(dest.toString());
            pregunta.setFechaEdicion(LocalDateTime.now());
            return toAdmin(preguntaRepository.save(pregunta));
        } catch (IOException e) {
            throw new BadRequestException("No se pudo guardar el archivo de sustento");
        }
    }

    @Transactional
    public SsoPreguntaAdminResponse eliminarSustento(Long id) {
        SsoPregunta pregunta = preguntaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pregunta no encontrada"));
        borrarArchivoFisico(pregunta.getSustentoRuta());
        pregunta.setSustentoNombre(null);
        pregunta.setSustentoTipo(null);
        pregunta.setSustentoRuta(null);
        pregunta.setFechaEdicion(LocalDateTime.now());
        return toAdmin(pregunta);
    }

    public SustentoArchivo leerSustento(Long id) {
        SsoPregunta pregunta = preguntaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pregunta no encontrada"));
        if (pregunta.getSustentoRuta() == null || pregunta.getSustentoRuta().isBlank()) {
            throw new ResourceNotFoundException("Esta pregunta no tiene evidencia adjunta");
        }
        Path file = Path.of(pregunta.getSustentoRuta()).toAbsolutePath().normalize();
        Path base = dirSustento().toAbsolutePath().normalize();
        if (!file.startsWith(base) || !Files.isRegularFile(file)) {
            throw new ResourceNotFoundException("El archivo de sustento no está disponible");
        }
        String mime = pregunta.getSustentoTipo() == null || pregunta.getSustentoTipo().isBlank()
                ? "application/octet-stream"
                : pregunta.getSustentoTipo();
        String nombre = pregunta.getSustentoNombre() == null || pregunta.getSustentoNombre().isBlank()
                ? file.getFileName().toString()
                : pregunta.getSustentoNombre();
        return new SustentoArchivo(nombre, mime, new FileSystemResource(file));
    }

    @Transactional
    public SsoPreguntaAdminResponse togglePregunta(Long id) {
        SsoPregunta pregunta = preguntaRepository.findDetalleById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pregunta no encontrada"));
        pregunta.setActivo(!Boolean.TRUE.equals(pregunta.getActivo()));
        pregunta.setFechaEdicion(LocalDateTime.now());
        return toAdmin(pregunta);
    }

    private static final String[] COLUMNAS_EXCEL = {
            "id", "tema", "enunciado", "opcion_a", "opcion_b", "opcion_c", "opcion_d", "opcion_e",
            "correcta", "explicacion", "fuente", "activo"
    };

    /** Plantilla vacía con la cabecera, una fila de ejemplo y una hoja de instrucciones. */
    public byte[] plantillaExcel() {
        List<String[]> filas = new ArrayList<>();
        filas.add(new String[]{
                "", "Trabajos en altura", "¿A partir de qué altura se considera trabajo en altura?",
                "1,0 m", "1,8 m", "2,5 m", "3,0 m", "", "B",
                "Se considera trabajo en altura desde 1,8 m sobre el nivel inferior.",
                "DS 024-2016-EM", "SI"
        });
        return construirExcel(filas);
    }

    /** Todo el banco de preguntas en el mismo formato que acepta la importación. */
    public byte[] exportarExcel() {
        List<SsoPregunta> preguntas = new ArrayList<>(preguntaRepository.buscar("", null, null));
        preguntas.sort(Comparator.comparing(SsoPregunta::getId));
        List<String[]> filas = new ArrayList<>();
        for (SsoPregunta p : preguntas) {
            List<SsoOpcion> ops = opcionesOrdenadas(p);
            String[] f = new String[COLUMNAS_EXCEL.length];
            f[0] = String.valueOf(p.getId());
            f[1] = p.getTema().getNombre();
            f[2] = p.getEnunciado();
            String correcta = "";
            for (int i = 0; i < 5; i++) {
                f[3 + i] = i < ops.size() ? ops.get(i).getTexto() : "";
                if (i < ops.size() && Boolean.TRUE.equals(ops.get(i).getEsCorrecta())) {
                    correcta = String.valueOf((char) ('A' + i));
                }
            }
            f[8] = correcta;
            f[9] = p.getExplicacion() == null ? "" : p.getExplicacion();
            f[10] = p.getFuente() == null ? "" : p.getFuente();
            f[11] = Boolean.TRUE.equals(p.getActivo()) ? "SI" : "NO";
            filas.add(f);
        }
        return construirExcel(filas);
    }

    private byte[] construirExcel(List<String[]> filas) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Font negrita = wb.createFont();
            negrita.setBold(true);
            negrita.setColor(IndexedColors.WHITE.getIndex());
            CellStyle cab = wb.createCellStyle();
            cab.setFont(negrita);
            cab.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            cab.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle texto = wb.createCellStyle();
            texto.setWrapText(true);
            texto.setVerticalAlignment(VerticalAlignment.TOP);

            Sheet hoja = wb.createSheet("Preguntas");
            Row h = hoja.createRow(0);
            for (int c = 0; c < COLUMNAS_EXCEL.length; c++) {
                Cell cell = h.createCell(c);
                cell.setCellValue(COLUMNAS_EXCEL[c]);
                cell.setCellStyle(cab);
            }
            for (int r = 0; r < filas.size(); r++) {
                Row row = hoja.createRow(r + 1);
                String[] f = filas.get(r);
                for (int c = 0; c < f.length; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(f[c] == null ? "" : f[c]);
                    cell.setCellStyle(texto);
                }
            }
            int[] anchos = {8, 24, 60, 30, 30, 30, 30, 30, 10, 50, 30, 8};
            for (int c = 0; c < anchos.length; c++) {
                hoja.setColumnWidth(c, anchos[c] * 256);
            }
            hoja.createFreezePane(0, 1);
            hoja.setAutoFilter(new CellRangeAddress(0, Math.max(filas.size(), 1), 0, COLUMNAS_EXCEL.length - 1));

            Sheet ayuda = wb.createSheet("Instrucciones");
            String[] lineas = {
                    "Cómo usar este archivo",
                    "",
                    "id: déjalo vacío para crear una pregunta nueva. Si tiene número, se actualiza esa pregunta. No lo cambies.",
                    "tema: nombre del tema. Si no existe, se crea.",
                    "enunciado: el texto de la pregunta (obligatorio).",
                    "opcion_a a opcion_e: alternativas. A y B son obligatorias; C, D y E opcionales.",
                    "correcta: letra de la alternativa correcta (A, B, C, D o E).",
                    "explicacion: por qué la respuesta es la correcta.",
                    "fuente: norma o documento de referencia.",
                    "activo: SI o NO. Vacío deja la pregunta como está (o activa si es nueva).",
                    "",
                    "Para eliminar una pregunta del examen, pon activo = NO (no borres la fila).",
                    "No cambies los nombres de la primera fila de la hoja Preguntas.",
            };
            for (int i = 0; i < lineas.length; i++) {
                ayuda.createRow(i).createCell(0).setCellValue(lineas[i]);
            }
            ayuda.setColumnWidth(0, 110 * 256);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el Excel", e);
        }
    }

    public SsoImportPreviewResponse previsualizarImportacion(MultipartFile file) {
        List<List<String>> rows = leerFilas(file);
        if (rows.isEmpty()) {
            throw new BadRequestException("El archivo está vacío");
        }
        int filaCab = filaCabecera(rows);
        List<String> header = rows.get(Math.max(filaCab, 0)).stream().map(this::normHeader).toList();
        int iId = idx(header, "id");
        int iTema = idx(header, "tema");
        int iEnun = idx(header, "enunciado");
        int iA = idx(header, "opcion_a");
        int iB = idx(header, "opcion_b");
        int iC = idx(header, "opcion_c");
        int iD = idx(header, "opcion_d");
        int iE = idx(header, "opcion_e");
        int iCorr = idx(header, "correcta");
        int iExp = idx(header, "explicacion");
        int iFue = idx(header, "fuente");
        int iAct = idx(header, "activo");
        if (iTema < 0 || iEnun < 0 || iA < 0 || iB < 0 || iCorr < 0) {
            throw new BadRequestException(
                    "Cabecera inválida. Se espera: id,tema,enunciado,opcion_a,opcion_b,opcion_c,opcion_d,opcion_e,correcta,explicacion,fuente,activo. "
                            + "Se encontró: " + String.join(", ", rows.get(Math.max(filaCab, 0)).stream()
                                    .filter(s -> s != null && !s.isBlank()).toList())
                            + ". Descarga la plantilla para ver el formato.");
        }

        List<SsoPregunta> banco = preguntaRepository.buscar("", null, null);
        Map<Long, SsoPregunta> porId = new HashMap<>();
        Map<String, List<SsoPregunta>> porEnunciado = new HashMap<>();
        for (SsoPregunta p : banco) {
            porId.put(p.getId(), p);
            porEnunciado.computeIfAbsent(p.getEnunciado().trim().toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(p);
        }

        List<SsoImportPreviewResponse.Fila> filas = new ArrayList<>();
        Set<Long> idsEnArchivo = new HashSet<>();
        Set<String> temasNuevosVistos = new HashSet<>();
        int validas = 0;
        int errores = 0;
        int duplicados = 0;
        int nuevas = 0;
        int actualizadas = 0;
        int sinCambios = 0;

        for (int i = filaCab + 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.stream().allMatch(s -> s == null || s.isBlank())) {
                continue;
            }
            String idTxt = cell(row, iId);
            String tema = cell(row, iTema);
            String enunciado = cell(row, iEnun);
            String a = cell(row, iA);
            String b = cell(row, iB);
            String c = cell(row, iC);
            String d = cell(row, iD);
            String e = cell(row, iE);
            String correcta = cell(row, iCorr).toUpperCase(Locale.ROOT);
            String explicacion = cell(row, iExp);
            String fuente = cell(row, iFue);
            String activoTxt = cell(row, iAct);

            List<String> errs = new ArrayList<>();
            if (tema.isBlank()) errs.add("tema obligatorio");
            if (enunciado.isBlank()) errs.add("enunciado obligatorio");
            if (a.isBlank()) errs.add("opcion_a obligatoria");
            if (b.isBlank()) errs.add("opcion_b obligatoria");
            if (correcta.isBlank()) errs.add("correcta obligatoria");

            String[] ops = {a, b, c, d, e};
            if (!correcta.isBlank()) {
                int idxCorr = correcta.charAt(0) - 'A';
                if (correcta.length() != 1 || idxCorr < 0 || idxCorr > 4 || ops[idxCorr].isBlank()) {
                    errs.add("correcta debe ser A-E y tener texto en esa opción");
                }
            }

            Boolean activo = parseActivo(activoTxt);
            if (!activoTxt.isBlank() && activo == null) {
                errs.add("activo debe ser SI o NO");
            }

            // Busca la pregunta a actualizar: por id o, si no hay id, por enunciado idéntico.
            SsoPregunta existente = null;
            boolean duplicado = false;
            if (!idTxt.isBlank()) {
                Long id = parseId(idTxt);
                existente = id == null ? null : porId.get(id);
                if (existente == null) {
                    errs.add("no existe una pregunta con id " + idTxt + " (déjalo vacío para crearla)");
                }
            } else if (!enunciado.isBlank()) {
                List<SsoPregunta> iguales = porEnunciado.getOrDefault(enunciado.toLowerCase(Locale.ROOT), List.of());
                if (iguales.size() == 1) {
                    existente = iguales.get(0);
                } else if (iguales.size() > 1) {
                    duplicado = true;
                    errs.add("hay varias preguntas con este enunciado; indica el id");
                }
            }
            if (existente != null && !idsEnArchivo.add(existente.getId())) {
                errs.add("la pregunta id " + existente.getId() + " aparece más de una vez en el archivo");
            }
            if (existente != null) {
                List<SsoOpcion> actuales = opcionesOrdenadas(existente);
                for (int k = 0; k < actuales.size() && k < ops.length; k++) {
                    if (ops[k].isBlank() && intentoRespuestaRepository.existsByOpcionElegidaId(actuales.get(k).getId())) {
                        errs.add("la opción " + (char) ('A' + k) + " ya se usó en exámenes y no se puede vaciar");
                    }
                }
                if (actuales.size() > ops.length) {
                    errs.add("la pregunta tiene más de 5 opciones; edítala desde la pantalla");
                }
            }

            boolean temaNuevo = !tema.isBlank() && temaRepository.findByNombreIgnoreCase(tema).isEmpty();
            if (temaNuevo) temasNuevosVistos.add(tema.toLowerCase(Locale.ROOT));
            if (duplicado) duplicados++;

            String accion;
            boolean ok = errs.isEmpty();
            if (!ok) {
                accion = "error";
                errores++;
            } else {
                validas++;
                if (existente == null) {
                    accion = "crear";
                    nuevas++;
                } else if (sinCambios(existente, tema, enunciado, ops, correcta, explicacion, fuente, activo)) {
                    accion = "sin_cambios";
                    sinCambios++;
                } else {
                    accion = "actualizar";
                    actualizadas++;
                }
            }

            filas.add(SsoImportPreviewResponse.Fila.builder()
                    .fila(i + 1)
                    .id(existente == null ? null : existente.getId())
                    .accion(accion)
                    .ok(ok)
                    .errores(errs)
                    .temaNuevo(temaNuevo)
                    .posibleDuplicado(duplicado)
                    .tema(tema)
                    .enunciado(enunciado)
                    .opcionA(a)
                    .opcionB(b)
                    .opcionC(c)
                    .opcionD(d)
                    .opcionE(e)
                    .correcta(correcta)
                    .explicacion(explicacion)
                    .fuente(fuente)
                    .activo(activo)
                    .build());
        }

        int ausentes = (int) banco.stream()
                .filter(p -> Boolean.TRUE.equals(p.getActivo()) && !idsEnArchivo.contains(p.getId()))
                .count();

        return SsoImportPreviewResponse.builder()
                .totalFilas(filas.size())
                .validas(validas)
                .conError(errores)
                .temasNuevos(temasNuevosVistos.size())
                .posiblesDuplicados(duplicados)
                .nuevas(nuevas)
                .actualizadas(actualizadas)
                .sinCambios(sinCambios)
                .ausentes(ausentes)
                .filas(filas)
                .build();
    }

    @Transactional
    public SsoImportPreviewResponse confirmarImportacion(
            SsoImportPreviewResponse preview, boolean desactivarAusentes, Long usuarioId) {
        if (preview == null || preview.getFilas() == null) {
            throw new BadRequestException("No hay filas para importar");
        }
        Usuario autor = usuarioRepository.findById(usuarioId).orElse(null);
        Set<Long> idsEnArchivo = new HashSet<>();
        int nuevas = 0;
        int actualizadas = 0;
        for (SsoImportPreviewResponse.Fila fila : preview.getFilas()) {
            if (fila.getId() != null) {
                idsEnArchivo.add(fila.getId());
            }
            if (!fila.isOk() || "sin_cambios".equals(fila.getAccion())) {
                continue;
            }
            SsoTema tema = temaRepository.findByNombreIgnoreCase(fila.getTema().trim())
                    .orElseGet(() -> temaRepository.save(SsoTema.builder()
                            .nombre(fila.getTema().trim())
                            .activo(true)
                            .build()));
            String[] textos = {
                    fila.getOpcionA(), fila.getOpcionB(), fila.getOpcionC(),
                    fila.getOpcionD(), fila.getOpcionE()
            };
            int correcta = fila.getCorrecta().toUpperCase(Locale.ROOT).charAt(0) - 'A';

            if (fila.getId() == null) {
                if (preguntaRepository.existsByEnunciadoIgnoreCase(fila.getEnunciado().trim())) {
                    continue;
                }
                SsoPregunta pregunta = SsoPregunta.builder()
                        .tema(tema)
                        .enunciado(fila.getEnunciado().trim())
                        .explicacion(blankToNull(fila.getExplicacion()))
                        .fuente(blankToNull(fila.getFuente()))
                        .activo(fila.getActivo() == null || fila.getActivo())
                        .creadoPor(autor)
                        .fechaCreacion(LocalDateTime.now())
                        .fechaEdicion(LocalDateTime.now())
                        .build();
                for (int i = 0; i < textos.length; i++) {
                    if (textos[i] == null || textos[i].isBlank()) {
                        continue;
                    }
                    pregunta.getOpciones().add(SsoOpcion.builder()
                            .pregunta(pregunta)
                            .texto(textos[i].trim())
                            .esCorrecta(i == correcta)
                            .build());
                }
                preguntaRepository.save(pregunta);
                nuevas++;
                continue;
            }

            SsoPregunta pregunta = preguntaRepository.findDetalleById(fila.getId())
                    .orElseThrow(() -> new BadRequestException(
                            "Fila " + fila.getFila() + ": la pregunta id " + fila.getId() + " ya no existe"));
            pregunta.setTema(tema);
            pregunta.setEnunciado(fila.getEnunciado().trim());
            pregunta.setExplicacion(blankToNull(fila.getExplicacion()));
            pregunta.setFuente(blankToNull(fila.getFuente()));
            if (fila.getActivo() != null) {
                pregunta.setActivo(fila.getActivo());
            }
            pregunta.setFechaEdicion(LocalDateTime.now());

            // Las opciones se emparejan por posición (A = la primera, B = la segunda...).
            List<SsoOpcion> actuales = opcionesOrdenadas(pregunta);
            for (int i = 0; i < textos.length; i++) {
                boolean hayTexto = textos[i] != null && !textos[i].isBlank();
                SsoOpcion op = i < actuales.size() ? actuales.get(i) : null;
                if (op != null && hayTexto) {
                    op.setTexto(textos[i].trim());
                    op.setEsCorrecta(i == correcta);
                } else if (op != null) {
                    if (intentoRespuestaRepository.existsByOpcionElegidaId(op.getId())) {
                        throw new ConflictException("Fila " + fila.getFila() + ": la opción "
                                + (char) ('A' + i) + " ya se usó en exámenes y no se puede vaciar");
                    }
                    pregunta.getOpciones().remove(op);
                } else if (hayTexto) {
                    pregunta.getOpciones().add(SsoOpcion.builder()
                            .pregunta(pregunta)
                            .texto(textos[i].trim())
                            .esCorrecta(i == correcta)
                            .build());
                }
            }
            actualizadas++;
        }

        int desactivadas = 0;
        if (desactivarAusentes) {
            for (SsoPregunta p : preguntaRepository.findByActivoTrue()) {
                if (!idsEnArchivo.contains(p.getId())) {
                    p.setActivo(false);
                    p.setFechaEdicion(LocalDateTime.now());
                    desactivadas++;
                }
            }
        }

        preview.setValidas(nuevas + actualizadas);
        preview.setNuevas(nuevas);
        preview.setActualizadas(actualizadas);
        preview.setAusentes(desactivadas);
        return preview;
    }

    private boolean sinCambios(SsoPregunta p, String tema, String enunciado, String[] ops,
                               String correcta, String explicacion, String fuente, Boolean activo) {
        if (!p.getTema().getNombre().trim().equalsIgnoreCase(tema.trim())) return false;
        if (!p.getEnunciado().trim().equals(enunciado.trim())) return false;
        if (!Objects.equals(blankToNull(p.getExplicacion()), blankToNull(explicacion))) return false;
        if (!Objects.equals(blankToNull(p.getFuente()), blankToNull(fuente))) return false;
        if (activo != null && !activo.equals(p.getActivo())) return false;
        List<SsoOpcion> actuales = opcionesOrdenadas(p);
        for (int i = 0; i < ops.length; i++) {
            SsoOpcion op = i < actuales.size() ? actuales.get(i) : null;
            String actual = op == null ? "" : op.getTexto().trim();
            if (!actual.equals(ops[i].trim())) return false;
            boolean debeSerCorrecta = correcta.equals(String.valueOf((char) ('A' + i)));
            if (op != null && Boolean.TRUE.equals(op.getEsCorrecta()) != debeSerCorrecta) return false;
        }
        return true;
    }

    private List<SsoOpcion> opcionesOrdenadas(SsoPregunta p) {
        List<SsoOpcion> ops = new ArrayList<>(p.getOpciones());
        ops.sort(Comparator.comparing(SsoOpcion::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        return ops;
    }

    private Boolean parseActivo(String s) {
        String v = normHeader(s);
        return switch (v) {
            case "si", "s", "1", "true", "activo", "activa", "x" -> true;
            case "no", "n", "0", "false", "inactivo", "inactiva" -> false;
            default -> null;
        };
    }

    private Long parseId(String s) {
        try {
            return (long) Double.parseDouble(s.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void validarOpciones(List<SsoPreguntaRequest.SsoOpcionRequest> opciones) {
        long correctas = opciones.stream().filter(o -> Boolean.TRUE.equals(o.getEsCorrecta())).count();
        if (correctas != 1) {
            throw new BadRequestException("Debe haber exactamente una opción correcta");
        }
    }

    private void validarExplicacion(String explicacion) {
        if (explicacion == null || explicacion.isBlank()) {
            throw new BadRequestException("Indica por qué está mal");
        }
    }

    private SsoOpcion buildOpcion(SsoPregunta pregunta, SsoPreguntaRequest.SsoOpcionRequest op) {
        return SsoOpcion.builder()
                .pregunta(pregunta)
                .texto(op.getTexto().trim())
                .esCorrecta(Boolean.TRUE.equals(op.getEsCorrecta()))
                .argumento(null)
                .build();
    }

    private SsoTema getTema(Long id) {
        return temaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tema no encontrado"));
    }

    private SsoTemaResponse toTema(SsoTema tema) {
        return SsoTemaResponse.builder()
                .id(tema.getId())
                .nombre(tema.getNombre())
                .activo(tema.getActivo())
                .totalPreguntas(preguntaRepository.countByTemaId(tema.getId()))
                .preguntasActivas(preguntaRepository.countByTemaIdAndActivoTrue(tema.getId()))
                .build();
    }

    private SsoPreguntaAdminResponse toAdmin(SsoPregunta p) {
        List<SsoOpcion> ops = p.getOpciones() == null || p.getOpciones().isEmpty()
                ? opcionRepository.findByPreguntaId(p.getId())
                : p.getOpciones();
        return SsoPreguntaAdminResponse.builder()
                .id(p.getId())
                .temaId(p.getTema().getId())
                .temaNombre(p.getTema().getNombre())
                .enunciado(p.getEnunciado())
                .explicacion(p.getExplicacion())
                .fuente(p.getFuente())
                .sustentoTexto(p.getSustentoTexto())
                .imagen(p.getImagen())
                .sustentoNombre(p.getSustentoNombre())
                .sustentoTipo(p.getSustentoTipo())
                .tieneSustento(p.getSustentoRuta() != null && !p.getSustentoRuta().isBlank())
                .activo(p.getActivo())
                .fechaCreacion(p.getFechaCreacion())
                .fechaEdicion(p.getFechaEdicion())
                .usadaEnIntentos(intentoRespuestaRepository.existsByPreguntaId(p.getId()))
                .opciones(ops.stream()
                        .map(o -> SsoPreguntaAdminResponse.OpcionAdmin.builder()
                                .id(o.getId())
                                .texto(o.getTexto())
                                .esCorrecta(o.getEsCorrecta())
                                .argumento(o.getArgumento())
                                .build())
                        .toList())
                .build();
    }

    private List<List<String>> leerFilas(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".csv") || name.endsWith(".txt")) {
                return leerCsv(file.getInputStream());
            }
            try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
                if (wb.getNumberOfSheets() == 0) {
                    throw new BadRequestException("El Excel no tiene hojas");
                }
                DataFormatter fmt = new DataFormatter();
                List<List<String>> primera = null;
                // Usa la primera hoja que tenga la cabecera; si ninguna la tiene, la primera hoja.
                for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                    List<List<String>> rows = new ArrayList<>();
                    for (Row row : wb.getSheetAt(s)) {
                        List<String> cells = new ArrayList<>();
                        int last = Math.max(row.getLastCellNum(), 10);
                        for (int c = 0; c < last; c++) {
                            Cell cell = row.getCell(c);
                            cells.add(cell == null ? "" : fmt.formatCellValue(cell).trim());
                        }
                        rows.add(cells);
                    }
                    if (filaCabecera(rows) >= 0) {
                        return rows;
                    }
                    if (primera == null) {
                        primera = rows;
                    }
                }
                return primera;
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("No se pudo leer el archivo: " + e.getMessage());
        }
    }

    private List<List<String>> leerCsv(InputStream in) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            char sep = ',';
            boolean primera = true;
            while ((line = reader.readLine()) != null) {
                if (primera) {
                    line = line.replace("﻿", "");
                    // Excel en español guarda los CSV con punto y coma.
                    if (line.chars().filter(ch -> ch == ';').count() > line.chars().filter(ch -> ch == ',').count()) {
                        sep = ';';
                    }
                    primera = false;
                }
                rows.add(parseCsvLine(line, sep));
            }
        }
        return rows;
    }

    private List<String> parseCsvLine(String line, char sep) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    cur.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == sep) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString().trim());
        return out;
    }

    private String normHeader(String h) {
        if (h == null) return "";
        String n = h.replace("﻿", "").trim().toLowerCase(Locale.ROOT)
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u")
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return switch (n) {
            case "pregunta" -> "enunciado";
            case "respuesta", "respuesta_correcta", "opcion_correcta", "clave" -> "correcta";
            case "alternativa_a", "a" -> "opcion_a";
            case "alternativa_b", "b" -> "opcion_b";
            case "alternativa_c", "c" -> "opcion_c";
            case "alternativa_d", "d" -> "opcion_d";
            case "alternativa_e", "e" -> "opcion_e";
            default -> n;
        };
    }

    /** Busca la fila de cabecera entre las primeras filas (puede haber un título encima). */
    private int filaCabecera(List<List<String>> rows) {
        for (int i = 0; i < Math.min(rows.size(), 10); i++) {
            List<String> h = rows.get(i).stream().map(this::normHeader).toList();
            if (h.contains("tema") && h.contains("enunciado")) return i;
        }
        return -1;
    }

    private int idx(List<String> header, String name) {
        for (int i = 0; i < header.size(); i++) {
            if (name.equals(header.get(i))) return i;
        }
        return -1;
    }

    private String cell(List<String> row, int i) {
        if (i < 0 || i >= row.size() || row.get(i) == null) return "";
        return row.get(i).trim();
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private Path dirSustento() {
        try {
            Path dir = Path.of("uploads", "sso-sustentos").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            return dir;
        } catch (IOException e) {
            throw new BadRequestException("No se pudo crear la carpeta de sustentos");
        }
    }

    private void borrarArchivoFisico(String ruta) {
        if (ruta == null || ruta.isBlank()) return;
        try {
            Path file = Path.of(ruta).toAbsolutePath().normalize();
            Path base = dirSustento().toAbsolutePath().normalize();
            if (file.startsWith(base) && Files.isRegularFile(file)) {
                Files.deleteIfExists(file);
            }
        } catch (IOException ignored) {
            // Si el archivo ya no existe, igual se limpia la referencia en BD.
        }
    }

    private String sanitizarNombre(String original) {
        String clean = original.replace('\\', '_').replace('/', '_').replaceAll("[\\x00-\\x1F]", "").trim();
        if (clean.isBlank()) clean = "sustento";
        return recortar(clean, 180);
    }

    private String recortar(String valor, int max) {
        if (valor == null) return null;
        return valor.length() <= max ? valor : valor.substring(0, max);
    }

    private int entero(String clave, int def) {
        return configuracionRepository.findById(clave)
                .map(c -> {
                    try {
                        return Integer.parseInt(c.getValor().trim());
                    } catch (Exception e) {
                        return def;
                    }
                })
                .orElse(def);
    }

    private void guardarClave(String clave, String valor) {
        SsoConfiguracion cfg = configuracionRepository.findById(clave)
                .orElse(SsoConfiguracion.builder().clave(clave).build());
        cfg.setValor(valor);
        configuracionRepository.save(cfg);
    }
}
