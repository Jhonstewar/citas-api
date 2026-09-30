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
- RF-19, textual: «**Todo cambio de estado de cita** guarda: cita; estado nuevo; actor cuando existe; fuente `SYSTEM`, `USER` o `ADMIN`; fecha/hora; motivo opcional» (`PRD.md` §RF-19). Lo que se audita es el **cambio de estado de la cita**, no la decisión administrativa en sí. De ahí que: decidir una cita especializada la lleva de `REQUESTED` a `APPROVED` o `REJECTED` y escribe su fila con actor y origen `ADMIN`, con el motivo en el rechazo (RN-04); aprobar una reprogramación cambia la cita —fecha, hora y sede— y escribe fila `APPROVED`/`ADMIN` con un motivo que nombra la franja anterior y la nueva; y **rechazar una reprogramación no cambia el estado de la cita, que sigue `APPROVED`, así que no escribe fila**: decisor, fecha de decisión y motivo quedan en `reschedule_requests`, de donde los lee el paciente. Esto último es D39 ([[dec-006-decisiones-s4-ciclo-de-vida]]), que es la lectura fiel de RF-19.
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
- [ ] Cada **cambio de estado de cita** provocado por una decisión de ADMIN aparece en el historial de estados con actor, origen `ADMIN`, fecha y hora, y con motivo cuando lo hubo: la decisión sobre una cita especializada (`REQUESTED` → `APPROVED` o `REJECTED`) y la aprobación de una reprogramación (fila `APPROVED` que mueve la cita). El rechazo de una reprogramación no cambia el estado de la cita y, conforme a RF-19, no escribe fila: queda trazado en `reschedule_requests` con decisor, fecha de decisión y motivo (D39).
- [ ] No quedan dependencias bloqueantes dentro del alcance de la épica.

## Riesgos e incógnitas

- **INC-032** — El PRD no define qué debe ocurrir si, al aprobar una cita `REQUESTED`, la franja retenida ya no es válida porque el bloque de disponibilidad fue eliminado o el profesional fue desactivado.
- **INC-033** — El PRD no define si el motivo de rechazo es texto libre o un catálogo de motivos, ni una longitud mínima o máxima.
- **INC-034** — El PRD no define si la bandeja administrativa se restringe por sede o si todo ADMIN ve todas las sedes.
- **INC-035** — El PRD no define si ADMIN puede revertir una decisión ya tomada (por ejemplo, deshacer un rechazo). RN-11 y RN-12 sugieren que no, pero no es explícito.
- **INC-036** — El PRD no define el comportamiento ante una solicitud cuya fecha ya pasó mientras esperaba decisión administrativa.

## Historial

- 2026-09-30 — **Corregida una deriva respecto del PRD.** La regla de RF-19 de esta épica y la casilla correspondiente del criterio de completitud decían «cada **decisión** se registra en el historial con actor y origen `ADMIN`» y «cada **decisión** aparece en el historial de estados con origen `ADMIN`». RF-19 no dice eso: dice «**todo cambio de estado de cita**» (`PRD.md` §RF-19). La reformulación de la épica añadía una exigencia que el PRD no contiene, y fue la que en su momento justificó D22 (escribir historial también al rechazar una reprogramación, aunque la cita no cambie). Ambos textos se reescriben citando RF-19 tal cual y registrando D39 como la decisión que lo respeta. **No se relaja ningún requisito ni se baja el listón: se vuelve al PRD.** Aprobado **directamente por el usuario** el 2026-09-30 (no es aprobación delegada). Afecta a [[HU-031-aprobar-o-rechazar-reprogramacion]], cuyos textos heredados se alinean el mismo día; [[HU-030-aprobar-o-rechazar-cita-especializada]] no cambia, porque allí la decisión **sí** es un cambio de estado de la cita.
