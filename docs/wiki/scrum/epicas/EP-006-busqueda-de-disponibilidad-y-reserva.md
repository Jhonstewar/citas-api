---
id: EP-006
tipo: epica
titulo: "Búsqueda de disponibilidad y reserva de citas"
estado: Borrador
requisitos: [RF-10, RF-11, RF-12]
historias:
  - "[[HU-022-buscar-disponibilidad-con-filtros]]"
  - "[[HU-023-agendar-cita-de-medicina-general]]"
  - "[[HU-024-solicitar-cita-especializada]]"
dependencias:
  - "[[EP-005-agenda-del-profesional]]"
  - "[[EP-003-catalogos-del-sistema]]"
---

# EP-006 — Búsqueda de disponibilidad y reserva de citas

## Objetivo

Permitir que un `USER` encuentre horarios que puedan completar toda la duración requerida por la especialidad y reserve una cita, que nace `APPROVED` si es de Medicina General o `REQUESTED` con los slots retenidos si es especializada.

## Valor esperado

Es el momento en que el producto entrega su promesa central: el paciente obtiene una cita. La retención de slots al confirmar es lo que impide la doble reserva y hace que la oferta mostrada sea confiable.

## Actores

- USER

## Alcance

- Búsqueda de disponibilidad con filtros por sede, tipo de cita general o especializada, especialidad, profesional y fecha (RF-10).
- Presentación únicamente de horarios que puedan completar la duración requerida (RF-10, RN-05).
- Creación de cita de Medicina General con aprobación automática sobre un profesional general elegido por el usuario (RF-11).
- Creación de solicitud de cita especializada en estado `REQUESTED` con retención de los slots (RF-12).
- Verificación de disponibilidad en el momento de confirmar, no solo en el momento de buscar (RF-11).

## Fuera de alcance

- Decisión administrativa de aprobar o rechazar la cita especializada: pertenece a [[EP-008-operacion-administrativa-de-solicitudes]].
- Consulta posterior, cancelación y reprogramación: pertenecen a [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]].
- Publicación de la disponibilidad: pertenece a [[EP-005-agenda-del-profesional]].
- Pagos, autorizaciones de EPS y facturación (PRD §9).

## Reglas de negocio

- RN-01: ninguna cita puede ocupar slots ya reservados o retenidos.
- RN-02: las citas generales se aprueban automáticamente y nacen `APPROVED`.
- RN-03: las citas especializadas nacen `REQUESTED` y requieren decisión de ADMIN.
- RN-05: cuando la duración es de 60 minutos, los dos slots deben ser consecutivos y estar ambos disponibles.
- RN-06: no se permiten citas en el pasado.
- RN-07: el profesional solo ofrece agenda en sedes asignadas.
- RN-08: la especialidad debe estar activa y asociada al profesional para poder reservarse.
- RF-09: la duración proviene de la especialidad y el usuario no la modifica.
- RF-19: la creación de la cita registra su estado inicial en el historial, con origen `SYSTEM` para la aprobación automática y `USER` para la solicitud.

## Dependencias

- [[EP-005-agenda-del-profesional]] — sin bloques ni slots no hay disponibilidad que mostrar.
- [[EP-003-catalogos-del-sistema]] — especialidades activas, duración y sedes.
- [[EP-002-perfil-y-afiliacion-del-paciente]] — la afiliación puede asociarse a la cita (pendiente de la incógnita INC-008).
- [[EP-009-trazabilidad-y-contrato-rest]] — el alta de la cita escribe historial.

## Historias de usuario

- [[HU-022-buscar-disponibilidad-con-filtros]]
- [[HU-023-agendar-cita-de-medicina-general]]
- [[HU-024-solicitar-cita-especializada]]

## Criterio de completitud de la épica

- [ ] Todas las HU obligatorias de esta épica están `Completada`.
- [ ] La búsqueda nunca ofrece una franja que no pueda alojar la duración completa de la especialidad.
- [ ] Dos confirmaciones concurrentes sobre la misma franja no producen dos citas: una de ellas se rechaza con un error explícito.
- [ ] Una cita de Medicina General queda `APPROVED` sin intervención de ADMIN.
- [ ] Una cita especializada queda `REQUESTED` con sus slots retenidos y visible en la bandeja de [[EP-008-operacion-administrativa-de-solicitudes]].
- [ ] No quedan dependencias bloqueantes dentro del alcance de la épica.

## Riesgos e incógnitas

- **INC-022** — El PRD no define una antelación mínima para reservar (por ejemplo, si puede reservarse un slot que empieza en cinco minutos). RN-06 solo prohíbe el pasado.
- **INC-023** — El PRD no define un horizonte máximo de fechas consultables en la búsqueda de disponibilidad.
- **INC-024** — El PRD no define si la retención de slots de una cita `REQUESTED` caduca cuando ADMIN no decide, ni en qué plazo. Es relevante porque la retención bloquea la oferta a otros pacientes (RN-01).
- **INC-025** — El PRD no limita cuántas citas `REQUESTED` simultáneas puede tener un mismo usuario, ni prohíbe que un usuario reserve dos citas que se solapen entre sí.
- **INC-026** — RF-11 exige que el usuario escoja "uno de los profesionales generales disponibles" pero no define si existe una opción de asignación automática.
