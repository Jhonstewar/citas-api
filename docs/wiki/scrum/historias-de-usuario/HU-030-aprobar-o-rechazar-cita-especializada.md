---
id: HU-030
tipo: historia-de-usuario
titulo: "Aprobar o rechazar una cita especializada"
estado: Completada
epica: "[[EP-008-operacion-administrativa-de-solicitudes]]"
requisitos: [RF-12, RF-19]
esfuerzo: "Alto"
sprint_sugerido: "Sprint 6"
dependencias:
  - "[[HU-029-consultar-bandeja-administrativa]]"
  - "[[HU-024-solicitar-cita-especializada]]"
  - "[[HU-032-auditar-cambios-de-estado-de-cita]]"
relacionadas:
  - "[[HU-025-consultar-mis-citas-y-detalle]]"
  - "[[HU-020-consultar-agenda-de-citas-aprobadas]]"
  - "[[HU-026-cancelar-una-cita-futura]]"
  - "[[HU-031-aprobar-o-rechazar-reprogramacion]]"
---

# HU-030 — Aprobar o rechazar una cita especializada

## Historia de usuario

**COMO** ADMIN  
**QUIERO** aprobar una cita especializada solicitada o rechazarla indicando el motivo  
**PARA** confirmar la atención al paciente o devolver la franja a la agenda dejando constancia de por qué

> Como ADMIN, quiero aprobar una cita especializada solicitada o rechazarla indicando el motivo para confirmar la atención al paciente o devolver la franja a la agenda dejando constancia de por qué.

## Contexto y descripción

RF-12 cierra el flujo especializado: ADMIN puede aprobar o rechazar; al aprobar la cita pasa a `APPROVED`; al rechazar pasa a `REJECTED`, exige motivo (RN-04) y libera los slots (RN-09). Ambas son transiciones explícitas desde `REQUESTED` (RN-11) y cada una se registra en el historial con origen `ADMIN` y el administrador como actor (RF-19).

Los efectos sobre agenda son asimétricos. Aprobar no toca `slot_reservations`: las filas de tipo `APPOINTMENT` ya retenían la franja y simplemente pasan a representar una cita confirmada. Rechazar borra esas filas, lo que devuelve los slots al pool y los hace reaparecer en [[HU-022-buscar-disponibilidad-con-filtros]]. El motivo de rechazo se guarda en `appointment_status_history.reason` y es el mismo dato que el paciente ve en [[HU-025-consultar-mis-citas-y-detalle]].

## Alcance

- Endpoints REST de aprobación y de rechazo de una cita especializada, restringidos a `ADMIN`.
- Validación de que la cita existe, es de tipo especializado y está en `REQUESTED`.
- Transición `REQUESTED → APPROVED` sin cambios en la ocupación de slots.
- Transición `REQUESTED → REJECTED` con motivo obligatorio y liberación de sus filas en `slot_reservations`.
- Registro en el historial con estado nuevo, actor ADMIN, origen `ADMIN`, fecha y hora y, en el rechazo, motivo.
- Protección frente a decisiones concurrentes sobre la misma cita.
- Pantalla "aprobar/rechazar citas" en `citas-web` (PRD §6), con captura obligatoria del motivo al rechazar.

## Fuera de alcance

- Consulta de pendientes, que se cubre en [[HU-029-consultar-bandeja-administrativa]].
- Decisiones sobre reprogramaciones, que se cubren en [[HU-031-aprobar-o-rechazar-reprogramacion]].
- Reversión de una decisión ya tomada: pendiente de INC-035.
- Aprobación de citas generales: son automáticas (RN-02).
- Catálogo de motivos de rechazo: pendiente de INC-033.
- Notificación al paciente por correo: pertenece a la automatización posterior de PRD §10.

## Reglas de negocio

- Las citas especializadas requieren decisión de ADMIN (RN-03).
- Solo una cita en `REQUESTED` admite aprobación o rechazo (RN-11).
- Aprobar lleva la cita a `APPROVED` (RF-12).
- Rechazar lleva la cita a `REJECTED` y exige motivo no vacío (RF-12, RN-04).
- Rechazar libera los slots retenidos (RF-12, RN-09).
- Cada decisión se registra en el historial con actor, origen `ADMIN` y, en el rechazo, el motivo (RF-19).
- `REJECTED` es terminal: no admite nuevas transiciones (catálogo de estados de RF-05).
- Solo ADMIN ejecuta estas decisiones (PRD §8).

## Dependencias y relaciones

