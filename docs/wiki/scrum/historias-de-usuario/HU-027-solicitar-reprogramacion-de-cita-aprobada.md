---
id: HU-027
tipo: historia-de-usuario
titulo: "Solicitar la reprogramación de una cita aprobada"
estado: Completada
epica: "[[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]"
requisitos: [RF-15]
esfuerzo: "Alto"
sprint_sugerido: "Sprint 7"
dependencias:
  - "[[HU-025-consultar-mis-citas-y-detalle]]"
  - "[[HU-022-buscar-disponibilidad-con-filtros]]"
relacionadas:
  - "[[HU-031-aprobar-o-rechazar-reprogramacion]]"
  - "[[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]]"
---

# HU-027 — Solicitar la reprogramación de una cita aprobada

## Historia de usuario

**COMO** USER autenticado con una cita aprobada y futura
**QUIERO** solicitar mover una cita aprobada a otro horario disponible
**PARA** cambiar la fecha sin perder la cita que ya tengo

> Como USER autenticado con una cita aprobada y futura, quiero solicitar mover una cita aprobada a otro horario disponible para cambiar la fecha sin perder la cita que ya tengo.

## Contexto y descripción

RF-15 describe la reprogramación como una solicitud, no como un cambio inmediato. Solo una cita `APPROVED` y futura puede originarla, la solicitud conserva profesional y especialidad, nace en estado `PENDING` y la nueva franja se retiene mientras espera decisión. RN-10 añade la garantía que da sentido a toda la historia: la cita original conserva su franja hasta que ADMIN decida, de modo que el paciente nunca queda sin cita por haber intentado moverla.

La consecuencia directa es que mientras la solicitud está `PENDING` el sistema mantiene dos franjas retenidas para el mismo paciente: la original y la propuesta. Es una ocupación deliberada y temporal, y se resuelve en [[HU-031-aprobar-o-rechazar-reprogramacion]]. Esta HU introduce además la tabla de solicitudes de reprogramación mediante migración Flyway, con su estado y las fechas anterior y propuesta, porque la información no cabe en la propia cita sin destruir el dato original.

## Alcance

- Endpoint de creación de una solicitud de reprogramación sobre una cita propia en `citas-api`.
- Validación de que la cita origen está `APPROVED` y es futura.
- Conservación obligatoria del profesional y de la especialidad de la cita original.
- Creación de la solicitud en estado `PENDING`.
- Retención de la nueva franja propuesta mientras la solicitud está `PENDING`.
- Conservación intacta de la franja y del estado de la cita original.
- Validación de la nueva franja: no pasada, libre y con dos slots consecutivos cuando la duración es de 60 minutos.
- Migración Flyway de la tabla de solicitudes de reprogramación con estado, fecha y hora anterior y fecha y hora propuesta.
- Pantalla "solicitar reprogramación" en `citas-web` (PRD §6).

## Fuera de alcance

- Aprobación o rechazo de la solicitud, que se cubre en [[HU-031-aprobar-o-rechazar-reprogramacion]].
- Decisión del paciente tras un rechazo, que se cubre en [[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]].
- Cambio de profesional: RF-15 lo trata como una cita nueva, que se crea desde [[HU-022-buscar-disponibilidad-con-filtros]].
- Cambio de especialidad, por la misma razón.
- Reprogramación de citas `REQUESTED`, `CANCELLED`, `REJECTED`, `COMPLETED` o `NO_SHOW`: RF-15 la limita a `APPROVED`.
- Notificación por correo de la solicitud: pertenece a la automatización posterior de PRD §10.

## Reglas de negocio

- Solo una cita en estado `APPROVED` y con fecha futura admite solicitud de reprogramación (RF-15).
- La solicitud conserva el profesional y la especialidad de la cita original; cambiar de profesional se trata como una cita nueva (RF-15).
- La solicitud nace en estado `PENDING` (RF-15).
- La nueva franja queda retenida mientras la solicitud está `PENDING` y ninguna otra cita puede ocuparla (RF-15, RN-01).
- La cita original conserva su franja y su estado `APPROVED` hasta la decisión administrativa (RN-10).
- La nueva fecha y hora no pueden estar en el pasado (RN-06).
- Si la duración de la especialidad es de 60 minutos, la nueva franja exige dos slots de 30 minutos consecutivos y disponibles (RN-05, RF-09).
- La nueva franja no puede ocupar slots ya reservados o retenidos por otra cita o solicitud (RN-01).
- Un usuario solo puede solicitar la reprogramación de sus propias citas (PRD §8, ownership).

