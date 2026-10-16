-- ============================================================
-- V12: Simulador de evaluaciones SSO
-- ============================================================

ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS vuelta_actual INTEGER NOT NULL DEFAULT 1;

CREATE TABLE sso_temas (
    id          BIGSERIAL PRIMARY KEY,
    nombre      VARCHAR(160) NOT NULL UNIQUE,
    activo      BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE sso_preguntas (
    id               BIGSERIAL PRIMARY KEY,
    tema_id          BIGINT NOT NULL REFERENCES sso_temas(id),
    enunciado        TEXT NOT NULL,
    explicacion      TEXT,
    fuente           VARCHAR(255),
    imagen           VARCHAR(500),
    activo           BOOLEAN NOT NULL DEFAULT TRUE,
    creado_por       BIGINT REFERENCES usuarios(id),
    fecha_creacion   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    fecha_edicion    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sso_preguntas_tema ON sso_preguntas(tema_id);
CREATE INDEX idx_sso_preguntas_activo ON sso_preguntas(activo);

CREATE TABLE sso_opciones (
    id           BIGSERIAL PRIMARY KEY,
    pregunta_id  BIGINT NOT NULL REFERENCES sso_preguntas(id) ON DELETE CASCADE,
    texto        TEXT NOT NULL,
    es_correcta  BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_sso_opciones_pregunta ON sso_opciones(pregunta_id);

CREATE TABLE sso_configuracion (
    clave  VARCHAR(80) PRIMARY KEY,
    valor  VARCHAR(80) NOT NULL
);

INSERT INTO sso_configuracion (clave, valor) VALUES
    ('nota_minima', '70'),
    ('tiempo_20_min', '30'),
    ('tiempo_40_min', '60'),
    ('horas_para_abandonar', '24');

CREATE TABLE sso_intentos (
    id                 BIGSERIAL PRIMARY KEY,
    usuario_id         BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    modo               VARCHAR(20) NOT NULL,
    tema_id            BIGINT REFERENCES sso_temas(id),
    estado             VARCHAR(20) NOT NULL DEFAULT 'en_curso',
    fecha_inicio       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    fecha_fin          TIMESTAMP WITH TIME ZONE,
    tiempo_limite_min  INTEGER NOT NULL,
    total_preguntas    INTEGER NOT NULL DEFAULT 0,
    correctas          INTEGER,
    nota               NUMERIC(6, 2),
    aprobado           BOOLEAN
);

CREATE INDEX idx_sso_intentos_usuario ON sso_intentos(usuario_id);
CREATE INDEX idx_sso_intentos_estado ON sso_intentos(estado);

CREATE TABLE sso_intento_respuestas (
    id                 BIGSERIAL PRIMARY KEY,
    intento_id         BIGINT NOT NULL REFERENCES sso_intentos(id) ON DELETE CASCADE,
    pregunta_id        BIGINT NOT NULL REFERENCES sso_preguntas(id),
    orden              INTEGER NOT NULL,
    orden_opciones     TEXT NOT NULL,
    opcion_elegida_id  BIGINT REFERENCES sso_opciones(id),
    es_correcta        BOOLEAN
);

CREATE INDEX idx_sso_intento_resp_intento ON sso_intento_respuestas(intento_id);

CREATE TABLE sso_rotacion_usuario (
    usuario_id   BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    pregunta_id  BIGINT NOT NULL REFERENCES sso_preguntas(id) ON DELETE CASCADE,
    vuelta       INTEGER NOT NULL,
    PRIMARY KEY (usuario_id, pregunta_id, vuelta)
);

INSERT INTO modulos (codigo, nombre, descripcion, grupo, icono, orden) VALUES
('simulador', 'Simulador SSO', 'Exámenes de práctica HSE', 'Gestión HSE', 'quiz', 13);

INSERT INTO rol_modulo (rol, modulo_codigo, habilitado) VALUES
('SUPER_ADMIN',  'simulador', TRUE),
('ADMIN_EMPRESA','simulador', TRUE),
('USUARIO',      'simulador', TRUE);

-- Temas propuestos
INSERT INTO sso_temas (nombre, activo) VALUES
('Incendios y extintores', TRUE),
('Detección y alarmas', TRUE),
('Señalización', TRUE),
('Izaje (ASME B30)', TRUE),
('Trabajos en altura (ANSI Z359)', TRUE),
('Riesgo eléctrico', TRUE),
('Higiene industrial (ruido, químicos)', TRUE),
('Ergonomía', TRUE),
('Psicosocial', TRUE),
('Legislación ecuatoriana', TRUE),
('Indicadores de accidentabilidad', TRUE),
('Materiales peligrosos', TRUE),
('EPP', TRUE),
('Áreas clasificadas', TRUE);

-- Banco de prueba (10 preguntas) para no esperar el Excel de Los mejores SSO
INSERT INTO sso_preguntas (tema_id, enunciado, explicacion, fuente, activo, creado_por)
SELECT t.id,
       '¿Qué norma regula la instalación de bombas estacionarias contra incendios?',
       'La NFPA 20 establece los requisitos de instalación de bombas estacionarias para protección contra incendios.',
       'NFPA 20', TRUE, (SELECT id FROM usuarios ORDER BY id LIMIT 1)
FROM sso_temas t WHERE t.nombre = 'Incendios y extintores';

INSERT INTO sso_opciones (pregunta_id, texto, es_correcta)
SELECT p.id, v.texto, v.ok
FROM sso_preguntas p
CROSS JOIN (VALUES
    ('NFPA 20', TRUE),
    ('NFPA 72', FALSE),
    ('NFPA 10', FALSE),
    ('NFPA 170', FALSE)
) AS v(texto, ok)
WHERE p.enunciado LIKE '¿Qué norma regula la instalación de bombas%';

INSERT INTO sso_preguntas (tema_id, enunciado, explicacion, fuente, activo, creado_por)
SELECT t.id,
       '¿Qué norma se usa para los símbolos de seguridad contra incendios y emergencias?',
       'La NFPA 170 define los símbolos de seguridad contra incendios y emergencias.',
       'NFPA 170', TRUE, (SELECT id FROM usuarios ORDER BY id LIMIT 1)
FROM sso_temas t WHERE t.nombre = 'Señalización';

INSERT INTO sso_opciones (pregunta_id, texto, es_correcta)
SELECT p.id, v.texto, v.ok
FROM sso_preguntas p
CROSS JOIN (VALUES
    ('ISO 7010', FALSE),
    ('NFPA 170', TRUE),
    ('ANSI Z535', FALSE),
    ('INEN 439', FALSE)
) AS v(texto, ok)
WHERE p.enunciado LIKE '¿Qué norma se usa para los símbolos%';

INSERT INTO sso_preguntas (tema_id, enunciado, explicacion, fuente, activo, creado_por)
SELECT t.id,
       '¿Cuál es la norma de referencia para sistemas de detección y alarma de incendio?',
       'NFPA 72 es el código nacional de alarmas y señalización de incendio.',
       'NFPA 72', TRUE, (SELECT id FROM usuarios ORDER BY id LIMIT 1)
FROM sso_temas t WHERE t.nombre = 'Detección y alarmas';

INSERT INTO sso_opciones (pregunta_id, texto, es_correcta)
SELECT p.id, v.texto, v.ok
FROM sso_preguntas p
CROSS JOIN (VALUES
    ('NFPA 13', FALSE),
    ('NFPA 72', TRUE),
    ('NFPA 25', FALSE),
    ('NFPA 70', FALSE)
) AS v(texto, ok)
WHERE p.enunciado LIKE '¿Cuál es la norma de referencia para sistemas de detección%';

INSERT INTO sso_preguntas (tema_id, enunciado, explicacion, fuente, activo, creado_por)
SELECT t.id,
       'En izaje, ¿qué norma ASME cubre las grúas móviles y locomotoras?',
       'ASME B30.5 cubre grúas móviles y locomotoras.',
       'ASME B30.5', TRUE, (SELECT id FROM usuarios ORDER BY id LIMIT 1)
FROM sso_temas t WHERE t.nombre = 'Izaje (ASME B30)';

INSERT INTO sso_opciones (pregunta_id, texto, es_correcta)
SELECT p.id, v.texto, v.ok
FROM sso_preguntas p
CROSS JOIN (VALUES
    ('ASME B30.2', FALSE),
    ('ASME B30.5', TRUE),
    ('ASME B30.9', FALSE),
    ('ASME B30.16', FALSE)
) AS v(texto, ok)
WHERE p.enunciado LIKE 'En izaje, ¿qué norma ASME cubre las grúas móviles%';

INSERT INTO sso_preguntas (tema_id, enunciado, explicacion, fuente, activo, creado_por)
SELECT t.id,
       '¿Qué familia normativa ANSI cubre los sistemas personales de detención de caídas?',
       'ANSI/ASSP Z359 es la familia de normas de protección contra caídas.',
       'ANSI Z359', TRUE, (SELECT id FROM usuarios ORDER BY id LIMIT 1)
FROM sso_temas t WHERE t.nombre = 'Trabajos en altura (ANSI Z359)';

INSERT INTO sso_opciones (pregunta_id, texto, es_correcta)
SELECT p.id, v.texto, v.ok
FROM sso_preguntas p
CROSS JOIN (VALUES
    ('ANSI Z87', FALSE),
    ('ANSI Z89', FALSE),
    ('ANSI Z359', TRUE),
    ('ANSI Z117', FALSE)
) AS v(texto, ok)
WHERE p.enunciado LIKE '¿Qué familia normativa ANSI cubre los sistemas personales%';

INSERT INTO sso_preguntas (tema_id, enunciado, explicacion, fuente, activo, creado_por)
SELECT t.id,
       'Antes de trabajar en un tablero eléctrico, ¿cuál es la primera medida de control?',
       'La consigna o bloqueo/etiquetado (LOTO) elimina la energía peligrosa antes de intervenir.',
       'NFPA 70E / LOTO', TRUE, (SELECT id FROM usuarios ORDER BY id LIMIT 1)
FROM sso_temas t WHERE t.nombre = 'Riesgo eléctrico';

INSERT INTO sso_opciones (pregunta_id, texto, es_correcta)
SELECT p.id, v.texto, v.ok
FROM sso_preguntas p
CROSS JOIN (VALUES
    ('Usar solo guantes dieléctricos', FALSE),
    ('Aplicar bloqueo y etiquetado de energías', TRUE),
    ('Medir voltaje con el circuito energizado', FALSE),
    ('Mojar el piso para disipar estática', FALSE)
) AS v(texto, ok)
WHERE p.enunciado LIKE 'Antes de trabajar en un tablero eléctrico%';

INSERT INTO sso_preguntas (tema_id, enunciado, explicacion, fuente, activo, creado_por)
SELECT t.id,
       'El límite de exposición ocupacional a ruido de 85 dB(A) se refiere típicamente a:',
       '85 dB(A) es el criterio común de exposición ponderada a 8 horas (TWA).',
       'ACGIH / OSHA', TRUE, (SELECT id FROM usuarios ORDER BY id LIMIT 1)
FROM sso_temas t WHERE t.nombre = 'Higiene industrial (ruido, químicos)';

INSERT INTO sso_opciones (pregunta_id, texto, es_correcta)
SELECT p.id, v.texto, v.ok
FROM sso_preguntas p
CROSS JOIN (VALUES
    ('Un pico instantáneo', FALSE),
    ('Una jornada de 8 horas (TWA)', TRUE),
    ('Una exposición de 15 minutos', FALSE),
    ('Ruido de impacto únicamente', FALSE)
) AS v(texto, ok)
WHERE p.enunciado LIKE 'El límite de exposición ocupacional a ruido%';

INSERT INTO sso_preguntas (tema_id, enunciado, explicacion, fuente, activo, creado_por)
SELECT t.id,
       '¿Qué identifica un diamante NFPA 704 de color rojo?',
       'El rojo representa el grado de inflamabilidad.',
       'NFPA 704', TRUE, (SELECT id FROM usuarios ORDER BY id LIMIT 1)
FROM sso_temas t WHERE t.nombre = 'Materiales peligrosos';

INSERT INTO sso_opciones (pregunta_id, texto, es_correcta)
SELECT p.id, v.texto, v.ok
FROM sso_preguntas p
CROSS JOIN (VALUES
    ('Riesgo para la salud', FALSE),
    ('Inflamabilidad', TRUE),
    ('Reactividad', FALSE),
    ('Riesgo especial', FALSE)
) AS v(texto, ok)
WHERE p.enunciado LIKE '¿Qué identifica un diamante NFPA 704 de color rojo%';

INSERT INTO sso_preguntas (tema_id, enunciado, explicacion, fuente, activo, creado_por)
SELECT t.id,
       'El casco de seguridad clase E (eléctrico) está previsto para:',
       'La clase E (Electrical) ofrece protección dieléctrica hasta 20 000 V.',
       'ANSI Z89.1', TRUE, (SELECT id FROM usuarios ORDER BY id LIMIT 1)
FROM sso_temas t WHERE t.nombre = 'EPP';

INSERT INTO sso_opciones (pregunta_id, texto, es_correcta)
SELECT p.id, v.texto, v.ok
FROM sso_preguntas p
CROSS JOIN (VALUES
    ('Impactos menores en minería únicamente', FALSE),
    ('Protección dieléctrica (hasta 20 000 V)', TRUE),
    ('Ambientes con metales fundidos', FALSE),
    ('Sustituir el arnés de altura', FALSE)
) AS v(texto, ok)
WHERE p.enunciado LIKE 'El casco de seguridad clase E%';

INSERT INTO sso_preguntas (tema_id, enunciado, explicacion, fuente, activo, creado_por)
SELECT t.id,
       'En Ecuador, el instrumento que regula el régimen de seguridad y salud en el trabajo del IESS es:',
       'El Decreto Ejecutivo 255 y la normativa del IESS/SART rigen la gestión de SST.',
       'Decreto Ejecutivo 255', TRUE, (SELECT id FROM usuarios ORDER BY id LIMIT 1)
FROM sso_temas t WHERE t.nombre = 'Legislación ecuatoriana';

INSERT INTO sso_opciones (pregunta_id, texto, es_correcta)
SELECT p.id, v.texto, v.ok
FROM sso_preguntas p
CROSS JOIN (VALUES
    ('Ley Orgánica de Educación', FALSE),
    ('Decreto Ejecutivo 255 / normativa IESS', TRUE),
    ('Código de Comercio', FALSE),
    ('NFPA 1 como ley nacional', FALSE)
) AS v(texto, ok)
WHERE p.enunciado LIKE 'En Ecuador, el instrumento que regula el régimen%';
