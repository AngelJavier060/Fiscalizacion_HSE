package com.fiscalizacionhse.service;

import com.fiscalizacionhse.model.PuntoClave;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Puntos IA y traducción fuera de la cola de extracción de texto.
 * Así un PDF corto no espera minutos detrás de DeepSeek de un libro grande.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentoIaBackgroundService {

    private final DocumentoProcesamientoPersistence procesamientoPersistence;
    private final TraduccionService traduccionService;
    private final IaService iaService;

    @Async("documentoIaExecutor")
    public void generarPuntosTrasTexto(Long documentoId, String titulo, String textoExtraido, String idiomaDetectado) {
        if (textoExtraido == null || textoExtraido.isBlank()) {
            log.info("Sin texto para IA en documento {}", documentoId);
            return;
        }
        try {
            String textoTraducido = null;
            boolean requiereTraduccion = idiomaDetectado != null
                    && !"es".equals(idiomaDetectado)
                    && !"und".equals(idiomaDetectado)
                    && !"desconocido".equals(idiomaDetectado);
            if (requiereTraduccion) {
                try {
                    textoTraducido = traduccionService.traducirAIngles(textoExtraido, idiomaDetectado);
                    procesamientoPersistence.guardarTraduccion(documentoId, textoTraducido);
                } catch (Exception e) {
                    log.warn("Traducción en segundo plano falló (doc {}): {}", documentoId, e.getMessage());
                }
            }

            String textoParaAnalisis = (textoTraducido != null && !textoTraducido.isBlank())
                    ? textoTraducido
                    : textoExtraido;

            List<IaService.PuntoClaveIa> puntosIa = iaService.extraerPuntosClaveSubidaRapida(
                    titulo, textoParaAnalisis);
            if (puntosIa.isEmpty()) {
                return;
            }
            List<PuntoClave> puntosGuardados = new ArrayList<>();
            for (IaService.PuntoClaveIa puntoIa : puntosIa) {
                puntosGuardados.add(PuntoClave.builder()
                        .contenido(puntoIa.getContenido())
                        .titulo(puntoIa.getTitulo())
                        .tema(puntoIa.getTema())
                        .codigo(puntoIa.getCodigo())
                        .tipo(puntoIa.getTipo() != null ? puntoIa.getTipo() : "GENERAL")
                        .orden(puntoIa.getOrden())
                        .esIa(true)
                        .confianzaIa(puntoIa.getConfianza() != null
                                ? puntoIa.getConfianza()
                                : BigDecimal.valueOf(0.85))
                        .revisado(false)
                        .build());
            }
            procesamientoPersistence.guardarPuntosIa(documentoId, puntosGuardados);
            log.info("✅ IA en segundo plano: {} puntos para doc {}", puntosGuardados.size(), documentoId);
        } catch (Exception e) {
            log.error("❌ IA en segundo plano falló (doc {}): {}", documentoId, e.getMessage());
        }
    }
}
