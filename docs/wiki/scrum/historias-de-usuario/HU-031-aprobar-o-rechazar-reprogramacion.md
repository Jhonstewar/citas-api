---
id: HU-031
tipo: historia-de-usuario
titulo: "Aprobar o rechazar una reprogramación"
estado: Completada
epica: "[[EP-008-operacion-administrativa-de-solicitudes]]"
requisitos: [RF-15, RF-19]
esfuerzo: "Alto"
sprint_sugerido: "Sprint 7"
dependencias:
  - "[[HU-027-solicitar-reprogramacion-de-cita-aprobada]]"
  - "[[HU-029-consultar-bandeja-administrativa]]"
  - "[[HU-032-auditar-cambios-de-estado-de-cita]]"
relacionadas:
  - "[[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]]"
  - "[[HU-030-aprobar-o-rechazar-cita-especializada]]"
  - "[[HU-025-consultar-mis-citas-y-detalle]]"
---

# HU-031 — Aprobar o rechazar una reprogramación

## Historia de usuario

**COMO** ADMIN  
**QUIERO** aprobar una solicitud de reprogramación pendiente o rechazarla indicando el motivo  
**PARA** mover la cita a la nueva franja liberando la anterior, o mantener la cita original liberando la franja propuesta

> Como ADMIN, quiero aprobar una solicitud de reprogramación pendiente o rechazarla indicando el motivo para mover la cita a la nueva franja liberando la anterior, o mantener la cita original liberando la franja propuesta.

## Contexto y descripción

RF-15 describe el cierre de la reprogramación iniciada en [[HU-027-solicitar-reprogramacion-de-cita-aprobada]]. Mientras la solicitud está `PENDING` existen dos ocupaciones en `slot_reservations`: las filas `APPOINTMENT` de la franja original y las filas `RESCHEDULE_REQUEST` de la franja propuesta. La decisión de ADMIN resuelve esa doble ocupación:

- **Aprobar:** se liberan los slots antiguos, los slots retenidos pasan a pertenecer a la cita y la cita se actualiza con la nueva fecha y horas. No se crea una cita nueva: el identificador de la cita se conserva (criterio de completitud de [[EP-008-operacion-administrativa-de-solicitudes]]). La solicitud pasa a `APPROVED`.
- **Rechazar:** se liberan los slots retenidos por la solicitud, la cita original se mantiene intacta (RN-10) y la solicitud pasa a `REJECTED` con motivo obligatorio (RN-04).

La cita sigue en `APPROVED` en los dos casos, pero solo la aprobación la **cambia**: le mueve fecha, hora y sede. RF-19 audita «todo cambio de estado de cita» (`PRD.md` §RF-19), así que la aprobación escribe su fila en `appointment_status_history` —estado `APPROVED`, origen `ADMIN`, con un motivo que nombra la franja anterior y la nueva, y que al leerse se distingue con el evento `RESCHEDULED`—, mientras que el rechazo, que no toca la cita, no escribe ninguna: su decisor, su fecha de decisión y su motivo quedan en `reschedule_requests` (`decided_by_user_id`, `decided_at`, `decision_reason`), que es de donde el paciente los lee. Es lo que decide D39, fiel a RF-19. Tras un rechazo, el paciente decide qué hacer en [[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]].

## Alcance

- Endpoints REST de aprobación y de rechazo de una solicitud de reprogramación, restringidos a `ADMIN`.
- Validación de que la solicitud está `PENDING` y su cita sigue `APPROVED`.
- Aprobación: eliminación de las reservas `APPOINTMENT` antiguas, conversión de las reservas `RESCHEDULE_REQUEST` de la solicitud en reservas `APPOINTMENT` de la cita, actualización de fecha y horas de la cita y paso de la solicitud a `APPROVED`.
- Rechazo: motivo obligatorio, eliminación de las reservas `RESCHEDULE_REQUEST`, cita sin cambios y paso de la solicitud a `REJECTED`.
- Registro de decisor, fecha de decisión y motivo en la solicitud, en las dos decisiones; y registro en el historial de la cita, con origen `ADMIN`, **solo de la aprobación**, porque es la única que cambia la cita y RF-19 audita «todo cambio de estado de cita» (`PRD.md` §RF-19, D39).
- Protección frente a decisiones concurrentes y frente a una cancelación simultánea de la cita.
- Pantalla "aprobar/rechazar reprogramaciones" en `citas-web` (PRD §6).

## Fuera de alcance

- Creación de la solicitud, que se cubre en [[HU-027-solicitar-reprogramacion-de-cita-aprobada]].
- Consulta de pendientes, que se cubre en [[HU-029-consultar-bandeja-administrativa]].
- Decisión del paciente tras el rechazo, que se cubre en [[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]].
- Cambio de profesional o de especialidad: RF-15 lo trata como cita nueva.
- Reversión de la decisión: pendiente de INC-035.
- Notificación por correo: pertenece a la automatización posterior de PRD §10.

