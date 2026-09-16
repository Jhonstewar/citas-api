-- =====================================================================================
-- V3 - Agenda del profesional, citas, auditoria de estados y reprogramaciones
-- Cubre RF-08..RF-19 y las reglas RN-01, RN-05, RN-06, RN-09, RN-10, RN-11, RN-12.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- availability_blocks: franja continua que el profesional publica en una sede y un dia
-- (RF-08). Varios bloques por dia son validos (08:00-12:00 y 14:00-17:00).
-- La no superposicion de bloques del mismo profesional es una regla transaccional que se
-- valida en el dominio: SQL no puede expresar "sin solape" con una sola restriccion.
-- Lo que si se garantiza aqui es que no existan dos bloques identicos.
-- -------------------------------------------------------------------------------------
CREATE TABLE availability_blocks (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    professional_id BIGINT      NOT NULL,
    site_id         SMALLINT    NOT NULL,
    block_date      DATE        NOT NULL,
    start_time      TIME        NOT NULL,
    end_time        TIME        NOT NULL,
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_availability_blocks PRIMARY KEY (id),
    CONSTRAINT uq_availability_blocks_start UNIQUE (professional_id, block_date, start_time),
    CONSTRAINT ck_availability_blocks_range CHECK (end_time > start_time),
    -- El bloque se discretiza en slots de 30 minutos (RF-08): sus extremos deben caer en
    -- la rejilla :00 / :30 para que la discretizacion sea exacta.
    CONSTRAINT ck_availability_blocks_grid CHECK (
        MINUTE(start_time) IN (0, 30) AND SECOND(start_time) = 0
        AND MINUTE(end_time) IN (0, 30) AND SECOND(end_time) = 0
    ),
    CONSTRAINT fk_availability_blocks_professional FOREIGN KEY (professional_id)
        REFERENCES professionals (id),
    CONSTRAINT fk_availability_blocks_site FOREIGN KEY (site_id) REFERENCES sites (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Bloques de disponibilidad por profesional, sede y dia (RF-08).';

CREATE INDEX ix_availability_blocks_lookup ON availability_blocks (site_id, block_date);
CREATE INDEX ix_availability_blocks_prof_date ON availability_blocks (professional_id, block_date);

-- -------------------------------------------------------------------------------------
-- availability_slots: unidad atomica de agenda, 30 minutos (RF-08, RF-09).
-- Deliberadamente NO repite professional_id, site_id ni la fecha: todos dependen del
-- bloque (slot_id -> availability_block_id -> professional_id/site_id/block_date). Copiarlos
-- seria una dependencia transitiva y violaria 3FN.
-- end_time es una columna GENERADA: start_time + 30 min. Al ser derivada por el motor no
-- puede desincronizarse y no introduce una dependencia entre atributos no clave.
-- -------------------------------------------------------------------------------------
CREATE TABLE availability_slots (
    id                    BIGINT NOT NULL AUTO_INCREMENT,
    availability_block_id BIGINT NOT NULL,
    start_time            TIME   NOT NULL,
    end_time              TIME GENERATED ALWAYS AS (ADDTIME(start_time, '00:30:00')) STORED,
    CONSTRAINT pk_availability_slots PRIMARY KEY (id),
    CONSTRAINT uq_availability_slots_block_start UNIQUE (availability_block_id, start_time),
    CONSTRAINT ck_availability_slots_grid CHECK (
        MINUTE(start_time) IN (0, 30) AND SECOND(start_time) = 0
    ),
    CONSTRAINT fk_availability_slots_block FOREIGN KEY (availability_block_id)
        REFERENCES availability_blocks (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Slots de 30 minutos derivados de un bloque de disponibilidad.';

-- -------------------------------------------------------------------------------------
-- appointments: la cita (RF-11, RF-12, RF-13).
-- Solo claves foraneas: no guarda el nombre de la EPS, del plan, del regimen, del
-- profesional, de la especialidad ni del estado. Tampoco guarda appointment_type_id
-- (se alcanza por specialty_id -> appointment_type_id; copiarlo seria transitivo) ni la
-- duracion en minutos (se deriva de start_time/end_time) ni el motivo de rechazo (vive en
-- appointment_status_history, RF-19).
-- scheduled_date/start_time/end_time si se conservan: son el acuerdo operativo con el
-- paciente y deben sobrevivir a que el profesional edite o borre sus bloques futuros.
-- -------------------------------------------------------------------------------------
CREATE TABLE appointments (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    patient_user_id BIGINT      NOT NULL,
    professional_id BIGINT      NOT NULL,
    site_id         SMALLINT    NOT NULL,
    specialty_id    INT         NOT NULL,
    -- Afiliacion vigente del paciente al momento de agendar. Nullable: el PRD no obliga a
    -- tener afiliacion para pedir cita. Que la afiliacion pertenezca al paciente es una
    -- regla de negocio que valida el dominio.
    affiliation_id  BIGINT      NULL,
    status_id       SMALLINT    NOT NULL,
    scheduled_date  DATE        NOT NULL,
    start_time      TIME        NOT NULL,
    end_time        TIME        NOT NULL,
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_appointments PRIMARY KEY (id),
    CONSTRAINT ck_appointments_range CHECK (end_time > start_time),
    CONSTRAINT fk_appointments_patient FOREIGN KEY (patient_user_id) REFERENCES users (id),
    CONSTRAINT fk_appointments_professional FOREIGN KEY (professional_id)
        REFERENCES professionals (id),
    CONSTRAINT fk_appointments_site FOREIGN KEY (site_id) REFERENCES sites (id),
    CONSTRAINT fk_appointments_specialty FOREIGN KEY (specialty_id) REFERENCES specialties (id),
    CONSTRAINT fk_appointments_affiliation FOREIGN KEY (affiliation_id)
        REFERENCES affiliations (id),
    CONSTRAINT fk_appointments_status FOREIGN KEY (status_id)
        REFERENCES appointment_statuses (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Citas. Solo FKs hacia catalogos; nada de nombres denormalizados.';

CREATE INDEX ix_appointments_patient ON appointments (patient_user_id, scheduled_date);
CREATE INDEX ix_appointments_professional ON appointments (professional_id, scheduled_date);
CREATE INDEX ix_appointments_admin_inbox ON appointments (status_id, scheduled_date);
CREATE INDEX ix_appointments_site_date ON appointments (site_id, scheduled_date);
CREATE INDEX ix_appointments_specialty ON appointments (specialty_id);
CREATE INDEX ix_appointments_affiliation ON appointments (affiliation_id);

-- -------------------------------------------------------------------------------------
-- appointment_status_history: auditoria append-only de cada cambio de estado (RF-19, RN-12).
-- actor_user_id es nullable porque los cambios automaticos (source = SYSTEM) no tienen
-- actor humano. La fila NO se actualiza ni se borra como un CRUD normal.
-- -------------------------------------------------------------------------------------
CREATE TABLE appointment_status_history (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    appointment_id BIGINT       NOT NULL,
    status_id      SMALLINT     NOT NULL,
    actor_user_id  BIGINT       NULL,
    source         ENUM('SYSTEM', 'USER', 'ADMIN') NOT NULL,
    reason         VARCHAR(500) NULL,
    changed_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_appointment_status_history PRIMARY KEY (id),
    -- Un cambio con actor humano debe identificar al actor; SYSTEM no lo exige.
    CONSTRAINT ck_ash_actor CHECK (source = 'SYSTEM' OR actor_user_id IS NOT NULL),
    CONSTRAINT fk_ash_appointment FOREIGN KEY (appointment_id)
        REFERENCES appointments (id) ON DELETE CASCADE,
    CONSTRAINT fk_ash_status FOREIGN KEY (status_id) REFERENCES appointment_statuses (id),
    CONSTRAINT fk_ash_actor FOREIGN KEY (actor_user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Auditoria append-only de transiciones de estado de cita (RF-19).';

CREATE INDEX ix_ash_appointment ON appointment_status_history (appointment_id, changed_at);
CREATE INDEX ix_ash_actor ON appointment_status_history (actor_user_id);
CREATE INDEX ix_ash_status ON appointment_status_history (status_id);

-- -------------------------------------------------------------------------------------
-- reschedule_requests: solicitud de reprogramacion de una cita aprobada (RF-15).
-- Conserva profesional y especialidad de la cita original: por eso no los repite, se leen
-- de appointments (appointment_id -> professional_id/specialty_id). Cambiar de profesional
-- es una cita nueva, no una reprogramacion.
-- active_marker garantiza a nivel de motor que una cita no tenga dos solicitudes sin
-- decidir a la vez (una solicitud sin decision es, por definicion, la que esta PENDING).
-- -------------------------------------------------------------------------------------
CREATE TABLE reschedule_requests (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    appointment_id      BIGINT       NOT NULL,
    status_id           SMALLINT     NOT NULL,
    requested_by_user_id BIGINT      NOT NULL,
    proposed_date       DATE         NOT NULL,
    proposed_start_time TIME         NOT NULL,
    proposed_end_time   TIME         NOT NULL,
    request_reason      VARCHAR(500) NULL,
    decided_by_user_id  BIGINT       NULL,
    decided_at          DATETIME(6)  NULL,
    -- RN-04: el rechazo exige motivo. La aplicacion lo valida; aqui queda el dato.
    decision_reason     VARCHAR(500) NULL,
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    active_marker       TINYINT GENERATED ALWAYS AS (CASE WHEN decided_at IS NULL THEN 1 ELSE NULL END) STORED,
    CONSTRAINT pk_reschedule_requests PRIMARY KEY (id),
    CONSTRAINT uq_reschedule_requests_active UNIQUE (appointment_id, active_marker),
    CONSTRAINT ck_reschedule_requests_range CHECK (proposed_end_time > proposed_start_time),
    CONSTRAINT ck_reschedule_requests_decision CHECK (
        (decided_at IS NULL AND decided_by_user_id IS NULL)
        OR (decided_at IS NOT NULL AND decided_by_user_id IS NOT NULL)
    ),
    CONSTRAINT fk_reschedule_requests_appointment FOREIGN KEY (appointment_id)
        REFERENCES appointments (id) ON DELETE CASCADE,
    CONSTRAINT fk_reschedule_requests_status FOREIGN KEY (status_id)
        REFERENCES reschedule_statuses (id),
    CONSTRAINT fk_reschedule_requests_requester FOREIGN KEY (requested_by_user_id)
        REFERENCES users (id),
    CONSTRAINT fk_reschedule_requests_decider FOREIGN KEY (decided_by_user_id)
        REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Solicitudes de reprogramacion (RF-15). Una activa por cita.';

CREATE INDEX ix_reschedule_requests_status ON reschedule_requests (status_id, created_at);
CREATE INDEX ix_reschedule_requests_requester ON reschedule_requests (requested_by_user_id);
CREATE INDEX ix_reschedule_requests_decider ON reschedule_requests (decided_by_user_id);

-- -------------------------------------------------------------------------------------
-- slot_reservations: LIBRO UNICO DE OCUPACION DE SLOTS. Es la pieza central de RN-01.
--
-- Por que una sola tabla y no dos (appointment_slots + reschedule_request_slots):
-- un slot puede estar ocupado por una cita confirmada/solicitada O retenido por una
-- solicitud de reprogramacion PENDING (RF-15). Si fueran dos tablas, ninguna restriccion
-- UNIQUE podria impedir que la misma franja quedara tomada en ambas a la vez, y la
-- no-doble-reserva dependeria solo del codigo. Con una sola tabla, la PK sobre slot_id es
-- la garantia: UN SLOT NO PUEDE APARECER DOS VECES. Dos transacciones concurrentes que
-- intenten tomar el mismo slot producen un error de clave duplicada; el motor, no la
-- aplicacion, impide la doble reserva.
--
-- Liberar slots (RN-09: cancelacion, rechazo, reprogramacion aprobada) es borrar filas de
-- esta tabla, lo que devuelve el slot al pool de disponibles.
-- slot_order (1 o 2) ordena los slots de una atencion de 60 minutos; que sean consecutivos
-- (RN-05) lo valida el dominio al elegirlos.
-- -------------------------------------------------------------------------------------
CREATE TABLE slot_reservations (
    -- slot_id es la PK: un indice unico sobre el slot reservado.
    slot_id               BIGINT   NOT NULL,
    reservation_type      ENUM('APPOINTMENT', 'RESCHEDULE_REQUEST') NOT NULL,
    appointment_id        BIGINT   NULL,
    reschedule_request_id BIGINT   NULL,
    slot_order            SMALLINT NOT NULL DEFAULT 1,
    created_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_slot_reservations PRIMARY KEY (slot_id),
    -- Exactamente un titular segun el tipo de reserva.
    CONSTRAINT ck_slot_reservations_owner CHECK (
        (reservation_type = 'APPOINTMENT'
            AND appointment_id IS NOT NULL AND reschedule_request_id IS NULL)
        OR (reservation_type = 'RESCHEDULE_REQUEST'
            AND reschedule_request_id IS NOT NULL AND appointment_id IS NULL)
    ),
    -- Una especialidad ocupa 1 slot (30 min) o 2 (60 min): RF-09.
    CONSTRAINT ck_slot_reservations_order CHECK (slot_order IN (1, 2)),
    -- Impide que un mismo titular repita la posicion 1 o 2.
    CONSTRAINT uq_slot_reservations_appointment_order UNIQUE (appointment_id, slot_order),
    CONSTRAINT uq_slot_reservations_request_order UNIQUE (reschedule_request_id, slot_order),
    CONSTRAINT fk_slot_reservations_slot FOREIGN KEY (slot_id)
        REFERENCES availability_slots (id),
    CONSTRAINT fk_slot_reservations_appointment FOREIGN KEY (appointment_id)
        REFERENCES appointments (id) ON DELETE CASCADE,
    CONSTRAINT fk_slot_reservations_request FOREIGN KEY (reschedule_request_id)
        REFERENCES reschedule_requests (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Ocupacion de slots. La PK sobre slot_id impide la doble reserva (RN-01).';
