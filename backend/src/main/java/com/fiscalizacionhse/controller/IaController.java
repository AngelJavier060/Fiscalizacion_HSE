package com.fiscalizacionhse.controller;

import com.fiscalizacionhse.dto.response.AnalizarReporteResponse;
import com.fiscalizacionhse.dto.response.BusquedaAsistidaResponse;
import com.fiscalizacionhse.dto.response.BusquedaIaResponse;
import com.fiscalizacionhse.dto.response.ConsultaIaResponse;
import com.fiscalizacionhse.model.ConsultaIa;
import com.fiscalizacionhse.repository.ConsultaIaRepository;
import com.fiscalizacionhse.service.AnalizarReporteService;
import com.fiscalizacionhse.service.IaBusquedaService;
import com.fiscalizacionhse.service.IaChatService;
import com.fiscalizacionhse.service.OllamaClient;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ia")
@RequiredArgsConstructor
public class IaController {

    private final IaBusquedaService busquedaService;
    private final IaChatService chatService;
    private final ConsultaIaRepository consultaIaRepository;
    private final AnalizarReporteService analizarReporteService;
    private final OllamaClient ollamaClient;

    /**
     * Consultar con RAG: pregunta + contexto de documentos
     */
    @PostMapping("/consultar")
    public ResponseEntity<BusquedaIaResponse> consultar(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        String pregunta = body.get("pregunta") != null ? body.get("pregunta").toString().trim() : "";
        long empresaId = parseLongObj(body.get("empresaId"), 0L);
        Long usuarioId = Long.parseLong(authentication.getName());
        Long documentoId = parseDocumentoId(body.get("documentoId"));
        String modelo = body.get("modelo") != null ? body.get("modelo").toString().trim() : "";

        if (pregunta.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (empresaId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        String modeloFinal = "";

        return ResponseEntity.ok(busquedaService.consultarConRag(pregunta, empresaId, usuarioId, documentoId, modeloFinal));
    }

    /** Comprobación rápida de que el backend está arriba (sin autenticación). */
    @GetMapping("/salud")
    public ResponseEntity<Map<String, Object>> salud() {
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "servicio", "FISCALIZA-AI",
                "mensaje", "Backend activo en puerto 8080"));
    }

    /**
     * Estado del asistente para una empresa (motor DeepSeek, documentos e índice).
     */
    @GetMapping("/estado/{empresaId}")
    public ResponseEntity<Map<String, Object>> estado(@PathVariable Long empresaId) {
        if (empresaId == null || empresaId <= 0) {
            return ResponseEntity.badRequest().build();
        }
        Map<String, Object> estado = new LinkedHashMap<>(busquedaService.estadoEmpresa(empresaId));
        OllamaClient.EstadoOllama ollama = ollamaClient.estado();
        estado.put("motor", ollama.listo() ? "ollama" : "interno");
        estado.put("ollamaActivo", ollama.procesoActivo());
        estado.put("ollamaListo", ollama.listo());
        estado.put("ollamaModelo", ollama.modelo());
        estado.put("ollamaMensaje", ollama.mensaje());
        return ResponseEntity.ok(estado);
    }

    /** Estado de Ollama en este servidor (sin enviar documentos). */
    @GetMapping("/ollama")
    public ResponseEntity<Map<String, Object>> estadoOllama() {
        OllamaClient.EstadoOllama ollama = ollamaClient.estado();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("procesoActivo", ollama.procesoActivo());
        body.put("listo", ollama.listo());
        body.put("modelo", ollama.modelo());
        body.put("modelos", ollama.modelos());
        body.put("mensaje", ollama.mensaje());
        body.put("baseUrl", ollamaClient.baseUrl());
        return ResponseEntity.ok(body);
    }

