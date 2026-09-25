---
id: HU-023
tipo: historia-de-usuario
titulo: "Agendar cita de Medicina General"
estado: Completada
epica: "[[EP-006-busqueda-de-disponibilidad-y-reserva]]"
requisitos: [RF-11, RF-19]
esfuerzo: "Alto"
sprint_sugerido: "Sprint 5"
dependencias:
  - "[[HU-022-buscar-disponibilidad-con-filtros]]"
  - "[[HU-032-auditar-cambios-de-estado-de-cita]]"
relacionadas:
  - "[[HU-024-solicitar-cita-especializada]]"
  - "[[HU-025-consultar-mis-citas-y-detalle]]"
  - "[[HU-020-consultar-agenda-de-citas-aprobadas]]"
  - "[[HU-018-editar-y-eliminar-bloques-futuros]]"
---

# HU-023 — Agendar cita de Medicina General

## Historia de usuario

**COMO** USER autenticado  
**QUIERO** reservar una cita de Medicina General con el profesional general y el horario disponible que elija  
**PARA** obtener una cita confirmada al instante sin esperar la intervención de un administrador

> Como USER autenticado, quiero reservar una cita de Medicina General con el profesional general y el horario disponible que elija para obtener una cita confirmada al instante sin esperar la intervención de un administrador.

## Contexto y descripción

RF-11 describe el flujo general: el usuario selecciona `Medicina General`, escoge uno de los profesionales generales disponibles y, si el horario sigue disponible al confirmar, la cita se crea `APPROVED` automáticamente sin intervención de ADMIN (RN-02). La expresión "si sigue disponible al confirmar" es la clave técnica: la disponibilidad mostrada por [[HU-022-buscar-disponibilidad-con-filtros]] puede quedar obsoleta, y la verificación definitiva ocurre en la escritura.

Esa verificación la garantiza la base de datos. La tabla `slot_reservations` de V3 tiene `slot_id` como PK: insertar la reserva de un slot ya reservado o retenido produce una violación de clave duplicada, de modo que dos confirmaciones concurrentes sobre la misma franja no pueden tener éxito a la vez. El caso de uso traduce esa violación a un conflicto 409 y revierte toda la operación.

La cita nace con historial: [[HU-032-auditar-cambios-de-estado-de-cita]] se planifica antes para que la creación registre el estado inicial `APPROVED` con origen `SYSTEM`.

## Alcance

- Endpoint REST de creación de cita general en `citas-api` para el rol `USER`.
- Validación de especialidad de tipo `GENERAL`, activa y asociada al profesional elegido; profesional activo; slots futuros; sede asignada.
- Selección de 1 slot (30 minutos) o de 2 slots consecutivos del mismo bloque (60 minutos) según la duración de la especialidad.
- Creación de la cita en `appointments` con estado `APPROVED` y con fecha, hora de inicio y hora de fin derivadas de los slots.
- Inserción de las filas de `slot_reservations` de tipo `APPOINTMENT` con `slot_order` 1 y 2.
- Registro del estado inicial en `appointment_status_history` con origen `SYSTEM`.
- Todo lo anterior en una única transacción; conflicto 409 ante doble reserva.
- Pantalla "solicitar cita" en `citas-web` para el flujo general, con confirmación y manejo del conflicto.

## Fuera de alcance

- Citas especializadas, que se cubren en [[HU-024-solicitar-cita-especializada]].
- Consulta posterior de la cita, que se cubre en [[HU-025-consultar-mis-citas-y-detalle]].
- Asignación automática del profesional: pendiente de INC-026.
- Creación de citas por ADMIN en nombre de un paciente: no está en el PRD.
- Pagos, autorizaciones de EPS y facturación (PRD §9).
- Notificación por correo: pertenece a la automatización posterior de PRD §10.

## Reglas de negocio

- La especialidad debe ser `Medicina General` o, en general, de tipo `GENERAL` (RF-11).
- El usuario escoge un profesional general disponible (RF-11).
- Si el horario sigue disponible al confirmar, la cita nace `APPROVED` sin intervención de ADMIN (RF-11, RN-02).
- Ninguna cita puede ocupar slots ya reservados o retenidos (RN-01).
- 60 minutos = 2 slots consecutivos disponibles; 30 minutos = 1 slot (RF-09, RN-05).
- La duración proviene de la especialidad y el usuario no la modifica (RF-09).
- No se permiten citas en el pasado (RN-06).
- La especialidad debe estar activa y asociada al profesional (RN-08); el profesional debe estar activo y la sede asignada (RN-07).
- La creación registra el estado inicial en el historial con origen `SYSTEM` (RF-19, EP-006).
- La cita pertenece al usuario autenticado; el titular no se toma del cuerpo de la petición (PRD §8).

