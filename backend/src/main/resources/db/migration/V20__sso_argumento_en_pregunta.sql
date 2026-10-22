-- El "por qué está mal" queda una sola vez en la pregunta.
-- Si solo estaba en una opción, se copia. Después se vacía en todas las opciones.
UPDATE sso_preguntas p
SET explicacion = src.argumento
FROM (
    SELECT DISTINCT ON (pregunta_id) pregunta_id, btrim(argumento) AS argumento
    FROM sso_opciones
    WHERE argumento IS NOT NULL
      AND btrim(argumento) <> ''
    ORDER BY pregunta_id, id
) src
WHERE p.id = src.pregunta_id
  AND (p.explicacion IS NULL OR btrim(p.explicacion) = '');

UPDATE sso_opciones
SET argumento = NULL
WHERE argumento IS NOT NULL;
