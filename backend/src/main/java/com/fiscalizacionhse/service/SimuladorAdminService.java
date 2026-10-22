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

    public SsoImportPreviewResponse previsualizarImportacion(MultipartFile file) {
        List<List<String>> rows = leerFilas(file);
        if (rows.isEmpty()) {
            throw new BadRequestException("El archivo está vacío");
        }
        List<String> header = rows.get(0).stream().map(this::normHeader).toList();
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
        if (iTema < 0 || iEnun < 0 || iA < 0 || iB < 0 || iCorr < 0) {
            throw new BadRequestException(
                    "Cabecera inválida. Se espera: tema,enunciado,opcion_a,opcion_b,opcion_c,opcion_d,opcion_e,correcta,explicacion,fuente");
        }

        List<SsoImportPreviewResponse.Fila> filas = new ArrayList<>();
        int validas = 0;
        int errores = 0;
        int temasNuevos = 0;
        int duplicados = 0;

        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.stream().allMatch(s -> s == null || s.isBlank())) {
                continue;
            }
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

            boolean temaNuevo = !tema.isBlank() && temaRepository.findByNombreIgnoreCase(tema).isEmpty();
            boolean duplicado = !enunciado.isBlank() && preguntaRepository.existsByEnunciadoIgnoreCase(enunciado);
            if (temaNuevo) temasNuevos++;
            if (duplicado) duplicados++;

            boolean ok = errs.isEmpty();
            if (ok) validas++;
            else errores++;

            filas.add(SsoImportPreviewResponse.Fila.builder()
                    .fila(i + 1)
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
                    .build());
        }

        return SsoImportPreviewResponse.builder()
                .totalFilas(filas.size())
                .validas(validas)
                .conError(errores)
                .temasNuevos(temasNuevos)
                .posiblesDuplicados(duplicados)
                .filas(filas)
                .build();
    }

    @Transactional
    public SsoImportPreviewResponse confirmarImportacion(SsoImportPreviewResponse preview, Long usuarioId) {
        if (preview == null || preview.getFilas() == null) {
            throw new BadRequestException("No hay filas para importar");
        }
        Usuario autor = usuarioRepository.findById(usuarioId).orElse(null);
        int cargadas = 0;
        for (SsoImportPreviewResponse.Fila fila : preview.getFilas()) {
            if (!fila.isOk()) {
                continue;
            }
            if (preguntaRepository.existsByEnunciadoIgnoreCase(fila.getEnunciado().trim())) {
                continue;
            }
            SsoTema tema = temaRepository.findByNombreIgnoreCase(fila.getTema())
                    .orElseGet(() -> temaRepository.save(SsoTema.builder()
                            .nombre(fila.getTema().trim())
                            .activo(true)
                            .build()));

            SsoPregunta pregunta = SsoPregunta.builder()
                    .tema(tema)
                    .enunciado(fila.getEnunciado().trim())
                    .explicacion(blankToNull(fila.getExplicacion()))
                    .fuente(blankToNull(fila.getFuente()))
                    .activo(true)
                    .creadoPor(autor)
                    .fechaCreacion(LocalDateTime.now())
                    .fechaEdicion(LocalDateTime.now())
                    .build();

            String[] textos = {
                    fila.getOpcionA(), fila.getOpcionB(), fila.getOpcionC(),
                    fila.getOpcionD(), fila.getOpcionE()
            };
            int correcta = fila.getCorrecta().toUpperCase(Locale.ROOT).charAt(0) - 'A';
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
            cargadas++;
        }
        preview.setValidas(cargadas);
        return preview;
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
                Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
                if (sheet == null) {
                    throw new BadRequestException("El Excel no tiene hojas");
                }
                DataFormatter fmt = new DataFormatter();
                List<List<String>> rows = new ArrayList<>();
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    int last = Math.max(row.getLastCellNum(), 10);
                    for (int c = 0; c < last; c++) {
                        Cell cell = row.getCell(c);
                        cells.add(cell == null ? "" : fmt.formatCellValue(cell).trim());
                    }
                    rows.add(cells);
                }
                return rows;
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
            while ((line = reader.readLine()) != null) {
                rows.add(parseCsvLine(line));
            }
        }
        return rows;
    }

    private List<String> parseCsvLine(String line) {
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
            } else if (ch == ',') {
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
        return h == null ? "" : h.trim().toLowerCase(Locale.ROOT)
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u");
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
