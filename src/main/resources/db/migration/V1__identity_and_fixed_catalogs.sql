-- =====================================================================================
-- V1 - Identidad, catalogos fijos y tokens de seguridad
-- Proyecto: FCV Citas (laboratorio). Motor: MySQL 8.4 / InnoDB / utf8mb4.
-- Todas las tablas son InnoDB por integridad referencial y transacciones (RN-01, RN-09).
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- document_types: catalogo fijo de tipos de documento (RF-01).
-- Se normaliza como tabla en vez de ENUM para poder referenciarla por FK y para no
-- requerir un ALTER TABLE cada vez que cambie el catalogo.
-- -------------------------------------------------------------------------------------
CREATE TABLE document_types (
    id     SMALLINT    NOT NULL AUTO_INCREMENT,
    code   VARCHAR(10) NOT NULL,
    name   VARCHAR(60) NOT NULL,
    active BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_document_types PRIMARY KEY (id),
    CONSTRAINT uq_document_types_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Catalogo fijo de tipos de documento de identidad.';

-- -------------------------------------------------------------------------------------
-- roles: catalogo fijo de roles (RF-05). USER, PROFESSIONAL, ADMIN.
-- -------------------------------------------------------------------------------------
CREATE TABLE roles (
    id          SMALLINT     NOT NULL AUTO_INCREMENT,
    code        VARCHAR(30)  NOT NULL,
    name        VARCHAR(60)  NOT NULL,
    description VARCHAR(200) NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Catalogo fijo de roles de autorizacion (solo lectura para la API).';

-- -------------------------------------------------------------------------------------
-- users: identidad unica de toda persona del sistema (paciente, profesional o admin).
-- Un profesional NO vive en otra tabla de personas: es un rol mas un perfil
-- (professionals), de modo que los datos personales existen una sola vez y no hay
-- anomalias de actualizacion.
-- -------------------------------------------------------------------------------------
CREATE TABLE users (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    document_type_id SMALLINT     NOT NULL,
    document_number  VARCHAR(20)  NOT NULL,
    first_names      VARCHAR(100) NOT NULL,
    last_names       VARCHAR(100) NOT NULL,
    email            VARCHAR(160) NOT NULL,
    phone            VARCHAR(30)  NULL,
    -- VARCHAR(255): admite BCrypt (60 caracteres) y una migracion futura a Argon2 sin
    -- ALTER TABLE. Jamas se almacena la clave en claro (PRD seccion 8).
    password_hash    VARCHAR(255) NOT NULL,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_users PRIMARY KEY (id),
    -- RF-01: email y numero de documento unicos. El documento se declara unico de forma
    -- global (no por tipo) siguiendo la letra del RF-01: en este laboratorio dos personas
    -- distintas no comparten numero de documento.
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_document_number UNIQUE (document_number),
    CONSTRAINT fk_users_document_type FOREIGN KEY (document_type_id)
        REFERENCES document_types (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Identidad unica de personas. Los roles se asignan en user_roles.';

-- -------------------------------------------------------------------------------------
-- user_roles: N:M usuario-rol. PK compuesta y sin atributos que dependan de una sola de
-- las dos FK, por lo que cumple 2FN de forma trivial.
-- -------------------------------------------------------------------------------------
CREATE TABLE user_roles (
    user_id     BIGINT      NOT NULL,
    role_id     SMALLINT    NOT NULL,
    assigned_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id)
        REFERENCES roles (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Asignacion N:M de roles a usuarios (contexto de autorizacion, RF-02).';

CREATE INDEX ix_user_roles_role ON user_roles (role_id);

-- -------------------------------------------------------------------------------------
-- appointment_types: GENERAL / SPECIALIZED (RF-11, RF-12).
-- La politica de aprobacion depende del TIPO, no de la especialidad. Guardarla aqui evita
-- repetirla en cada fila de specialties, que seria la dependencia transitiva
-- specialty_id -> appointment_type_id -> requires_admin_approval.
-- -------------------------------------------------------------------------------------
CREATE TABLE appointment_types (
    id                      SMALLINT    NOT NULL AUTO_INCREMENT,
    code                    VARCHAR(20) NOT NULL,
    name                    VARCHAR(60) NOT NULL,
    requires_admin_approval BOOLEAN     NOT NULL,
    CONSTRAINT pk_appointment_types PRIMARY KEY (id),
    CONSTRAINT uq_appointment_types_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Tipo de cita y su politica de aprobacion (RN-02, RN-03).';

-- -------------------------------------------------------------------------------------
-- appointment_statuses: catalogo fijo de estados de cita (RF-05, RN-11).
-- is_terminal y releases_slots dependen del propio estado (clave candidata code), no de un
-- atributo no clave, por lo que no hay dependencia transitiva. Centralizan la semantica de
-- RN-09 y RF-14 en un solo lugar en vez de repetirla en el codigo.
-- -------------------------------------------------------------------------------------
CREATE TABLE appointment_statuses (
    id             SMALLINT    NOT NULL AUTO_INCREMENT,
    code           VARCHAR(20) NOT NULL,
    name           VARCHAR(60) NOT NULL,
    is_terminal    BOOLEAN     NOT NULL DEFAULT FALSE,
    releases_slots BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_appointment_statuses PRIMARY KEY (id),
    CONSTRAINT uq_appointment_statuses_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Catalogo fijo de estados de cita.';

-- -------------------------------------------------------------------------------------
-- reschedule_statuses: catalogo fijo de estados de solicitud de reprogramacion (RF-15).
-- -------------------------------------------------------------------------------------
CREATE TABLE reschedule_statuses (
    id          SMALLINT    NOT NULL AUTO_INCREMENT,
    code        VARCHAR(20) NOT NULL,
    name        VARCHAR(60) NOT NULL,
    is_terminal BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_reschedule_statuses PRIMARY KEY (id),
    CONSTRAINT uq_reschedule_statuses_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Catalogo fijo de estados de solicitud de reprogramacion.';

-- -------------------------------------------------------------------------------------
-- regimes: catalogo fijo de regimenes de afiliacion (RF-04, RF-05).
-- -------------------------------------------------------------------------------------
CREATE TABLE regimes (
    id   SMALLINT    NOT NULL AUTO_INCREMENT,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(60) NOT NULL,
    CONSTRAINT pk_regimes PRIMARY KEY (id),
    CONSTRAINT uq_regimes_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Catalogo fijo de regimenes (contributivo, subsidiado, especial).';

-- -------------------------------------------------------------------------------------
-- sites: catalogo fijo de sedes del laboratorio (HIC e ICV, PRD seccion 3).
-- Direccion, ciudad y departamento dependen funcionalmente de la sede, no de otro atributo
-- no clave: no hay transitividad.
-- -------------------------------------------------------------------------------------
CREATE TABLE sites (
    id         SMALLINT     NOT NULL AUTO_INCREMENT,
    code       VARCHAR(20)  NOT NULL,
    name       VARCHAR(160) NOT NULL,
    address    VARCHAR(200) NOT NULL,
    city       VARCHAR(80)  NOT NULL,
    department VARCHAR(80)  NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_sites PRIMARY KEY (id),
    CONSTRAINT uq_sites_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Catalogo fijo de sedes (HIC, ICV).';

-- -------------------------------------------------------------------------------------
-- refresh_tokens: almacena SOLO el hash del refresh token (RF-02).
-- Nunca se guarda el token en claro: si la base se filtra, los tokens no son reutilizables.
-- Preparado para rotacion por familia: cada inicio de sesion abre una familia (family_id);
-- cada uso marca used_at y encadena replaced_by_token_id. Detectar el uso de un token ya
-- consumido permite revocar la familia completa.
-- -------------------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    user_id              BIGINT       NOT NULL,
    -- SHA-256 en hexadecimal del token opaco entregado al cliente.
    token_hash           CHAR(64)     NOT NULL,
    -- Identificador de la cadena de rotacion (uno por inicio de sesion).
    family_id            CHAR(36)     NOT NULL,
    issued_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at           DATETIME(6)  NOT NULL,
    used_at              DATETIME(6)  NULL,
    revoked_at           DATETIME(6)  NULL,
    revoked_reason       VARCHAR(100) NULL,
    replaced_by_token_id BIGINT       NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_refresh_tokens_replaced_by FOREIGN KEY (replaced_by_token_id)
        REFERENCES refresh_tokens (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Refresh tokens hasheados, con soporte de rotacion por familia.';

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_expires ON refresh_tokens (expires_at);

-- -------------------------------------------------------------------------------------
-- password_reset_tokens: token temporal de un solo uso (RF-03). Tambien se guarda solo el
-- hash. No lleva family_id ni replaced_by porque no se rota: se consume (used_at) o se
-- revoca (revoked_at) al emitir uno nuevo.
-- -------------------------------------------------------------------------------------
CREATE TABLE password_reset_tokens (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    token_hash     CHAR(64)     NOT NULL,
    issued_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at     DATETIME(6)  NOT NULL,
    used_at        DATETIME(6)  NULL,
    revoked_at     DATETIME(6)  NULL,
    revoked_reason VARCHAR(100) NULL,
    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Tokens de recuperacion de clave, hasheados y de un solo uso.';

CREATE INDEX ix_password_reset_tokens_user ON password_reset_tokens (user_id);
CREATE INDEX ix_password_reset_tokens_expires ON password_reset_tokens (expires_at);
