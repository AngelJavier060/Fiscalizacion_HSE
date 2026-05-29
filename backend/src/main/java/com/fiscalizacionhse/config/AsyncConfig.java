package com.fiscalizacionhse.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Configuration
@EnableAsync
@EnableScheduling
@Slf4j
public class AsyncConfig {

    /**
     * Cola de procesamiento PDF (extracción + IA).
     * maxPoolSize=2 reduce presión de memoria en VPS; cola grande absorbe ráfagas de subida masiva.
     */
    @Bean(name = "documentoTaskExecutor")
    public Executor documentoTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("doc-proc-");
        executor.setRejectedExecutionHandler((runnable, pool) -> {
            log.error(
                    "Cola de procesamiento PDF llena (activos={}, cola={}). "
                            + "El documento quedará PROCESANDO; el job de recuperación lo reintentará.",
                    pool.getActiveCount(), pool.getQueue().size());
            throw new RejectedExecutionException("Cola de procesamiento PDF saturada");
        });
        executor.initialize();
        return executor;
    }
}
