-- ============================================================
-- V7: Permisos por módulo + vigencia de acceso
-- ============================================================

-- ── Vigencia de servicio en empresas ──────────────────────────────────
ALTER TABLE empresas ADD COLUMN vigencia_desde DATE;
ALTER TABLE empresas ADD COLUMN vigencia_hasta DATE;

-- ── Vigencia y accesos personalizados en usuarios ─────────────────────
ALTER TABLE usuarios ADD COLUMN acceso_desde DATE;
ALTER TABLE usuarios ADD COLUMN acceso_hasta DATE;
ALTER TABLE usuarios ADD COLUMN accesos_personalizados BOOLEAN NOT NULL DEFAULT FALSE;

-- ── Catálogo de módulos del sistema ───────────────────────────────────
CREATE TABLE modulos (
    codigo       VARCHAR(40)  PRIMARY KEY,
    nombre       VARCHAR(80)  NOT NULL,
    descripcion  VARCHAR(200),
    grupo        VARCHAR(60)  NOT NULL,
    icono        VARCHAR(40),
    orden        INT          NOT NULL DEFAULT 0
);

-- ── Permisos por rol (matriz rol × módulo) ────────────────────────────
CREATE TABLE rol_modulo (
    rol            VARCHAR(30) NOT NULL,
    modulo_codigo  VARCHAR(40) NOT NULL,
    habilitado     BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (rol, modulo_codigo),
    CONSTRAINT fk_rolmod_modulo FOREIGN KEY (modulo_codigo)
        REFERENCES modulos (codigo) ON DELETE CASCADE
);

-- ── Override de accesos por usuario individual ────────────────────────
CREATE TABLE usuario_modulo (
    usuario_id     BIGINT      NOT NULL,
    modulo_codigo  VARCHAR(40) NOT NULL,
    habilitado     BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (usuario_id, modulo_codigo),
    CONSTRAINT fk_usrmod_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id) ON DELETE CASCADE,
    CONSTRAINT fk_usrmod_modulo FOREIGN KEY (modulo_codigo)
        REFERENCES modulos (codigo) ON DELETE CASCADE
);

-- ── Seed: catálogo de módulos ─────────────────────────────────────────
INSERT INTO modulos (codigo, nombre, descripcion, grupo, icono, orden) VALUES
('usuarios',      'Usuarios',            'Gestión de cuentas y roles',     'Administración',               'group',       1),
('empresas',      'Empresas',            'Alta y gestión de empresas',     'Administración',               'business',    2),
('auditoria',     'Auditoría',           'Registro de acciones del sistema','Administración',              'history',     3),
('documentos',    'Documentos',          'Documentos normativos PDF',      'Gestión HSE',                  'description', 4),
('puntos_clave',  'Puntos clave',        'Controles y extractos clave',    'Gestión HSE',                  'star',        5),
('recordatorios', 'Recordatorios',       'Avisos programados',             'Gestión HSE',                  'alarm',       6),
('actividades',   'Actividades diarias', 'Registro diario',                'Gestión HSE',                  'event_note',  7),
('controles',     'Controles críticos',  'Controles críticos',             'Gestión HSE',                  'security',    8),
('permisos_hse',  'Permisos de trabajo', 'Permisos HSE',                   'Gestión HSE',                  'lock_person', 9),
('conocimientos', 'Conocimientos',       'Base de conocimiento',           'Gestión HSE',                  'menu_book',  10),
('notificaciones','Notificaciones',      'Bandeja de avisos',              'Comunicación e Inteligencia',  'notifications',11),
('ia',            'FISCALIZA-AI',        'Asistente y búsqueda con IA',    'Comunicación e Inteligencia',  'psychology', 12);

-- ── Seed: permisos por rol ────────────────────────────────────────────
-- SUPER_ADMIN: acceso total
INSERT INTO rol_modulo (rol, modulo_codigo, habilitado)
SELECT 'SUPER_ADMIN', codigo, TRUE FROM modulos;

-- ADMIN_EMPRESA
INSERT INTO rol_modulo (rol, modulo_codigo, habilitado) VALUES
('ADMIN_EMPRESA','usuarios',      TRUE),
('ADMIN_EMPRESA','empresas',      FALSE),
('ADMIN_EMPRESA','auditoria',     FALSE),
('ADMIN_EMPRESA','documentos',    TRUE),
('ADMIN_EMPRESA','puntos_clave',  TRUE),
('ADMIN_EMPRESA','recordatorios', TRUE),
('ADMIN_EMPRESA','actividades',   FALSE),
('ADMIN_EMPRESA','controles',     FALSE),
('ADMIN_EMPRESA','permisos_hse',  FALSE),
('ADMIN_EMPRESA','conocimientos', FALSE),
('ADMIN_EMPRESA','notificaciones',TRUE),
('ADMIN_EMPRESA','ia',            TRUE);

-- USUARIO
INSERT INTO rol_modulo (rol, modulo_codigo, habilitado) VALUES
('USUARIO','usuarios',      FALSE),
('USUARIO','empresas',      FALSE),
('USUARIO','auditoria',     FALSE),
('USUARIO','documentos',    TRUE),
('USUARIO','puntos_clave',  TRUE),
('USUARIO','recordatorios', TRUE),
('USUARIO','actividades',   FALSE),
('USUARIO','controles',     FALSE),
('USUARIO','permisos_hse',  FALSE),
('USUARIO','conocimientos', FALSE),
('USUARIO','notificaciones',TRUE),
('USUARIO','ia',            TRUE);