- Épica: [[EP-008-operacion-administrativa-de-solicitudes]]
- Dependencias: [[HU-029-consultar-bandeja-administrativa]], [[HU-024-solicitar-cita-especializada]], [[HU-032-auditar-cambios-de-estado-de-cita]]
- Relacionadas: [[HU-025-consultar-mis-citas-y-detalle]], [[HU-020-consultar-agenda-de-citas-aprobadas]], [[HU-026-cancelar-una-cita-futura]], [[HU-022-buscar-disponibilidad-con-filtros]], [[HU-016-activar-o-desactivar-profesional]], [[HU-031-aprobar-o-rechazar-reprogramacion]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Alto

**Justificación de dificultad:** Son dos transiciones con efectos distintos sobre agenda y auditoría que deben ser atómicas, con una validación de motivo obligatoria y con un caso concurrente real (dos administradores, o un administrador y una cancelación del paciente, actuando sobre la misma cita) que debe resolverse sin dobles transiciones ni slots huérfanos.

## Tareas de desarrollo

- [ ] **T-01 — Modelar las transiciones de aprobación y rechazo en el dominio**  
  Dificultad: Medio  
  Descripción: Operaciones explícitas sobre el agregado cita que exigen estado `REQUESTED` y tipo especializado, validan motivo no vacío en el rechazo, producen el nuevo estado y el evento de historial, e indican si deben liberarse slots. Sin dependencias de framework.

- [ ] **T-02 — Implementar los casos de uso de aprobación y rechazo**  
  Dificultad: Alto  
  Descripción: Casos de uso que recuperan la cita con control de concurrencia (bloqueo o versión), aplican la transición, liberan las reservas en el rechazo y registran el historial con origen `ADMIN` y actor en una única transacción.

- [ ] **T-03 — Implementar la liberación de reservas de la cita**  
  Dificultad: Medio  
  Descripción: Operación del adaptador de reservas que elimina las filas `APPOINTMENT` de la cita en `slot_reservations`, compartida con la cancelación de [[HU-026-cancelar-una-cita-futura]].

- [ ] **T-04 — Exponer los adaptadores REST de aprobación y rechazo**  
  Dificultad: Medio  
  Descripción: Endpoints restringidos a `ADMIN`; el rechazo exige un motivo en el cuerpo. Respuestas diferenciadas: éxito con el nuevo estado, error de validación por motivo ausente o vacío, conflicto 409 por estado no `REQUESTED`, y recurso no encontrado.

- [ ] **T-05 — Construir la pantalla de aprobar/rechazar citas en citas-web**  
  Dificultad: Medio  
  Descripción: Acciones desde la bandeja con confirmación, diálogo de rechazo que no permite enviar sin motivo, y refresco de la bandeja tras la decisión o ante un 409.

- [ ] **T-06 — Pruebas de decisión sobre cita especializada**  
  Dificultad: Alto  
  Descripción: Pruebas de dominio de las transiciones válidas e inválidas, integración de aprobación y rechazo con verificación de slots e historial, motivo vacío, decisión concurrente y rol.

## Criterios de aceptación

### CA-01 — Aprobación de una cita REQUESTED

**Dado** una cita especializada en `REQUESTED` con sus slots retenidos  
**Cuando** ADMIN la aprueba  
**Entonces** la cita queda en `APPROVED`, sus filas en `slot_reservations` siguen existiendo sin cambios y la cita deja de aparecer en la bandeja.

### CA-02 — Rechazo con motivo libera los slots

**Dado** una cita especializada de 60 minutos en `REQUESTED` que retiene dos slots  
**Cuando** ADMIN la rechaza indicando un motivo  
**Entonces** la cita queda en `REJECTED`, sus dos filas de `slot_reservations` desaparecen y una búsqueda de disponibilidad vuelve a ofrecer esa franja a cualquier paciente (RN-09).

### CA-03 — Rechazo sin motivo rechazado

**Dado** una cita especializada en `REQUESTED`  
**Cuando** ADMIN intenta rechazarla sin motivo, con motivo vacío o solo con espacios  
**Entonces** la API responde con un error de validación, la cita sigue en `REQUESTED`, sus slots siguen retenidos y no se crea registro de historial (RN-04).

### CA-04 — Transiciones inválidas rechazadas

**Dado** citas en `APPROVED`, `REJECTED`, `CANCELLED`, `COMPLETED` y `NO_SHOW`, y una cita general `APPROVED`  
**Cuando** ADMIN intenta aprobar o rechazar cualquiera de ellas  
**Entonces** la API responde 409 indicando que la cita no está en `REQUESTED`, y no cambia ningún estado, reserva ni historial (RN-11).

### CA-05 — Historial de la decisión con origen ADMIN

**Dado** una aprobación y un rechazo realizados correctamente  
**Cuando** se consulta el historial de cada cita  
**Entonces** cada una tiene exactamente un registro nuevo con el estado nuevo, el identificador del ADMIN como actor, el origen `ADMIN` y la fecha y hora; el del rechazo contiene además el motivo enviado (RF-19).

### CA-06 — El paciente ve el motivo de rechazo

**Dado** una cita rechazada con un motivo  
**Cuando** su paciente consulta el detalle de la cita  
**Entonces** ve el estado `REJECTED` y el mismo motivo registrado por ADMIN, verificado contra [[HU-025-consultar-mis-citas-y-detalle]].

### CA-07 — Decisiones concurrentes sobre la misma cita

**Dado** una cita en `REQUESTED`  
**Cuando** dos operaciones concurrentes intentan decidir sobre ella (por ejemplo, dos ADMIN aprobando y rechazando a la vez)  
**Entonces** exactamente una tiene éxito, la otra recibe 409, y la cita tiene un único estado final con un único registro de historial de decisión.

### CA-08 — Solo ADMIN decide

**Dado** un usuario autenticado con rol `USER` (incluido el paciente titular) o `PROFESSIONAL`  
**Cuando** intenta aprobar o rechazar una cita especializada  
**Entonces** la API responde con un error de autorización y la cita no cambia.

## Definition of Done

- [x] Los criterios CA-01 a CA-08 están validados con evidencia concreta.
- [x] Aprobar y rechazar son operaciones explícitas del dominio con validación de estado origen; no existe una actualización genérica de estado expuesta.
- [x] Cambio de estado, liberación de reservas e historial ocurren en una única transacción, sin resultados parciales.
- [x] La liberación de reservas reutiliza la misma operación que la cancelación y no deja filas huérfanas en `slot_reservations`.
- [x] El motivo obligatorio se valida en el servidor y se persiste en `appointment_status_history.reason`.
- [x] Existe control de concurrencia demostrado con una prueba de dos decisiones simultáneas.
- [x] No se crean migraciones salvo cambio de esquema justificado (por ejemplo una columna de versión), en una migración Flyway posterior a V4.
- [x] Los endpoints exigen rol `ADMIN` aplicando [[HU-005-autorizar-peticiones-por-rol-y-ownership]].
- [x] La pantalla de `citas-web` no permite enviar un rechazo sin motivo y refresca la bandeja ante un 409.
- [x] Existen pruebas automatizadas de aprobación, rechazo con liberación, motivo vacío, transiciones inválidas, concurrencia y rol, y pasan.
- [x] El contrato de los endpoints de decisión está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [x] La trazabilidad de esta HU y de [[EP-008-operacion-administrativa-de-solicitudes]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Ejecución de referencia del **2026-09-23**: backend `docker compose run --rm citas-api-dev mvn -B test` → **242 pruebas, 0 fallos** en 22 clases; frontend `npm test` en `citas-web` → **88 pruebas, 0 fallos**, con `typecheck`, `lint` y `build` en verde. La verificación fue independiente (`backend-verifier` y `frontend-verifier`, agentes que no escribieron el código): `EVIDENCIAS_S3.md` §10. Las clases de prueba del backend cuelgan de `citas-api/src/test/java/com/fcv/citas/`.

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | `infrastructure/rest/AdminDecisionIntegrationTest#approvalKeepsReservationsAndLeavesTheInbox` | 200 con `status = APPROVED`; las dos filas de `slot_reservations` siguen existiendo y la cita ya no aparece en la bandeja |
| CA-02 | Cumple | `AdminDecisionIntegrationTest#rejectionReleasesSlotsAndThePatientSeesTheReason` | Cita de 60 minutos con dos slots retenidos: tras el rechazo quedan cero filas en `slot_reservations` y la búsqueda de disponibilidad vuelve a ofrecer las 08:00 (RN-09). Confirmado también contra la API real en `EVIDENCIAS_S3.md` §11, donde la franja liberada por la cita 4 la vuelve a tomar la cita 9 |
| CA-03 | Cumple | `AdminDecisionIntegrationTest#rejectionWithoutReasonChangesNothing`; `domain/appointment/AppointmentTest#rejectionRequiresAReasonAndReleasesSlots` | Motivo con solo espacios y cuerpo ausente → 400 con `fieldErrors.reason`; la cita sigue `REQUESTED`, con sus dos slots retenidos y una sola fila de historial |
| CA-04 | Cumple | `domain/appointment/AppointmentTest#onlyRequestedCanBeDecided`, parametrizada sobre **todos** los estados distintos de `REQUESTED` (`APPROVED`, `REJECTED`, `CANCELLED`, `COMPLETED`, `NO_SHOW`), tanto para aprobar como para rechazar; `AdminDecisionIntegrationTest#decidingANonRequestedAppointmentIs409` (409 `INVALID_TRANSITION`, sin cambios en historial ni reservas); `infrastructure/rest/VerificationGapsIntegrationTest#rejectingAnApprovedAppointmentIs409EvenWithoutReason` (cita **general** `APPROVED` → 409 y no 400) | La última prueba fija el orden de comprobación: sobre una cita ya decidida el problema es la transición, no el motivo |
| CA-05 | Cumple | `AdminDecisionIntegrationTest#approvalKeepsReservationsAndLeavesTheInbox` (`history.length() = 2`, la segunda con `source = ADMIN` y `actor_user_id` igual al ADMIN); `#rejectionReleasesSlotsAndThePatientSeesTheReason` (`history[1].reason` con el texto enviado) | Exactamente un registro nuevo por decisión |
| CA-06 | Cumple | `AdminDecisionIntegrationTest#rejectionReleasesSlotsAndThePatientSeesTheReason` (el paciente consulta su detalle y ve `REJECTED` y el mismo motivo); `citas-web/src/patientBooking.test.tsx`: «una cita rechazada muestra el motivo y el historial (HU-030 CA-06)» | Verificado contra [[HU-025-consultar-mis-citas-y-detalle]]. `VerificationGapsIntegrationTest#patientSeesTheRoleNotTheNameOfTheAdmin` añade que el paciente ve «Administración» y no el nombre del empleado |
| CA-07 | Cumple | `AdminDecisionIntegrationTest#concurrentDecisionsLetExactlyOneWin` (aprobar y rechazar a la vez: los estados devueltos son exactamente 200 y 409, y la cita queda con dos filas de historial, es decir una sola decisión) | El control lo da `AppointmentRepository#lockById` (`SELECT … FOR UPDATE`) en `AdminAppointmentsUseCase`: la segunda transacción ve el estado ya cambiado |
| CA-08 | Cumple | `AdminDecisionIntegrationTest#onlyAdminDecides` (el paciente titular → 403 y el historial no cambia); `infrastructure/rest/AuthorizationIntegrationTest#onlyAdminReachesAdminRoutes` (PROFESSIONAL → 403 en todo `/api/admin/**`) | — |
| DoD — CA-01 a CA-08 validados con evidencia concreta | Cumple | Filas CA-01 a CA-08 de esta tabla | — |
| DoD — Aprobar y rechazar son operaciones explícitas del dominio con validación de estado origen | Cumple | `domain/appointment/Appointment#approve` y `#reject` (ambas pasan por `requireRequested()`); `Appointment#transitionTo` es la única puerta de cambio de estado y valida contra `AppointmentStatus#canTransitionTo`; `AppointmentTest` (8 pruebas) | No existe ningún endpoint ni caso de uso que fije el estado de una cita de forma genérica |
| DoD — Estado, liberación de reservas e historial en una única transacción | Cumple | `application/appointment/AdminAppointmentsUseCase#approve` y `#reject` (`tx.inTransaction`); `AdminDecisionIntegrationTest#rejectionWithoutReasonChangesNothing` comprueba que un rechazo inválido no deja nada a medias | — |
| DoD — La liberación de reservas no deja filas huérfanas y es una operación única | Cumple | `domain/appointment/AppointmentStatus#releasesSlots()` (única definición: `REJECTED` o `CANCELLED`) y `AppointmentRepository#releaseSlots`, único punto de liberación; `AdminDecisionIntegrationTest#rejectionReleasesSlotsAndThePatientSeesTheReason` (cero filas para la cita) | La cancelación aún no existe ([[HU-026-cancelar-una-cita-futura]], S4). Hoy no hay dos implementaciones que puedan divergir: el predicado ya contempla `CANCELLED` y la operación de liberación es una sola. Que la cancelación la reutilice se comprobará al cerrar HU-026 |
| DoD — Motivo obligatorio validado en el servidor y persistido en `appointment_status_history.reason` | Cumple | `Appointment#reject` (rechaza nulo o en blanco) y `StatusChange` (recorta, rechaza vacío y limita a 500 caracteres); `AdminDecisionIntegrationTest#rejectionWithoutReasonChangesNothing` y `#rejectionReleasesSlotsAndThePatientSeesTheReason` | La validación no depende del formulario: la segunda petición de la prueba va sin cuerpo |
| DoD — Control de concurrencia demostrado con dos decisiones simultáneas | Cumple | `AdminDecisionIntegrationTest#concurrentDecisionsLetExactlyOneWin`; `AppointmentRepository#lockById` | — |
| DoD — Sin migraciones salvo cambio de esquema justificado | Cumple | La concurrencia se resuelve con bloqueo pesimista sobre la fila, sin columna de versión: no hubo cambio de esquema para esta HU | V5 (auditoría) pertenece a [[HU-032-auditar-cambios-de-estado-de-cita]] |
| DoD — Los endpoints exigen rol ADMIN aplicando [[HU-005-autorizar-peticiones-por-rol-y-ownership]] | Cumple | `SecurityConfig` (`/api/admin/**` → `hasRole("ADMIN")`); `AdminDecisionIntegrationTest#onlyAdminDecides`; `AuthorizationIntegrationTest#anonymousGets401AndWrongRoleGets403OnAdminRoutes` | — |
| DoD — `citas-web` no permite enviar un rechazo sin motivo y refresca la bandeja ante un 409 | Cumple | `citas-web/src/adminOperations.test.tsx`: «rechazar exige motivo (contador de 500) y lo envía recortado», «un 409 al aprobar informa y recarga la bandeja» y «M7: un 409 al rechazar cierra el modal, informa y recarga la bandeja» | La tercera prueba salió de la verificación independiente |
| DoD — Pruebas de aprobación, rechazo con liberación, motivo vacío, transiciones inválidas, concurrencia y rol | Cumple | `AdminDecisionIntegrationTest` (13 pruebas); `AppointmentTest` (8); `VerificationGapsIntegrationTest#rejectingAnApprovedAppointmentIs409EvenWithoutReason` y `#patientSeesTheRoleNotTheNameOfTheAdmin` | — |
| DoD — Contrato de los endpoints de decisión reflejado en [[HU-033-publicar-contrato-rest-documentado]] | Cumple | `citas-api/docs/wiki/llm-wiki/wiki/contrato-rest-citas.md`, sección «Operación — ADMIN (HU-029, HU-030, HU-032)»: `approve`, `reject`, sus códigos y la regla de liberación | — |
| DoD — Trazabilidad de esta HU y de [[EP-008-operacion-administrativa-de-solicitudes]] actualizada | Cumple | Esta tabla y el historial de validación | — |

## Historial de validación

- 2026-09-23 — Estado `Completada` (fase F11 de `PLAN_RETOMA_S3.md`): matriz de evidencia completa, los 8 criterios y los 12 ítems de DoD en `Cumple`. Queda anotado que la reutilización de la liberación de slots por la cancelación solo podrá comprobarse al cerrar [[HU-026-cancelar-una-cita-futura]]; hoy el punto de liberación es único. Cierre dentro de la aprobación delegada de S3 (`AGENTS.md` §6); el usuario puede reabrirla.
- 2026-09-23 — Estado `En validación` (skill `scrum-spec-orchestrator`, paso 10): se recolecta del repositorio la evidencia de cada criterio y de cada ítem de DoD.
- 2026-09-18 — Estado `En desarrollo` (skill `scrum-spec-orchestrator`, paso 9): se inicia la implementación en la fase F6 de `PLAN_RETOMA_S3.md`.
- 2026-09-18 — Estado `Aprobada` por **aprobación delegada** de S3: el usuario pidió continuar S3 dejando las decisiones de diseño a criterio del agente (`AGENTS.md` §6). Alcance y decisiones D5–D13 en `PLAN_RETOMA_S3.md` y [[dec-004-decisiones-s3-reserva]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- Incógnita abierta **INC-032** (ver [[EP-008-operacion-administrativa-de-solicitudes]]): no está definido qué ocurre si al aprobar la franja ya no es válida (bloque eliminado o profesional desactivado). Como [[HU-018-editar-y-eliminar-bloques-futuros]] impide eliminar bloques con citas comprometidas, el caso restante es el profesional desactivado; esta HU no lo bloquea hasta que se decida.
- Incógnita abierta **INC-033** (ver [[EP-008-operacion-administrativa-de-solicitudes]]): el motivo se trata como texto libre no vacío; la columna `reason` de V3 admite hasta 500 caracteres, que actúa como límite técnico hasta que se decida una longitud funcional.
- Incógnita abierta **INC-035** (ver [[EP-008-operacion-administrativa-de-solicitudes]]): no se ofrece reversión de decisiones; CA-04 lo hace explícito para `REJECTED` y `APPROVED`.
- Incógnita abierta **INC-036** (ver [[EP-008-operacion-administrativa-de-solicitudes]]): no está definido si puede aprobarse una solicitud cuya fecha ya pasó. Esta HU no añade esa restricción; si se decide aplicar RN-06, se incorporará como criterio adicional.