## Dependencias y relaciones

- Épica: [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]
- Dependencias: [[HU-025-consultar-mis-citas-y-detalle]], [[HU-022-buscar-disponibilidad-con-filtros]]
- Relacionadas: [[HU-031-aprobar-o-rechazar-reprogramacion]], [[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]], [[HU-029-consultar-bandeja-administrativa]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Alto

**Justificación de dificultad:** Es la única operación del producto que mantiene dos franjas retenidas simultáneamente para la misma cita, y hacerlo sin romper RN-01 exige que la retención de la nueva franja compita correctamente con cualquier otra reserva concurrente. Añade además un agregado nuevo con su propia migración Flyway, un conjunto de validaciones que reutiliza las reglas de reserva de la épica de disponibilidad, y una pantalla que combina el detalle de la cita con la búsqueda de huecos del mismo profesional.

## Tareas de desarrollo

- [ ] **T-01 — Modelar el agregado de solicitud de reprogramación en el dominio**
  Dificultad: Alto
  Descripción: Entidad de dominio con referencia a la cita original, fecha y hora anterior, fecha y hora propuesta y estado, con las invariantes de cita `APPROVED` y futura, profesional y especialidad conservados, y fecha propuesta no pasada, sin dependencias de framework.

- [ ] **T-02 — Crear la migración Flyway de solicitudes de reprogramación**
  Dificultad: Medio
  Descripción: Migración que crea la tabla de solicitudes con referencia a la cita, estado de reprogramación tomado del catálogo fijo de RF-05, fecha y hora anterior, fecha y hora propuesta, motivo de la decisión y marcas temporales, con las claves foráneas correspondientes.

- [ ] **T-03 — Implementar el caso de uso de solicitud de reprogramación**
  Dificultad: Alto
  Descripción: Caso de uso que valida propiedad y estado de la cita, verifica la disponibilidad de la nueva franja, retiene sus slots, crea la solicitud en `PENDING` y deja intacta la cita original, todo en una única transacción.

- [ ] **T-04 — Implementar la retención de la nueva franja en persistencia**
  Dificultad: Alto
  Descripción: Adaptador que reserva los slots propuestos de forma que dos solicitudes o reservas concurrentes sobre la misma franja no puedan tener éxito a la vez, y que distingue la retención provisional de la ocupación definitiva.

- [ ] **T-05 — Exponer el adaptador REST de solicitud de reprogramación**
  Dificultad: Medio
  Descripción: Endpoint autenticado que recibe la cita y la nueva fecha y hora, con respuestas diferenciadas para cita no `APPROVED`, cita pasada, franja pasada, franja ocupada, ausencia de dos slots consecutivos e intento de cambio de profesional.

- [ ] **T-06 — Construir la pantalla de solicitar reprogramación en citas-web**
  Dificultad: Medio
  Descripción: Vista que parte del detalle de la cita, ofrece únicamente los huecos disponibles del mismo profesional y especialidad, impide seleccionar otro profesional, y muestra el estado `PENDING` resultante junto con la cita original que se conserva.

- [ ] **T-07 — Pruebas de solicitud y de doble retención**
  Dificultad: Alto
  Descripción: Pruebas de dominio para las invariantes, y pruebas de integración para la doble retención simultánea, la franja de 60 minutos con dos slots consecutivos, la franja pasada, la franja ya ocupada, la cita no aprobada y la cita ajena.

## Criterios de aceptación

### CA-01 — Solo una cita APPROVED y futura admite solicitud

**Dado** citas propias en estados `REQUESTED`, `CANCELLED`, `REJECTED` y `COMPLETED`, y una cita `APPROVED` con fecha ya pasada
**Cuando** el usuario intenta solicitar reprogramación sobre cada una de ellas
**Entonces** la API rechaza todas las peticiones con un error de regla de negocio y no crea ninguna solicitud ni retiene ninguna franja.

### CA-02 — La solicitud conserva profesional y especialidad

**Dado** una cita `APPROVED` y futura con un profesional y una especialidad concretos
**Cuando** el usuario envía una solicitud de reprogramación indicando un profesional distinto del de la cita original
**Entonces** la API rechaza la petición indicando que el cambio de profesional se trata como una cita nueva (RF-15), y la solicitud creada en el caso válido conserva exactamente el profesional y la especialidad de la cita original.

### CA-03 — La solicitud nace en estado PENDING

**Dado** una cita `APPROVED` y futura y una nueva franja válida del mismo profesional
**Cuando** el usuario envía la solicitud de reprogramación
**Entonces** la API responde con éxito y la solicitud queda persistida en estado `PENDING`, con la fecha y hora anterior y la fecha y hora propuesta registradas.

### CA-04 — La nueva franja queda retenida mientras la solicitud está PENDING

**Dado** una solicitud de reprogramación en estado `PENDING` sobre una franja concreta
**Cuando** otro paciente busca disponibilidad e intenta reservar esa misma franja
**Entonces** la franja no aparece como disponible y cualquier intento directo de reservarla se rechaza (RN-01).

### CA-05 — La cita original conserva su franja y su estado

**Dado** una solicitud de reprogramación en estado `PENDING`
**Cuando** se consulta la cita original y la ocupación de sus slots
**Entonces** la cita sigue en estado `APPROVED`, sus slots originales siguen ocupados por ella, y quedan retenidas simultáneamente la franja original y la franja propuesta (RN-10).

### CA-06 — La nueva franja no puede estar en el pasado

**Dado** una cita `APPROVED` y futura
**Cuando** el usuario propone como nueva fecha y hora un instante anterior al momento actual
**Entonces** la API responde con un error de regla de negocio, no se crea la solicitud y no se retiene ningún slot (RN-06).

### CA-07 — Duración de 60 minutos exige dos slots consecutivos

**Dado** una cita de una especialidad de 60 minutos y una franja propuesta cuyo slot siguiente está ocupado o no existe
**Cuando** el usuario envía la solicitud sobre esa franja
**Entonces** la API responde con un error de regla de negocio, no se crea la solicitud y no se retiene el primer slot; y cuando los dos slots consecutivos sí están libres, la solicitud se crea reteniendo ambos (RN-05).

### CA-08 — La nueva franja no puede pisar slots ocupados o retenidos

**Dado** una franja del mismo profesional ya ocupada por otra cita `APPROVED` o retenida por una cita `REQUESTED`
**Cuando** el usuario la propone como nueva fecha y hora
**Entonces** la API responde con un error de regla de negocio y no se crea la solicitud (RN-01).

### CA-09 — El esquema de solicitudes se crea por migración versionada

**Dado** una base de datos MySQL 8.4 con las migraciones previas aplicadas
**Cuando** se arranca `citas-api`
**Entonces** Flyway aplica la migración que crea la tabla de solicitudes de reprogramación con estado, fecha y hora anterior y fecha y hora propuesta, y el arranque finaliza sin error.

### CA-10 — Una sola solicitud sin decidir por cita

**Dado** una cita `APPROVED` y futura con una solicitud de reprogramación `PENDING`
**Cuando** el usuario envía una segunda solicitud sobre la misma cita
**Entonces** la API responde 409, no se crea la segunda solicitud y no se retiene ninguna franja nueva; y una vez que ADMIN decide la primera (aprobada o rechazada), una nueva solicitud sobre la misma cita, si sigue `APPROVED` y futura, sí se admite (D20).

## Definition of Done

- [x] Los criterios CA-01 a CA-10 están validados con evidencia concreta.
- [x] Existe una migración Flyway versionada para la tabla de solicitudes de reprogramación, aplicada sobre el esquema existente sin pérdida de datos.
- [x] Está demostrado con una prueba que, con la solicitud en `PENDING`, la cita original conserva su franja y la franja propuesta está retenida al mismo tiempo (RN-10).
- [x] La retención de la nueva franja usa el mismo mecanismo de reserva que la creación de citas, de modo que RN-01 se cumple frente a peticiones concurrentes.
- [x] El agregado de solicitud de reprogramación vive en el dominio sin depender de Spring ni de JPA.
- [x] La creación de la solicitud, la retención de slots y la persistencia ocurren en una única transacción, sin resultados parciales.
- [ ] La pantalla de `citas-web` no permite seleccionar un profesional distinto del de la cita original.
- [x] Existen pruebas automatizadas de la solicitud válida, de la franja pasada, de la franja ocupada, de los dos slots consecutivos y del intento de cambio de profesional, y pasan.
- [x] El contrato del endpoint de solicitud de reprogramación está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [x] La trazabilidad de esta HU y de [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Ejecución de referencia del **2026-09-30** (iteración 2 del LOOP_02 de S4): el `backend-verifier`, agente independiente que no escribió el código, reejecutó la suite completa de `citas-api` → **480 pruebas, 0 fallos, 0 errores, `BUILD SUCCESS`**, con `HexagonalArchitectureTest` 4/4 (`evidencias/s4/loops/LOOP-02/iter-2-verifier.json`). Abreviaturas: **RRIT** = `src/test/java/com/fcv/citas/infrastructure/rest/RescheduleRequestIntegrationTest.java`, **RRT** = `src/test/java/com/fcv/citas/domain/appointment/RescheduleRequestTest.java`.

**Cerrado el 2026-10-04** (ver historial): lo de frontend pasó a `Cumple`. Texto del 2026-09-30: lo de **frontend** quedaba en `Pendiente`: el `backend-verifier` lo clasificó `NO VERIFICABLE` porque `citas-web` está fuera de su repositorio, y el `frontend-verifier` dio PASS solo al alcance de la iteración 2, sin la prueba manual en navegador que pide la fase F10 del plan.

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | RRIT:313 `onlyAnApprovedFutureAppointmentAdmitsARequest` (`REQUESTED`, `CANCELLED`, `REJECTED`, `COMPLETED` y `NO_SHOW` → 409 `INVALID_TRANSITION`, cero solicitudes y el detalle con `reschedulable: false`; `APPROVED` con fecha de ayer → 409 `APPOINTMENT_EXPIRED`); al final `heldSlots()` es 0 y el slot de las 10:00 no tiene titular; dominio RRT:74 | Cubre un estado más de los que enumera el criterio (`NO_SHOW`) |
| CA-02 | Cumple | RRIT:285 `changingProfessionalOrSpecialtyIsRejectedAsANewAppointment` (otro profesional y otra especialidad → 422 `WRONG_FLOW`, cero solicitudes y cero retenciones; enviar el **mismo** profesional y especialidad sí se admite); RRIT:190 comprueba en `appointments` que la cita de la solicitud creada conserva `professional_id` y `specialty_id`; dominio RRT:103 | — |
| CA-03 | Cumple | RRIT:190 `aValidRequestIsPendingAndHoldsBothSlotsAtOnce` (201 con `status = PENDING`, `previous.date/startTime/endTime/site` y `proposed.*`; y en `reschedule_requests`: `code = PENDING`, `previous_date`, `previous_start_time`, `previous_end_time`, `previous_site_id`, `proposed_start_time`, `proposed_site_id`, `requested_by_user_id` y `decided_at` nulo) | La "fecha y hora anterior registradas" que pide el criterio es posible desde `V10` (D31). Antes de esa migración el criterio no se cumplía literalmente; ver «Notas y decisiones» |
| CA-04 | Cumple | RRIT:342 `heldSlotsAreNeitherOfferedNorBookable` (con la solicitud `PENDING`, la franja 09:00–10:00 desaparece de `/api/patient/availability` para otro paciente y reservarla directamente responde 409 `SLOT_TAKEN`; las dos filas siguen con `reservation_type = RESCHEDULE_REQUEST`) | La misma prueba cubre CA-03 de [[HU-022-buscar-disponibilidad-con-filtros]], que no tenía productor en S3 |
| CA-05 | Cumple | RRIT:190, líneas 232–256: la cita sigue `APPROVED` a las 08:00 con su profesional y especialidad, el slot de las 08:00 es `APPOINTMENT` y el de las 10:00 es `RESCHEDULE_REQUEST` **al mismo tiempo**, y el detalle expone `pendingReschedule: true`, `reschedulable: false`, `cancellable: true` y `lastReschedule`; `history(id)` no cambia | La doble retención simultánea de RN-10 se asevera fila a fila, no por conteo |
| CA-06 | Cumple | RRIT:371 `aProposedSlotInThePastIsRejected` (franja de ayer → 422 `PAST_TIME`, cero solicitudes y el slot de ayer sin titular); dominio RRT:89 | — |
| CA-07 | Cumple | RRIT:389 `sixtyMinutesNeedsTwoConsecutiveFreeSlots` (sin slot siguiente → 422 `SLOT_NOT_AVAILABLE`; con el siguiente ocupado → 409 `SLOT_TAKEN` sin retener el primero; con los dos libres → 201 reteniendo 09:30 y 10:00 con `slot_order` 1 y 2); RRIT:422 `aProposalOverlappingTheCurrentSlotIsRejected` | Las dos mitades del criterio (rechazo y creación reteniendo ambos) están en la misma prueba |
| CA-08 | Cumple | RRIT:444 `aTakenOrRetainedSlotCannotBeProposed` (franja ocupada por una `APPROVED` ajena y franja retenida por una `REQUESTED` ajena → 409 `SLOT_TAKEN`; cero solicitudes, cero retenciones y la cita ajena conserva sus dos reservas) | — |
| CA-09 | Cumple | `V3__schedule_and_appointments.sql` crea `reschedule_requests` con su estado del catálogo y la franja propuesta; `V10__reschedule_previous_slot.sql` (D31) añade la franja **anterior** y la sede propuesta, obligatorias y con clave foránea a `sites`; `FlywayMigratesEmptySchemaTest#laReprogramacionGuardaLaFranjaAnteriorYLaSedePropuesta` arranca Flyway sobre un esquema vacío y comprueba en `information_schema` que las cinco columnas existen, son `NOT NULL` y tienen las dos FK | El arranque sin error lo demuestra toda la suite de integración, que levanta el contexto contra MySQL 8.4 |
| CA-10 | Cumple | RRIT:474 `onlyOneUndecidedRequestPerAppointment` (con una `PENDING`, la segunda solicitud → 409 `RESCHEDULE_PENDING`, una sola solicitud y el slot propuesto sin titular; tras el **rechazo** se admite otra y tras la **aprobación** una tercera: tres solicitudes en total); `uq_reschedule_requests_active` (`V3__schedule_and_appointments.sql:162`) | Implementa D20 en los dos sentidos: el límite y la reapertura tras decidir |
| DoD — CA-01 a CA-10 validados con evidencia concreta | Cumple | Filas CA-01 a CA-10 de esta tabla | Los diez son observables por API y base de datos |
| DoD — Migración Flyway versionada, aplicada sobre el esquema existente sin pérdida de datos | Cumple (2026-10-04) | **Re-verificación:** `FlywayV10BackfillTest` (4 pruebas sobre un esquema desechable: migra a V9, siembra 2 solicitudes y migra a V10; `:135` `ningunaSolicitudPreviaSePierde`, `:144` franja actual y sede del slot retenido, `:163` sede de la cita sin slots, `:180` columnas obligatorias sin NULL). **Matiz:** valida el backfill de V10 (D31), no la V3 que la DoD cita literalmente. **Texto del 2026-09-30:** `V10__reschedule_previous_slot.sql`: añade las columnas como `NULL`, **rellena** las filas existentes con la franja actual de la cita y la sede de sus slots retenidos, y solo después las vuelve `NOT NULL` y añade las restricciones; ninguna migración previa se editó (el `backend-verifier` validó los checksums) | Hallazgo INFO n.º 8 del Verifier: la base de desarrollo `citas_fcv_training` está en V4 y Flyway aplicará V5–V10 al arrancar. Conviene saberlo antes de la prueba manual en navegador |
| DoD — Prueba de que con la solicitud `PENDING` conviven la franja original y la retenida (RN-10) | Cumple | RRIT:190, líneas 240–243 (`holderOf(gpBlock, "08:00") = APPOINTMENT` y `holderOf(gpBlock, "10:00") = RESCHEDULE_REQUEST`, con una sola fila por la solicitud) | — |
| DoD — La retención usa el mismo mecanismo de reserva que la creación de citas (RN-01 frente a concurrencia) | Cumple | `JpaAppointmentRepositoryAdapter#holdForReschedule` y `#reserveSlots` escriben en la misma tabla `slot_reservations`, cuya PK `slot_id` es la barrera (`V3…sql:200-208`); RRIT:559 `aRequestAndABookingRacingForTheSameSlotLetExactlyOneWin` (cinco franjas, una solicitud contra una reserva normal: exactamente una gana y la otra recibe 409 `SLOT_TAKEN`) | El Verifier inventarió las mutaciones del libro de slots y confirmó que `ReservationHolder.Kind` sigue teniendo tres valores, sin caminos divergentes |
| DoD — El agregado vive en el dominio sin depender de Spring ni de JPA | Cumple | `domain/appointment/RescheduleRequest.java`, `RescheduleStatus.java` y el puerto `RescheduleRequestRepository.java`; `HexagonalArchitectureTest` (cero imports de Spring o JPA en `domain/` y `application/`, 4/4); RRT (12 pruebas sin framework) | — |
| DoD — Creación, retención y persistencia en una única transacción, sin resultados parciales | Cumple | `RescheduleAppointmentUseCase#request` (todo el cuerpo en `tx.inTransaction`, con `appointments.lockById` y `reschedules.lockPendingByAppointment` antes de decidir); RRIT:543 `aFailureHoldingTheSlotRollsBackTheRequest` (fallo simulado en `holdForReschedule` → 500, cero solicitudes y el slot sin titular) | — |
| DoD — La pantalla de `citas-web` no permite seleccionar un profesional distinto del de la cita original | Cumple (2026-10-04) | `citas-web/src/pages/patient/ReschedulePage.tsx` y `citas-web/src/recoveryAndReschedule.test.tsx:178-210` (no permite elegir otro profesional); suite vitest 256/256, typecheck, lint y build limpios | PASS del `frontend-verifier` en la re-verificación independiente del 2026-10-04 |
| DoD — Pruebas de solicitud válida, franja pasada, franja ocupada, dos slots consecutivos y cambio de profesional, y pasan | Cumple | RRIT (18 pruebas de integración) y RRT (12 de dominio); suite completa 480/480, `BUILD SUCCESS`, reejecutada por el `backend-verifier` | — |
| DoD — El contrato del endpoint está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]] | Cumple | `citas-api/docs/wiki/llm-wiki/wiki/contrato-rest-citas.md`: tipo `RescheduleRequest`, los campos aditivos `pendingReschedule` y `lastReschedule`, la fila `POST /api/patient/appointments/{id}/reschedule` con su cuerpo y sus códigos de error, la migración `V10` (D31) y la aclaración de que **pedir no escribe historial** | Los defectos que el `backend-verifier` encontró en ese documento afectan a la **decisión** (HU-031) y a D38, no a este endpoint |
| DoD — Trazabilidad de esta HU y de [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]] actualizada | Cumple | Esta matriz, el historial de validación y la nota que registra D31 como resolución de la divergencia de esquema; EP-007 enlaza la HU y no mantiene estados por historia | — |

