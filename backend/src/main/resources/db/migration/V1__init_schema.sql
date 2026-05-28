-- ============================================
-- FISCALIZACION HSE - ESQUEMA INICIAL (FASE 1)
-- ============================================

-- ============================================
-- TABLA: empresas
-- ============================================
CREATE TABLE empresas (
    id              BIGSERIAL PRIMARY KEY,
    nombre          VARCHAR(255) NOT NULL,
    ruc             VARCHAR(20) UNIQUE,
    direccion       VARCHAR(500),
    email           VARCHAR(255),
    telefono        VARCHAR(50),
    activa          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_empresas_activa ON empresas(activa);
CREATE INDEX idx_empresas_nombre ON empresas(nombre);

-- ============================================
-- TABLA: usuarios
-- ============================================
CREATE TABLE usuarios (
    id              BIGSERIAL PRIMARY KEY,
    nombre          VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    rol             VARCHAR(30) NOT NULL DEFAULT 'USUARIO',
    activo          BOOLEAN NOT NULL DEFAULT TRUE,
    empresa_id      BIGINT REFERENCES empresas(id) ON DELETE CASCADE,
    ultimo_acceso   TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_usuarios_email ON usuarios(email);
CREATE INDEX idx_usuarios_rol ON usuarios(rol);
CREATE INDEX idx_usuarios_empresa ON usuarios(empresa_id);
CREATE INDEX idx_usuarios_activo ON usuarios(activo);

-- ============================================
-- TABLA: auditoria
-- ============================================
CREATE TABLE auditoria (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT NOT NULL REFERENCES usuarios(id),
    empresa_id      BIGINT REFERENCES empresas(id),
    accion          VARCHAR(100) NOT NULL,
    entidad         VARCHAR(100) NOT NULL,
    entidad_id      BIGINT,
    detalle         TEXT,
    direccion_ip    VARCHAR(50),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_auditoria_usuario ON auditoria(usuario_id);
CREATE INDEX idx_auditoria_empresa ON auditoria(empresa_id);
CREATE INDEX idx_auditoria_accion ON auditoria(accion);
CREATE INDEX idx_auditoria_fecha ON auditoria(created_at);

-- ============================================
-- INSERTAR SUPER ADMIN POR DEFECTO
-- ============================================
-- Password: AdminHSE2024! (BCrypt encoded)
INSERT INTO usuarios (nombre, email, password, rol)
VALUES (
    'Super Administrador',
    'admin@fiscalizacionhse.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'SUPER_ADMIN'
);
