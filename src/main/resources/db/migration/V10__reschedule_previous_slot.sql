-- =====================================================================================
-- V10 — Franja anterior y sede propuesta en reschedule_requests (S4, F5, decision D31)
--
-- HU-027 CA-03 / CA-09 piden que la solicitud guarde la fecha y hora ANTERIOR y la PROPUESTA.
-- V3 solo guardaba la propuesta (sin sede). Tras aprobar, la cita se mueve (HU-031) y su franja
-- anterior desaparece de appointments, y D21 permite proponer otra sede del mismo profesional.
-- Por eso la solicitud guarda ahora:
--   previous_date, previous_start_time, previous_end_time, previous_site_id  (franja de la cita al pedirla)
--   proposed_site_id                                                        (sede de la franja pedida)
--
-- Por que V10 y no V9: ver V9 (Flyway con outOfOrder = false sobre bases persistentes).
--
-- Filas existentes: hasta hoy ningun caso de uso producia solicitudes, pero una base de desarrollo o
-- de pruebas podria tener filas sembradas a mano. Se rellenan antes de volver obligatorias:
--   - la franja anterior, con la franja ACTUAL de la cita. Es exacta para una solicitud sin decidir
--     (la cita aun no se movio). Para una ya APPROVED seria la franja nueva, pero ninguna existe:
--     la aprobacion llega en esta misma fase.
--   - la sede propuesta, con la sede del bloque de sus slots retenidos; si ya no retiene nada
--     (decidida o cancelada), con la sede de la cita.
-- =====================================================================================

ALTER TABLE reschedule_requests
    ADD COLUMN previous_date       DATE     NULL AFTER requested_by_user_id,
    ADD COLUMN previous_start_time TIME     NULL AFTER previous_date,
    ADD COLUMN previous_end_time   TIME     NULL AFTER previous_start_time,
    ADD COLUMN previous_site_id    SMALLINT NULL AFTER previous_end_time,
    ADD COLUMN proposed_site_id    SMALLINT NULL AFTER proposed_end_time;

UPDATE reschedule_requests r
JOIN appointments a ON a.id = r.appointment_id
SET r.previous_date       = a.scheduled_date,
    r.previous_start_time = a.start_time,
    r.previous_end_time   = a.end_time,
    r.previous_site_id    = a.site_id,
    r.proposed_site_id    = COALESCE(
        (SELECT b.site_id
         FROM slot_reservations sr
         JOIN availability_slots s ON s.id = sr.slot_id
         JOIN availability_blocks b ON b.id = s.availability_block_id
         WHERE sr.reschedule_request_id = r.id
         ORDER BY sr.slot_order
         LIMIT 1),
        a.site_id);

ALTER TABLE reschedule_requests
    MODIFY previous_date       DATE     NOT NULL,
    MODIFY previous_start_time TIME     NOT NULL,
    MODIFY previous_end_time   TIME     NOT NULL,
    MODIFY previous_site_id    SMALLINT NOT NULL,
    MODIFY proposed_site_id    SMALLINT NOT NULL,
    ADD CONSTRAINT ck_reschedule_requests_previous_range CHECK (previous_end_time > previous_start_time),
    ADD CONSTRAINT fk_reschedule_requests_previous_site FOREIGN KEY (previous_site_id) REFERENCES sites (id),
    ADD CONSTRAINT fk_reschedule_requests_proposed_site FOREIGN KEY (proposed_site_id) REFERENCES sites (id);
