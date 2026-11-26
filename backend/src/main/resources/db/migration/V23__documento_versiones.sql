ALTER TABLE documentos
    ADD COLUMN IF NOT EXISTS version_actual INTEGER NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS documento_versiones (
    id                BIGSERIAL PRIMARY KEY,
    documento_id      BIGINT NOT NULL REFERENCES documentos(id) ON DELETE CASCADE,
    numero_version    INTEGER NOT NULL,
    titulo            VARCHAR(500) NOT NULL,
    descripcion       TEXT,
    archivo_nombre    VARCHAR(500) NOT NULL,
    archivo_tipo      VARCHAR(100) NOT NULL,
    archivo_tamano    BIGINT NOT NULL,
    ruta_archivo      VARCHAR(1000) NOT NULL,
    reemplazado_por   BIGINT REFERENCES usuarios(id),
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_doc_versiones_documento
    ON documento_versiones(documento_id);
