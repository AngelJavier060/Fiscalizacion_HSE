-- ============================================
-- FISCALIZACION HSE - FASE 3: RECORDATORIOS
-- PostgreSQL
-- ============================================

-- ============================================
-- TABLA: recordatorios
-- ============================================
CREATE TABLE recordatorios (
    id                    BIGSERIAL PRIMARY KEY,
    titulo                VARCHAR(300) NOT NULL,
    descripcion           TEXT,
    tipo_recurrencia      VARCHAR(20) NOT NULL DEFAULT 'ONE_TIME'
                          CHECK (tipo_recurrencia IN ('ONE_TIME', 'DAILY', 'WEEKLY', 'MONTHLY', 'CUSTOM')),
    intervalo_dias        INTEGER DEFAULT 1,
    dia_semana            INTEGER CHECK (dia_semana BETWEEN 0 AND 6),
    dia_mes               INTEGER CHECK (dia_mes BETWEEN 1 AND 31),
    fecha_inicio          DATE NOT NULL,
    fecha_fin             DATE,
    hora_recordatorio     TIME NOT NULL DEFAULT '08:00:00',
    proxima_ejecucion     TIMESTAMP WITH TIME ZONE,
    ultima_ejecucion      TIMESTAMP WITH TIME ZONE,
    incluir_audio         BOOLEAN NOT NULL DEFAULT FALSE,
    mensaje_personalizado TEXT,
    activo                BOOLEAN NOT NULL DEFAULT TRUE,
    documento_id          BIGINT REFERENCES documentos(id) ON DELETE SET NULL,
    empresa_id            BIGINT NOT NULL REFERENCES empresas(id) ON DELETE CASCADE,
    creado_por            BIGINT NOT NULL REFERENCES usuarios(id),
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recordatorios_empresa ON recordatorios(empresa_id);
CREATE INDEX idx_recordatorios_creado_por ON recordatorios(creado_por);
CREATE INDEX idx_recordatorios_activo ON recordatorios(activo);
CREATE INDEX idx_recordatorios_proxima_ejecucion ON recordatorios(proxima_ejecucion);
CREATE INDEX idx_recordatorios_documento ON recordatorios(documento_id);

-- ============================================
-- TABLA: recordatorio_usuarios (destinatarios)
-- ============================================
CREATE TABLE recordatorio_usuarios (
    id                BIGSERIAL PRIMARY KEY,
    recordatorio_id   BIGINT NOT NULL REFERENCES recordatorios(id) ON DELETE CASCADE,
    usuario_id        BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(recordatorio_id, usuario_id)
);

CREATE INDEX idx_rec_usuarios_recordatorio ON recordatorio_usuarios(recordatorio_id);
CREATE INDEX idx_rec_usuarios_usuario ON recordatorio_usuarios(usuario_id);

-- ============================================
-- TABLA: notificaciones
-- ============================================
CREATE TABLE notificaciones (
    id                BIGSERIAL PRIMARY KEY,
    titulo            VARCHAR(300) NOT NULL,
    mensaje           TEXT NOT NULL,
    tipo              VARCHAR(30) NOT NULL DEFAULT 'RECORDATORIO'
                      CHECK (tipo IN ('RECORDATORIO', 'DOCUMENTO', 'SISTEMA', 'ALERTA')),
    leida             BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_lectura     TIMESTAMP WITH TIME ZONE,
    tiene_audio       BOOLEAN NOT NULL DEFAULT FALSE,
    ruta_audio        VARCHAR(1000),
    duracion_audio    INTEGER, -- segundos
    usuario_id        BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    recordatorio_id   BIGINT REFERENCES recordatorios(id) ON DELETE SET NULL,
    documento_id      BIGINT REFERENCES documentos(id) ON DELETE SET NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notificaciones_usuario ON notificaciones(usuario_id);
CREATE INDEX idx_notificaciones_leida ON notificaciones(leida);
CREATE INDEX idx_notificaciones_created_at ON notificaciones(created_at DESC);
CREATE INDEX idx_notificaciones_tipo ON notificaciones(tipo);