## Dependencias y relaciones

- Épica: [[EP-006-busqueda-de-disponibilidad-y-reserva]]
- Dependencias: [[HU-022-buscar-disponibilidad-con-filtros]], [[HU-032-auditar-cambios-de-estado-de-cita]]
- Relacionadas: [[HU-024-solicitar-cita-especializada]], [[HU-025-consultar-mis-citas-y-detalle]], [[HU-020-consultar-agenda-de-citas-aprobadas]], [[HU-018-editar-y-eliminar-bloques-futuros]], [[HU-016-activar-o-desactivar-profesional]], [[HU-014-asignar-especialidades-y-especialidad-primaria]], [[HU-009-registrar-afiliacion-a-eps-y-plan]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Alto

**Justificación de dificultad:** Es la primera escritura de cita del producto e inaugura el mecanismo de reserva que reutilizan la cita especializada y la reprogramación. Combina validaciones de seis entidades, la consecutividad de slots, la escritura coordinada en tres tablas dentro de una transacción y el tratamiento correcto de la concurrencia a partir de la PK de `slot_reservations`, que debe traducirse a un 409 sin dejar resultados parciales.

## Tareas de desarrollo

- [ ] **T-01 — Modelar el agregado cita y su creación general en el dominio**  
  Dificultad: Alto  
  Descripción: Entidad cita con paciente, profesional, sede, especialidad, fecha, inicio, fin y estado; fábrica de creación general que exige tipo `GENERAL`, franja futura y slots coherentes con la duración, y produce el estado `APPROVED` y el evento de transición inicial. Sin dependencias de framework.

- [ ] **T-02 — Implementar el puerto y el adaptador de reserva de slots**  
  Dificultad: Alto  
  Descripción: Puerto de dominio "reservar slots para cita" y adaptador que inserta en `slot_reservations` las filas de tipo `APPOINTMENT` con su `slot_order`, detecta la violación de la PK y la traduce a un error de dominio de franja no disponible. Reutilizable por [[HU-024-solicitar-cita-especializada]].

- [ ] **T-03 — Implementar el caso de uso de agendamiento general**  
  Dificultad: Alto  
  Descripción: Caso de uso que resuelve el usuario desde el contexto de autenticación, valida profesional activo, especialidad activa y asociada, sede asignada, consecutividad y futuro de los slots, persiste la cita, reserva los slots y registra el historial con origen `SYSTEM` en una única transacción.

- [ ] **T-04 — Exponer el adaptador REST de creación de cita general**  
  Dificultad: Medio  
  Descripción: Endpoint restringido a `USER` con DTO validado (profesional, especialidad, slot inicial) y respuestas diferenciadas: creación con la cita `APPROVED`, 409 por franja no disponible, y errores de regla de negocio para franja pasada, slots no consecutivos, especialidad no general, especialidad no asociada o inactiva y profesional inactivo.

- [ ] **T-05 — Construir el flujo de solicitar cita general en citas-web**  
  Dificultad: Medio  
  Descripción: Paso de confirmación desde el resultado de búsqueda, llamada a la API, presentación de la cita aprobada y, ante 409, mensaje de franja ya tomada con opción de volver a buscar.

- [ ] **T-06 — Pruebas de agendamiento y concurrencia**  
  Dificultad: Alto  
  Descripción: Pruebas de dominio de la fábrica, integración de la creación de 30 y 60 minutos, prueba concurrente de dos confirmaciones sobre el mismo slot contra MySQL, y pruebas de cada rechazo con verificación de ausencia de filas parciales.

## Criterios de aceptación

### CA-01 — Cita general creada APPROVED

**Dado** un USER autenticado, un profesional activo asociado a `Medicina General` (30 minutos) y un slot libre futuro de ese profesional en una sede asignada  
**Cuando** el usuario confirma la reserva de ese slot  
**Entonces** la API responde con éxito, la cita queda persistida en estado `APPROVED` a nombre del usuario autenticado, con la fecha, la hora de inicio y la hora de fin del slot, sin ninguna acción de ADMIN (RN-02).

### CA-02 — Los slots quedan reservados y dejan de ofrecerse

**Dado** una cita general recién creada  
**Cuando** cualquier usuario busca disponibilidad que incluiría esa franja  
**Entonces** los slots de la cita existen en `slot_reservations` asociados a ella y la franja ya no aparece en la búsqueda (RN-01).

### CA-03 — Doble reserva rechazada con 409

**Dado** un slot ya reservado por otra cita o retenido por una solicitud de reprogramación `PENDING`  
**Cuando** un usuario confirma una cita general sobre ese slot  
**Entonces** la API responde 409 indicando que la franja ya no está disponible, y no se crea cita, reserva ni registro de historial.

### CA-04 — Confirmaciones concurrentes: solo una tiene éxito

**Dado** dos usuarios que confirman simultáneamente una cita general sobre el mismo slot libre  
**Cuando** ambas peticiones se procesan  
**Entonces** exactamente una crea la cita y la otra recibe 409, y en base de datos existe una única reserva para ese slot.

### CA-05 — Especialidad de 60 minutos reserva dos slots consecutivos

**Dado** una especialidad general de 60 minutos y un slot inicial libre cuyo slot consecutivo del mismo bloque está libre  
**Cuando** el usuario confirma la cita  
**Entonces** se reservan ambos slots con `slot_order` 1 y 2 y la cita dura 60 minutos; y **dado** que el consecutivo está ocupado o no existe, la API rechaza la reserva sin reservar el primer slot (RN-05).

### CA-06 — Cita en el pasado rechazada

**Dado** un slot cuya hora de inicio ya pasó respecto del instante de la petición  
**Cuando** el usuario intenta reservarlo  
**Entonces** la API responde con un error de regla de negocio y no se persiste nada (RN-06).

### CA-07 — Especialidad no general, inactiva o no asociada rechazada

**Dado** una especialidad de tipo `SPECIALIZED`, una especialidad general desactivada y un profesional no asociado a la especialidad enviada  
**Cuando** el usuario intenta crear una cita general con cualquiera de esas combinaciones  
**Entonces** la API responde con un error de regla de negocio y no se persiste nada; en el caso especializado el error indica que debe usarse el flujo de [[HU-024-solicitar-cita-especializada]] (RN-08).

### CA-08 — Historial inicial con origen SYSTEM

**Dado** una cita general creada correctamente  
**Cuando** se consulta su historial de estados  
**Entonces** existe exactamente un registro con la cita, el estado `APPROVED`, el origen `SYSTEM` y la fecha y hora de creación (RF-19).

### CA-09 — Solo USER agenda, y siempre para sí mismo

**Dado** un usuario con rol `PROFESSIONAL` o `ADMIN`, y un USER que envía en el cuerpo el identificador de otro paciente  
**Cuando** invocan la creación de cita general  
**Entonces** el primero recibe un error de autorización, y la cita del segundo, si se crea, queda a nombre del usuario autenticado y nunca del identificador recibido.

## Definition of Done

- [x] Los criterios CA-01 a CA-09 están validados con evidencia concreta.
- [x] La no-doble-reserva descansa en la PK `slot_id` de `slot_reservations` y está demostrada con una prueba concurrente contra MySQL 8.4, no solo con un mock.
- [x] La violación de la PK se traduce a 409 y la transacción revierte cita, reservas e historial: no quedan filas parciales.
- [x] La creación de la cita general es una operación explícita del dominio que produce `APPROVED`; el estado no se asigna desde el adaptador REST.
- [x] El historial se escribe mediante el puerto de [[HU-032-auditar-cambios-de-estado-de-cita]] en la misma transacción.
- [x] Se reutiliza la regla de franja ofrecible de [[HU-022-buscar-disponibilidad-con-filtros]] para validar consecutividad y futuro.
- [x] No se crean migraciones salvo cambio de esquema justificado, en una migración Flyway posterior a V4.
- [x] El endpoint exige rol `USER` y toma al paciente del contexto de autenticación.
- [x] El flujo de `citas-web` muestra la cita aprobada y trata el 409 con un mensaje comprensible y la opción de volver a buscar.
- [x] Existen pruebas automatizadas de 30 y 60 minutos, concurrencia, pasado, especialidad no general, inactiva o no asociada y rol, y pasan.
- [x] El contrato del endpoint de cita general está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [x] La trazabilidad de esta HU y de [[EP-006-busqueda-de-disponibilidad-y-reserva]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Ejecución de referencia del **2026-09-23**: backend `docker compose run --rm citas-api-dev mvn -B test` → **242 pruebas, 0 fallos** en 22 clases; frontend `npm test` en `citas-web` → **88 pruebas, 0 fallos**, con `typecheck`, `lint` y `build` en verde. La verificación fue independiente (`backend-verifier` y `frontend-verifier`, agentes que no escribieron el código): `EVIDENCIAS_S3.md` §10. Las clases de prueba del backend cuelgan de `citas-api/src/test/java/com/fcv/citas/`.

Además de las pruebas, la no-doble-reserva se ejercitó **contra la API real** el 2026-09-23, con dos clientes HTTP compitiendo por la misma franja sobre un Tomcat y una MySQL reales: `EVIDENCIAS_S3.md` §11.

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | `infrastructure/rest/BookingIntegrationTest#generalAppointmentIsApprovedReservedAndAudited` | 201 con `status = APPROVED`, 08:30–09:00, 30 minutos, sede y especialidad; en base, `patient_user_id` es el del token. Ninguna acción de ADMIN interviene |
| CA-02 | Cumple | `BookingIntegrationTest#generalAppointmentIsApprovedReservedAndAudited` | Una fila en `slot_reservations` para la cita, y la búsqueda deja de ofrecer las 08:30 mientras sigue ofreciendo las 09:00 |
| CA-03 | Cumple | `BookingIntegrationTest#doubleBookingIsRejectedWith409` (409 `SLOT_TAKEN`; una sola cita y una sola fila de historial); `EVIDENCIAS_S3.md` §11 caso 2, reintento secuencial contra la API real | La rama del slot retenido por una **reprogramación** pendiente no es ejercitable en S3: no existe productor de retenciones de tipo `RESCHEDULE_REQUEST` (llega con [[HU-027-solicitar-reprogramacion-de-cita-aprobada]]). El criterio queda cubierto por su otra rama y el mecanismo es común: la clave primaria `slot_id` de `slot_reservations` no mira el motivo de la retención |
| CA-04 | Cumple | `BookingIntegrationTest#concurrentBookingsOfTheSameSlotLetExactlyOneWin` (8 hilos: uno responde 201 y siete responden 409 con `code = SLOT_TAKEN`; una cita y una fila en `slot_reservations`); `EVIDENCIAS_S3.md` §11 caso 1, dos pacientes reales lanzados a la vez | La prueba afirma el `code` y no solo el estado HTTP, para que quitar el bloqueo pesimista rompa una prueba en vez de degradar el contrato a `CONCURRENT_CHANGE` ([[dec-003-libro-unico-slot-reservations]]) |
| CA-05 | Cumple | `infrastructure/rest/VerificationGapsIntegrationTest#generalSixtyMinutesReservesTwoConsecutiveSlots` (especialidad GENERAL de 60 minutos: fin a las 09:30 y `slot_order` 1 y 2); `BookingIntegrationTest#sixtyMinutesWithTakenSecondSlotRetainsNothing` y `#sixtyMinutesMustFitInsideTheBlock` (422 `SLOT_NOT_AVAILABLE`) | Las dos últimas corren sobre el flujo especializado; ambos flujos entran por el mismo `BookAppointmentUseCase#book`, que resuelve los slots antes de distinguir el flujo |
| CA-06 | Cumple | `BookingIntegrationTest#pastSlotCannotBeBooked` (422 `PAST_TIME`, cero citas del profesional) | El instante de comparación se toma en `America/Bogota` (`domain/shared/SystemZone`) |
| CA-07 | Cumple | `BookingIntegrationTest#wrongFlowIsRejectedBothWays` (422 `WRONG_FLOW`, con un `detail` que remite al flujo especializado); `VerificationGapsIntegrationTest#bookingWithAnInactiveSpecialtyIsRejected` (422 `SPECIALTY_INACTIVE`, cero citas); `BookingIntegrationTest#specialtyNotAssignedToTheProfessionalIsRejected` (422 `SPECIALTY_NOT_ASSIGNED`) | Los tres casos del criterio, cada uno con su código propio |
| CA-08 | Cumple | `BookingIntegrationTest#generalAppointmentIsApprovedReservedAndAudited` (una única fila de historial, con `source = SYSTEM`, `actor_user_id` nulo y estado `APPROVED`); `domain/appointment/AppointmentTest#generalIsBornApprovedWithSystemHistoryWithoutActor` | La fecha y hora del cambio las pone MySQL al insertar (`StatusHistoryJpaEntity`) |
| CA-09 | Cumple | `BookingIntegrationTest#onlyUsersBookAndAlwaysForThemselves` | Un PROFESSIONAL recibe 403. Un USER que manda `patientUserId` en el cuerpo crea la cita **a su propio nombre**: ese campo no existe en `BookingRequest` y se descarta |
| DoD — CA-01 a CA-09 validados con evidencia concreta | Cumple | Filas CA-01 a CA-09 de esta tabla | — |
| DoD — La no-doble-reserva descansa en la PK `slot_id`, con prueba concurrente contra MySQL 8.4 | Cumple | `V3__schedule_and_appointments.sql` (clave primaria sobre `slot_id` en `slot_reservations`); `BookingIntegrationTest#concurrentBookingsOfTheSameSlotLetExactlyOneWin`; prueba de mutación de `SlotReservationJpaEntity#isNew()` en `EVIDENCIAS_S3.md` §7; `EVIDENCIAS_S3.md` §11 | No es un mock: las pruebas de integración corren contra MySQL 8.4. En el camino de escritura no hay ninguna comprobación previa de «slot libre»: la única barrera es la clave primaria |
| DoD — La violación de la PK se traduce a 409 y la transacción revierte cita, reservas e historial | Cumple | `BookingIntegrationTest#doubleBookingIsRejectedWith409` (tras el intento fallido siguen existiendo una cita y una fila de historial); `#sixtyMinutesWithTakenSecondSlotRetainsNothing` (el primer slot no queda retenido) | — |
| DoD — La creación de la cita general es una operación explícita del dominio | Cumple | `domain/appointment/Appointment#bookGeneral` devuelve la cita ya `APPROVED` junto con su `StatusChange`; `AppointmentTest#generalIsBornApprovedWithSystemHistoryWithoutActor`; `infrastructure/rest/appointment/PatientBookingController` no asigna estado | — |
| DoD — El historial se escribe por el puerto de [[HU-032-auditar-cambios-de-estado-de-cita]] en la misma transacción | Cumple | `domain/appointment/AppointmentRepository#create(Appointment, StatusChange)`, invocado dentro del `tx.inTransaction` de `BookAppointmentUseCase#book` | — |
| DoD — Se reutiliza la regla de franja ofrecible de [[HU-022-buscar-disponibilidad-con-filtros]] para consecutividad y futuro | Cumple | `domain/schedule/AvailabilityBlock#canHost`, invocada por `BookAppointmentUseCase#book`; `domain/schedule/AvailabilityBlockTest#sixtyMinutesNeedsTheNextSlotInsideTheSameBlock` | El camino de escritura sí usa la regla del dominio. La búsqueda la reimplementa en SQL, lo que se registra como defecto en la matriz de HU-022, no aquí |
| DoD — Sin migraciones salvo cambio de esquema justificado | Cumple | Se usan `appointments`, `slot_reservations` y `appointment_status_history` de V3; las migraciones posteriores (V5 a V7) son de otras HU | — |
| DoD — El endpoint exige rol USER y toma al paciente del contexto de autenticación | Cumple | `SecurityConfig` (prefijo `/api/patient/**` con `hasRole("USER")`); `infrastructure/rest/AuthorizationIntegrationTest#onlyUserReachesPatientRoutes`; `PatientBookingController#bookGeneral` con `CurrentUser.id(auth)`; `BookingIntegrationTest#onlyUsersBookAndAlwaysForThemselves` | — |
| DoD — `citas-web` muestra la cita aprobada y trata el 409 con mensaje comprensible y reintento | Cumple | `citas-web/src/patientBooking.test.tsx`: «cita general: preselecciona Medicina General, resalta los días con cupo y confirma al instante», «409 SLOT_TAKEN: avisa, recarga las franjas y deja elegir otra hasta enviar la solicitud» y «F4: tras un 409 con filtro de profesional, el filtro se reinicia y no queda una lista vacía» | — |
| DoD — Pruebas de 30 y 60 minutos, concurrencia, pasado, especialidad y rol | Cumple | `BookingIntegrationTest` (17 pruebas); `VerificationGapsIntegrationTest#generalSixtyMinutesReservesTwoConsecutiveSlots` y `#bookingWithAnInactiveSpecialtyIsRejected`; `AppointmentTest` (8); `AvailabilityBlockTest` (8) | — |
| DoD — Contrato del endpoint de cita general reflejado en [[HU-033-publicar-contrato-rest-documentado]] | Cumple | `citas-api/docs/wiki/llm-wiki/wiki/contrato-rest-citas.md`, sección «Reserva — USER (HU-022 a HU-025)»: ruta, cuerpo, 201 y catálogo de errores | — |
| DoD — Trazabilidad de esta HU y de [[EP-006-busqueda-de-disponibilidad-y-reserva]] actualizada | Cumple | Esta tabla y el historial de validación | — |

## Historial de validación

- 2026-09-23 — Estado `Completada` (fase F11 de `PLAN_RETOMA_S3.md`): matriz de evidencia completa, los 9 criterios y los 12 ítems de DoD en `Cumple`. Se deja anotado en «Notas y decisiones» un riesgo no bloqueante de la verificación independiente sobre `slot_reservations`. Cierre dentro de la aprobación delegada de S3 (`AGENTS.md` §6); el usuario puede reabrirla.
- 2026-09-23 — Estado `En validación` (skill `scrum-spec-orchestrator`, paso 10): se recolecta del repositorio la evidencia de cada criterio y de cada ítem de DoD, incluida la ejecución contra la API real de `EVIDENCIAS_S3.md` §11.
- 2026-09-18 — Estado `En desarrollo` (skill `scrum-spec-orchestrator`, paso 9): se inicia la implementación en la fase F5 de `PLAN_RETOMA_S3.md`.
- 2026-09-18 — Estado `Aprobada` por **aprobación delegada** de S3: el usuario pidió continuar S3 dejando las decisiones de diseño a criterio del agente (`AGENTS.md` §6). Alcance y decisiones D5–D13 en `PLAN_RETOMA_S3.md` y [[dec-004-decisiones-s3-reserva]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- Incógnita abierta **INC-008** (ver [[EP-002-perfil-y-afiliacion-del-paciente]]): el PRD no define si la afiliación es obligatoria. `appointments.affiliation_id` es nullable; esta HU no exige afiliación ni fija cómo se asocia la vigente.
- Incógnita abierta **INC-022** (ver [[EP-006-busqueda-de-disponibilidad-y-reserva]]): no hay antelación mínima; CA-06 solo prohíbe el pasado.
- Incógnita abierta **INC-025** (ver [[EP-006-busqueda-de-disponibilidad-y-reserva]]): el PRD no prohíbe que un mismo usuario tenga dos citas solapadas entre sí con profesionales distintos. Esta HU no lo impide.
- Incógnita abierta **INC-026** (ver [[EP-006-busqueda-de-disponibilidad-y-reserva]]): no hay asignación automática de profesional general.
- El registro de historial con origen `SYSTEM` no identifica actor (`actor_user_id` nulo, permitido por la restricción de V3). No está definido si además debe conservarse qué USER originó la reserva; el titular ya consta en `appointments.patient_user_id`.
- El PRD no define si dos slots consecutivos pueden pertenecer a bloques contiguos; se sigue la misma suposición que [[HU-022-buscar-disponibilidad-con-filtros]] (mismo bloque).
- Riesgo no bloqueante detectado en la verificación independiente del 2026-09-23 (registrado también en `wiki/log.md` y en [[dec-003-libro-unico-slot-reservations]]): **`appointments` no tiene ninguna restricción que obligue a una cita a tener filas en `slot_reservations`.** La clave primaria `slot_id` garantiza que una franja no se ocupe dos veces, pero que **toda cita ocupe la suya** lo garantiza hoy el código, porque `BookAppointmentUseCase#book` es el único camino de creación y escribe cita y reservas en la misma transacción. Si apareciera un segundo camino de alta, la base no lo impediría. No afecta a ningún criterio de esta HU; queda como deuda de esquema a evaluar con el usuario.
