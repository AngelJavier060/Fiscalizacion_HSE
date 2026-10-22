package com.fiscalizacionhse.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscalizacionhse.dto.request.SsoIniciarExamenRequest;
import com.fiscalizacionhse.dto.request.SsoResponderRequest;
import com.fiscalizacionhse.dto.response.SsoExamenResponse;
import com.fiscalizacionhse.dto.response.SsoHistorialItemResponse;
import com.fiscalizacionhse.dto.response.SsoInicioResponse;
import com.fiscalizacionhse.dto.response.SsoResultadoResponse;
import com.fiscalizacionhse.dto.response.SsoTemaResponse;
import com.fiscalizacionhse.exception.BadRequestException;
import com.fiscalizacionhse.exception.ResourceNotFoundException;
import com.fiscalizacionhse.model.SsoIntento;
import com.fiscalizacionhse.model.SsoIntentoRespuesta;
import com.fiscalizacionhse.model.SsoOpcion;
import com.fiscalizacionhse.model.SsoPregunta;
import com.fiscalizacionhse.model.SsoRotacionUsuario;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SimuladorExamenService {

    private final UsuarioRepository usuarioRepository;
    private final SsoTemaRepository temaRepository;
    private final SsoPreguntaRepository preguntaRepository;
    private final SsoOpcionRepository opcionRepository;
    private final SsoIntentoRepository intentoRepository;
    private final SsoIntentoRespuestaRepository intentoRespuestaRepository;
    private final SsoRotacionUsuarioRepository rotacionRepository;
    private final SsoConfiguracionRepository configuracionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public SsoInicioResponse inicio(Long usuarioId) {
        Usuario usuario = getUsuario(usuarioId);
        int vuelta = usuario.getVueltaActual() == null ? 1 : usuario.getVueltaActual();
        long banco = preguntaRepository.countByActivoTrue();
        long vistas = rotacionRepository.countByUsuarioIdAndVuelta(usuarioId, vuelta);

        List<SsoIntento> historial = intentoRepository.findByUsuarioIdOrderByFechaInicioDesc(usuarioId);
        SsoIntento pendiente = historial.stream()
                .filter(i -> "en_curso".equals(i.getEstado()))
                .findFirst()
                .orElse(null);
        SsoIntento ultimo = historial.stream()
                .filter(i -> "finalizado".equals(i.getEstado()) && !"falladas".equals(i.getModo()))
                .findFirst()
                .orElse(null);

        Set<Long> cumplidasIds = rotacionRepository.findByUsuarioIdAndVuelta(usuarioId, vuelta)
                .stream().map(SsoRotacionUsuario::getPreguntaId).collect(Collectors.toSet());
        SsoIntento ultimoBanco = historial.stream()
                .filter(i -> "finalizado".equals(i.getEstado()) && esModoBanco(i.getModo()))
                .findFirst().orElse(null);
        boolean repetir = ultimoBanco != null && !Boolean.TRUE.equals(ultimoBanco.getAprobado());

        List<SsoTemaResponse> temas = temaRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(t -> {
                    long activasTema = preguntaRepository.countByTemaIdAndActivoTrue(t.getId());
                    long cumplidasTema = preguntaRepository.findByActivoTrueAndTemaId(t.getId()).stream()
                            .filter(p -> cumplidasIds.contains(p.getId()))
                            .count();
                    return SsoTemaResponse.builder()
                            .id(t.getId())
                            .nombre(t.getNombre())
                            .activo(true)
                            .totalPreguntas(preguntaRepository.countByTemaId(t.getId()))
                            .preguntasActivas(activasTema)
                            .cumplidasVuelta(cumplidasTema)
                            .build();
                })
                .toList();

        return SsoInicioResponse.builder()
                .vueltaActual(vuelta)
                .preguntasVistasVuelta(vistas)
                .preguntasBanco(banco)
                .falladasPendientes(preguntasFalladas(usuarioId).size())
                .ultimaNota(ultimo == null ? null : ultimo.getNota())
                .ultimoAprobado(ultimo == null ? null : ultimo.getAprobado())
                .ultimaFecha(ultimo == null ? null : ultimo.getFechaFin())
                .intentoPendienteId(pendiente == null ? null : pendiente.getId())
                .intentoPendienteModo(pendiente == null ? null : pendiente.getModo())
                .temas(temas)
                .notaMinima(entero("nota_minima", 90))
                .tiempo20Min(tiempoExamenMin())
                .tiempo40Min(tiempoExamenMin())
                .tiempoExamenMin(tiempoExamenMin())
                .preguntasPorBanco(preguntasPorBanco())
                .bloqueRepetir(repetir)
                .bloqueRepetirNota(ultimoBanco == null ? null : ultimoBanco.getNota())
                .bloqueRepetirTotal(ultimoBanco == null ? 0 : ultimoBanco.getTotalPreguntas())
                .bloqueRepetirTemaId(ultimoBanco == null || ultimoBanco.getTema() == null
                        ? null : ultimoBanco.getTema().getId())
                .bloqueRepetirTemaNombre(ultimoBanco == null
                        ? null
                        : (ultimoBanco.getTema() == null ? "General" : ultimoBanco.getTema().getNombre()))
                .historial(historial.stream().limit(20).map(this::toHistorial).toList())
                .build();
    }

    @Transactional
    public SsoExamenResponse iniciar(Long usuarioId, SsoIniciarExamenRequest request) {
        Usuario usuario = getUsuario(usuarioId);
        if (usuario.getRol() == RolUsuario.ADMIN_EMPRESA) {
            throw new BadRequestException("El administrador de empresa consulta el banco de estudio, no rinde el examen.");
        }
        SsoIntento pendiente = intentoRepository
                .findFirstByUsuarioIdAndEstadoOrderByFechaInicioDesc(usuarioId, "en_curso")
                .orElse(null);
        if (pendiente != null) {
            SsoIntento detalle = intentoRepository
                    .findDetalleByIdAndUsuarioId(pendiente.getId(), usuarioId)
                    .orElse(pendiente);
            if (!cerrarSiVencido(detalle) && "en_curso".equals(detalle.getEstado())) {
                return toExamen(detalle, false, false, null);
            }
        }

        SsoIntento origen = cargarOrigen(usuarioId, request.getIntentoOrigenId());

        String modo = request.getModo() == null ? "" : request.getModo().trim().toLowerCase();
        if (modo.isBlank() && origen != null) {
            modo = origen.getModo() == null ? "" : origen.getModo().trim().toLowerCase();
        }
        if ("20".equals(modo) || "40".equals(modo)) {
            modo = "banco";
        }
        if (!List.of("banco", "falladas").contains(modo)) {
            throw new BadRequestException("Elija general, un tema o el repaso de falladas");
        }

        SsoTema tema = null;
        Long temaId = request.getTemaId() != null
                ? request.getTemaId()
                : (origen != null && origen.getTema() != null ? origen.getTema().getId() : null);
        if (!"falladas".equals(modo) && temaId != null) {
            tema = temaRepository.findById(temaId)
                    .orElseThrow(() -> new ResourceNotFoundException("Tema no encontrado"));
            if (!Boolean.TRUE.equals(tema.getActivo())) {
                throw new BadRequestException("El tema está inactivo");
            }
        }

        boolean bancoInsuficiente = false;
        String aviso = null;
        List<SsoPregunta> preguntas = null;
        int nBanco = request.getPreguntasPorBanco() == null
                ? (origen != null ? bancoDesdeTotal(origen.getTotalPreguntas()) : preguntasPorBanco())
                : SsoBancoReglas.normalizarBanco(request.getPreguntasPorBanco());
        boolean repetirMismo = Boolean.FALSE.equals(request.getNuevoSet());

        if ("falladas".equals(modo)) {
            tema = null;
            if (repetirMismo && origen != null) {
                preguntas = preguntasActivasDe(origen);
                aviso = "Repite las mismas preguntas (otro orden).";
            } else {
                preguntas = preguntasFalladas(usuarioId);
                if (preguntas.isEmpty()) {
                    throw new BadRequestException("No tienes preguntas falladas para repasar");
                }
                Collections.shuffle(preguntas);
                int tope = SsoBancoReglas.tope(nBanco, preguntas.size());
                if (preguntas.size() > tope) {
                    preguntas = new ArrayList<>(preguntas.subList(0, tope));
                }
            }
        } else {
            List<SsoPregunta> activas = tema == null
                    ? preguntaRepository.findByActivoTrue()
                    : preguntaRepository.findByActivoTrueAndTemaId(tema.getId());
            if (activas.isEmpty()) {
                throw new BadRequestException("No hay preguntas activas para este alcance");
            }
            SsoIntento ultimo = origen != null ? origen : ultimoBancoMismoAlcance(usuarioId, tema);
            boolean ultimoReprobado = ultimo != null && !Boolean.TRUE.equals(ultimo.getAprobado());
            if (repetirMismo && ultimo != null) {
                SsoIntento detalle = intentoRepository
                        .findDetalleByIdAndUsuarioId(ultimo.getId(), usuarioId)
                        .orElse(ultimo);
                preguntas = preguntasActivasDe(detalle);
                aviso = "Repite las mismas preguntas (otro orden). Nota anterior: "
                        + (ultimo.getNota() == null ? "—" : ultimo.getNota().stripTrailingZeros().toPlainString())
                        + ".";
            } else {
                Set<Long> evitar = Set.of();
                if (ultimo != null && (origen != null || ultimoReprobado)) {
                    SsoIntento detalle = intentoRepository
                            .findDetalleByIdAndUsuarioId(ultimo.getId(), usuarioId)
                            .orElse(ultimo);
                    evitar = detalle.getRespuestas().stream()
                            .map(r -> r.getPregunta().getId())
                            .collect(Collectors.toSet());
                }
                preguntas = siguienteBanco(usuario, activas, nBanco, tema == null, evitar);
                if (nBanco > 0 && preguntas.size() < nBanco) {
                    bancoInsuficiente = activas.size() < nBanco;
                    aviso = "Último banco de esta vuelta: " + preguntas.size() + " preguntas pendientes.";
                }
            }
        }

        if (preguntas == null || preguntas.isEmpty()) {
            throw new BadRequestException("No hay preguntas para armar el examen");
        }

        int tiempo = SsoBancoReglas.tiempoSegunCantidad(preguntas.size());

        SsoIntento intento = SsoIntento.builder()
                .usuario(usuario)
                .modo(modo)
                .tema(tema)
                .estado("en_curso")
                .fechaInicio(LocalDateTime.now())
                .tiempoLimiteMin(tiempo)
                .totalPreguntas(preguntas.size())
                .build();
        intento = intentoRepository.save(intento);

        List<SsoPregunta> ordenadas = new ArrayList<>(preguntas);
        Collections.shuffle(ordenadas);
        int orden = 1;
        for (SsoPregunta pregunta : ordenadas) {
            List<SsoOpcion> ops = opcionesDe(pregunta);
            List<Long> ids = ops.stream().map(SsoOpcion::getId).collect(Collectors.toCollection(ArrayList::new));
            Collections.shuffle(ids);
            SsoIntentoRespuesta resp = SsoIntentoRespuesta.builder()
                    .intento(intento)
                    .pregunta(pregunta)
                    .orden(orden++)
                    .ordenOpciones(writeJson(ids))
                    .build();
            intento.getRespuestas().add(resp);
        }
        usuarioRepository.save(usuario);
        return toExamen(intento, false, bancoInsuficiente, aviso);
    }

    @Transactional
    public SsoExamenResponse obtenerExamen(Long usuarioId, Long intentoId) {
        SsoIntento intento = intentoRepository.findDetalleByIdAndUsuarioId(intentoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Examen no encontrado"));
        if (cerrarSiVencido(intento)) {
            throw new BadRequestException("El tiempo del examen se agotó");
        }
        if (!"en_curso".equals(intento.getEstado())) {
            throw new BadRequestException("Este examen ya no está en curso");
        }
        return toExamen(intento, false, false, null);
    }

    @Transactional
    public SsoExamenResponse responder(Long usuarioId, Long intentoId, SsoResponderRequest request) {
        SsoIntento intento = intentoRepository.findDetalleByIdAndUsuarioId(intentoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Examen no encontrado"));
        if (cerrarSiVencido(intento)) {
            throw new BadRequestException("El tiempo del examen se agotó");
        }
        if (!"en_curso".equals(intento.getEstado())) {
            throw new BadRequestException("Este examen ya no está en curso");
        }
        SsoIntentoRespuesta resp = intento.getRespuestas().stream()
                .filter(r -> r.getPregunta().getId().equals(request.getPreguntaId()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("La pregunta no pertenece a este examen"));

        if (request.getOpcionId() == null) {
            resp.setOpcionElegida(null);
        } else {
            List<Long> permitidas = readIds(resp.getOrdenOpciones());
            if (!permitidas.contains(request.getOpcionId())) {
                throw new BadRequestException("Opción inválida para esta pregunta");
            }
            SsoOpcion opcion = opcionRepository.findById(request.getOpcionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Opción no encontrada"));
            resp.setOpcionElegida(opcion);
        }
        return toExamen(intento, false, false, null);
    }

    @Transactional
    public SsoResultadoResponse finalizar(Long usuarioId, Long intentoId) {
        SsoIntento intento = intentoRepository.findDetalleByIdAndUsuarioId(intentoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Examen no encontrado"));
        if (!"en_curso".equals(intento.getEstado())) {
            return toResultado(intento);
        }
        calificar(intento);
        return toResultado(intento);
    }

    @Transactional(readOnly = true)
    public SsoResultadoResponse resultado(Long usuarioId, Long intentoId) {
        SsoIntento intento = intentoRepository.findDetalleByIdAndUsuarioId(intentoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Examen no encontrado"));
        if ("en_curso".equals(intento.getEstado())) {
            throw new BadRequestException("El examen aún no ha finalizado");
        }
        return toResultado(intento);
    }

    @Transactional(readOnly = true)
    public List<SsoHistorialItemResponse> historial(Long usuarioId) {
        return intentoRepository.findByUsuarioIdOrderByFechaInicioDesc(usuarioId).stream()
                .map(this::toHistorial)
                .toList();
    }

    private void calificar(SsoIntento intento) {
        int correctas = 0;
        for (SsoIntentoRespuesta r : intento.getRespuestas()) {
            boolean ok = r.getOpcionElegida() != null
                    && Boolean.TRUE.equals(r.getOpcionElegida().getEsCorrecta());
            r.setEsCorrecta(ok);
            if (ok) {
                correctas++;
            }
        }
        int total = intento.getTotalPreguntas() == null || intento.getTotalPreguntas() == 0
                ? intento.getRespuestas().size()
                : intento.getTotalPreguntas();
        BigDecimal nota = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(correctas)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
        int minima = entero("nota_minima", 90);
        intento.setCorrectas(correctas);
        intento.setNota(nota);
        intento.setAprobado(nota.compareTo(BigDecimal.valueOf(minima)) >= 0);
        intento.setEstado("finalizado");
        intento.setFechaFin(LocalDateTime.now());
        if (Boolean.TRUE.equals(intento.getAprobado()) && esModoBanco(intento.getModo())) {
            registrarCumplidas(intento);
        }
    }

    private void registrarCumplidas(SsoIntento intento) {
        Usuario usuario = intento.getUsuario();
        int vuelta = usuario.getVueltaActual() == null ? 1 : usuario.getVueltaActual();
        for (SsoIntentoRespuesta r : intento.getRespuestas()) {
            Long pid = r.getPregunta().getId();
            if (!rotacionRepository.existsByUsuarioIdAndPreguntaIdAndVuelta(usuario.getId(), pid, vuelta)) {
                rotacionRepository.save(SsoRotacionUsuario.builder()
                        .usuarioId(usuario.getId())
                        .preguntaId(pid)
                        .vuelta(vuelta)
                        .build());
            }
        }
        long banco = preguntaRepository.countByActivoTrue();
        long cumplidas = rotacionRepository.countByUsuarioIdAndVuelta(usuario.getId(), vuelta);
        if (banco > 0 && cumplidas >= banco) {
            usuario.setVueltaActual(vuelta + 1);
            usuarioRepository.save(usuario);
        }
    }

    private List<SsoPregunta> siguienteBanco(
            Usuario usuario, List<SsoPregunta> activas, int n, boolean general, Set<Long> evitar) {
        int vuelta = usuario.getVueltaActual() == null ? 1 : usuario.getVueltaActual();
        usuario.setVueltaActual(vuelta);
        Set<Long> cumplidas = rotacionRepository.findByUsuarioIdAndVuelta(usuario.getId(), vuelta)
                .stream().map(SsoRotacionUsuario::getPreguntaId).collect(Collectors.toSet());
        List<SsoPregunta> pendientes = activas.stream()
                .filter(p -> !cumplidas.contains(p.getId()))
                .collect(Collectors.toCollection(ArrayList::new));
        if (pendientes.isEmpty()) {
            if (!general) {
                throw new BadRequestException("Ya cumplió este tema en la vuelta " + vuelta
                        + ". Elija otro tema o el banco general para seguir.");
            }
            vuelta = vuelta + 1;
            usuario.setVueltaActual(vuelta);
            pendientes = new ArrayList<>(activas);
        }
        Set<Long> excluir = evitar == null ? Set.of() : evitar;
        List<SsoPregunta> nuevas = pendientes.stream()
                .filter(p -> !excluir.contains(p.getId()))
                .collect(Collectors.toCollection(ArrayList::new));
        List<SsoPregunta> previas = pendientes.stream()
                .filter(p -> excluir.contains(p.getId()))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(nuevas);
        Collections.shuffle(previas);
        int tomar = SsoBancoReglas.tope(n, pendientes.size());
        List<SsoPregunta> armado = new ArrayList<>(nuevas);
        if (armado.size() < tomar) {
            for (SsoPregunta p : previas) {
                if (armado.size() >= tomar) {
                    break;
                }
                armado.add(p);
            }
        }
        if (armado.size() > tomar) {
            armado = new ArrayList<>(armado.subList(0, tomar));
        }
        return armado;
    }

    private SsoIntento ultimoBancoMismoAlcance(Long usuarioId, SsoTema tema) {
        Long temaId = tema == null ? null : tema.getId();
        return intentoRepository.findByUsuarioIdOrderByFechaInicioDesc(usuarioId).stream()
                .filter(i -> "finalizado".equals(i.getEstado()) && esModoBanco(i.getModo()))
                .filter(i -> {
                    Long actual = i.getTema() == null ? null : i.getTema().getId();
                    return Objects.equals(actual, temaId);
                })
                .findFirst()
                .orElse(null);
    }

    private SsoIntento cargarOrigen(Long usuarioId, Long intentoOrigenId) {
        if (intentoOrigenId == null) {
            return null;
        }
        SsoIntento origen = intentoRepository.findDetalleByIdAndUsuarioId(intentoOrigenId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Resultado no encontrado"));
        if ("en_curso".equals(origen.getEstado())) {
            throw new BadRequestException("Ese examen aún no ha finalizado");
        }
        return origen;
    }

    private int bancoDesdeTotal(Integer total) {
        int n = total == null ? 0 : total;
        if (n == 20 || n == 40 || n == 60) {
            return n;
        }
        return SsoBancoReglas.TODAS;
    }

    private List<SsoPregunta> preguntasActivasDe(SsoIntento intento) {
        List<SsoPregunta> out = new ArrayList<>();
        for (SsoIntentoRespuesta r : intento.getRespuestas()) {
            SsoPregunta p = r.getPregunta();
            if (p != null && Boolean.TRUE.equals(p.getActivo())) {
                SsoPregunta detalle = preguntaRepository.findDetalleById(p.getId()).orElse(p);
                out.add(detalle);
            }
        }
        if (out.isEmpty()) {
            throw new BadRequestException("Ese banco ya no tiene preguntas activas. Inicie el siguiente.");
        }
        return out;
    }

    private boolean esModoBanco(String modo) {
        return modo != null && Set.of("banco", "20", "40").contains(modo);
    }

    private int preguntasPorBanco() {
        return SsoBancoReglas.normalizarBanco(entero("preguntas_por_banco", 20));
    }

    private List<SsoPregunta> preguntasFalladas(Long usuarioId) {
        List<SsoIntento> historial = intentoRepository.findByUsuarioIdOrderByFechaInicioDesc(usuarioId);
        Map<Long, Boolean> ultima = new LinkedHashMap<>();
        for (SsoIntento i : historial) {
            if (!"finalizado".equals(i.getEstado()) || "falladas".equals(i.getModo())) {
                continue;
            }
            SsoIntento detalle = intentoRepository.findDetalleByIdAndUsuarioId(i.getId(), usuarioId).orElse(i);
            for (SsoIntentoRespuesta r : detalle.getRespuestas()) {
                ultima.putIfAbsent(r.getPregunta().getId(), Boolean.TRUE.equals(r.getEsCorrecta()));
            }
        }
        List<Long> ids = ultima.entrySet().stream()
                .filter(e -> Boolean.FALSE.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .map(id -> preguntaRepository.findDetalleById(id).orElse(null))
                .filter(Objects::nonNull)
                .filter(p -> Boolean.TRUE.equals(p.getActivo()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private SsoExamenResponse toExamen(SsoIntento intento, boolean conCorrecta,
                                       boolean bancoInsuficiente, String aviso) {
        List<SsoIntentoRespuesta> respuestas = new ArrayList<>(intento.getRespuestas());
        respuestas.sort(Comparator.comparing(SsoIntentoRespuesta::getOrden));
        int respondidas = (int) respuestas.stream().filter(r -> r.getOpcionElegida() != null).count();

        List<SsoExamenResponse.Pregunta> preguntas = new ArrayList<>();
        for (SsoIntentoRespuesta r : respuestas) {
            Map<Long, SsoOpcion> porId = opcionesDe(r.getPregunta()).stream()
                    .collect(Collectors.toMap(SsoOpcion::getId, o -> o));
            List<SsoExamenResponse.Opcion> ops = new ArrayList<>();
            for (Long oid : readIds(r.getOrdenOpciones())) {
                SsoOpcion o = porId.get(oid);
                if (o != null) {
                    ops.add(SsoExamenResponse.Opcion.builder()
                            .id(o.getId())
                            .texto(o.getTexto())
                            .build());
                }
            }
            SsoPregunta pregunta = r.getPregunta();
            preguntas.add(SsoExamenResponse.Pregunta.builder()
                    .id(pregunta.getId())
                    .orden(r.getOrden())
                    .enunciado(pregunta.getEnunciado())
                    .temaNombre(pregunta.getTema() == null ? "General" : pregunta.getTema().getNombre())
                    .opcionElegidaId(r.getOpcionElegida() == null ? null : r.getOpcionElegida().getId())
                    .opciones(ops)
                    .build());
        }

        return SsoExamenResponse.builder()
                .id(intento.getId())
                .modo(intento.getModo())
                .temaId(intento.getTema() == null ? null : intento.getTema().getId())
                .temaNombre(intento.getTema() == null ? "Todos los temas" : intento.getTema().getNombre())
                .estado(intento.getEstado())
                .fechaInicio(intento.getFechaInicio())
                .tiempoLimiteMin(intento.getTiempoLimiteMin())
                .segundosRestantes(segundosRestantes(intento))
                .totalPreguntas(intento.getTotalPreguntas())
                .respondidas(respondidas)
                .bancoInsuficiente(bancoInsuficiente)
                .aviso(aviso)
                .preguntas(preguntas)
                .build();
    }

    private SsoResultadoResponse toResultado(SsoIntento intento) {
        List<SsoIntentoRespuesta> respuestas = new ArrayList<>(intento.getRespuestas());
        respuestas.sort(Comparator.comparing(SsoIntentoRespuesta::getOrden));

        Map<String, int[]> porTema = new LinkedHashMap<>();
        List<SsoResultadoResponse.Revision> revision = new ArrayList<>();

        for (SsoIntentoRespuesta r : respuestas) {
            SsoPregunta p = r.getPregunta();
            String temaNom = p.getTema() == null ? "Sin tema" : p.getTema().getNombre();
            int[] acc = porTema.computeIfAbsent(temaNom, k -> new int[2]);
            acc[0]++;
            if (Boolean.TRUE.equals(r.getEsCorrecta())) {
                acc[1]++;
            }

            Map<Long, SsoOpcion> porId = opcionesDe(p).stream()
                    .collect(Collectors.toMap(SsoOpcion::getId, o -> o));
            Long correctaId = porId.values().stream()
                    .filter(o -> Boolean.TRUE.equals(o.getEsCorrecta()))
                    .map(SsoOpcion::getId)
                    .findFirst().orElse(null);
            Long elegidaId = r.getOpcionElegida() == null ? null : r.getOpcionElegida().getId();

            List<SsoResultadoResponse.OpcionRev> ops = new ArrayList<>();
            for (Long oid : readIds(r.getOrdenOpciones())) {
                SsoOpcion o = porId.get(oid);
                if (o == null) continue;
                ops.add(SsoResultadoResponse.OpcionRev.builder()
                        .id(o.getId())
                        .texto(o.getTexto())
                        .correcta(Boolean.TRUE.equals(o.getEsCorrecta()))
                        .elegida(o.getId().equals(elegidaId))
                        .argumento(o.getArgumento())
                        .build());
            }

            revision.add(SsoResultadoResponse.Revision.builder()
                    .preguntaId(p.getId())
                    .orden(r.getOrden())
                    .enunciado(p.getEnunciado())
                    .explicacion(p.getExplicacion())
                    .fuente(p.getFuente())
                    .sustentoTexto(p.getSustentoTexto())
                    .tieneSustento(p.getSustentoRuta() != null && !p.getSustentoRuta().isBlank())
                    .sustentoNombre(p.getSustentoNombre())
                    .sustentoTipo(p.getSustentoTipo())
                    .temaNombre(temaNom)
                    .opcionElegidaId(elegidaId)
                    .opcionCorrectaId(correctaId)
                    .esCorrecta(r.getEsCorrecta())
                    .opciones(ops)
                    .build());
        }

        return SsoResultadoResponse.builder()
                .id(intento.getId())
                .modo(intento.getModo())
                .temaId(intento.getTema() == null ? null : intento.getTema().getId())
                .temaNombre(intento.getTema() == null ? "Todos los temas" : intento.getTema().getNombre())
                .estado(intento.getEstado())
                .fechaInicio(intento.getFechaInicio())
                .fechaFin(intento.getFechaFin())
                .totalPreguntas(intento.getTotalPreguntas())
                .correctas(intento.getCorrectas() == null ? 0 : intento.getCorrectas())
                .nota(intento.getNota())
                .aprobado(Boolean.TRUE.equals(intento.getAprobado()))
                .aciertosPorTema(porTema.entrySet().stream()
                        .map(e -> SsoResultadoResponse.AciertosTema.builder()
                                .tema(e.getKey())
                                .total(e.getValue()[0])
                                .correctas(e.getValue()[1])
                                .build())
                        .toList())
                .revision(revision)
                .build();
    }

    private SsoHistorialItemResponse toHistorial(SsoIntento i) {
        return SsoHistorialItemResponse.builder()
                .id(i.getId())
                .fechaInicio(i.getFechaInicio())
                .fechaFin(i.getFechaFin())
                .modo(i.getModo())
                .temaNombre(i.getTema() == null ? "Todos los temas" : i.getTema().getNombre())
                .nota(i.getNota())
                .aprobado(i.getAprobado())
                .estado(i.getEstado())
                .totalPreguntas(i.getTotalPreguntas())
                .correctas(i.getCorrectas())
                .build();
    }

    private List<SsoOpcion> opcionesDe(SsoPregunta pregunta) {
        if (pregunta.getOpciones() != null && !pregunta.getOpciones().isEmpty()) {
            return pregunta.getOpciones();
        }
        return opcionRepository.findByPreguntaId(pregunta.getId());
    }

    private Usuario getUsuario(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 20_000)
    @Transactional
    public void cerrarExamenesVencidos() {
        List<SsoIntento> abiertos = intentoRepository.findByEstado("en_curso");
        for (SsoIntento resumen : abiertos) {
            if (!vencido(resumen)) {
                continue;
            }
            SsoIntento detalle = intentoRepository
                    .findDetalleByIdAndUsuarioId(resumen.getId(), resumen.getUsuario().getId())
                    .orElse(null);
            if (detalle != null && cerrarSiVencido(detalle)) {
                log.info("Examen SSO {} cerrado por tiempo agotado", detalle.getId());
            }
        }
    }

    private int tiempoExamenMin() {
        return SsoBancoReglas.tiempoMin(preguntasPorBanco());
    }

    private int segundosRestantes(SsoIntento intento) {
        Integer minutos = intento.getTiempoLimiteMin();
        if (minutos == null || minutos <= 0 || intento.getFechaInicio() == null) {
            return 0;
        }
        long seg = Duration.between(LocalDateTime.now(), intento.getFechaInicio().plusMinutes(minutos))
                .getSeconds();
        long tope = minutos * 60L;
        return (int) Math.max(0, Math.min(seg, tope));
    }

    private boolean vencido(SsoIntento intento) {
        Integer minutos = intento.getTiempoLimiteMin();
        if (minutos == null || minutos <= 0 || intento.getFechaInicio() == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(intento.getFechaInicio().plusMinutes(minutos));
    }

    private boolean cerrarSiVencido(SsoIntento intento) {
        if (!"en_curso".equals(intento.getEstado()) || !vencido(intento)) {
            return false;
        }
        calificar(intento);
        return true;
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

    private String writeJson(List<Long> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (Exception e) {
            return ids.toString();
        }
    }

    private List<Long> readIds(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

}
