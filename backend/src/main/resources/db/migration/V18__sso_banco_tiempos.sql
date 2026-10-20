-- Bancos vigentes: 20, 40, 60 o 0 (todas). Los tiempos 15/50/90 los aplica la app.
UPDATE sso_configuracion SET valor = '20'
WHERE clave = 'preguntas_por_banco' AND valor IN ('10');

UPDATE sso_configuracion SET valor = '40'
WHERE clave = 'preguntas_por_banco' AND valor IN ('30');
