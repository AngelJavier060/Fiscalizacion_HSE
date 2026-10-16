package com.fiscalizacionhse.service;

import com.fiscalizacionhse.dto.response.ExtraccionProgresoResponse;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Progreso en memoria de la extracción de letras (página a página).
 */
@Component
public class DocumentoExtraccionProgresoStore {

    static final int MAX_TEXTO_EN_PROGRESO = 1_500_000;

    private final Map<Long, ExtraccionProgresoResponse> porDocumento = new ConcurrentHashMap<>();

    public void enCola(Long documentoId) {
        porDocumento.put(documentoId, base(documentoId)
                .fase("EN_COLA")
                .mensaje("En cola detrás de un libro grande. Los PDF de hasta 25 MB extraen letras al subir.")
                .porcentaje(4)
                .build());
    }

    public void abriendoPdf(Long documentoId) {
        porDocumento.put(documentoId, base(documentoId)
                .fase("ABRIENDO")
                .mensaje("Abriendo el PDF…")
                .porcentaje(8)
                .build());
    }

    public void pagina(Long documentoId, int paginaActual, int paginasTotal) {
        int total = Math.max(paginasTotal, 1);
        int pct = 10 + (int) Math.round((paginaActual / (double) total) * 85);
        porDocumento.put(documentoId, base(documentoId)
                .fase("EXTRAYENDO")
                .mensaje("Extrayendo letras · página " + paginaActual + " de " + total)
                .paginaActual(paginaActual)
                .paginasTotal(total)
                .porcentaje(Math.min(pct, 95))
                .build());
    }

    public void ocr(Long documentoId, int paginaActual, int paginasTotal) {
        int total = Math.max(paginasTotal, 1);
        int pct = 10 + (int) Math.round((paginaActual / (double) total) * 85);
        porDocumento.put(documentoId, base(documentoId)
                .fase("OCR")
                .mensaje("Pasando imagen a letras · página " + paginaActual + " de " + total)
                .paginaActual(paginaActual)
                .paginasTotal(total)
                .porcentaje(Math.min(pct, 95))
                .build());
    }

    public void listo(Long documentoId, String texto) {
        String plano = texto == null ? "" : texto;
        int caracteres = plano.length();
        String paraUi = caracteres > MAX_TEXTO_EN_PROGRESO
                ? plano.substring(0, MAX_TEXTO_EN_PROGRESO)
                : plano;
        porDocumento.put(documentoId, base(documentoId)
                .fase("LISTO")
                .mensaje(caracteres > 0
                        ? "Letras listas · " + caracteres + " caracteres"
                        : "Extracción terminada")
                .porcentaje(100)
                .caracteres(caracteres)
                .textoPlano(paraUi)
                .build());
    }

    public void sinTexto(Long documentoId) {
        porDocumento.put(documentoId, base(documentoId)
                .fase("SIN_TEXTO")
                .mensaje("No se leyeron letras. Si es una foto, pulse Reprocesar PDF para pasar imagen a letras.")
                .porcentaje(100)
                .caracteres(0)
                .build());
    }

    public void error(Long documentoId, String mensaje) {
        porDocumento.put(documentoId, base(documentoId)
                .fase("ERROR")
                .mensaje(mensaje != null ? mensaje : "Error al extraer letras")
                .porcentaje(0)
                .build());
    }

    public ExtraccionProgresoResponse consultar(Long documentoId) {
        return porDocumento.get(documentoId);
    }

    private static ExtraccionProgresoResponse.ExtraccionProgresoResponseBuilder base(Long documentoId) {
        return ExtraccionProgresoResponse.builder()
                .documentoId(documentoId)
                .paginaActual(0)
                .paginasTotal(0)
                .caracteres(0);
    }
}
