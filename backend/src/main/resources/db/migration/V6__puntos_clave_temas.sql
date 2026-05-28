-- Campos opcionales para organizar puntos clave por tema / control crítico (CC1, CC7, etc.)
ALTER TABLE puntos_clave
    ADD COLUMN IF NOT EXISTS titulo VARCHAR(500),
    ADD COLUMN IF NOT EXISTS tema VARCHAR(500),
    ADD COLUMN IF NOT EXISTS codigo VARCHAR(50),
    ADD COLUMN IF NOT EXISTS tipo VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_puntos_clave_tema ON puntos_clave(tema);
CREATE INDEX IF NOT EXISTS idx_puntos_clave_codigo ON puntos_clave(codigo);
CREATE INDEX IF NOT EXISTS idx_puntos_clave_tipo ON puntos_clave(tipo);
