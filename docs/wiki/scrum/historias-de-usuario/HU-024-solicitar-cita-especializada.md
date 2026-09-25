---
id: HU-024
tipo: historia-de-usuario
titulo: "Solicitar cita especializada"
estado: Completada
epica: "[[EP-006-busqueda-de-disponibilidad-y-reserva]]"
requisitos: [RF-12, RF-19]
esfuerzo: "Alto"
sprint_sugerido: "Sprint 5"
dependencias:
  - "[[HU-022-buscar-disponibilidad-con-filtros]]"
  - "[[HU-023-agendar-cita-de-medicina-general]]"
  - "[[HU-032-auditar-cambios-de-estado-de-cita]]"
relacionadas:
  - "[[HU-029-consultar-bandeja-administrativa]]"
  - "[[HU-030-aprobar-o-rechazar-cita-especializada]]"
  - "[[HU-025-consultar-mis-citas-y-detalle]]"
  - "[[HU-009-registrar-afiliacion-a-eps-y-plan]]"
---

# HU-024 — Solicitar cita especializada

## Historia de usuario

**COMO** USER autenticado  
**QUIERO** solicitar una cita especializada eligiendo especialidad, sede, profesional y horario  
**PARA** asegurar esa franja mientras un administrador decide si la aprueba

> Como USER autenticado, quiero solicitar una cita especializada eligiendo especialidad, sede, profesional y horario para asegurar esa franja mientras un administrador decide si la aprueba.

## Contexto y descripción

RF-12 define el flujo especializado: el usuario selecciona especialidad, sede, profesional y horario; la solicitud nace `REQUESTED` y el horario queda retenido para evitar doble reserva. La decisión posterior corresponde a ADMIN (RN-03) y se cubre en [[HU-030-aprobar-o-rechazar-cita-especializada]], a partir de la bandeja de [[HU-029-consultar-bandeja-administrativa]].

Técnicamente es el mismo mecanismo que [[HU-023-agendar-cita-de-medicina-general]]: la retención se materializa como filas de tipo `APPOINTMENT` en `slot_reservations`, cuya PK `slot_id` impide que otra cita o retención tome la misma franja. Lo que cambia es la política: tipo `SPECIALIZED`, estado inicial `REQUESTED` y registro en el historial con origen `USER` y el paciente como actor. Por eso esta HU depende de HU-023 y reutiliza su puerto de reserva en lugar de duplicarlo.

## Alcance

- Endpoint REST de solicitud de cita especializada en `citas-api` para el rol `USER`.
- Validación de especialidad de tipo `SPECIALIZED`, activa y asociada al profesional; profesional activo; sede asignada; slots futuros y consecutivos según duración.
- Creación de la cita en estado `REQUESTED`.
- Retención de 1 o 2 slots en `slot_reservations` reutilizando el puerto de reserva de HU-023.
- Registro del estado inicial en `appointment_status_history` con origen `USER` y actor el paciente.
- Todo en una única transacción; conflicto 409 ante doble reserva.
- Flujo especializado de la pantalla "solicitar cita" en `citas-web`, que comunica que la cita queda pendiente de aprobación.

## Fuera de alcance

- Aprobación o rechazo, que se cubre en [[HU-030-aprobar-o-rechazar-cita-especializada]].
- Visualización en la bandeja administrativa, que se cubre en [[HU-029-consultar-bandeja-administrativa]].
- Caducidad automática de la retención: pendiente de INC-024.
- Cancelación de la solicitud por el usuario: se trata en [[HU-026-cancelar-una-cita-futura]] (INC-030).
- Autorizaciones de EPS, pagos y facturación (PRD §9).

## Reglas de negocio

