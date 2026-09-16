-- =====================================================================================
-- V4 - Seeds de los catalogos FIJOS (RF-05).
-- Solo se siembran catalogos de solo lectura. Los catalogos configurables (eps, eps_plans,
-- specialties) los administra ADMIN por CRUD (RF-06) y no se precargan aqui.
-- No hay datos personales: nada de pacientes, profesionales ni credenciales.
-- =====================================================================================

-- Tipos de documento (RF-01).
INSERT INTO document_types (code, name) VALUES
    ('CC', 'Cédula de ciudadanía'),
    ('TI', 'Tarjeta de identidad'),
    ('CE', 'Cédula de extranjería'),
    ('PA', 'Pasaporte'),
    ('RC', 'Registro civil');

-- Roles del PRD (seccion 2).
INSERT INTO roles (code, name, description) VALUES
    ('USER',         'Usuario',      'Paciente ficticio; se registra por si mismo (RF-01).'),
    ('PROFESSIONAL', 'Profesional',  'Gestiona su disponibilidad y consulta su agenda (RF-08, RF-16).'),
    ('ADMIN',        'Administrador','Gestiona catalogos, profesionales y aprobaciones (RF-06, RF-18).');

-- Tipos de cita y su politica de aprobacion (RN-02, RN-03).
INSERT INTO appointment_types (code, name, requires_admin_approval) VALUES
    ('GENERAL',     'Cita general',      FALSE),
    ('SPECIALIZED', 'Cita especializada', TRUE);

-- Estados de cita (RF-11, RF-12, RF-14, RF-17).
-- releases_slots marca los estados que devuelven los slots al pool (RN-09).
INSERT INTO appointment_statuses (code, name, is_terminal, releases_slots) VALUES
    ('REQUESTED', 'Solicitada', FALSE, FALSE),
    ('APPROVED',  'Aprobada',   FALSE, FALSE),
    ('REJECTED',  'Rechazada',  TRUE,  TRUE),
    ('CANCELLED', 'Cancelada',  TRUE,  TRUE),
    ('COMPLETED', 'Atendida',   TRUE,  FALSE),
    ('NO_SHOW',   'No asistio', TRUE,  FALSE);

-- Estados de solicitud de reprogramacion (RF-15).
INSERT INTO reschedule_statuses (code, name, is_terminal) VALUES
    ('PENDING',   'Pendiente', FALSE),
    ('APPROVED',  'Aprobada',  TRUE),
    ('REJECTED',  'Rechazada', TRUE),
    ('CANCELLED', 'Cancelada', TRUE);

-- Regimenes de afiliacion (RF-04, RF-05).
INSERT INTO regimes (code, name) VALUES
    ('CONTRIBUTIVO', 'Régimen contributivo'),
    ('SUBSIDIADO',   'Régimen subsidiado'),
    ('ESPECIAL',     'Régimen especial');

-- Las dos sedes fijas del laboratorio (PRD seccion 3).
INSERT INTO sites (code, name, address, city, department) VALUES
    ('HIC', 'Hospital Internacional de Colombia',
            'Km 7 Autopista Bucaramanga - Piedecuesta, Valle de Menzulí',
            'Piedecuesta', 'Santander'),
    ('ICV', 'Fundación Cardiovascular de Colombia / Instituto Cardiovascular',
            'Calle 155A No. 23-58, Urbanización El Bosque',
            'Floridablanca', 'Santander');
