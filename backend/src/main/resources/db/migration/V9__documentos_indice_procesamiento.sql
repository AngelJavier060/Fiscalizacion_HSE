-- Acelera listados y job de recuperación de documentos atascados en PROCESANDO
CREATE INDEX IF NOT EXISTS idx_documentos_estado_updated
    ON documentos (estado_procesamiento, updated_at)
    WHERE activo = true;