- El usuario selecciona especialidad, sede, profesional y horario (RF-12).
- Las citas especializadas nacen `REQUESTED` y requieren decisión de ADMIN (RF-12, RN-03).
- El horario queda retenido mientras la solicitud está `REQUESTED` (RF-12, RN-01).
- 60 minutos = 2 slots consecutivos disponibles; 30 minutos = 1 slot (RF-09, RN-05).
- No se permiten citas en el pasado (RN-06).
- La especialidad debe estar activa y asociada al profesional (RN-08); el profesional debe estar activo y la sede asignada (RN-07).
- La creación registra el estado inicial con origen `USER` y el paciente como actor (RF-19, EP-006).
- La solicitud pertenece al usuario autenticado (PRD §8).

## Dependencias y relaciones

- Épica: [[EP-006-busqueda-de-disponibilidad-y-reserva]]
- Dependencias: [[HU-022-buscar-disponibilidad-con-filtros]], [[HU-023-agendar-cita-de-medicina-general]], [[HU-032-auditar-cambios-de-estado-de-cita]]
- Relacionadas: [[HU-029-consultar-bandeja-administrativa]], [[HU-030-aprobar-o-rechazar-cita-especializada]], [[HU-025-consultar-mis-citas-y-detalle]], [[HU-026-cancelar-una-cita-futura]], [[HU-009-registrar-afiliacion-a-eps-y-plan]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Alto

**Justificación de dificultad:** Reutiliza el mecanismo de reserva de HU-023, pero introduce el primer estado no definitivo que ocupa agenda: una retención que bloquea oferta real hasta una decisión humana. Debe garantizar la misma atomicidad y el mismo comportamiento concurrente, distinguir la política de aprobación por tipo de especialidad sin duplicar lógica y registrar un actor humano en el historial.

## Tareas de desarrollo

- [ ] **T-01 — Extender el agregado cita con la creación especializada**  
  Dificultad: Medio  
  Descripción: Fábrica de dominio que exige tipo `SPECIALIZED`, franja futura y slots coherentes con la duración, y produce el estado `REQUESTED` y el evento de transición inicial con origen `USER`, compartiendo las validaciones comunes con la creación general.

- [ ] **T-02 — Implementar el caso de uso de solicitud especializada**  
  Dificultad: Alto  
  Descripción: Caso de uso que resuelve el paciente desde la autenticación, valida profesional, especialidad, sede, futuro y consecutividad, persiste la cita, retiene los slots con el puerto de reserva existente y registra el historial en una única transacción.

- [ ] **T-03 — Exponer el adaptador REST de solicitud especializada**  
  Dificultad: Medio  
  Descripción: Endpoint restringido a `USER` con DTO validado (especialidad, sede, profesional, slot inicial) y respuestas diferenciadas: creación con la cita `REQUESTED`, 409 por franja no disponible, y errores de regla de negocio para pasado, no consecutivos, especialidad general, inactiva o no asociada y profesional inactivo.

- [ ] **T-04 — Construir el flujo de solicitud especializada en citas-web**  
  Dificultad: Medio  
  Descripción: Confirmación desde el resultado de búsqueda con aviso explícito de que la cita queda pendiente de aprobación, presentación del estado `REQUESTED` y tratamiento del 409.

- [ ] **T-05 — Pruebas de solicitud especializada y retención**  
  Dificultad: Alto  
  Descripción: Pruebas de dominio de la fábrica, integración de 30 y 60 minutos, concurrencia entre una solicitud especializada y una cita general sobre el mismo slot, y rechazos con verificación de ausencia de filas parciales.

## Criterios de aceptación

### CA-01 — Solicitud creada en estado REQUESTED

**Dado** un USER autenticado, un profesional activo asociado a una especialidad `SPECIALIZED` activa y un slot libre futuro en una sede asignada  
**Cuando** el usuario confirma la solicitud indicando especialidad, sede, profesional y horario  
**Entonces** la API responde con éxito y la cita queda persistida en estado `REQUESTED` a nombre del usuario autenticado, con la fecha y horas de la franja.

### CA-02 — La franja queda retenida

**Dado** una cita especializada en estado `REQUESTED`  
**Cuando** otro usuario busca disponibilidad o intenta reservar esa franja por cualquiera de los dos flujos  
**Entonces** la franja no aparece en la búsqueda y el intento de reserva recibe 409 (RF-12, RN-01).

### CA-03 — Doble reserva rechazada con 409, también en concurrencia

**Dado** dos usuarios que confirman simultáneamente una solicitud especializada y una cita general sobre el mismo slot libre, o una solicitud sobre un slot ya reservado  
**Cuando** las peticiones se procesan  
**Entonces** solo una operación tiene éxito, las demás reciben 409, existe una única reserva para el slot y no quedan citas ni historial de las operaciones rechazadas.

### CA-04 — Especialidad de 60 minutos retiene dos slots consecutivos

**Dado** una especialidad `SPECIALIZED` de 60 minutos y un slot inicial libre con su consecutivo del mismo bloque libre  
**Cuando** el usuario confirma la solicitud  
**Entonces** se retienen ambos slots con `slot_order` 1 y 2; y **dado** que el consecutivo está ocupado o no existe, la API rechaza la solicitud sin retener el primer slot (RN-05).

### CA-05 — Solicitud en el pasado rechazada

**Dado** un slot cuya hora de inicio ya pasó respecto del instante de la petición  
**Cuando** el usuario intenta solicitarlo  
**Entonces** la API responde con un error de regla de negocio y no se persiste nada (RN-06).

### CA-06 — Especialidad general, inactiva o no asociada rechazada

**Dado** una especialidad de tipo `GENERAL`, una especialidad especializada desactivada y un profesional no asociado a la especialidad enviada  
**Cuando** el usuario intenta crear una solicitud especializada con cualquiera de esas combinaciones  
**Entonces** la API responde con un error de regla de negocio y no se persiste nada; en el caso general el error indica que debe usarse el flujo de [[HU-023-agendar-cita-de-medicina-general]] (RN-08).

### CA-07 — Historial inicial con origen USER y actor

**Dado** una solicitud especializada creada correctamente  
**Cuando** se consulta su historial de estados  
**Entonces** existe exactamente un registro con la cita, el estado `REQUESTED`, el identificador del paciente como actor, el origen `USER` y la fecha y hora de creación (RF-19).

### CA-08 — La solicitud no se aprueba sola

**Dado** una solicitud especializada recién creada  
**Cuando** transcurre el procesamiento completo de la petición sin intervención de ADMIN  
**Entonces** la cita permanece en `REQUESTED` y no existe ningún registro de historial `APPROVED` para ella (RN-03).

### CA-09 — Solo USER solicita, y siempre para sí mismo

**Dado** un usuario con rol `PROFESSIONAL` o `ADMIN`, y un USER que envía en el cuerpo el identificador de otro paciente  
**Cuando** invocan la solicitud especializada  
**Entonces** el primero recibe un error de autorización, y la solicitud del segundo, si se crea, queda a nombre del usuario autenticado.

## Definition of Done

- [x] Los criterios CA-01 a CA-09 están validados con evidencia concreta.
- [x] La retención usa el mismo puerto y la misma tabla `slot_reservations` que [[HU-023-agendar-cita-de-medicina-general]]; no existe un segundo mecanismo de reserva.
- [x] La no-doble-reserva entre flujo general y especializado está demostrada con una prueba concurrente contra MySQL 8.4.
- [x] Cita, retención e historial se escriben en una única transacción; un 409 no deja filas parciales.
- [x] La política "especializada nace `REQUESTED`" se decide en el dominio a partir del tipo de especialidad y no en el adaptador REST.
- [x] El historial se escribe mediante el puerto de [[HU-032-auditar-cambios-de-estado-de-cita]] con origen `USER` y actor.
- [x] No se crean migraciones salvo cambio de esquema justificado, en una migración Flyway posterior a V4.
- [x] El flujo de `citas-web` informa que la cita está pendiente de aprobación y trata el 409.
- [x] Existen pruebas automatizadas de 30 y 60 minutos, concurrencia, pasado, especialidad incorrecta y rol, y pasan.
- [x] El contrato del endpoint de solicitud especializada está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [x] La trazabilidad de esta HU y de [[EP-006-busqueda-de-disponibilidad-y-reserva]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Ejecución de referencia del **2026-09-23**: backend `docker compose run --rm citas-api-dev mvn -B test` → **242 pruebas, 0 fallos** en 22 clases; frontend `npm test` en `citas-web` → **88 pruebas, 0 fallos**, con `typecheck`, `lint` y `build` en verde. La verificación fue independiente (`backend-verifier` y `frontend-verifier`, agentes que no escribieron el código): `EVIDENCIAS_S3.md` §10. Las clases de prueba del backend cuelgan de `citas-api/src/test/java/com/fcv/citas/`.

Esta es la HU de **GOAL_02**. Además de las pruebas, la solicitud especializada y su retención se ejercitaron contra la API real el 2026-09-23: `EVIDENCIAS_S3.md` §11 caso 3 y §8 (prueba de humo de extremo a extremo, 29 de 29).

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | `infrastructure/rest/BookingIntegrationTest#specializedRequestRetainsTwoConsecutiveSlots` | 201 con `status = REQUESTED`, fin a las 09:30 y 60 minutos; el titular es el usuario del token |
| CA-02 | Cumple | `BookingIntegrationTest#specializedRequestRetainsTwoConsecutiveSlots` | Con la solicitud viva, la búsqueda deja de ofrecer las 08:30 y otro paciente que intenta la misma franja recibe 409 `SLOT_TAKEN`. La exclusión en la búsqueda la produce `JdbcAvailabilityQueries`, que descarta todo slot con fila en `slot_reservations` |
| CA-03 | Cumple | `BookingIntegrationTest#concurrentGeneralAndSpecializedOnTheSameSlotLetExactlyOneWin` (un profesional con una especialidad general y otra especializada, ambas de 30 minutos; dos pacientes lanzan `/general` y `/specialized` a la vez: uno responde 201 y el otro 409 `SLOT_TAKEN`, una sola fila para ese slot y una sola cita); `#doubleBookingIsRejectedWith409`; `EVIDENCIAS_S3.md` §11 caso 3 | La prueba no asume quién gana: ramifica según el ganador leído de la base y exige `APPROVED` con historial `SYSTEM` sin actor, o `REQUESTED` con historial `USER` y el paciente como actor. En tres ejecuciones ganó el general dos veces y el especializado una |
| CA-04 | Cumple | `BookingIntegrationTest#specializedRequestRetainsTwoConsecutiveSlots` (`slot_order` 1 y 2); `#sixtyMinutesWithTakenSecondSlotRetainsNothing` (con el consecutivo ocupado, el primer slot no queda retenido); `#sixtyMinutesMustFitInsideTheBlock` (422 `SLOT_NOT_AVAILABLE` si el consecutivo no existe dentro del bloque, decisión D9) | — |
| CA-05 | Cumple | `BookingIntegrationTest#pastSlotCannotBeBooked` (422 `PAST_TIME`, cero citas) | La prueba usa el flujo general; la comprobación de pasado está en `BookAppointmentUseCase#book`, antes de bifurcar por flujo, así que es la misma para los dos. Equivalencia verificada por lectura del código |
| CA-06 | Cumple | `BookingIntegrationTest#wrongFlowIsRejectedBothWays` (una especialidad general por la ruta especializada → 422 `WRONG_FLOW`, con `detail` que remite al flujo general); `infrastructure/rest/VerificationGapsIntegrationTest#bookingWithAnInactiveSpecialtyIsRejected` (especializada desactivada → 422 `SPECIALTY_INACTIVE`, cero citas); `BookingIntegrationTest#specialtyNotAssignedToTheProfessionalIsRejected` (422 `SPECIALTY_NOT_ASSIGNED`) | Los tres casos se ejercitan sobre la ruta especializada |
| CA-07 | Cumple | `BookingIntegrationTest#specializedRequestRetainsTwoConsecutiveSlots` (una única fila con `source = USER` y `actor_user_id` igual al paciente); `domain/appointment/AppointmentTest#specializedIsBornRequestedWithThePatientAsActor` | La fecha y hora las pone MySQL al insertar |
| CA-08 | Cumple | `BookingIntegrationTest#specializedRequestRetainsTwoConsecutiveSlots`: tras la petición completa, cero filas de historial con estado `APPROVED` para esa cita | Ninguna aprobación automática (RN-03) |
| CA-09 | Cumple | `BookingIntegrationTest#onlyUsersBookAndAlwaysForThemselves`; `infrastructure/rest/AuthorizationIntegrationTest#onlyUserReachesPatientRoutes` (PROFESSIONAL y ADMIN → 403) | La prueba ejercita el flujo general; las dos rutas están bajo el mismo prefijo `/api/patient/**` y `PatientBookingController#requestSpecialized` toma el paciente de `CurrentUser.id(auth)`, no del cuerpo, que no tiene ese campo |
| DoD — CA-01 a CA-09 validados con evidencia concreta | Cumple | Filas CA-01 a CA-09 de esta tabla | — |
| DoD — La retención usa el mismo puerto y la misma tabla que [[HU-023-agendar-cita-de-medicina-general]] | Cumple | `domain/appointment/AppointmentRepository#reserveSlots`, único método de retención, invocado por `BookAppointmentUseCase#book` para los dos flujos; `infrastructure/persistence/appointment/SlotReservationJpaEntity` | No hay un segundo mecanismo de reserva: [[dec-003-libro-unico-slot-reservations]] |
| DoD — No-doble-reserva entre flujo general y especializado, demostrada contra MySQL 8.4 | Cumple | `BookingIntegrationTest#concurrentGeneralAndSpecializedOnTheSameSlotLetExactlyOneWin`; `EVIDENCIAS_S3.md` §11, apartado «Huecos de evidencia cerrados el 2026-09-23» | Este hueco lo señaló la verificación independiente y se cerró el 2026-09-23 sin tocar `src/main`: faltaba la prueba, no el comportamiento |
| DoD — Cita, retención e historial en una única transacción; un 409 no deja filas parciales | Cumple | `BookAppointmentUseCase#book` (`tx.inTransaction`); `BookingIntegrationTest#sixtyMinutesWithTakenSecondSlotRetainsNothing` y `#doubleBookingIsRejectedWith409` | — |
| DoD — «Especializada nace REQUESTED» se decide en el dominio, no en el adaptador REST | Cumple | `domain/appointment/Appointment#requestSpecialized`; la bifurcación se hace por `Specialty#appointmentType` en `BookAppointmentUseCase#book`; `AppointmentTest#specializedIsBornRequestedWithThePatientAsActor` | El controlador no asigna estados |
| DoD — El historial se escribe por el puerto de [[HU-032-auditar-cambios-de-estado-de-cita]] con origen `USER` y actor | Cumple | `AppointmentRepository#create(Appointment, StatusChange)`; `domain/appointment/StatusChange` rechaza un origen distinto de `SYSTEM` sin actor (`AppointmentTest#nonSystemChangesRequireAnActor`) | — |
| DoD — Sin migraciones salvo cambio de esquema justificado | Cumple | Se usan `appointments`, `slot_reservations` y `appointment_status_history` de V3 | — |
| DoD — `citas-web` informa de que la cita queda pendiente de aprobación y trata el 409 | Cumple | `citas-web/src/patientBooking.test.tsx`: «409 SLOT_TAKEN: avisa, recarga las franjas y deja elegir otra hasta enviar la solicitud» y «422: muestra el detail del servidor y se queda en la confirmación»; `citas-web/src/pages/patient/booking/BookingPage.tsx`; `EVIDENCIAS_S3.md` §9, paso 10 de la guía manual | La insignia de estado usa `statusName` del backend («Solicitada») |
| DoD — Pruebas de 30 y 60 minutos, concurrencia, pasado, especialidad incorrecta y rol | Cumple | `BookingIntegrationTest` (17 pruebas); `VerificationGapsIntegrationTest#bookingWithAnInactiveSpecialtyIsRejected`; `AppointmentTest` (8); `AvailabilityBlockTest` (8) | — |
| DoD — Contrato del endpoint especializado reflejado en [[HU-033-publicar-contrato-rest-documentado]] | Cumple | `citas-api/docs/wiki/llm-wiki/wiki/contrato-rest-citas.md`, sección «Reserva — USER (HU-022 a HU-025)» | — |
| DoD — Trazabilidad de esta HU y de [[EP-006-busqueda-de-disponibilidad-y-reserva]] actualizada | Cumple | Esta tabla y el historial de validación | — |

## Historial de validación

- 2026-09-23 — Estado `Completada` (fase F11 de `PLAN_RETOMA_S3.md`): matriz de evidencia completa, los 9 criterios y los 11 ítems de DoD en `Cumple`, incluida la concurrencia cruzada general ↔ especializada que faltaba. Se deja anotado en «Notas y decisiones» un riesgo no bloqueante sobre `slot_reservations`. Cierre dentro de la aprobación delegada de S3 (`AGENTS.md` §6); el usuario puede reabrirla.
- 2026-09-23 — Estado `En validación` (skill `scrum-spec-orchestrator`, paso 10): se recolecta del repositorio la evidencia de cada criterio y de cada ítem de DoD, incluida la ejecución contra la API real de `EVIDENCIAS_S3.md` §11.
- 2026-09-18 — Estado `En desarrollo` (skill `scrum-spec-orchestrator`, paso 9): se inicia la implementación en la fase F5 de `PLAN_RETOMA_S3.md`.
- 2026-09-18 — Estado `Aprobada` por **aprobación delegada** de S3: el usuario pidió continuar S3 dejando las decisiones de diseño a criterio del agente (`AGENTS.md` §6). Alcance y decisiones D5–D13 en `PLAN_RETOMA_S3.md` y [[dec-004-decisiones-s3-reserva]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- Incógnita abierta **INC-024** (ver [[EP-006-busqueda-de-disponibilidad-y-reserva]]): el PRD no define si la retención de una cita `REQUESTED` caduca sin decisión de ADMIN. Esta HU mantiene la retención indefinidamente hasta la decisión; es un riesgo real de bloqueo de agenda.
- Incógnita abierta **INC-025** (ver [[EP-006-busqueda-de-disponibilidad-y-reserva]]): no hay límite de solicitudes `REQUESTED` simultáneas por usuario ni prohibición de solapes entre citas propias.
- Incógnita abierta **INC-008** (ver [[EP-002-perfil-y-afiliacion-del-paciente]]): la afiliación no se exige ni se asocia obligatoriamente; si se decidiera obligatoria para citas especializadas, se añadiría un criterio de bloqueo aquí.
- Incógnita abierta **INC-036** (ver [[EP-008-operacion-administrativa-de-solicitudes]]): una solicitud cuya fecha pasa sin decisión conserva su retención; el tratamiento corresponde a la operación administrativa.
- Riesgo no bloqueante detectado en la verificación independiente del 2026-09-23 (registrado también en `wiki/log.md` y en [[dec-003-libro-unico-slot-reservations]]): **`appointments` no tiene ninguna restricción que obligue a una cita a tener filas en `slot_reservations`.** La clave primaria `slot_id` garantiza que una franja no se ocupe dos veces, pero que toda solicitud retenga la suya lo garantiza hoy el código, porque `BookAppointmentUseCase#book` es el único camino de creación. No afecta a ningún criterio de esta HU; queda como deuda de esquema a evaluar con el usuario.
- La retención de una solicitud especializada usa `reservation_type = 'APPOINTMENT'`. El otro valor previsto por V3, `RESCHEDULE_REQUEST`, **no tiene productor** en S3: llega con [[HU-027-solicitar-reprogramacion-de-cita-aprobada]].
