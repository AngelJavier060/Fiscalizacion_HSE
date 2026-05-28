-- ============================================
-- FISCALIZACION HSE - FASE 4: FIX
-- Cambiar columna rol de enum a VARCHAR(30)
-- para compatibilidad con JPA EnumType.STRING
-- ============================================

ALTER TABLE usuarios ALTER COLUMN rol TYPE VARCHAR(30) USING rol::text;

-- Nota: mantenemos el tipo enum por si se necesita después
-- DROP TYPE IF EXISTS rol_usuario;
