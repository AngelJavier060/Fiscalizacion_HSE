INSERT INTO sso_configuracion (clave, valor)
SELECT 'tiempo_examen_min', '30'
WHERE NOT EXISTS (SELECT 1 FROM sso_configuracion WHERE clave = 'tiempo_examen_min');
