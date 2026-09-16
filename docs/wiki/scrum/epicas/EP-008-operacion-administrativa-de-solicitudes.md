---
id: EP-008
tipo: epica
titulo: "Operación administrativa de solicitudes"
estado: Borrador
requisitos: [RF-12, RF-15, RF-18]
historias:
  - "[[HU-029-consultar-bandeja-administrativa]]"
  - "[[HU-030-aprobar-o-rechazar-cita-especializada]]"
  - "[[HU-031-aprobar-o-rechazar-reprogramacion]]"
dependencias:
  - "[[EP-006-busqueda-de-disponibilidad-y-reserva]]"
  - "[[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]"
---

# EP-008 — Operación administrativa de solicitudes

## Objetivo

Dar a ADMIN una bandeja única con las citas especializadas `REQUESTED` y las reprogramaciones `PENDING`, filtrable por sede, profesional, especialidad y fecha, y permitirle aprobar o rechazar cada solicitud con motivo obligatorio en el rechazo y con el efecto correcto sobre los slots.

## Valor esperado

Es el punto de control humano del sistema. Sin esta épica las citas especializadas quedan retenidas para siempre y las reprogramaciones nunca se resuelven, bloqueando la oferta de agenda.

## Actores

- ADMIN

## Alcance

- Bandeja con citas especializadas en estado `REQUESTED` (RF-18).
- Bandeja con solicitudes de reprogramación en estado `PENDING` (RF-18).
- Filtros por sede, profesional, especialidad y fecha (RF-18).
- Aprobación de cita especializada, que la lleva a `APPROVED` (RF-12).
- Rechazo de cita especializada con motivo obligatorio, que la lleva a `REJECTED` y libera sus slots (RF-12, RN-04, RN-09).
- Aprobación de reprogramación, que libera los slots antiguos, asigna los nuevos y actualiza la cita (RF-15).
- Rechazo de reprogramación con motivo, que libera la reserva provisional y mantiene la cita original (RF-15).

## Fuera de alcance

- Creación de citas por ADMIN en nombre de un paciente: no está en el PRD.
- Cancelación de citas por ADMIN: RF-14 asigna la cancelación al usuario.
- Aprobación de citas generales, que son automáticas (RN-02).
- Gestión de catálogos y profesionales, que viven en [[EP-003-catalogos-del-sistema]] y [[EP-004-gestion-de-profesionales]].

## Reglas de negocio

- RN-03: las citas especializadas requieren decisión de ADMIN.
- RN-04: todo rechazo administrativo requiere motivo.
- RN-09: rechazar libera las reservas correspondientes.
- RN-10: la cita original se mantiene hasta que la reprogramación sea aprobada.
- RN-01: al aprobar una reprogramación, los nuevos slots ya retenidos se confirman y los antiguos se liberan.
- RN-11: las transiciones de estado son explícitas y verificables.
- RF-19: cada decisión se registra en el historial con actor y origen `ADMIN`, incluyendo el motivo cuando existe.
- Solo el rol `ADMIN` puede ejecutar estas decisiones (PRD §8).

## Dependencias

- [[EP-006-busqueda-de-disponibilidad-y-reserva]] — deben existir citas `REQUESTED`.
- [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]] — deben existir solicitudes de reprogramación `PENDING`.
- [[EP-009-trazabilidad-y-contrato-rest]] — las decisiones se auditan.

## Historias de usuario

- [[HU-029-consultar-bandeja-administrativa]]
- [[HU-030-aprobar-o-rechazar-cita-especializada]]
- [[HU-031-aprobar-o-rechazar-reprogramacion]]

## Criterio de completitud de la épica

- [ ] Todas las HU obligatorias de esta épica están `Completada`.
- [ ] ADMIN ve en una sola bandeja las citas `REQUESTED` y las reprogramaciones `PENDING`, con los cuatro filtros de RF-18.
- [ ] Ningún rechazo se persiste sin motivo.
- [ ] Un rechazo de cita especializada deja sus slots disponibles para otro paciente.
- [ ] Una aprobación de reprogramación deja la franja antigua libre, la nueva ocupada y la cita actualizada, sin crear una cita duplicada.
- [ ] Cada decisión aparece en el historial de estados con origen `ADMIN`.
- [ ] No quedan dependencias bloqueantes dentro del alcance de la épica.

## Riesgos e incógnitas

- **INC-032** — El PRD no define qué debe ocurrir si, al aprobar una cita `REQUESTED`, la franja retenida ya no es válida porque el bloque de disponibilidad fue eliminado o el profesional fue desactivado.
- **INC-033** — El PRD no define si el motivo de rechazo es texto libre o un catálogo de motivos, ni una longitud mínima o máxima.
- **INC-034** — El PRD no define si la bandeja administrativa se restringe por sede o si todo ADMIN ve todas las sedes.
- **INC-035** — El PRD no define si ADMIN puede revertir una decisión ya tomada (por ejemplo, deshacer un rechazo). RN-11 y RN-12 sugieren que no, pero no es explícito.
- **INC-036** — El PRD no define el comportamiento ante una solicitud cuya fecha ya pasó mientras esperaba decisión administrativa.
