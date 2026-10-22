-- Texto propio del sustento. No es la normativa ni el archivo PDF.
ALTER TABLE sso_preguntas
    ADD COLUMN IF NOT EXISTS sustento_texto TEXT;
