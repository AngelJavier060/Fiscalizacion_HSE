package com.fiscalizacionhse.controller;

import com.fiscalizacionhse.dto.request.SsoConfiguracionRequest;
import com.fiscalizacionhse.dto.request.SsoPreguntaRequest;
import com.fiscalizacionhse.dto.request.SsoTemaRequest;
import com.fiscalizacionhse.dto.response.SsoCoberturaResponse;
import com.fiscalizacionhse.dto.response.SsoConfiguracionResponse;
import com.fiscalizacionhse.dto.response.SsoEstadisticasResponse;
import com.fiscalizacionhse.dto.response.SsoImportPreviewResponse;
import com.fiscalizacionhse.dto.response.SsoPreguntaAdminResponse;
import com.fiscalizacionhse.dto.response.SsoTemaResponse;
import com.fiscalizacionhse.service.SimuladorAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/simulador/admin")
@RequiredArgsConstructor
public class SimuladorAdminController {

    private final SimuladorAdminService adminService;

    @GetMapping("/configuracion")
    public ResponseEntity<SsoConfiguracionResponse> configuracion() {
        return ResponseEntity.ok(adminService.obtenerConfiguracion());
    }

    @PutMapping("/configuracion")
    public ResponseEntity<SsoConfiguracionResponse> guardarConfiguracion(
            @Valid @RequestBody SsoConfiguracionRequest request) {
        return ResponseEntity.ok(adminService.guardarConfiguracion(request));
    }

    @GetMapping("/cobertura")
    public ResponseEntity<SsoCoberturaResponse> cobertura() {
        return ResponseEntity.ok(adminService.cobertura());
    }

    @GetMapping("/estadisticas")
    public ResponseEntity<SsoEstadisticasResponse> estadisticas(
            @RequestParam(required = false, defaultValue = "0") Integer dias) {
        return ResponseEntity.ok(adminService.estadisticas(dias));
    }

    @GetMapping("/temas")
    public ResponseEntity<List<SsoTemaResponse>> temas() {
        return ResponseEntity.ok(adminService.listarTemas());
    }

    @PostMapping("/temas")
    public ResponseEntity<SsoTemaResponse> crearTema(@Valid @RequestBody SsoTemaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.crearTema(request));
    }

    @PutMapping("/temas/{id}")
    public ResponseEntity<SsoTemaResponse> actualizarTema(
            @PathVariable Long id, @Valid @RequestBody SsoTemaRequest request) {
        return ResponseEntity.ok(adminService.actualizarTema(id, request));
    }

    @GetMapping("/preguntas")
    public ResponseEntity<List<SsoPreguntaAdminResponse>> preguntas(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long temaId,
            @RequestParam(required = false) Boolean activo) {
        return ResponseEntity.ok(adminService.listarPreguntas(q, temaId, activo));
    }

    @GetMapping("/preguntas/{id}")
    public ResponseEntity<SsoPreguntaAdminResponse> pregunta(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.obtenerPregunta(id));
    }

    @PostMapping("/preguntas")
    public ResponseEntity<SsoPreguntaAdminResponse> crearPregunta(
            @Valid @RequestBody SsoPreguntaRequest request, Authentication auth) {
        Long usuarioId = Long.parseLong(auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminService.crearPregunta(request, usuarioId));
    }

    @PutMapping("/preguntas/{id}")
    public ResponseEntity<SsoPreguntaAdminResponse> actualizarPregunta(
            @PathVariable Long id, @Valid @RequestBody SsoPreguntaRequest request) {
        return ResponseEntity.ok(adminService.actualizarPregunta(id, request));
    }

    @PatchMapping("/preguntas/{id}/activo")
    public ResponseEntity<SsoPreguntaAdminResponse> togglePregunta(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.togglePregunta(id));
    }

    @PostMapping(value = "/preguntas/{id}/sustento", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SsoPreguntaAdminResponse> subirSustento(
            @PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(adminService.guardarSustento(id, file));
    }

    @DeleteMapping("/preguntas/{id}/sustento")
    public ResponseEntity<SsoPreguntaAdminResponse> eliminarSustento(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.eliminarSustento(id));
    }

    @GetMapping("/preguntas/{id}/sustento")
    public ResponseEntity<Resource> verSustentoAdmin(@PathVariable Long id) {
        return archivoSustento(adminService.leerSustento(id));
    }

    static ResponseEntity<Resource> archivoSustento(SimuladorAdminService.SustentoArchivo arch) {
        ContentDisposition cd = ContentDisposition.inline()
                .filename(arch.nombre(), StandardCharsets.UTF_8)
                .build();
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(arch.tipoMime());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(mediaType)
                .body(arch.resource());
    }

    @PostMapping("/preguntas/importar/preview")
    public ResponseEntity<SsoImportPreviewResponse> preview(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(adminService.previsualizarImportacion(file));
    }

    @PostMapping("/preguntas/importar")
    public ResponseEntity<SsoImportPreviewResponse> importar(
            @RequestBody SsoImportPreviewResponse preview, Authentication auth) {
        Long usuarioId = Long.parseLong(auth.getName());
        return ResponseEntity.ok(adminService.confirmarImportacion(preview, usuarioId));
    }
}
