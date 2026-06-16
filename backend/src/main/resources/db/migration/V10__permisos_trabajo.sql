-- ============================================================
-- V10: Permisos de trabajo HSE
-- ============================================================

CREATE TABLE permisos_trabajo (
    id                  VARCHAR(40) PRIMARY KEY,
    title               VARCHAR(255) NOT NULL,
    area                VARCHAR(255) NOT NULL DEFAULT 'Sin asignar',
    responsible         VARCHAR(255) NOT NULL DEFAULT 'Sin asignar',
    start_date          TIMESTAMP WITH TIME ZONE NOT NULL,
    end_date            TIMESTAMP WITH TIME ZONE NOT NULL,
    image_path          TEXT,
    critical_task       VARCHAR(30),
    description         TEXT,
    emisor              VARCHAR(255),
    ejecutante          VARCHAR(255),
    empresa_ejecutante  VARCHAR(255),
    nota                TEXT,
    start_time          TIMESTAMP WITH TIME ZONE,
    end_time            TIMESTAMP WITH TIME ZONE,
    empresa_id          BIGINT NOT NULL REFERENCES empresas(id) ON DELETE CASCADE,
    creado_por          BIGINT NOT NULL REFERENCES usuarios(id),
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_permisos_empresa ON permisos_trabajo(empresa_id);
CREATE INDEX idx_permisos_fechas ON permisos_trabajo(start_date, end_date);
CREATE INDEX idx_permisos_activo ON permisos_trabajo(activo);
CREATE INDEX idx_permisos_critical_task ON permisos_trabajo(critical_task);
CREATE INDEX idx_permisos_creado_por ON permisos_trabajo(creado_por);
