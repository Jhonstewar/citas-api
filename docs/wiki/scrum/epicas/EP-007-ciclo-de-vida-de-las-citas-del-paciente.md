---
id: EP-007
tipo: epica
titulo: "Ciclo de vida de las citas del paciente"
estado: Borrador
requisitos: [RF-13, RF-14, RF-15]
historias:
  - "[[HU-025-consultar-mis-citas-y-detalle]]"
  - "[[HU-026-cancelar-una-cita-futura]]"
  - "[[HU-027-solicitar-reprogramacion-de-cita-aprobada]]"
  - "[[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]]"
dependencias:
  - "[[EP-006-busqueda-de-disponibilidad-y-reserva]]"
---

# EP-007 — Ciclo de vida de las citas del paciente

## Objetivo

Permitir que un `USER` consulte sus citas con su detalle completo, cancele una cita futura liberando los slots, solicite la reprogramación de una cita aprobada sin perder la cita original, y decida qué hacer con ella cuando la reprogramación es rechazada.

## Valor esperado

El paciente gestiona sus compromisos sin llamar a nadie, y el sistema devuelve al mercado las franjas que dejan de usarse. La regla de conservar la cita original hasta la decisión administrativa evita que un paciente se quede sin cita por intentar moverla.

## Actores

- USER

## Alcance

- Listado de las citas propias con filtros por estado y por fecha (RF-13).
- Detalle de cita con sede, profesional, especialidad, fecha y hora, duración, estado y motivo de rechazo cuando exista (RF-13).
- Cancelación de una cita futura en estado no terminal, con liberación de slots e historial (RF-14).
- Solicitud de reprogramación sobre una cita aprobada y futura, conservando profesional y especialidad (RF-15).
- Retención provisional de la nueva franja mientras la solicitud está `PENDING`, sin liberar la franja original (RF-15, RN-10).
- Decisión del usuario de conservar o cancelar su cita después de un rechazo de reprogramación (RF-15).

## Fuera de alcance

- Decisión administrativa sobre la reprogramación: pertenece a [[EP-008-operacion-administrativa-de-solicitudes]].
- Cambio de profesional dentro de una reprogramación: el PRD lo trata como una cita nueva (RF-15), que se crea en [[EP-006-busqueda-de-disponibilidad-y-reserva]].
- Reactivación de una cita cancelada: el PRD la prohíbe explícitamente (RF-14).
- Cierre de atención `COMPLETED` o `NO_SHOW`, que corresponde al profesional en [[EP-005-agenda-del-profesional]].

## Reglas de negocio

- El usuario solo ve y opera sobre sus propias citas (ownership, PRD §8).
- RF-14: solo puede cancelarse una cita futura y en estado no terminal.
- RN-09: cancelar libera las reservas correspondientes.
- RF-14: una cita cancelada no se reactiva directamente.
- RF-15: solo una cita `APPROVED` y futura puede solicitar reprogramación.
- RF-15: la reprogramación conserva profesional y especialidad.
- RN-10: la reprogramación no destruye la cita anterior hasta que sea aprobada; la cita original conserva su franja hasta la decisión de ADMIN.
- RN-01 y RN-05: la nueva franja propuesta debe estar libre y, si la duración es de 60 minutos, ocupar dos slots consecutivos.
- RN-06: la nueva fecha y hora no pueden estar en el pasado.
- RF-19 y RN-11: cada transición de estado se registra en el historial con actor y origen `USER`.

## Dependencias

- [[EP-006-busqueda-de-disponibilidad-y-reserva]] — debe existir la capacidad de crear citas y de consultar disponibilidad.
- [[EP-008-operacion-administrativa-de-solicitudes]] — la reprogramación necesita una decisión administrativa para cerrarse.
- [[EP-009-trazabilidad-y-contrato-rest]] — todas las transiciones se auditan.

## Historias de usuario

- [[HU-025-consultar-mis-citas-y-detalle]]
- [[HU-026-cancelar-una-cita-futura]]
- [[HU-027-solicitar-reprogramacion-de-cita-aprobada]]
- [[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]]

## Criterio de completitud de la épica

- [ ] Todas las HU obligatorias de esta épica están `Completada`.
- [ ] El detalle de cita muestra los siete datos mínimos exigidos por RF-13, incluido el motivo de rechazo cuando aplica.
- [ ] Una cancelación devuelve los slots a disponibles y queda registrada en el historial.
- [ ] Mientras una reprogramación está `PENDING`, la cita original sigue ocupando su franja y la nueva franja queda retenida.
- [ ] Tras un rechazo, el usuario puede conservar la cita original o cancelarla, y la franja provisional queda liberada.
- [ ] No quedan dependencias bloqueantes dentro del alcance de la épica.

## Riesgos e incógnitas

- **INC-027** — El PRD no define una antelación mínima para cancelar una cita futura ("cita futura" es la única condición de RF-14).
- **INC-028** — El PRD no define cuántas solicitudes de reprogramación puede hacer un usuario sobre la misma cita, ni si puede tener más de una `PENDING` a la vez.
- **INC-029** — El PRD no define si el usuario puede retirar su propia solicitud de reprogramación antes de que ADMIN decida.
- **INC-030** — El PRD no define si una cita `REQUESTED` puede cancelarse. RF-14 habla de "cita futura no terminal", lo que incluiría `REQUESTED`, pero conviene confirmarlo explícitamente.
- **INC-031** — El PRD no define si la reprogramación puede cambiar de sede, dado que solo obliga a conservar profesional y especialidad.
