-- Estado de procesamiento post-subida (extracción texto, traducción, IA)
ALTER TABLE documentos
    ADD COLUMN estado_procesamiento VARCHAR(20) NOT NULL DEFAULT 'COMPLETADO',
    ADD COLUMN error_procesamiento TEXT;

UPDATE documentos SET estado_procesamiento = 'COMPLETADO' WHERE estado_procesamiento IS NULL;

CREATE INDEX idx_documentos_estado_procesamiento ON documentos(estado_procesamiento);