## Reglas de negocio

- Solo una solicitud `PENDING` admite decisión (RF-15, RN-11).
- Aprobar libera los slots antiguos, asigna los nuevos y actualiza la cita (RF-15).
- Rechazar libera la reserva provisional y mantiene la cita original (RF-15, RN-10).
- Todo rechazo administrativo requiere motivo (RN-04).
- La cita original no se destruye ni se duplica en ningún caso (RN-10).
- Un slot nunca puede quedar ocupado dos veces durante la conversión de reservas (RN-01).
- Profesional y especialidad de la cita no cambian (RF-15).
- Cada decisión queda registrada con actor y origen `ADMIN` (RF-19, EP-008).
- Solo ADMIN decide (PRD §8).

## Dependencias y relaciones

- Épica: [[EP-008-operacion-administrativa-de-solicitudes]]
- Dependencias: [[HU-027-solicitar-reprogramacion-de-cita-aprobada]], [[HU-029-consultar-bandeja-administrativa]], [[HU-032-auditar-cambios-de-estado-de-cita]]
- Relacionadas: [[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]], [[HU-030-aprobar-o-rechazar-cita-especializada]], [[HU-025-consultar-mis-citas-y-detalle]], [[HU-026-cancelar-una-cita-futura]], [[HU-020-consultar-agenda-de-citas-aprobadas]], [[HU-022-buscar-disponibilidad-con-filtros]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Alto

**Justificación de dificultad:** La aprobación es la operación de agenda más delicada del producto: modifica tres tablas (`slot_reservations`, `appointments`, `reschedule_requests`) más el historial, convierte reservas de un titular a otro sin abrir ninguna ventana en la que un slot quede libre o doblemente ocupado, y debe coordinarse con operaciones concurrentes del paciente sobre la misma cita.

## Tareas de desarrollo

- [ ] **T-01 — Modelar la decisión sobre la solicitud en el dominio**  
  Dificultad: Alto  
  Descripción: Operaciones explícitas de aprobar y rechazar sobre el agregado de solicitud, coordinadas con el agregado cita: exigen solicitud `PENDING` y cita `APPROVED`, validan motivo no vacío en el rechazo, calculan las reservas a liberar y a transferir y actualizan la franja de la cita en la aprobación. Sin dependencias de framework.

- [ ] **T-02 — Implementar la transferencia y liberación de reservas**  
  Dificultad: Alto  
  Descripción: Adaptador que, en la aprobación, elimina las filas `APPOINTMENT` antiguas y actualiza las filas `RESCHEDULE_REQUEST` de la solicitud a tipo `APPOINTMENT` con la cita como titular conservando `slot_id` y `slot_order`; y en el rechazo elimina las filas `RESCHEDULE_REQUEST`. Respeta las restricciones de V3 durante toda la operación.

- [ ] **T-03 — Implementar los casos de uso de aprobación y rechazo**  
  Dificultad: Alto  
  Descripción: Casos de uso que bloquean o versionan solicitud y cita, aplican la decisión y persisten solicitud, cita y reservas en una única transacción. La **aprobación** añade en esa misma transacción la fila de historial con origen `ADMIN`, porque cambia la cita; el **rechazo** no escribe historial, porque no cambia su estado y RF-19 audita «todo cambio de estado de cita» (`PRD.md` §RF-19, D39).

- [ ] **T-04 — Exponer los adaptadores REST de decisión**  
  Dificultad: Medio  
  Descripción: Endpoints restringidos a `ADMIN`; el rechazo exige motivo. Respuestas: éxito con la solicitud y la cita resultantes, error de validación por motivo vacío, 409 por solicitud no `PENDING` o cita no `APPROVED`, y recurso no encontrado.

- [ ] **T-05 — Construir la pantalla de aprobar/rechazar reprogramaciones en citas-web**  
  Dificultad: Medio  
  Descripción: Vista desde la bandeja que muestra franja actual y propuesta, acción de aprobar con confirmación, diálogo de rechazo con motivo obligatorio y refresco ante 409.

- [ ] **T-06 — Pruebas de decisión sobre reprogramación**  
  Dificultad: Alto  
  Descripción: Integración de aprobación y rechazo con 30 y 60 minutos verificando cada fila de `slot_reservations`, identificador de cita conservado, disponibilidad resultante, motivo vacío, estados inválidos, concurrencia con cancelación y rol.

## Criterios de aceptación

### CA-01 — Aprobación mueve la cita sin duplicarla

**Dado** una cita `APPROVED` de 60 minutos en la franja 08:00–09:00 y una solicitud `PENDING` hacia la franja 10:00–11:00 del mismo profesional  
**Cuando** ADMIN aprueba la solicitud  
**Entonces** la cita conserva su identificador y su estado `APPROVED`, su fecha y horas pasan a 10:00–11:00, profesional y especialidad no cambian, no existe ninguna cita nueva y la solicitud queda `APPROVED` con decisor y fecha de decisión.

### CA-02 — Aprobación: slots antiguos libres y nuevos ocupados por la cita

**Dado** la aprobación del CA-01  
**Cuando** se consulta `slot_reservations` y se busca disponibilidad para ese profesional y fecha  
**Entonces** los dos slots de 08:00–09:00 ya no tienen reserva y vuelven a ofrecerse, y los dos slots de 10:00–11:00 tienen reservas de tipo `APPOINTMENT` de la cita con `slot_order` 1 y 2, sin ninguna reserva `RESCHEDULE_REQUEST` restante de la solicitud.

### CA-03 — Rechazo mantiene la cita y libera la franja propuesta

**Dado** una cita `APPROVED` con una solicitud `PENDING` hacia otra franja  
**Cuando** ADMIN rechaza la solicitud con un motivo  
**Entonces** la solicitud queda `REJECTED` con el motivo, decisor y fecha de decisión; la cita conserva estado, fecha, horas y reservas originales; y los slots de la franja propuesta quedan libres y vuelven a ofrecerse (RN-10, RN-09).

### CA-04 — Rechazo sin motivo rechazado

**Dado** una solicitud `PENDING`  
**Cuando** ADMIN intenta rechazarla sin motivo o con motivo vacío  
**Entonces** la API responde con un error de validación y la solicitud, la cita y todas las reservas permanecen sin cambios (RN-04).

### CA-05 — Solo solicitudes PENDING sobre citas APPROVED

**Dado** solicitudes ya `APPROVED` o `REJECTED`, y una solicitud `PENDING` cuya cita ya no está `APPROVED`  
**Cuando** ADMIN intenta aprobar o rechazar cualquiera de ellas  
**Entonces** la API responde 409 y no cambia ninguna solicitud, cita, reserva ni historial (RN-11).

### CA-06 — Solo la aprobación registra historial; el motivo del rechazo vive en la solicitud

**Dado** una aprobación y un rechazo realizados correctamente  
**Cuando** se consulta el historial de cada cita y la solicitud decidida  
**Entonces** la cita aprobada tiene exactamente un registro nuevo con estado `APPROVED`, origen `ADMIN`, el identificador del administrador como actor, la fecha y hora y un motivo que nombra la franja anterior y la nueva, y ese registro se lee con el evento `RESCHEDULED` que lo distingue de una aprobación de cita; y la cita rechazada no gana ningún registro de historial, porque el rechazo no la modifica: el motivo enviado queda únicamente en `reschedule_requests.decision_reason`, que el detalle de la cita expone al paciente titular (RF-19, D39, que refina D22).

### CA-07 — Decisiones concurrentes o cancelación simultánea

**Dado** una solicitud `PENDING`  
**Cuando** dos ADMIN deciden a la vez sobre ella, o ADMIN la aprueba mientras el paciente cancela la cita  
**Entonces** las operaciones se serializan y el estado final es coherente: entre dos decisiones, exactamente una tiene éxito y la otra recibe 409; entre aprobación y cancelación, si la cancelación se aplica primero la solicitud queda `CANCELLED` con las dos franjas libres (D18) y la aprobación recibe 409, y si la aprobación se aplica primero la cancelación posterior actúa sobre la cita ya movida y libera la franja nueva. En ningún caso un slot queda con dos reservas, queda una reserva huérfana o la cita queda `CANCELLED` con alguna franja ocupada.

### CA-08 — Solo ADMIN decide

**Dado** un usuario autenticado con rol `USER` (incluido el titular) o `PROFESSIONAL`  
**Cuando** intenta aprobar o rechazar una reprogramación  
**Entonces** la API responde con un error de autorización y nada cambia.

### CA-09 — No se aprueba una reprogramación cuya franja propuesta ya pasó

**Dado** una solicitud `PENDING` cuya franja propuesta tiene una hora de inicio anterior al instante actual  
**Cuando** ADMIN intenta aprobarla  
**Entonces** la API responde 409 y no cambia la solicitud, la cita, las reservas ni el historial; y cuando ADMIN la rechaza con motivo, el rechazo se aplica como en CA-03 (RN-06, D23).

## Definition of Done

- [x] Los criterios CA-01 a CA-09 están validados con evidencia concreta.
- [x] La aprobación conserva el identificador de la cita y está demostrada con una prueba que verifica fila a fila `slot_reservations` antes y después.
- [x] Solicitud, cita, reservas e historial se modifican en una única transacción; ante cualquier fallo no hay resultados parciales.
- [x] La PK `slot_id` de `slot_reservations` se mantiene como garantía de no-doble-reserva durante la transferencia; no se desactivan restricciones.
- [x] Las decisiones son operaciones explícitas del dominio con validación de estado origen de solicitud y cita.
- [x] El motivo obligatorio del rechazo se valida en el servidor y se persiste en `reschedule_requests.decision_reason`; **no** se escribe en el historial de la cita, porque el rechazo no la modifica (D39).
- [x] Existe control de concurrencia demostrado frente a decisión doble y frente a cancelación simultánea.
- [x] No se crean migraciones salvo cambio de esquema justificado, en una migración Flyway posterior a V4.
- [x] Los endpoints exigen rol `ADMIN` aplicando [[HU-005-autorizar-peticiones-por-rol-y-ownership]].
- [ ] La pantalla de `citas-web` muestra franja actual y propuesta y no permite rechazar sin motivo.
- [x] Existen pruebas automatizadas de aprobación y rechazo con 30 y 60 minutos, motivo vacío, estados inválidos, concurrencia y rol, y pasan.
- [x] El contrato de los endpoints de decisión de reprogramación está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [x] La trazabilidad de esta HU y de [[EP-008-operacion-administrativa-de-solicitudes]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Ejecución de referencia del **2026-09-30** (iteración 2 del LOOP_02 de S4): el `backend-verifier`, agente independiente que no escribió el código, reejecutó la suite completa de `citas-api` → **480 pruebas, 0 fallos, 0 errores, `BUILD SUCCESS`**, con `HexagonalArchitectureTest` 4/4 y las clases de concurrencia 89/89 en dos pasadas idénticas (`evidencias/s4/loops/LOOP-02/iter-2-verifier.json`). Las rutas de prueba se abrevian: **RDIT** = `src/test/java/com/fcv/citas/infrastructure/rest/RescheduleDecisionIntegrationTest.java`, **RRT** = `src/test/java/com/fcv/citas/domain/appointment/RescheduleRequestTest.java`.

Lo de **frontend** estuvo en `Pendiente` el 2026-09-30 (el `backend-verifier` lo clasificó `NO VERIFICABLE` porque `citas-web` está fuera de su repositorio). **El 2026-10-04** la verificación independiente de F10 (backend leyendo código y pruebas, suite 513/513; frontend vitest 248/248, typecheck, lint y build limpios) lo dio en PASS y la HU pasa a `Completada`.

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | RDIT:220 `approvingMovesTheSameAppointmentAndConvertsTheHeldRows` (60 min: la respuesta trae el **mismo** `id`, `APPROVED`, 10:00–11:00, profesional y especialidad intactos; el recuento de citas del paciente no cambia; la solicitud queda `APPROVED` con `decided_by_user_id` y `decided_at`); RDIT:294 `approvingThirtyMinutesToAnotherSiteMovesTheSite` (30 min, otro día y otra sede, D21); dominio RRT:121 | La cita no se duplica ni se recrea: se comprueba contra la tabla `appointments`, no solo en la respuesta |
| CA-02 | Cumple | RDIT:220, líneas 258–272 (08:00 y 08:30 sin fila en `slot_reservations`; 10:00 y 10:30 con `reservation_type = APPOINTMENT`, `appointment_id` de la cita, `reschedule_request_id` nulo y `slot_order` 1 y 2; cero filas con `reschedule_request_id` de la solicitud y exactamente 2 de la cita) y 274–276 (la búsqueda vuelve a ofrecer 08:00 y ya no ofrece 10:00) | Verificación fila a fila del libro de slots, no por diferencia de conteos |
| CA-03 | Cumple | RDIT:321 `rejectingKeepsTheAppointmentAndReleasesTheProposal` (solicitud `REJECTED` con motivo, decisor y `decided_at`; la cita sigue `APPROVED` a las 08:00 con sus dos reservas `APPOINTMENT`; 10:00 y 10:30 quedan libres y vuelven a ofrecerse); RDIT:369 `rejectingThirtyMinutesReleasesTheHeldSlot` (30 min) | Cubre RN-10 y RN-09 en las dos duraciones |
| CA-04 | Cumple | RDIT:441 `rejectingWithoutAReasonIsAValidationError` (sin cuerpo, `{}` y motivo en blanco → 400 con `fieldErrors.reason`; el `snapshot` de cita, solicitud, reservas e historial es idéntico y la solicitud sigue `PENDING`); dominio RRT:155 | La validación ocurre en el dominio y en Bean Validation, antes de tocar la base |
| CA-05 | Cumple | RDIT:458 `anAlreadyDecidedRequestCannotBeDecidedAgain` (solicitud ya `APPROVED` y ya `REJECTED`) y RDIT:478 `aPendingRequestOnANonApprovedAppointmentCannotBeDecided` (cita `COMPLETED`): 409 `INVALID_TRANSITION` al aprobar y al rechazar, con `snapshot` idéntico; RDIT:493 (solicitud inexistente → 404); dominio RRT:168 | El `snapshot` incluye el recuento de historial, así que "no cambia ningún historial" queda aseverado |
| CA-06 | Cumple | RDIT:508 `onlyTheApprovalIsRecordedInTheHistory`: la cita aprobada gana **una** fila `APPROVED`/`ADMIN` con el administrador como actor, `changed_at` y un motivo que nombra día, 08:00 y HIC (anterior) y día siguiente, 09:00 e ICV (nueva); la cita rechazada **no gana ninguna** (0 filas `source = 'ADMIN'`) y el paciente lee el motivo en `lastReschedule.decisionReason`; RDIT:551 `anApprovedRescheduleCarriesTheRescheduledEvent` (`event = 'RESCHEDULED'` solo en las filas que repiten estado); `domain/appointment/HistoryEvent#between` y `HistoryEventTest` (4 pruebas sin framework); RRT:155 (rechazar no produce transición de la cita) | **Verificado contra D39**, no contra D22: el criterio se reescribió el 2026-09-30 y el código lo cumple a propósito. Límite conocido del Verifier (BAJA n.5): la derivación del evento se apoya en un invariante de aplicación —dos filas consecutivas con el mismo estado solo las produce una reprogramación aprobada— que el esquema no protege frente a SQL manual o semillas |
| CA-07 | Cumple | RDIT:577 `concurrentDecisionsLetExactlyOneWin` (las tres parejas aprobar/rechazar: exactamente 200 y 409, ninguna reserva colgando de la solicitud, una sola reserva de la cita, y 1 o 0 filas `ADMIN` según qué decisión ganó) y RDIT:603 `approvalAndCancellationRacingEndInACoherentState` (4 rondas: la cancelación siempre 200, cita `CANCELLED`, solicitud `APPROVED` si la aprobación llegó primero o `CANCELLED` con 409 si llegó después, y cero reservas huérfanas); `RescheduleAppointmentUseCase#lock` (bloquea primero la cita y después la solicitud: orden único que serializa sin interbloquear) | Cubre los dos órdenes que exige el criterio tras D18. Aparte del texto de CA-07, el `backend-verifier` deja `NO VERIFICABLE` la carrera «cerrar la atención» contra «decidir», que abre D38: existe el caso secuencial (RDIT:478) y el argumento estructural, no la prueba concurrente |
| CA-08 | Cumple | RDIT:722 `onlyAdminDecides` (USER titular y PROFESSIONAL → 403 al aprobar y al rechazar; anónimo → 401; `snapshot` idéntico); `SecurityConfig:83` (`/api/admin/**` exige `ROLE_ADMIN`); `AdminAppointmentController:90` y `:96` | — |
| CA-09 | Cumple | RDIT:742 `aRequestWhoseProposedSlotAlreadyPassedCannotBeApproved` (409 `APPOINTMENT_EXPIRED` y `snapshot` idéntico; el rechazo con motivo sí se aplica y deja la retención liberada); dominio RRT:139 | Implementa D23 igual que D12 para las `REQUESTED` vencidas |
| DoD — CA-01 a CA-09 validados con evidencia concreta | Cumple | Filas CA-01 a CA-09 de esta tabla | Los nueve son de backend y todos tienen prueba de integración propia |
| DoD — La aprobación conserva el identificador y hay prueba fila a fila de `slot_reservations` antes y después | Cumple | RDIT:220 (captura `heldFirst`/`heldSecond` antes de aprobar y compara fila a fila después; `created_at` idéntico demuestra que la fila se **actualiza en sitio**, no se borra y reinserta; `$.id` es el de la cita y el recuento de citas del paciente no cambia) | Con esto el Verifier descartó el riesgo n.º 1 del plan (que aprobar abriera un hueco) |
| DoD — Solicitud, cita, reservas e historial en una única transacción, sin resultados parciales | Cumple | `RescheduleAppointmentUseCase#approve` y `#reject` (todo el cuerpo dentro de `tx.inTransaction`); RDIT:705 `aFailureDuringApprovalRollsBackEverything` (fallo simulado en `convertHeldToAppointment` → 500 y `snapshot` idéntico, solicitud otra vez `PENDING`) | — |
| DoD — La PK `slot_id` se mantiene durante la transferencia; no se desactivan restricciones | Cumple | `V3__schedule_and_appointments.sql:200-208` (`pk_slot_reservations PRIMARY KEY (slot_id)`); `JpaAppointmentRepositoryAdapter#convertHeldToAppointment` (`UPDATE` en sitio, con el comentario que lo justifica); RDIT:660 `approvingNeverOpensAGapOnTheNewSlot` (dos reservas rivales lanzadas durante la conversión reciben 409 `SLOT_TAKEN` y al final el slot tiene una sola fila, de la cita) | El `backend-verifier` verificó además que ninguna migración aplicada se editó (checksums validados en cada arranque) |
| DoD — Las decisiones son operaciones explícitas del dominio con validación de estado origen | Cumple | `domain/appointment/RescheduleRequest#approve` y `#reject` (exigen solicitud `PENDING` y cita `APPROVED`); RRT:121, :139, :155, :168; `HexagonalArchitectureTest` (cero imports de Spring o JPA en `domain/` y `application/`) | — |
| DoD — El motivo del rechazo se valida en el servidor y se persiste en `decision_reason`, **no** en el historial (D39) | Cumple | RRT:155 (motivo nulo o en blanco → `VALIDATION`; el motivo se recorta y queda en `decisionReason`); RDIT:441 (400 `fieldErrors.reason`); RDIT:321 líneas 334–340 (`decision_reason` persistido tal cual) y RDIT:508 líneas 519–529 (cero filas de historial y cero filas `ADMIN` por el rechazo) | Ítem reescrito el 2026-09-30: antes exigía persistirlo "y en el historial", lo contrario de D39 |
| DoD — Control de concurrencia frente a decisión doble y frente a cancelación simultánea | Cumple | RDIT:577 y RDIT:603; `RescheduleAppointmentUseCase#lock` | Límite anotado: la carrera contra el cierre de atención que abre D38 sigue sin prueba (`NO VERIFICABLE`, hallazgo BAJA n.6 del Verifier). No es una condición de este ítem, que habla de decisión doble y cancelación |
| DoD — Sin migraciones salvo cambio de esquema justificado, en una Flyway posterior a V4 | Cumple | `V10__reschedule_previous_slot.sql` (D31, posterior a V4: franja anterior y sede propuesta); `FlywayMigratesEmptySchemaTest#laReprogramacionGuardaLaFranjaAnteriorYLaSedePropuesta` | La migración la justifica D31; el historial de la cita **no** necesitó esquema nuevo (D39 deriva el evento en lectura) |
| DoD — Los endpoints exigen rol `ADMIN` aplicando [[HU-005-autorizar-peticiones-por-rol-y-ownership]] | Cumple | `AdminAppointmentController:90` (`/reschedules/{id}/approve`) y `:96` (`/reschedules/{id}/reject`) bajo el prefijo `/api/admin`; `SecurityConfig:83`; RDIT:722 | — |
| DoD — La pantalla de `citas-web` muestra franja actual y propuesta y no permite rechazar sin motivo | Cumple (2026-10-04) | `citas-web/src/pages/admin/InboxPage.tsx` y `citas-web/src/adminS4.test.tsx:96` (franja actual → propuesta, solicitante, profesional, especialidad y motivo), `:119` (aprobar con confirmación; la reprogramación sale de la bandeja) y `:140` (rechazar exige motivo: sin él no se envía); vitest 248/248, typecheck, lint y build limpios | PASS del `frontend-verifier` en la verificación independiente de F10 (2026-10-04) |
| DoD — Pruebas automatizadas de aprobación y rechazo con 30 y 60 min, motivo vacío, estados inválidos, concurrencia y rol, y pasan | Cumple | RDIT (24 pruebas: 30 y 60 min en aprobación y rechazo, motivo vacío, ya decidida, cita no `APPROVED`, dos carreras, rol y franja vencida) y RRT (12 pruebas de dominio); suite completa 480/480 `BUILD SUCCESS` reejecutada por el `backend-verifier` | — |
| DoD — El contrato de los endpoints de decisión está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]] | Cumple | `citas-api/docs/wiki/llm-wiki/wiki/contrato-rest-citas.md`: las filas `POST /api/admin/reschedules/{id}/approve` y `…/reject` con sus códigos de error, la nota «Historial (D22, refinado por D39)» —**solo aprobar** escribe fila `APPROVED`/`ADMIN` con el motivo que nombra las dos franjas y se lee con `event: 'RESCHEDULED'`; **rechazar no añade ninguna fila** y su motivo viaja solo en `lastReschedule.decisionReason`; las cancelaciones de D18 y D38 tampoco escriben fila—, el tipo `HistoryEntry` ya con `event?: 'RESCHEDULED'` y su regla de derivación, y el efecto de D38 documentado en los endpoints de cierre | Era el bloqueador ALTA n.º 1 del `backend-verifier` (junto con MEDIA n.º 3). **Corregido el 2026-09-30** por el agente del contrato REST en la iteración 3 del LOOP_02; al escribir esta fila el cambio está en el árbol de trabajo, sin commitear. `llm-wiki/` queda fuera del límite de escritura de esta skill: la evidencia se leyó, no se produjo aquí |
| DoD — Trazabilidad de esta HU y de [[EP-008-operacion-administrativa-de-solicitudes]] actualizada | Cumple | Esta matriz, el historial de validación y la nota de D39; el texto de «Contexto y descripción», la viñeta de «Alcance» y la tarea T-03 alineados con RF-19 (`PRD.md` §RF-19); en [[EP-008-operacion-administrativa-de-solicitudes]], la regla de RF-19 y la casilla del criterio de completitud reescritas con la cita textual del PRD, y la sección «Historial» de la épica que registra la corrección; `docs/wiki/scrum/README.md` con la tabla de verificación del LOOP_02 | La redacción heredada de D22 —que decía «cada decisión»— quedó alineada el 2026-09-30 con **aprobación directa del usuario**. Era la única condición que bloqueaba este ítem |

