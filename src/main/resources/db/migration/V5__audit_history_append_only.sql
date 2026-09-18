-- =====================================================================================
-- V5 — Historial de estados de cita: append-only de verdad (decision D8, S3).
--
-- Corrige dos defectos de V3 verificados en S2 (wiki: sintesis-preguntas-abiertas E1/E2):
--   E1. fk_ash_appointment tenia ON DELETE CASCADE: borrar una cita borraba su auditoria,
--       en contra de RN-12 ("datos de auditoria no se modifican como CRUD normal"). Ahora es
--       RESTRICT: una cita con historial no se puede borrar fisicamente.
--   E2. El origen PROFESSIONAL faltaba aunque el profesional cierra atenciones (RF-17).
-- =====================================================================================

ALTER TABLE appointment_status_history DROP FOREIGN KEY fk_ash_appointment;

ALTER TABLE appointment_status_history
    ADD CONSTRAINT fk_ash_appointment FOREIGN KEY (appointment_id)
        REFERENCES appointments (id) ON DELETE RESTRICT;

ALTER TABLE appointment_status_history
    MODIFY source ENUM('SYSTEM', 'USER', 'ADMIN', 'PROFESSIONAL') NOT NULL;
