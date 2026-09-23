---
id: HU-032
tipo: historia-de-usuario
titulo: "Auditar los cambios de estado de cita"
estado: Completada
epica: "[[EP-009-trazabilidad-y-contrato-rest]]"
requisitos: [RF-19]
esfuerzo: "Alto"
sprint_sugerido: "Sprint 5"
dependencias:
  - "[[HU-005-autorizar-peticiones-por-rol-y-ownership]]"
relacionadas:
  - "[[HU-023-agendar-cita-de-medicina-general]]"
  - "[[HU-024-solicitar-cita-especializada]]"
  - "[[HU-026-cancelar-una-cita-futura]]"
  - "[[HU-030-aprobar-o-rechazar-cita-especializada]]"
  - "[[HU-031-aprobar-o-rechazar-reprogramacion]]"
  - "[[HU-021-registrar-cierre-de-atencion]]"
---

# HU-032 — Auditar los cambios de estado de cita

## Historia de usuario

**COMO** ADMIN responsable de la operación  
**QUIERO** que cada cambio de estado de una cita quede registrado con estado nuevo, actor, origen, fecha y hora y motivo, sin posibilidad de editarlo ni borrarlo  
**PARA** poder responder con evidencia quién cambió qué cita, cuándo y por qué

> Como ADMIN responsable de la operación, quiero que cada cambio de estado de una cita quede registrado con estado nuevo, actor, origen, fecha y hora y motivo, sin posibilidad de editarlo ni borrarlo, para poder responder con evidencia quién cambió qué cita, cuándo y por qué.

## Contexto y descripción

RF-19 exige que todo cambio de estado de cita guarde cita, estado nuevo, actor cuando existe, origen `SYSTEM`/`USER`/`ADMIN`, fecha y hora y motivo opcional. RN-11 exige que las transiciones sean explícitas y verificables, y RN-12 que los datos de auditoría no se modifiquen como CRUD normal.

La tabla ya existe en V3: `appointment_status_history`, append-only por diseño, con `actor_user_id` nulo permitido solo para origen `SYSTEM` (restricción `ck_ash_actor`). Esta HU construye las piezas transversales que el resto de HU usan: el modelo explícito de transiciones de estado de cita, el puerto de dominio que registra cada transición dentro de la misma transacción del cambio, la ausencia deliberada de operaciones de edición y borrado, y la consulta del historial.

Se adelanta al Sprint 5, antes de [[HU-023-agendar-cita-de-medicina-general]] y [[HU-024-solicitar-cita-especializada]], para que ninguna cita nazca sin historial.

## Alcance

- Máquina de estados de cita en el dominio con las transiciones permitidas del PRD: creación → `APPROVED` (general, origen `SYSTEM`), creación → `REQUESTED` (especializada, origen `USER`), `REQUESTED` → `APPROVED` / `REJECTED` (`ADMIN`), `REQUESTED` / `APPROVED` → `CANCELLED` (`USER`), `APPROVED` → `COMPLETED` / `NO_SHOW` (profesional).
- Estados terminales tomados del catálogo `appointment_statuses.is_terminal` (V4), compartidos con todas las HU.
- Puerto de dominio de registro de transición y adaptador de persistencia de solo inserción sobre `appointment_status_history`.
- Motivo obligatorio cuando la transición es un rechazo administrativo (RN-04).
- Endpoint REST de consulta del historial de una cita, en orden cronológico.
- Ausencia de endpoints y de métodos de repositorio de actualización o borrado del historial.
- Vista del historial en el detalle de cita del dashboard ADMIN de `citas-web`.

## Fuera de alcance

- Las transiciones concretas de cada flujo, que implementan [[HU-023-agendar-cita-de-medicina-general]], [[HU-024-solicitar-cita-especializada]], [[HU-026-cancelar-una-cita-futura]], [[HU-030-aprobar-o-rechazar-cita-especializada]], [[HU-031-aprobar-o-rechazar-reprogramacion]] y [[HU-021-registrar-cierre-de-atencion]].
- Auditoría de entidades distintas de la cita (usuarios, catálogos, bloques): RF-19 solo exige la cita.
- Workflows n8n que reaccionan a cambios de estado (PRD §10).
- Exportación del historial: no está en el PRD.

