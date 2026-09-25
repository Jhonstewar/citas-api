-- =====================================================================================
-- V8 — Nombre de especialidad unico en la base de datos (S4, F2, R1 · HU-011 DoD)
--
-- Hasta S3 la unicidad del nombre vivia solo en ManageSpecialtiesUseCase (existsByName): dos
-- altas simultaneas, o cualquier escritura que no pasara por el caso de uso, podian dejar dos
-- especialidades con el mismo nombre. Ahora la garantia la da el motor, igual que con el codigo
-- (uq_specialties_code, V2) y con la doble reserva (dec-003).
--
-- La collation utf8mb4_0900_ai_ci (accent-insensitive, case-insensitive) hace que
-- "Cardiología", "cardiologia" y "CARDIOLOGÍA" choquen, que es la misma regla que el caso de
-- uso ya aplicaba. Se fija en la columna para no depender del valor por defecto de la tabla.
--
-- Comprobacion previa (2026-09-25): ni citas_fcv_training ni citas_fcv_training_test tienen
-- nombres repetidos. Si una base los tuviera, esta migracion fallaria con "Duplicate entry"
-- y no se aplicaria; detectarlos con:
--   SELECT name, COUNT(*) FROM specialties GROUP BY name HAVING COUNT(*) > 1;
-- =====================================================================================

ALTER TABLE specialties
    MODIFY name VARCHAR(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    ADD CONSTRAINT uq_specialties_name UNIQUE (name);