## Historial de validación

- 2026-10-04 — **Verificación independiente de F10.** CA-01 a CA-09 y toda la DoD en `Cumple` (PASS), incluida la pantalla de ADMIN (`adminS4.test.tsx:96`, `:119`, `:140`). Estado: `En validación` → `Completada`. **Observación sin cambio de estado:** la carrera «cerrar la atención» contra «decidir la reprogramación» (D38) sigue sin prueba concurrente; [[dec-006-decisiones-s4-ciclo-de-vida]] la declara abierta. Existe el caso secuencial (RDIT:478) y el argumento estructural, y el ítem de DoD de concurrencia habla de decisión doble y cancelación, no de cierre.
- 2026-09-30 — **Alineados con RF-19 los tres textos heredados**, con **aprobación directa del usuario** (no delegada): el párrafo de «Contexto y descripción», la viñeta de «Alcance» sobre el registro de la decisión y la descripción de la tarea T-03. Los tres estaban redactados para los dos caminos porque [[EP-008-operacion-administrativa-de-solicitudes]] reformulaba RF-19 como «cada decisión se registra en el historial», y RF-19 dice «**todo cambio de estado de cita**» (`PRD.md` §RF-19). Rechazar una reprogramación no cambia el estado de la cita, así que la fila del rechazo era una exigencia que la épica había añadido sobre el PRD. **Es la corrección de una deriva documental, no una flexibilización:** se vuelve a lo que el PRD pide, y D39 es la decisión fiel a RF-19. La regla de RF-19 y la casilla del criterio de completitud de EP-008 se corrigieron el mismo día, con la misma aprobación. Con esto queda cerrado el ítem de trazabilidad de la DoD, que era lo único que esta redacción bloqueaba.
- 2026-09-30 — **CA-06 reescrito para ejecutar D39** (que refina D22), registrada en [[dec-006-decisiones-s4-ciclo-de-vida]] y que allí ya decía «HU-031 CA-06 se ajusta a D39» sin que el ajuste se hubiera escrito aquí. Antes exigía que «cada cita tiene exactamente un registro nuevo con estado `APPROVED`, origen `ADMIN` … y en el rechazo contiene el motivo enviado (RF-19, D22)»; ahora exige que **solo la aprobación** escriba la fila —con el evento `RESCHEDULED` que la distingue— y que el rechazo **no** escriba ninguna, dejando su motivo en `reschedule_requests.decision_reason`. En la misma línea, el ítem de DoD del motivo del rechazo dejó de pedir que se persistiera «y en el historial», y la nota que describía D22 como decisión vigente se sustituyó por la de D39. Cambio ejecutado bajo la **aprobación delegada** de S4 (D15, `AGENTS.md` §6): no hay aprobación humana de este ajuste y el usuario puede revertirlo o devolver la HU a `Pendiente de aprobación`. No se tocó ningún otro criterio.
- 2026-09-30 — Estado `En validación` (skill `scrum-spec-orchestrator`, paso 10): se registra la matriz de evidencia recolectada del repositorio y de la verificación independiente del `backend-verifier` en la iteración 2 del LOOP_02 de S4 (480/480, `BUILD SUCCESS`). **No pasa a `Completada`:** la DoD de la pantalla de ADMIN y la de trazabilidad quedan en `Pendiente`. El ítem del contrato REST —bloqueador ALTA n.º 1 del Verifier— pasó a `Cumple` el mismo día, cuando el agente del contrato alineó `llm-wiki/wiki/contrato-rest-citas.md` con D38 y D39.
- 2026-09-25 — CA-06 ajustado a D22: el registro de historial lleva estado `APPROVED` y, en la aprobación, un motivo que nombra la franja anterior y la nueva. **Superado por la entrada del 2026-09-30 (D39).**
- 2026-09-25 — CA-07 ajustado a D18: antes exigía que, entre aprobación y cancelación simultáneas, "la otra recibe 409" en cualquier orden. Con D18 y RF-14 eso solo vale si la cancelación va primero; si la aprobación va primero, la cancelación posterior es legítima sobre la cita ya movida. El criterio exige ahora serialización y estado final coherente en los dos órdenes.
- 2026-09-25 — Se añade CA-09 por D23 (franja propuesta ya pasada → 409 al aprobar), que ningún criterio cubría; la DoD pasa a CA-01 a CA-09.
- 2026-09-25 — Aprobada por **aprobación delegada** del usuario para S4 (D15, PLAN_RETOMA_S4.md). Alcance en `PLAN_RETOMA_S4.md` §3 (bloque «Operación administrativa», fase F5 / LOOP_02) y decisiones D15–D30 en [[dec-006-decisiones-s4-ciclo-de-vida]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- **Resuelta (D39, provisional bajo delegación; refina D22):** N3 — **solo la aprobación** escribe fila de historial, porque es la única decisión que cambia la cita (fecha, hora y sede): una fila con estado `APPROVED`, origen `ADMIN` y un motivo que nombra la franja anterior y la nueva. El **rechazo no toca la cita**, así que no escribe fila: su motivo vive solo en `reschedule_requests.decision_reason`, que el detalle muestra en el aviso de [[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]]. La cancelación de la solicitud por D18 (el paciente cancela la cita) y por D38 (el profesional cierra la atención) tampoco escribe fila extra. Como la fila de la aprobación repite el estado anterior, la lectura la marca con `event = 'RESCHEDULED'`, derivado sin columna ni migración nuevas, y la interfaz la rotula «Reprogramada» ([[dec-006-decisiones-s4-ciclo-de-vida]], D39). CA-06 lo refleja desde el 2026-09-30.
- **Deriva respecto del PRD, corregida el 2026-09-30 (aprobación directa del usuario):** «Contexto y descripción», la viñeta de «Alcance» y la tarea T-03 decían que esta HU registra en el historial **la decisión**, los dos caminos. Venían de [[EP-008-operacion-administrativa-de-solicitudes]], que reformulaba RF-19 como «cada decisión se registra en el historial». **RF-19 no dice eso:** dice «Todo cambio de estado de cita guarda: cita; estado nuevo; actor cuando existe; fuente `SYSTEM`, `USER` o `ADMIN`; fecha/hora; motivo opcional» (`PRD.md` §RF-19). Rechazar una reprogramación no cambia el estado de la cita, que sigue `APPROVED`, así que RF-19 nunca pidió una fila para el rechazo: la exigencia la había añadido la épica, y fue la que justificó D22. Los cinco textos —los tres de esta HU y los dos de EP-008— se alinearon con RF-19 citándolo y con D39 como la decisión que lo respeta. **No se relajó ningún requisito: se volvió al PRD.**
- **D22 → D39 afecta también a [[HU-032-auditar-cambios-de-estado-de-cita]]** (`Completada`), que define el historial como registro de transiciones: su nota y el ítem de DoD del contrato del historial se actualizaron el 2026-09-30.
- **Resuelta (D21, provisional bajo delegación):** INC-031 (ver [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]): la franja propuesta puede estar en otra sede si el profesional atiende en ella; al aprobar, la cita actualiza también su sede ([[dec-006-decisiones-s4-ciclo-de-vida]]).
- Incógnita abierta **INC-035** (ver [[EP-008-operacion-administrativa-de-solicitudes]]): no hay reversión de decisiones. No la resuelve ninguna decisión D15–D30.
- **Resuelta (D23, provisional bajo delegación):** INC-036 (ver [[EP-008-operacion-administrativa-de-solicitudes]]): si la franja propuesta ya pasó, aprobar responde 409 y ADMIN debe rechazar con motivo, igual que D12 para las citas `REQUESTED` vencidas y RN-06 ([[dec-006-decisiones-s4-ciclo-de-vida]]). CA-09 lo cubre. D23 no trata el caso de que haya pasado solo la franja **original**; aprobar hacia una franja futura sigue siendo válido en ese caso.
- **Resuelta (D18, respondida por el usuario):** cancelar una cita con solicitud `PENDING` pasa la solicitud a `CANCELLED` y libera las dos franjas en la misma transacción. Queda reflejado en CA-09 de [[HU-026-cancelar-una-cita-futura]] y en CA-07 de esta HU.
