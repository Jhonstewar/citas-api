-- =====================================================================================
-- V6 — Especialidad "Medicina General" precargada (decision D7, S3).
--
-- RF-11 exige que el usuario pueda escoger "Medicina General" para una cita general. Sin esta
-- semilla, un entorno recien migrado no ofrece ninguna cita general hasta que un ADMIN la cree.
-- Es la unica especialidad protegida: la aplicacion no permite desactivarla ni cambiar su tipo.
-- =====================================================================================

INSERT INTO specialties (appointment_type_id, code, name, duration_minutes)
SELECT id, 'MEDICINA_GENERAL', 'Medicina General', 30
FROM appointment_types
WHERE code = 'GENERAL';