    /**
     * Cruza un informe de campo (texto pegado o archivo) con los PDF de la empresa
     * y propone un borrador. Si Ollama está en este servidor, redacta con ese motor.
     */
    @PostMapping(value = "/analizar-reporte", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnalizarReporteResponse> analizarReporte(
            @RequestParam("empresaId") long empresaId,
            @RequestParam(value = "texto", required = false) String texto,
            @RequestParam(value = "archivo", required = false) MultipartFile archivo) {
        return ResponseEntity.ok(analizarReporteService.analizar(empresaId, texto, archivo));
    }

    private static long parseLongObj(Object raw, long defaultVal) {
        if (raw == null) {
            return defaultVal;
        }
        if (raw instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(raw.toString().trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private static Long parseDocumentoId(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            long v = n.longValue();
            return v > 0 ? v : null;
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            long v = Long.parseLong(s);
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Búsqueda semántica (solo resultados, sin respuesta generada)
     */
    @PostMapping("/buscar")
    public ResponseEntity<?> buscar(
            @RequestBody Map<String, String> body) {
        String consulta = body.getOrDefault("consulta", "");
        Long empresaId = Long.parseLong(body.getOrDefault("empresaId", "0"));
        int limite = Integer.parseInt(body.getOrDefault("limite", "10"));

        if (consulta.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(busquedaService.buscar(consulta, empresaId, limite));
    }

    /**
     * Búsqueda asistida: fragmentos internos + análisis DeepSeek (solo contexto de sus PDF).
     */
    @PostMapping("/buscar-asistido")
    public ResponseEntity<BusquedaAsistidaResponse> buscarAsistido(
            @RequestBody Map<String, String> body) {
        String consulta = body.getOrDefault("consulta", "").trim();
        Long empresaId = Long.parseLong(body.getOrDefault("empresaId", "0"));
        int limite = Integer.parseInt(body.getOrDefault("limite", "10"));

        if (consulta.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(busquedaService.buscarAsistido(consulta, empresaId, limite));
    }

    /**
     * Generar resumen de un documento
     */
    @PostMapping("/resumir/{documentoId}")
    public ResponseEntity<?> resumir(@PathVariable Long documentoId) {
        // Implementación básica - se puede expandir
        return ResponseEntity.ok(Map.of(
                "mensaje", "Resumen tomado del texto extraído del PDF",
                "documentoId", documentoId
        ));
    }

    /**
     * Indexar un documento (generar embeddings)
     */
    @PostMapping("/indexar/{documentoId}")
    public ResponseEntity<?> indexarDocumento(@PathVariable Long documentoId) {
        int chunks = busquedaService.indexarDocumento(documentoId);
        return ResponseEntity.ok(Map.of(
                "mensaje", "Documento indexado correctamente",
                "chunksGenerados", chunks
        ));
    }

    /**
     * Indexar toda una empresa
     */
    @PostMapping("/indexar/empresa/{empresaId}")
    public ResponseEntity<?> indexarEmpresa(@PathVariable Long empresaId) {
        int total = busquedaService.indexarEmpresa(empresaId);
        return ResponseEntity.ok(Map.of(
                "mensaje", "Empresa indexada correctamente",
                "totalChunks", total
        ));
    }

    /**
     * Historial de consultas del usuario
     */
    @GetMapping("/historial")
    public ResponseEntity<Page<ConsultaIaResponse>> historial(
            Authentication authentication,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long usuarioId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(
                consultaIaRepository.findByUsuarioIdOrderByCreatedAtDesc(usuarioId, pageable)
                        .map(this::toResponse)
        );
    }

    /**
     * Historial de consultas de una empresa (Admin)
     */
    @GetMapping("/historial/empresa/{empresaId}")
    public ResponseEntity<Page<ConsultaIaResponse>> historialEmpresa(
            @PathVariable Long empresaId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                consultaIaRepository.findByEmpresaIdOrderByCreatedAtDesc(empresaId, pageable)
                        .map(this::toResponse)
        );
    }

    private ConsultaIaResponse toResponse(ConsultaIa c) {
        return ConsultaIaResponse.builder()
                .id(c.getId())
                .pregunta(c.getPregunta())
                .respuesta(c.getRespuesta())
                .documentosReferencia(c.getDocumentosReferencia())
                .tipo(c.getTipo())
                .feedback(c.getFeedback())
                .empresaId(c.getEmpresa().getId())
                .empresaNombre(c.getEmpresa().getNombre())
                .usuarioId(c.getUsuario().getId())
                .usuarioNombre(c.getUsuario().getNombre())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
