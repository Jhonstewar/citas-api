---
id: EP-003
tipo: epica
titulo: "Catálogos del sistema"
estado: Borrador
requisitos: [RF-05, RF-06, RF-09]
historias:
  - "[[HU-010-consultar-catalogos-fijos-precargados]]"
  - "[[HU-011-gestionar-especialidades-y-su-duracion]]"
  - "[[HU-012-gestionar-eps-y-planes]]"
dependencias:
  - "[[EP-001-identidad-y-acceso-seguro]]"
---

# EP-003 — Catálogos del sistema

## Objetivo

Disponer de los catálogos fijos precargados de solo lectura y permitir que ADMIN gestione los catálogos configurables (EPS, planes de EPS y especialidades), incluida la duración de cita que cada especialidad define.

## Valor esperado

Las citas, la agenda y las afiliaciones se apoyan en datos maestros consistentes. Separar catálogo fijo de catálogo configurable evita que un cambio administrativo rompa el modelo de estados o las sedes del laboratorio, y centralizar la duración en la especialidad hace que la regla de 30/60 minutos tenga una única fuente de verdad.

## Actores

- ADMIN
- USER (consumidor de catálogos en formularios de búsqueda y afiliación)
- PROFESSIONAL (consumidor de sedes y especialidades)

## Alcance

- Catálogos fijos precargados y de solo lectura: roles, estados de cita, estados de reprogramación, regímenes y sedes (RF-05).
- Las dos sedes del laboratorio quedan precargadas: Hospital Internacional de Colombia (HIC) y Fundación Cardiovascular de Colombia / Instituto Cardiovascular (ICV) (PRD §3).
- CRUD de especialidades por ADMIN, incluida la duración de 30 o 60 minutos y su tipo de cita general o especializada (RF-06, RF-09).
- CRUD de EPS y de planes de EPS por ADMIN (RF-06).
- Activación y desactivación en lugar de borrado físico cuando el catálogo está referenciado por transacciones (RF-06).

## Fuera de alcance

- Creación de nuevas sedes: las sedes son catálogo fijo del laboratorio (PRD §3, RF-05).
- Alta o modificación de roles y estados: son catálogo fijo (RF-05).
- Asignación de especialidades a un profesional, que pertenece a [[EP-004-gestion-de-profesionales]].
- Selección de plan por parte del paciente, que pertenece a [[EP-002-perfil-y-afiliacion-del-paciente]].

## Reglas de negocio

- Los catálogos fijos se cargan por seed y no se modifican desde la aplicación (RF-05).
- No se permite el borrado físico de un catálogo referenciado por transacciones; se usa activación/desactivación (RF-06).
- Cada especialidad define una duración de 30 o 60 minutos y el profesional no la sobrescribe (RF-09).
- RN-08: una especialidad debe estar activa para poder reservarse.
- La política de aprobación depende del tipo de cita de la especialidad, no de la especialidad en sí: general se aprueba automáticamente y especializada requiere ADMIN (RN-02, RN-03).
- Solo ADMIN puede gestionar catálogos configurables (PRD §8, autorización por rol).

## Dependencias

- [[EP-001-identidad-y-acceso-seguro]] — la gestión requiere sesión ADMIN autenticada y autorizada.
- Habilita a: [[EP-002-perfil-y-afiliacion-del-paciente]], [[EP-004-gestion-de-profesionales]], [[EP-005-agenda-del-profesional]], [[EP-006-busqueda-de-disponibilidad-y-reserva]].

## Historias de usuario

- [[HU-010-consultar-catalogos-fijos-precargados]]
- [[HU-011-gestionar-especialidades-y-su-duracion]]
- [[HU-012-gestionar-eps-y-planes]]

## Criterio de completitud de la épica

- [ ] Todas las HU obligatorias de esta épica están `Completada`.
- [ ] Los cinco catálogos fijos de RF-05 están precargados por migración o seed y son consultables desde la API.
- [ ] ADMIN puede crear, editar, activar y desactivar especialidades, EPS y planes desde `citas-web`.
- [ ] Ningún intento de borrado de un catálogo referenciado por una transacción elimina el registro.
- [ ] No quedan dependencias bloqueantes dentro del alcance de la épica.

## Riesgos e incógnitas

- **INC-009** — RF-06 permite CRUD de especialidades, pero RF-11 asume la existencia de `Medicina General` como especialidad general. Falta decidir si `Medicina General` es una especialidad protegida contra borrado/desactivación o una más del catálogo configurable.
- **INC-010** — El PRD no define qué ocurre con los bloques de disponibilidad y las citas futuras cuando se desactiva una especialidad. Requiere decisión: ¿se bloquean nuevas reservas y se respetan las citas ya aprobadas?
- **INC-011** — El PRD no define si el borrado de un catálogo aún no referenciado por transacciones es borrado físico o solo desactivación. RF-06 solo prohíbe el borrado físico del referenciado.
- **INC-012** — El PRD no indica si el tipo de cita (general/especializada) de una especialidad puede cambiarse después de tener citas asociadas.
