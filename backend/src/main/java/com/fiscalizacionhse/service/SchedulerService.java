package com.fiscalizacionhse.service;

import com.fiscalizacionhse.model.Recordatorio;
import com.fiscalizacionhse.repository.RecordatorioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Servicio de programación que ejecuta los recordatorios vencidos.
 * Corre cada 5 minutos revisando qué recordatorios deben dispararse.
 */
@Service
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class SchedulerService {

    private final RecordatorioRepository recordatorioRepository;
    private final NotificacionService notificacionService;

    /**
     * Ejecuta cada 5 minutos los recordatorios vencidos
     */
    @Scheduled(fixedRate = 300_000) // 5 minutos
    @Transactional
    public void ejecutarRecordatorios() {
        log.debug("⏰ Scheduler: revisando recordatorios vencidos...");

        LocalDateTime ahora = LocalDateTime.now();
        List<Recordatorio> vencidos = recordatorioRepository.findVencidos(ahora);

        if (vencidos.isEmpty()) {
            log.debug("⏰ No hay recordatorios vencidos");
            return;
        }

        log.info("⏰ Ejecutando {} recordatorio(s) vencido(s)", vencidos.size());

        for (Recordatorio recordatorio : vencidos) {
            try {
                ejecutarRecordatorio(recordatorio, ahora);
            } catch (Exception e) {
                log.error("❌ Error ejecutando recordatorio {}: {}",
                        recordatorio.getId(), e.getMessage());
            }
        }
    }

    /**
     * Revisa recordatorios que nunca se han iniciado (proximaEjecucion null)
     */
    @Scheduled(fixedRate = 600_000) // 10 minutos
    @Transactional
    public void inicializarRecordatoriosPendientes() {
        List<Recordatorio> pendientes = recordatorioRepository.findPendientesIniciar();

        if (!pendientes.isEmpty()) {
            log.info("⏰ Inicializando {} recordatorio(s) pendiente(s)", pendientes.size());

            for (Recordatorio r : pendientes) {
                try {
                    LocalDateTime proxima = LocalDateTime.of(
                            r.getFechaInicio(), r.getHoraRecordatorio());

                    if (proxima.isAfter(LocalDateTime.now()) || proxima.isEqual(LocalDateTime.now())) {
                        r.setProximaEjecucion(proxima);
                        recordatorioRepository.save(r);
                        log.info("✅ Recordatorio {} inicializado para {}", r.getId(), proxima);
                    }
                } catch (Exception e) {
                    log.error("❌ Error inicializando recordatorio {}: {}", r.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * Ejecuta un recordatorio y calcula su próxima ejecución
     */
    private void ejecutarRecordatorio(Recordatorio recordatorio, LocalDateTime ahora) {
        log.info("🔔 Ejecutando recordatorio: '{}' (ID: {})", recordatorio.getTitulo(), recordatorio.getId());

        // 1. Crear notificaciones para todos los destinatarios
        int notificaciones = notificacionService.notificarDestinatarios(recordatorio);
        log.info("🔔 {} notificaciones creadas para recordatorio '{}'", notificaciones, recordatorio.getTitulo());

        // 2. Actualizar última ejecución
        recordatorio.setUltimaEjecucion(ahora);

        // 3. Calcular próxima ejecución
        LocalDateTime proxima = calcularProximaEjecucion(recordatorio);
        recordatorio.setProximaEjecucion(proxima);

        recordatorioRepository.save(recordatorio);

        if (proxima != null) {
            log.info("📅 Próxima ejecución de '{}': {}", recordatorio.getTitulo(), proxima);
        } else {
            log.info("✅ Recordatorio '{}' finalizado (sin más ejecuciones)", recordatorio.getTitulo());
        }
    }

    private LocalDateTime calcularProximaEjecucion(Recordatorio r) {
        // Usar la lógica que ya está en RecordatorioService
        // pero simplificada aquí para el scheduler
        return switch (r.getTipoRecurrencia()) {
            case "ONE_TIME" -> null; // Una sola vez
            case "DAILY" -> r.getUltimaEjecucion()
                    .toLocalDate().plusDays(r.getIntervaloDias() != null ? r.getIntervaloDias() : 1)
                    .atTime(r.getHoraRecordatorio() != null ? r.getHoraRecordatorio() : LocalTime.of(8, 0));
            case "WEEKLY" -> {
                int diasAvanzar = 7; // una semana
                yield r.getUltimaEjecucion()
                        .toLocalDate().plusDays(diasAvanzar)
                        .atTime(r.getHoraRecordatorio() != null ? r.getHoraRecordatorio() : LocalTime.of(8, 0));
            }
            case "MONTHLY" -> r.getUltimaEjecucion()
                    .toLocalDate().plusMonths(1)
                    .atTime(r.getHoraRecordatorio() != null ? r.getHoraRecordatorio() : LocalTime.of(8, 0));
            case "CUSTOM" -> r.getUltimaEjecucion()
                    .toLocalDate().plusDays(r.getIntervaloDias() != null ? r.getIntervaloDias() : 1)
                    .atTime(r.getHoraRecordatorio() != null ? r.getHoraRecordatorio() : LocalTime.of(8, 0));
            default -> null;
        };
    }
}
