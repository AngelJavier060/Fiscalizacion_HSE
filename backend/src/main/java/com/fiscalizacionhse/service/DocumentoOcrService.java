package com.fiscalizacionhse.service;

import io.github.mymonstercat.Model;
import io.github.mymonstercat.ocr.InferenceEngine;
import com.benjaminwan.ocrlibrary.OcrResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pasa imagen a letras (OCR) cuando el PDF no trae capa de texto.
 * Motor local RapidOCR / PaddleOCR, sin instalar Tesseract.
 */
@Service
@Slf4j
public class DocumentoOcrService {

    private final AtomicBoolean listo = new AtomicBoolean(false);
    private final AtomicBoolean falloCarga = new AtomicBoolean(false);
    private volatile InferenceEngine engine;

    public boolean disponible() {
        return motor() != null;
    }

    public String reconocerPng(byte[] png) {
        InferenceEngine ocr = motor();
        if (ocr == null || png == null || png.length == 0) {
            return "";
        }
        Path tmp = null;
        try {
            tmp = Files.createTempFile("hse-ocr-", ".png");
            Files.write(tmp, png);
            OcrResult resultado = ocr.runOcr(tmp.toAbsolutePath().toString());
            if (resultado == null || resultado.getStrRes() == null) {
                return "";
            }
            return resultado.getStrRes().replace('\r', '\n').trim();
        } catch (Exception e) {
            log.warn("OCR falló: {}", e.getMessage());
            return "";
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (Exception ignored) {
                    // temp
                }
            }
        }
    }

    private InferenceEngine motor() {
        if (falloCarga.get()) {
            return null;
        }
        if (engine != null) {
            return engine;
        }
        synchronized (this) {
            if (engine != null) {
                return engine;
            }
            if (falloCarga.get()) {
                return null;
            }
            try {
                log.info("🔎 Cargando motor OCR (imagen → letras)…");
                engine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V3);
                listo.set(true);
                log.info("✅ Motor OCR listo");
                return engine;
            } catch (Throwable t) {
                falloCarga.set(true);
                log.error("❌ No se pudo iniciar OCR: {}", t.getMessage(), t);
                return null;
            }
        }
    }
}
