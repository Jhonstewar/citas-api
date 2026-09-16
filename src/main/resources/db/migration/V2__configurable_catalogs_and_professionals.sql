-- =====================================================================================
-- V2 - Catalogos configurables (RF-06), profesionales (RF-07) y afiliaciones (RF-04)
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- eps: catalogo configurable por ADMIN (RF-06).
-- No se borra fisicamente cuando esta referenciada: se desactiva con active = FALSE.
-- -------------------------------------------------------------------------------------
CREATE TABLE eps (
    id         INT          NOT NULL AUTO_INCREMENT,
    code       VARCHAR(20)  NOT NULL,
    name       VARCHAR(160) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_eps PRIMARY KEY (id),
    CONSTRAINT uq_eps_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'EPS. Catalogo configurable; se desactiva, no se borra (RF-06).';

-- -------------------------------------------------------------------------------------
-- eps_plans: plan comercial de una EPS dentro de un regimen.
-- regime_id vive aqui y no en eps porque una misma EPS puede ofrecer planes en mas de un
-- regimen; no existe la dependencia eps_id -> regime_id. Si el negocio confirmara esa
-- dependencia, la columna deberia subir a eps para no violar 3FN.
-- El codigo del plan es unico dentro de la EPS, no globalmente.
-- -------------------------------------------------------------------------------------
CREATE TABLE eps_plans (
    id         INT          NOT NULL AUTO_INCREMENT,
    eps_id     INT          NOT NULL,
    regime_id  SMALLINT     NOT NULL,
    code       VARCHAR(30)  NOT NULL,
    name       VARCHAR(160) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_eps_plans PRIMARY KEY (id),
    CONSTRAINT uq_eps_plans_eps_code UNIQUE (eps_id, code),
    CONSTRAINT fk_eps_plans_eps FOREIGN KEY (eps_id) REFERENCES eps (id),
    CONSTRAINT fk_eps_plans_regime FOREIGN KEY (regime_id) REFERENCES regimes (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Planes de EPS. El regimen se declara por plan, no por EPS.';

CREATE INDEX ix_eps_plans_regime ON eps_plans (regime_id);

-- -------------------------------------------------------------------------------------
-- specialties: catalogo configurable (RF-06) con la duracion de la atencion (RF-09).
-- La duracion pertenece a la especialidad, no al profesional ni a la cita: el profesional
-- no puede sobrescribirla (RF-09). Solo se admiten 30 o 60 minutos, es decir 1 o 2 slots.
-- El tipo (GENERAL/SPECIALIZED) es una FK; la politica de aprobacion no se copia aqui.
-- -------------------------------------------------------------------------------------
CREATE TABLE specialties (
    id                  INT          NOT NULL AUTO_INCREMENT,
    appointment_type_id SMALLINT     NOT NULL,
    code                VARCHAR(40)  NOT NULL,
    name                VARCHAR(120) NOT NULL,
    duration_minutes    SMALLINT     NOT NULL,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_specialties PRIMARY KEY (id),
    CONSTRAINT uq_specialties_code UNIQUE (code),
    CONSTRAINT ck_specialties_duration CHECK (duration_minutes IN (30, 60)),
    CONSTRAINT fk_specialties_appointment_type FOREIGN KEY (appointment_type_id)
        REFERENCES appointment_types (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Especialidades con duracion fija de 30 o 60 minutos (RF-09).';

CREATE INDEX ix_specialties_appointment_type ON specialties (appointment_type_id);

-- -------------------------------------------------------------------------------------
-- professionals: perfil profesional 1:1 con users (RF-07).
-- No repite nombres ni documento: esos datos son de users. Aqui solo vive lo que depende
-- de ser profesional (codigo, matricula, estado).
-- -------------------------------------------------------------------------------------
CREATE TABLE professionals (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    user_id           BIGINT      NOT NULL,
    professional_code VARCHAR(30) NOT NULL,
    license_number    VARCHAR(40) NOT NULL,
    active            BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_professionals PRIMARY KEY (id),
    -- 1:1 con users: un usuario tiene como maximo un perfil profesional.
    CONSTRAINT uq_professionals_user UNIQUE (user_id),
    CONSTRAINT uq_professionals_code UNIQUE (professional_code),
    CONSTRAINT uq_professionals_license UNIQUE (license_number),
    CONSTRAINT fk_professionals_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Perfil profesional (datos sinteticos). 1:1 con users.';

-- -------------------------------------------------------------------------------------
-- professional_specialties: N:M profesional-especialidad con la marca de primaria (RF-07).
-- is_primary es un atributo de la RELACION (depende de la PK completa), no del profesional
-- ni de la especialidad por separado: por eso vive aqui y cumple 2FN.
-- primary_marker es una columna generada que vale 1 cuando is_primary es verdadero y NULL
-- en caso contrario. Como MySQL admite multiples NULL en un indice unico, el UNIQUE
-- (professional_id, primary_marker) garantiza a nivel de motor COMO MAXIMO UNA especialidad
-- primaria por profesional.
-- -------------------------------------------------------------------------------------
CREATE TABLE professional_specialties (
    professional_id BIGINT      NOT NULL,
    specialty_id    INT         NOT NULL,
    is_primary      BOOLEAN     NOT NULL DEFAULT FALSE,
    assigned_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    primary_marker  TINYINT GENERATED ALWAYS AS (CASE WHEN is_primary = TRUE THEN 1 ELSE NULL END) STORED,
    CONSTRAINT pk_professional_specialties PRIMARY KEY (professional_id, specialty_id),
    CONSTRAINT uq_professional_specialties_primary UNIQUE (professional_id, primary_marker),
    CONSTRAINT fk_prof_specialties_professional FOREIGN KEY (professional_id)
        REFERENCES professionals (id) ON DELETE CASCADE,
    CONSTRAINT fk_prof_specialties_specialty FOREIGN KEY (specialty_id)
        REFERENCES specialties (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'N:M profesional-especialidad; una sola primaria por profesional (RF-07).';

CREATE INDEX ix_prof_specialties_specialty ON professional_specialties (specialty_id);

-- -------------------------------------------------------------------------------------
-- professional_sites: N:M profesional-sede (RF-07, RN-07).
-- Un profesional solo puede publicar agenda en las sedes que aparecen aqui.
-- -------------------------------------------------------------------------------------
CREATE TABLE professional_sites (
    professional_id BIGINT      NOT NULL,
    site_id         SMALLINT    NOT NULL,
    assigned_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_professional_sites PRIMARY KEY (professional_id, site_id),
    CONSTRAINT fk_professional_sites_professional FOREIGN KEY (professional_id)
        REFERENCES professionals (id) ON DELETE CASCADE,
    CONSTRAINT fk_professional_sites_site FOREIGN KEY (site_id) REFERENCES sites (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Sedes habilitadas para cada profesional (RN-07).';

CREATE INDEX ix_professional_sites_site ON professional_sites (site_id);

-- -------------------------------------------------------------------------------------
-- affiliations: afiliacion del usuario a un plan de EPS (RF-04).
-- Guarda SOLO la FK al plan: la EPS y el regimen se alcanzan por eps_plans, nunca se
-- copian aqui (eso seria la dependencia transitiva
-- affiliation_id -> eps_plan_id -> eps_id / regime_id).
-- uq_affiliations_user_plan cumple "evitar duplicar EPS, plan y regimen dentro del usuario".
-- current_marker replica la tecnica de la columna generada para garantizar a lo sumo una
-- afiliacion vigente por usuario.
-- -------------------------------------------------------------------------------------
CREATE TABLE affiliations (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    user_id           BIGINT      NOT NULL,
    eps_plan_id       INT         NOT NULL,
    membership_number VARCHAR(40) NULL,
    is_current        BOOLEAN     NOT NULL DEFAULT TRUE,
    started_on        DATE        NOT NULL,
    ended_on          DATE        NULL,
    created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    current_marker    TINYINT GENERATED ALWAYS AS (CASE WHEN is_current = TRUE THEN 1 ELSE NULL END) STORED,
    CONSTRAINT pk_affiliations PRIMARY KEY (id),
    CONSTRAINT uq_affiliations_user_plan UNIQUE (user_id, eps_plan_id),
    CONSTRAINT uq_affiliations_user_current UNIQUE (user_id, current_marker),
    CONSTRAINT ck_affiliations_dates CHECK (ended_on IS NULL OR ended_on >= started_on),
    CONSTRAINT fk_affiliations_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_affiliations_plan FOREIGN KEY (eps_plan_id) REFERENCES eps_plans (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Afiliacion del usuario a un plan de EPS (RF-04).';

CREATE INDEX ix_affiliations_plan ON affiliations (eps_plan_id);
