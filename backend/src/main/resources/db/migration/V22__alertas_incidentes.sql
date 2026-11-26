-- Alertas de incidentes registradas desde Super Admin.

CREATE TABLE IF NOT EXISTS alertas_incidentes (
    id                    BIGSERIAL PRIMARY KEY,
    codigo                VARCHAR(80)  NOT NULL,
    titulo                VARCHAR(500) NOT NULL,
    unidad_negocio        VARCHAR(255) NOT NULL,
    ambito                VARCHAR(255) NOT NULL,
    fecha                 DATE         NOT NULL,
    area                  VARCHAR(255) NOT NULL,
    dueno_area            VARCHAR(255) NOT NULL,
    hora                  TIME         NOT NULL,
    empresa_involucrada   VARCHAR(255) NOT NULL,
    impacto               VARCHAR(40)  NOT NULL,
    accidente_grave       BOOLEAN      NOT NULL,
    iap                   BOOLEAN      NOT NULL,
    eorpv                 VARCHAR(255) NOT NULL,
    archivo_nombre        VARCHAR(255),
    archivo_ruta          TEXT,
    prioridad             VARCHAR(10)  NOT NULL,
    creado_por            BIGINT REFERENCES usuarios(id),
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_alertas_incidentes_codigo UNIQUE (codigo),
    CONSTRAINT ck_alertas_impacto CHECK (impacto IN ('Sin lesión', 'Con lesión')),
    CONSTRAINT ck_alertas_prioridad CHECK (prioridad IN ('alta', 'media'))
);

CREATE INDEX IF NOT EXISTS idx_alertas_incidentes_fecha ON alertas_incidentes (fecha DESC);
CREATE INDEX IF NOT EXISTS idx_alertas_incidentes_ambito ON alertas_incidentes (ambito);
