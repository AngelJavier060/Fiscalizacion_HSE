-- ============================================
-- FISCALIZACION HSE - FASE 2: DOCUMENTOS
-- PostgreSQL
-- ============================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ============================================
-- TABLA: documentos
-- ============================================
CREATE TABLE documentos (
    id                    BIGSERIAL PRIMARY KEY,
    titulo                VARCHAR(500) NOT NULL,
    descripcion           TEXT,
    archivo_nombre        VARCHAR(500) NOT NULL,
    archivo_tipo          VARCHAR(100) NOT NULL DEFAULT 'application/pdf',
    archivo_tamano        BIGINT NOT NULL DEFAULT 0,
    ruta_archivo          VARCHAR(1000) NOT NULL,
    texto_extraido        TEXT,
    texto_traducido       TEXT,
    idioma_original       VARCHAR(10) DEFAULT 'es',
    idioma_detectado      VARCHAR(10),
    requiere_traduccion   BOOLEAN NOT NULL DEFAULT FALSE,
    traducido             BOOLEAN NOT NULL DEFAULT FALSE,
    puntos_generados_ia   BOOLEAN NOT NULL DEFAULT FALSE,
    activo                BOOLEAN NOT NULL DEFAULT TRUE,
    empresa_id            BIGINT NOT NULL REFERENCES empresas(id) ON DELETE CASCADE,
    subido_por            BIGINT NOT NULL REFERENCES usuarios(id),
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_documentos_empresa ON documentos(empresa_id);
CREATE INDEX idx_documentos_subido_por ON documentos(subido_por);
CREATE INDEX idx_documentos_activo ON documentos(activo);
CREATE INDEX idx_documentos_idioma ON documentos(idioma_original);
CREATE INDEX idx_documentos_titulo_trgm ON documentos USING gin (titulo gin_trgm_ops);
CREATE INDEX idx_documentos_fulltext ON documentos USING gin (to_tsvector('spanish', 
    coalesce(texto_extraido, '') || ' ' || coalesce(texto_traducido, '') || ' ' || coalesce(titulo, '')
));

-- ============================================
-- TABLA: puntos_clave
-- ============================================
CREATE TABLE puntos_clave (
    id              BIGSERIAL PRIMARY KEY,
    contenido       TEXT NOT NULL,
    orden           INTEGER NOT NULL DEFAULT 0,
    es_ia           BOOLEAN NOT NULL DEFAULT FALSE,
    confianza_ia    NUMERIC(5,2),
    revisado        BOOLEAN NOT NULL DEFAULT FALSE,
    documento_id    BIGINT NOT NULL REFERENCES documentos(id) ON DELETE CASCADE,
    creado_por      BIGINT REFERENCES usuarios(id),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_puntos_clave_documento ON puntos_clave(documento_id);
CREATE INDEX idx_puntos_clave_creado_por ON puntos_clave(creado_por);
CREATE INDEX idx_puntos_clave_revisado ON puntos_clave(revisado);
