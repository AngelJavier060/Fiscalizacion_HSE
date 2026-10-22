-- Cada pregunta conserva un solo "por qué está mal".
-- Se deja el de la opción incorrecta más antigua y se vacían los demás.
WITH sobrantes AS (
    SELECT id
    FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY pregunta_id ORDER BY id) AS rn
        FROM sso_opciones
        WHERE es_correcta = FALSE
          AND argumento IS NOT NULL
          AND btrim(argumento) <> ''
    ) t
    WHERE rn > 1
)
UPDATE sso_opciones
SET argumento = NULL
WHERE id IN (SELECT id FROM sobrantes);
