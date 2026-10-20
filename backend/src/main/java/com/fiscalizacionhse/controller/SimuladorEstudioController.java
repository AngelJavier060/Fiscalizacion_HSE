package com.fiscalizacionhse.controller;

import com.fiscalizacionhse.dto.response.SsoEstudioPreguntaResponse;
import com.fiscalizacionhse.dto.response.SsoTemaResponse;
import com.fiscalizacionhse.service.SimuladorEstudioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/simulador/estudio")
@RequiredArgsConstructor
public class SimuladorEstudioController {

    private final SimuladorEstudioService estudioService;

    @GetMapping("/temas")
    public ResponseEntity<List<SsoTemaResponse>> temas() {
        return ResponseEntity.ok(estudioService.listarTemas());
    }

    @GetMapping("/preguntas")
    public ResponseEntity<List<SsoEstudioPreguntaResponse>> preguntas(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long temaId) {
        return ResponseEntity.ok(estudioService.listarPreguntas(q, temaId));
    }
}
