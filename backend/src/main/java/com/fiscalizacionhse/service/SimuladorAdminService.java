package com.fiscalizacionhse.service;

import com.fiscalizacionhse.dto.request.SsoConfiguracionRequest;
import com.fiscalizacionhse.dto.request.SsoPreguntaRequest;
import com.fiscalizacionhse.dto.request.SsoTemaRequest;
import com.fiscalizacionhse.dto.response.SsoConfiguracionResponse;
import com.fiscalizacionhse.dto.response.SsoImportPreviewResponse;
import com.fiscalizacionhse.dto.response.SsoPreguntaAdminResponse;
import com.fiscalizacionhse.dto.response.SsoTemaResponse;
import com.fiscalizacionhse.exception.BadRequestException;
import com.fiscalizacionhse.exception.ConflictException;
import com.fiscalizacionhse.exception.ResourceNotFoundException;
import com.fiscalizacionhse.model.SsoConfiguracion;
import com.fiscalizacionhse.model.SsoOpcion;
import com.fiscalizacionhse.model.SsoPregunta;
import com.fiscalizacionhse.model.SsoTema;
import com.fiscalizacionhse.model.Usuario;
import com.fiscalizacionhse.repository.SsoConfiguracionRepository;
import com.fiscalizacionhse.repository.SsoIntentoRespuestaRepository;
import com.fiscalizacionhse.repository.SsoOpcionRepository;
import com.fiscalizacionhse.repository.SsoPreguntaRepository;
import com.fiscalizacionhse.repository.SsoTemaRepository;
import com.fiscalizacionhse.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SimuladorAdminService {

    private final SsoTemaRepository temaRepository;
    private final SsoPreguntaRepository preguntaRepository;
    private final SsoOpcionRepository opcionRepository;
    private final SsoIntentoRespuestaRepository intentoRespuestaRepository;
    private final UsuarioRepository usuarioRepository;
    private final SsoConfiguracionRepository configuracionRepository;

    private static final List<Integer> TIEMPOS_PERMITIDOS = List.of(5, 10, 20, 30);

    public SsoConfiguracionResponse obtenerConfiguracion() {
        return SsoConfiguracionResponse.builder()
                .tiempoExamenMin(entero("tiempo_examen_min", entero("tiempo_20_min", 30)))
                .notaMinima(entero("nota_minima", 70))
                .tiemposPermitidos(TIEMPOS_PERMITIDOS)
                .build();
    }

    @Transactional
    public SsoConfiguracionResponse guardarConfiguracion(SsoConfiguracionRequest request) {
        int minutos = request.getTiempoExamenMin();
        if (!TIEMPOS_PERMITIDOS.contains(minutos)) {
            throw new BadRequestException("El tiempo debe ser 5, 10, 20 o 30 minutos");
        }
        guardarClave("tiempo_examen_min", String.valueOf(minutos));
        guardarClave("tiempo_20_min", String.valueOf(minutos));
        guardarClave("tiempo_40_min", String.valueOf(minutos));
        return obtenerConfiguracion();
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
        SsoTema tema = getTema(request.getTemaId());
        Usuario autor = usuarioRepository.findById(usuarioId).orElse(null);

        SsoPregunta pregunta = SsoPregunta.builder()
                .tema(tema)
                .enunciado(request.getEnunciado().trim())
                .explicacion(blankToNull(request.getExplicacion()))
                .fuente(blankToNull(request.getFuente()))
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
        SsoPregunta pregunta = preguntaRepository.findDetalleById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pregunta no encontrada"));
        pregunta.setTema(getTema(request.getTemaId()));
        pregunta.setEnunciado(request.getEnunciado().trim());
        pregunta.setExplicacion(blankToNull(request.getExplicacion()));
        pregunta.setFuente(blankToNull(request.getFuente()));
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
                existente.setArgumento(Boolean.TRUE.equals(op.getEsCorrecta())
                        ? null
                        : blankToNull(op.getArgumento()));
            }
        }
        return toAdmin(pregunta);
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
        boolean faltaArgumento = opciones.stream()
                .anyMatch(o -> !Boolean.TRUE.equals(o.getEsCorrecta())
                        && (o.getArgumento() == null || o.getArgumento().isBlank()));
        if (faltaArgumento) {
            throw new BadRequestException("Cada opción incorrecta debe indicar por qué está mal");
        }
    }

    private SsoOpcion buildOpcion(SsoPregunta pregunta, SsoPreguntaRequest.SsoOpcionRequest op) {
        boolean correcta = Boolean.TRUE.equals(op.getEsCorrecta());
        return SsoOpcion.builder()
                .pregunta(pregunta)
                .texto(op.getTexto().trim())
                .esCorrecta(correcta)
                .argumento(correcta ? null : blankToNull(op.getArgumento()))
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
                .imagen(p.getImagen())
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
