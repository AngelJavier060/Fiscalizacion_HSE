-- ============================================
-- FISCALIZACION HSE - FASE 4: IA + EMBEDDINGS
-- PostgreSQL
-- ============================================

-- ============================================
-- TABLA: ia_consultas (historial de preguntas)
-- ============================================
CREATE TABLE ia_consultas (
    id                    BIGSERIAL PRIMARY KEY,
    pregunta              TEXT NOT NULL,
    respuesta             TEXT NOT NULL,
    documentos_referencia JSONB,
    tipo                  VARCHAR(30) NOT NULL DEFAULT 'CONSULTA'
                          CHECK (tipo IN ('CONSULTA', 'BUSQUEDA', 'RESUMEN')),
    feedback              VARCHAR(10),
    empresa_id            BIGINT NOT NULL REFERENCES empresas(id) ON DELETE CASCADE,
    usuario_id            BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ia_consultas_usuario ON ia_consultas(usuario_id);
CREATE INDEX idx_ia_consultas_empresa ON ia_consultas(empresa_id);
CREATE INDEX idx_ia_consultas_created_at ON ia_consultas(created_at DESC);

-- ============================================
-- TABLA: ia_embeddings (fragmentos + vectores)
-- ============================================
CREATE TABLE ia_embeddings (
    id                BIGSERIAL PRIMARY KEY,
    chunk_text        TEXT NOT NULL,
    chunk_order       INTEGER NOT NULL DEFAULT 0,
    embedding         TEXT,
    token_count       INTEGER DEFAULT 0,
    documento_id      BIGINT NOT NULL REFERENCES documentos(id) ON DELETE CASCADE,
    empresa_id        BIGINT NOT NULL REFERENCES empresas(id) ON DELETE CASCADE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ia_embeddings_documento ON ia_embeddings(documento_id);
CREATE INDEX idx_ia_embeddings_empresa ON ia_embeddings(empresa_id);
CREATE INDEX idx_ia_embeddings_vector ON ia_embeddings(embedding);

-- ============================================
-- TABLA: ia_prompt_cache (cache de respuestas)
-- ============================================
CREATE TABLE ia_prompt_cache (
    id                BIGSERIAL PRIMARY KEY,
    pregunta_hash     VARCHAR(64) NOT NULL UNIQUE,
    pregunta          TEXT NOT NULL,
    respuesta         TEXT NOT NULL,
    documentos_ids    JSONB,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at        TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_prompt_cache_hash ON ia_prompt_cache(pregunta_hash);
CREATE INDEX idx_prompt_cache_expires ON ia_prompt_cache(expires_at);