## Reglas de negocio

- Todo cambio de estado de cita guarda cita, estado nuevo, actor cuando existe, origen, fecha y hora y motivo opcional (RF-19).
- Las transiciones son explícitas y verificables; no se permiten cambios de estado arbitrarios (RN-11).
- Los datos de auditoría no se modifican ni se borran como CRUD normal (RN-12).
- El origen es `SYSTEM` cuando la transición no la ejecuta una persona, como la aprobación automática (RN-02); en ese caso el actor puede ser nulo.
- Con origen `USER` o `ADMIN` el actor es obligatorio (restricción de V3).
- Un rechazo administrativo exige motivo, que se conserva en el historial (RN-04).
- Un estado terminal no admite nuevas transiciones (catálogo de RF-05).
- Cambio de estado e historial se confirman juntos o no se confirman.

## Dependencias y relaciones

- Épica: [[EP-009-trazabilidad-y-contrato-rest]]
- Dependencias: [[HU-005-autorizar-peticiones-por-rol-y-ownership]]
- Relacionadas: [[HU-023-agendar-cita-de-medicina-general]], [[HU-024-solicitar-cita-especializada]], [[HU-025-consultar-mis-citas-y-detalle]], [[HU-026-cancelar-una-cita-futura]], [[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]], [[HU-030-aprobar-o-rechazar-cita-especializada]], [[HU-031-aprobar-o-rechazar-reprogramacion]], [[HU-021-registrar-cierre-de-atencion]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Alto

**Justificación de dificultad:** Es una pieza transversal de la que dependen seis HU: el modelo de transiciones debe ser único y correcto desde el principio, el registro debe participar en la transacción de cada caso de uso sin convertirse en una operación independiente, y la inmutabilidad debe garantizarse por diseño (sin rutas de escritura) y no solo por convención. Un error aquí contamina toda la trazabilidad del producto.

## Tareas de desarrollo

- [ ] **T-01 — Modelar la máquina de estados de cita en el dominio**  
  Dificultad: Alto  
  Descripción: Tipo de dominio con los estados del catálogo, la tabla de transiciones permitidas con su origen admisible y la validación que rechaza cualquier transición no declarada o desde un estado terminal. Sin dependencias de framework.

- [ ] **T-02 — Definir el evento de transición y el puerto de auditoría**  
  Dificultad: Medio  
  Descripción: Objeto de valor con cita, estado nuevo, actor opcional, origen, instante y motivo, con las invariantes de actor obligatorio salvo `SYSTEM` y motivo obligatorio en rechazo; y puerto de salida "registrar transición".

- [ ] **T-03 — Implementar el adaptador de solo inserción**  
  Dificultad: Medio  
  Descripción: Adaptador JPA sobre `appointment_status_history` que solo expone inserción y lectura, participa en la transacción del caso de uso invocante y no ofrece métodos de actualización ni borrado.

- [ ] **T-04 — Implementar el caso de uso y el endpoint de consulta del historial**  
  Dificultad: Medio  
  Descripción: Consulta cronológica del historial de una cita con estado, actor, origen, fecha y hora y motivo, restringida a los perfiles autorizados.

- [ ] **T-05 — Mostrar el historial en citas-web**  
  Dificultad: Bajo  
  Descripción: Sección de historial en el detalle de cita del dashboard ADMIN, en orden cronológico.

- [ ] **T-06 — Pruebas de transiciones y auditoría**  
  Dificultad: Alto  
  Descripción: Pruebas de dominio de todas las transiciones permitidas y prohibidas, integración del registro dentro de la transacción con reversión conjunta, verificación de la restricción de actor, y prueba de ausencia de rutas de edición o borrado.

## Criterios de aceptación

### CA-01 — Cada transición deja un registro completo

**Dado** una transición de estado de cita válida ejecutada por cualquier flujo  
**Cuando** se consulta el historial de la cita  
**Entonces** existe exactamente un registro nuevo con la cita, el estado nuevo, el actor cuando existe, el origen, la fecha y hora del cambio y el motivo cuando se aportó (RF-19).

### CA-02 — Origen SYSTEM sin actor; USER y ADMIN con actor

**Dado** una aprobación automática de cita general, una solicitud especializada de un USER y una decisión de un ADMIN  
**Cuando** se registran sus transiciones  
**Entonces** la primera tiene origen `SYSTEM` y puede no tener actor, y las otras dos tienen origen `USER` y `ADMIN` respectivamente con el identificador del actor; un intento de registrar origen `USER` o `ADMIN` sin actor se rechaza.

### CA-03 — Transiciones no permitidas rechazadas

**Dado** citas en estados terminales (`REJECTED`, `CANCELLED`, `COMPLETED`, `NO_SHOW`) y una cita `APPROVED`  
**Cuando** se intenta llevar una terminal a cualquier otro estado, o la `APPROVED` a `REQUESTED` o `REJECTED`  
**Entonces** el dominio rechaza la transición, la API responde 409, el estado no cambia y no se crea registro de historial (RN-11).

### CA-04 — Rechazo administrativo sin motivo no se registra

**Dado** una transición a `REJECTED` con origen `ADMIN`  
**Cuando** se intenta registrar sin motivo o con motivo vacío  
**Entonces** la operación se rechaza con un error de validación y ni el estado de la cita ni el historial cambian (RN-04).

### CA-05 — Estado e historial son atómicos

**Dado** un caso de uso de transición en el que la inserción del historial falla  
**Cuando** la transacción termina  
**Entonces** la cita conserva su estado anterior y no existe ningún cambio parcial; e inversamente, no existe ningún registro de historial para un cambio de estado que no se confirmó.

### CA-06 — El historial no se edita ni se borra por la API

**Dado** registros de historial existentes  
**Cuando** se inspeccionan los endpoints publicados y se intenta invocar métodos `PUT`, `PATCH` o `DELETE` sobre recursos de historial  
**Entonces** no existe ninguna operación que los modifique o elimine, y las peticiones reciben un error de método o recurso no soportado sin alterar datos (RN-12).

### CA-07 — Consulta cronológica del historial

**Dado** una cita que pasó por `REQUESTED`, `APPROVED` y `CANCELLED`  
**Cuando** un ADMIN consulta su historial  
**Entonces** recibe los tres registros en orden cronológico con estado, actor, origen, fecha y hora y motivo.

### CA-08 — Acceso al historial restringido

**Dado** una cita de un paciente  
**Cuando** consultan su historial un usuario no autenticado y un USER que no es titular de la cita  
**Entonces** ambos reciben el error de autenticación o de autorización correspondiente y no obtienen registros.

## Definition of Done

- [x] Los criterios CA-01 a CA-08 están validados con evidencia concreta.
- [x] Existe una única definición de transiciones permitidas y estados terminales en el dominio, sin dependencias de Spring ni de JPA, y las HU de transición la reutilizan.
- [x] El adaptador de historial no expone actualización ni borrado, verificable por inspección del código y por prueba.
- [x] El registro de historial se ejecuta dentro de la transacción del caso de uso, demostrado con una prueba de reversión conjunta.
- [x] Se usa `appointment_status_history` de V3; cualquier refuerzo de inmutabilidad en base de datos se añade con una migración Flyway posterior a V4.
- [x] El endpoint de consulta aplica [[HU-005-autorizar-peticiones-por-rol-y-ownership]].
- [x] Ningún registro ni log de auditoría contiene contraseñas ni tokens.
- [x] Existen pruebas automatizadas de transiciones permitidas y prohibidas, actor por origen, motivo obligatorio, atomicidad y ausencia de edición, y pasan.
- [x] El contrato del endpoint de historial y el modelo de estados están reflejados en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [x] La trazabilidad de esta HU y de [[EP-009-trazabilidad-y-contrato-rest]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Ejecución de referencia del **2026-09-23**: backend `docker compose run --rm citas-api-dev mvn -B test` → **242 pruebas, 0 fallos** en 22 clases; frontend `npm test` en `citas-web` → **88 pruebas, 0 fallos**, con `typecheck`, `lint` y `build` en verde. La verificación fue independiente (`backend-verifier` y `frontend-verifier`, agentes que no escribieron el código): `EVIDENCIAS_S3.md` §10. Las clases de prueba del backend cuelgan de `citas-api/src/test/java/com/fcv/citas/`.

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | `infrastructure/rest/BookingIntegrationTest#generalAppointmentIsApprovedReservedAndAudited` y `#specializedRequestRetainsTwoConsecutiveSlots` (una fila por creación, con estado, actor, origen y fecha); `infrastructure/rest/AdminDecisionIntegrationTest#approvalKeepsReservationsAndLeavesTheInbox` y `#rejectionReleasesSlotsAndThePatientSeesTheReason` (una fila nueva por decisión, con motivo cuando lo hay) | Los tres flujos que producen transiciones en S3 dejan exactamente un registro cada uno |
| CA-02 | Cumple | `domain/appointment/AppointmentTest#generalIsBornApprovedWithSystemHistoryWithoutActor`, `#specializedIsBornRequestedWithThePatientAsActor`, `#adminApprovesARequestedAppointment` y `#nonSystemChangesRequireAnActor`; `domain/appointment/StatusChange` (constructor compacto: un origen distinto de `SYSTEM` sin actor lanza excepción) | El rechazo de «origen `USER` o `ADMIN` sin actor» ocurre en el dominio, antes de tocar la base |
| CA-03 | Cumple | `AppointmentTest#terminalStatesHaveNoWayOut` (parametrizada sobre `REJECTED`, `CANCELLED`, `COMPLETED` y `NO_SHOW`: `allowedNext()` vacío) y `#onlyRequestedCanBeDecided`; `domain/appointment/AppointmentStatus#allowedNext` (desde `APPROVED` solo `CANCELLED`, `COMPLETED` y `NO_SHOW`, nunca `REQUESTED` ni `REJECTED`); `AdminDecisionIntegrationTest#decidingANonRequestedAppointmentIs409` y `infrastructure/rest/VerificationGapsIntegrationTest#rejectingAnApprovedAppointmentIs409EvenWithoutReason` (409 `INVALID_TRANSITION`, sin cambios) | La tabla de transiciones se prueba en el dominio sobre todos los estados; la traducción a 409 se prueba en la API |
| CA-04 | Cumple | `AdminDecisionIntegrationTest#rejectionWithoutReasonChangesNothing` (400 con `fieldErrors.reason`; la cita sigue `REQUESTED`, con sus dos slots y una sola fila de historial); `AppointmentTest#rejectionRequiresAReasonAndReleasesSlots` | — |
| CA-05 | Cumple | `application/appointment/BookAppointmentUseCase#book` y `AdminAppointmentsUseCase#approve`/`#reject` escriben cita, historial y reservas dentro de un único `tx.inTransaction`; `domain/appointment/AppointmentRepository#create(Appointment, StatusChange)` y `#apply(Transition)` no permiten guardar el estado sin su fila de historial; `BookingIntegrationTest#doubleBookingIsRejectedWith409` y `#sixtyMinutesWithTakenSecondSlotRetainsNothing` (la transacción se deshace entera: ni cita, ni reserva, ni historial) | La atomicidad se demuestra en el sentido contrario al que enuncia el criterio: falla la reserva y se revierten también cita e historial. No hay inyección de fallo sobre el `INSERT` del historial; que estado e historial comparten transacción se comprueba por lectura de las dos únicas firmas del puerto |
| CA-06 | Cumple | `infrastructure/persistence/appointment/SpringDataStatusHistoryRepository` extiende `Repository` (no `JpaRepository`) y solo declara `save`; `StatusHistoryJpaEntity` marca todas sus columnas `updatable = false`; `AdminDecisionIntegrationTest#historyAndAppointmentsCannotBeDeletedThroughTheApi` (`DELETE` → 405 y el historial intacto); `infrastructure/persistence/FlywayMigratesEmptySchemaTest#elHistorialEsAppendOnlyYAdmiteOrigenProfesional` (V5: la clave foránea deja de borrar en cascada) | Cuatro barreras: puerto sin métodos de escritura, entidad no actualizable, ausencia de rutas y `RESTRICT` en la base |
| CA-07 | Cumple | `infrastructure/persistence/appointment/JdbcAppointmentQueries#history` (`ORDER BY h.changed_at ASC, h.id ASC`, con estado, nombre de estado, origen, nombre del actor, motivo y fecha); `AdminDecisionIntegrationTest#approvalKeepsReservationsAndLeavesTheInbox` (`history.length() = 2`, la segunda con `source = ADMIN`) y `#rejectionReleasesSlotsAndThePatientSeesTheReason` (`history[1].reason`) | Límite explícito: en S3 una cita solo puede acumular **dos** transiciones, porque `CANCELLED` no tiene productor hasta [[HU-026-cancelar-una-cita-futura]]. El orden y el juego completo de campos quedan verificados con esas dos; el tercer paso del ejemplo del criterio no añade mecanismo nuevo |
| CA-08 | Cumple | `infrastructure/rest/AuthorizationIntegrationTest#onlyUserReachesPatientRoutes` y `#anonymousGets401AndWrongRoleGets403OnAdminRoutes` (sin token → 401); `BookingIntegrationTest#patientSeesOnlyOwnAppointmentsWithDetail` (el detalle —y con él el historial— de una cita ajena responde 404 sin cuerpo) | El historial no tiene ruta propia: viaja dentro del detalle de la cita, que ya aplica rol y propiedad |
| DoD — CA-01 a CA-08 validados con evidencia concreta | Cumple | Filas CA-01 a CA-08 de esta tabla | — |
| DoD — Una única definición de transiciones y estados terminales en el dominio, reutilizada | Cumple | `domain/appointment/AppointmentStatus#allowedNext` y `#canTransitionTo`; `Appointment#transitionTo` es la única puerta, usada por `approve`, `reject` y las dos creaciones; `HexagonalArchitectureTest` garantiza que no arrastra Spring ni JPA | — |
| DoD — El adaptador de historial no expone actualización ni borrado | Cumple | `SpringDataStatusHistoryRepository` (solo `save`); `StatusHistoryJpaEntity` (`updatable = false` en todas las columnas); `AdminDecisionIntegrationTest#historyAndAppointmentsCannotBeDeletedThroughTheApi` | — |
| DoD — El registro de historial se ejecuta dentro de la transacción del caso de uso | Cumple | `BookAppointmentUseCase#book` y `AdminAppointmentsUseCase`; `BookingIntegrationTest#doubleBookingIsRejectedWith409` (una sola fila de historial tras el intento fallido) | — |
| DoD — Se usa `appointment_status_history` de V3 y el refuerzo de inmutabilidad va en una migración posterior a V4 | Cumple | `V3__schedule_and_appointments.sql` (tabla) y `V5__audit_history_append_only.sql` (quita el borrado en cascada y añade el origen `PROFESSIONAL`, decisión D8); `FlywayMigratesEmptySchemaTest#elHistorialEsAppendOnlyYAdmiteOrigenProfesional` | Ninguna migración previa se editó |
| DoD — El endpoint de consulta aplica [[HU-005-autorizar-peticiones-por-rol-y-ownership]] | Cumple | `SecurityConfig`; `BookingIntegrationTest#patientSeesOnlyOwnAppointmentsWithDetail`; `AuthorizationIntegrationTest` | — |
| DoD — Ningún registro ni log de auditoría contiene contraseñas ni tokens | Cumple | `StatusHistoryJpaEntity` solo guarda cita, estado, actor, origen y motivo; `HexagonalArchitectureTest.nadieEscribeEnLaSalidaEstandar`; `AuthFlowIntegrationTest` comprueba con `CapturedOutput` que ni el registro ni el login vuelcan credenciales | — |
| DoD — Pruebas de transiciones, actor por origen, motivo obligatorio, atomicidad y ausencia de edición | Cumple | `AppointmentTest` (8); `AdminDecisionIntegrationTest#rejectionWithoutReasonChangesNothing`, `#decidingANonRequestedAppointmentIs409`, `#historyAndAppointmentsCannotBeDeletedThroughTheApi`; `BookingIntegrationTest#doubleBookingIsRejectedWith409`; `FlywayMigratesEmptySchemaTest#elHistorialEsAppendOnlyYAdmiteOrigenProfesional` | — |
| DoD — Contrato del historial y modelo de estados reflejados en [[HU-033-publicar-contrato-rest-documentado]] | Cumple | `citas-api/docs/wiki/llm-wiki/wiki/contrato-rest-citas.md`: tipo `HistoryEntry`, enumeración de estados y la nota de que el historial no tiene rutas de escritura | — |
| DoD — Trazabilidad de esta HU y de [[EP-009-trazabilidad-y-contrato-rest]] actualizada | Cumple | Esta tabla y el historial de validación | — |

## Historial de validación

- 2026-09-23 — Estado `Completada` (fase F11 de `PLAN_RETOMA_S3.md`): matriz de evidencia completa, los 8 criterios y los 10 ítems de DoD en `Cumple`. Dos límites quedan anotados en la matriz y en «Notas y decisiones»: en S3 una cita solo acumula dos transiciones (CA-07) y la atomicidad estado-historial se demuestra por reversión de la reserva, sin inyectar un fallo en el `INSERT` del historial (CA-05). Cierre dentro de la aprobación delegada de S3 (`AGENTS.md` §6); el usuario puede reabrirla.
- 2026-09-23 — Estado `En validación` (skill `scrum-spec-orchestrator`, paso 10): se recolecta del repositorio la evidencia de cada criterio y de cada ítem de DoD.
- 2026-09-18 — Estado `En desarrollo` (skill `scrum-spec-orchestrator`, paso 9): se inicia la implementación en la fase F2 de `PLAN_RETOMA_S3.md`.
- 2026-09-18 — Estado `Aprobada` por **aprobación delegada** de S3: el usuario pidió continuar S3 dejando las decisiones de diseño a criterio del agente (`AGENTS.md` §6). Alcance y decisiones D5–D13 en `PLAN_RETOMA_S3.md` y [[dec-004-decisiones-s3-reserva]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- Incógnita abierta **INC-037** (ver [[EP-009-trazabilidad-y-contrato-rest]]): no está definido quién consulta el historial. CA-07 y CA-08 solo fijan que ADMIN puede y que un no titular no puede; el acceso del paciente titular y del profesional asignado debe decidirse antes de aprobar la HU.
- RF-19 solo admite los orígenes `SYSTEM`, `USER` y `ADMIN`, y la columna `source` de V3 es un ENUM con esos tres valores. El PRD no indica qué origen corresponde al cierre de atención ejecutado por un `PROFESSIONAL` ([[HU-021-registrar-cierre-de-atencion]]). Añadir un valor `PROFESSIONAL` exigiría una migración nueva; debe decidirse con el usuario del proyecto.
- [[HU-031-aprobar-o-rechazar-reprogramacion]] registra en el historial decisiones que no cambian el estado de la cita, siguiendo el criterio de completitud de EP-008. Esta HU define el historial como registro de transiciones; debe acordarse si se admiten registros "sin cambio de estado" o si la decisión de reprogramación basta con `reschedule_requests`.
- La clave foránea `fk_ash_appointment` de V3 tiene `ON DELETE CASCADE`: un borrado físico de una cita borraría su historial. Ningún flujo del PRD borra citas; la DoD exige no exponer esa ruta, y endurecerlo en base de datos sería una migración nueva.
- La inclusión de `REQUESTED → CANCELLED` en la máquina de estados depende de **INC-030** (ver [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]); la condición exacta de `APPROVED → COMPLETED/NO_SHOW` depende de **INC-018** (ver [[EP-005-agenda-del-profesional]]).
