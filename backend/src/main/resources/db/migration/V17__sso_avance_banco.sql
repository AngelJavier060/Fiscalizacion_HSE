UPDATE sso_configuracion SET valor = '90' WHERE clave = 'nota_minima';

INSERT INTO sso_configuracion (clave, valor)
VALUES ('preguntas_por_banco', '20')
ON CONFLICT (clave) DO NOTHING;
