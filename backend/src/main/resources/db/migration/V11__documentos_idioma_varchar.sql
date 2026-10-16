-- idioma_detectado guardaba "desconocido" (11 chars) en VARCHAR(10) y rompía el procesamiento.
ALTER TABLE documentos
    ALTER COLUMN idioma_original TYPE VARCHAR(32),
    ALTER COLUMN idioma_detectado TYPE VARCHAR(32);
