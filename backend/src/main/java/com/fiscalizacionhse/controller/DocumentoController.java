package com.fiscalizacionhse.controller;

import com.fiscalizacionhse.dto.request.DocumentoRequest;
import com.fiscalizacionhse.dto.response.DocumentoResponse;
import com.fiscalizacionhse.dto.response.PuntoClaveResponse;
import com.fiscalizacionhse.dto.response.TextoCompletoResponse;
import com.fiscalizacionhse.security.AuthPrincipalIds;
import com.fiscalizacionhse.service.DocumentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/documentos")
@RequiredArgsConstructor
public class DocumentoController {

    private final DocumentoService documentoService;

    /**
     * Listar documentos de una empresa
     */
    @GetMapping("/empresa/{empresaId}")
    public ResponseEntity<Page<DocumentoResponse>> listarPorEmpresa(
            @PathVariable Long empresaId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(documentoService.listarPorEmpresa(empresaId, pageable));
    }

    /**
     * Buscar documentos por término
     */
    @GetMapping("/buscar/{empresaId}")
    public ResponseEntity<Page<DocumentoResponse>> buscar(
            @PathVariable Long empresaId,
            @RequestParam(required = false) String q,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(documentoService.buscar(empresaId, q, pageable));
    }

    /**
     * Obtener un documento por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<DocumentoResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(documentoService.obtener(id));
    }

    /**
     * Rasteriza una página del PDF como PNG (diagramas, tablas como imagen dentro del mismo documento).
     * Autenticado con JWT igual que {@link #verArchivoPdf}; no sustituye al PDF completo.
     */
    @GetMapping(value = "/{id}/paginas/{pagina}/preview", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> vistaPreviaPagina(@PathVariable Long id, @PathVariable int pagina) {
        byte[] png = documentoService.obtenerPreviewPaginaPng(id, pagina);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store, private")
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }

    /**
     * Descargar / visualizar el PDF (inline).
     */
    @GetMapping("/{id}/archivo")
    public ResponseEntity<Resource> verArchivoPdf(@PathVariable Long id) {
        DocumentoService.PdfArchivo arch = documentoService.obtenerArchivoPdf(id);
        ContentDisposition cd = ContentDisposition.inline()
                .filename(arch.nombreArchivo(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(MediaType.parseMediaType(arch.tipoMime()))
                .body(arch.resource());
    }

    /**
     * Subir un documento PDF con procesamiento completo
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentoResponse> subirDocumento(
            @RequestParam("titulo") String titulo,
            @RequestParam(value = "descripcion", required = false) String descripcion,
            @RequestParam("empresaId") Long empresaId,
            @RequestParam("archivo") MultipartFile archivo,
            Authentication authentication) {

        Long usuarioId = Long.parseLong(authentication.getName());

        DocumentoRequest request = new DocumentoRequest();
        request.setTitulo(titulo);
        request.setDescripcion(descripcion);
        request.setEmpresaId(empresaId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentoService.subirDocumento(request, archivo, usuarioId));
    }

    /**
     * Actualizar metadata del documento
     */
    @PutMapping("/{id}")
    public ResponseEntity<DocumentoResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody DocumentoRequest request,
            Authentication authentication) {
        long usuarioId = AuthPrincipalIds.usuarioId(authentication);
        return ResponseEntity.ok(documentoService.actualizar(id, request, usuarioId));
    }

    /**
     * Eliminar documento (borrado lógico)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication authentication) {
        long usuarioId = AuthPrincipalIds.usuarioId(authentication);
        documentoService.eliminar(id, usuarioId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Obtener el texto completo extraído del PDF con secciones detectadas.
     * Útil para el modo de lectura del documento.
     */
    @GetMapping("/{id}/texto-completo")
    public ResponseEntity<TextoCompletoResponse> obtenerTextoCompleto(@PathVariable Long id) {
        return ResponseEntity.ok(documentoService.obtenerTextoCompleto(id));
    }

    /**
     * Regenerar puntos clave con IA
     */
    @PostMapping("/{id}/regenerar-puntos-ia")
    public ResponseEntity<List<PuntoClaveResponse>> regenerarPuntosIa(@PathVariable Long id) {
        return ResponseEntity.ok(documentoService.regenerarPuntosIa(id));
    }

    /**
     * Actualizar el texto extraído editado manualmente
     */
    @PutMapping("/{id}/texto-extraido")
    public ResponseEntity<DocumentoResponse> actualizarTextoExtraido(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body,
            Authentication authentication) {
        long usuarioId = AuthPrincipalIds.usuarioId(authentication);
        String texto = body.getOrDefault("texto", "");
        return ResponseEntity.ok(documentoService.actualizarTextoExtraido(id, texto, usuarioId));
    }
}