## Historial de validación

- 2026-10-04 — **Segunda pasada de F10 (re-verificación independiente).** Frontend PASS a «no permite elegir otro profesional» (`recoveryAndReschedule.test.tsx:178-210`) y backend PASS a «migración sin pérdida de datos» con `FlywayV10BackfillTest` (suite 521/521; vitest 256/256). Con CA-01 a CA-10 y toda la DoD en `Cumple`, estado: `En validación` → `Completada`. Matiz anotado: esa prueba valida el backfill de `V10` (D31), no la `V3` que cita literalmente la DoD.
- 2026-09-30 — Estado `En validación` (skill `scrum-spec-orchestrator`, paso 10): se registra la matriz de evidencia recolectada del repositorio y de la verificación independiente del `backend-verifier` en la iteración 2 del LOOP_02 de S4 (480/480, `BUILD SUCCESS`). Los diez criterios cumplen. **No pasa a `Completada`:** la DoD de la pantalla de `citas-web` queda en `Pendiente` porque el `backend-verifier` no puede verificar otro repositorio y el `frontend-verifier` solo cubrió el alcance de la iteración 2, sin la prueba manual en navegador de F10.
- 2026-09-30 — La divergencia de esquema anotada el 2026-09-25 (CA-03 y CA-09 piden la franja anterior y V3 no la guardaba) queda **resuelta por D31** con la migración `V10`. Ningún criterio se reescribió: la nota de «Notas y decisiones» se actualiza para registrar qué salida se tomó. Bajo la **aprobación delegada** de S4 (D15); el usuario puede revertirlo.
- 2026-09-25 — Se añade CA-10 por D20 (una solicitud sin decidir por cita; tras la decisión se admite otra), que ningún criterio cubría; la DoD pasa a CA-01 a CA-10. Ningún criterio existente se reescribe. Queda anotada en notas una divergencia entre CA-03 / CA-09 y el esquema V3 que no resuelve ninguna decisión.
- 2026-09-25 — Aprobada por **aprobación delegada** del usuario para S4 (D15, PLAN_RETOMA_S4.md). Alcance en `PLAN_RETOMA_S4.md` §3 (bloque «Ciclo de vida del paciente», fase F5 / LOOP_02) y decisiones D15–D30 en [[dec-006-decisiones-s4-ciclo-de-vida]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- **Resuelta (D20, provisional bajo delegación):** INC-028 (ver [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]): **una** solicitud sin decidir por cita, que el esquema ya impone con `uq_reschedule_requests_active` (`V3__schedule_and_appointments.sql:162`); tras la decisión se puede pedir otra ([[dec-006-decisiones-s4-ciclo-de-vida]]). CA-10 lo cubre.
- **Resuelta (D20, provisional bajo delegación):** INC-029 (ver [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]): el paciente **no retira** su solicitud en S4; si ya no la quiere, puede cancelar la cita ([[HU-026-cancelar-una-cita-futura]], D18).
- **Resuelta (D21, provisional bajo delegación):** INC-031 (ver [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]): la nueva franja **puede estar en otra sede** si el profesional atiende en ella (RN-07); RF-15 solo obliga a conservar profesional y especialidad ([[dec-006-decisiones-s4-ciclo-de-vida]]). CA-02 no se amplía.
- **Resuelta (D18, respondida por el usuario):** N1 — si el paciente cancela la cita mientras la solicitud está `PENDING`, la solicitud pasa a `CANCELLED` y se liberan las dos franjas en la misma transacción. Lo verifica CA-09 de [[HU-026-cancelar-una-cita-futura]].
- **Divergencia con el esquema, resuelta por D31 (provisional bajo delegación):** `reschedule_requests` existe desde `V3__schedule_and_appointments.sql` (T-02 no crea la tabla), pero V3 solo guardaba la franja **propuesta** y sin sede, de modo que CA-03 y CA-09 —que piden la fecha y hora **anterior** registradas en la solicitud— no se cumplían literalmente. De las dos salidas anotadas el 2026-09-25 se eligió la segunda: **`V10__reschedule_previous_slot.sql`** añade `previous_date`, `previous_start_time`, `previous_end_time`, `previous_site_id` y `proposed_site_id`, obligatorias y con clave foránea a `sites` (D31 en [[dec-006-decisiones-s4-ciclo-de-vida]]; la sede propuesta la exige además D21). Ningún criterio se reescribió: con V10 ambos se cumplen tal como estaban redactados. La franja anterior se guarda al pedir la reprogramación, que es la única forma de conservarla después de que [[HU-031-aprobar-o-rechazar-reprogramacion]] mueva la cita.
- El estado `PENDING` proviene del catálogo fijo de estados de reprogramación de RF-05 y no se define aquí.
