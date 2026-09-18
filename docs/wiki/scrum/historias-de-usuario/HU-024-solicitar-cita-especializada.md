---
id: HU-024
tipo: historia-de-usuario
titulo: "Solicitar cita especializada"
estado: En desarrollo
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

- [ ] Los criterios CA-01 a CA-09 están validados con evidencia concreta.
- [ ] La retención usa el mismo puerto y la misma tabla `slot_reservations` que [[HU-023-agendar-cita-de-medicina-general]]; no existe un segundo mecanismo de reserva.
- [ ] La no-doble-reserva entre flujo general y especializado está demostrada con una prueba concurrente contra MySQL 8.4.
- [ ] Cita, retención e historial se escriben en una única transacción; un 409 no deja filas parciales.
- [ ] La política "especializada nace `REQUESTED`" se decide en el dominio a partir del tipo de especialidad y no en el adaptador REST.
- [ ] El historial se escribe mediante el puerto de [[HU-032-auditar-cambios-de-estado-de-cita]] con origen `USER` y actor.
- [ ] No se crean migraciones salvo cambio de esquema justificado, en una migración Flyway posterior a V4.
- [ ] El flujo de `citas-web` informa que la cita está pendiente de aprobación y trata el 409.
- [ ] Existen pruebas automatizadas de 30 y 60 minutos, concurrencia, pasado, especialidad incorrecta y rol, y pasan.
- [ ] El contrato del endpoint de solicitud especializada está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [ ] La trazabilidad de esta HU y de [[EP-006-busqueda-de-disponibilidad-y-reserva]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Pendiente | — | — |
| CA-02 | Pendiente | — | — |
| CA-03 | Pendiente | — | — |
| CA-04 | Pendiente | — | — |
| CA-05 | Pendiente | — | — |
| CA-06 | Pendiente | — | — |
| CA-07 | Pendiente | — | — |
| CA-08 | Pendiente | — | — |
| CA-09 | Pendiente | — | — |
| DoD | Pendiente | — | — |

## Historial de validación

- 2026-09-18 — Estado `En desarrollo` (skill `scrum-spec-orchestrator`, paso 9): se inicia la implementación en la fase F5 de `PLAN_RETOMA_S3.md`.
- 2026-09-18 — Estado `Aprobada` por **aprobación delegada** de S3: el usuario pidió continuar S3 dejando las decisiones de diseño a criterio del agente (`AGENTS.md` §6). Alcance y decisiones D5–D13 en `PLAN_RETOMA_S3.md` y [[dec-004-decisiones-s3-reserva]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- Incógnita abierta **INC-024** (ver [[EP-006-busqueda-de-disponibilidad-y-reserva]]): el PRD no define si la retención de una cita `REQUESTED` caduca sin decisión de ADMIN. Esta HU mantiene la retención indefinidamente hasta la decisión; es un riesgo real de bloqueo de agenda.
- Incógnita abierta **INC-025** (ver [[EP-006-busqueda-de-disponibilidad-y-reserva]]): no hay límite de solicitudes `REQUESTED` simultáneas por usuario ni prohibición de solapes entre citas propias.
- Incógnita abierta **INC-008** (ver [[EP-002-perfil-y-afiliacion-del-paciente]]): la afiliación no se exige ni se asocia obligatoriamente; si se decidiera obligatoria para citas especializadas, se añadiría un criterio de bloqueo aquí.
- Incógnita abierta **INC-036** (ver [[EP-008-operacion-administrativa-de-solicitudes]]): una solicitud cuya fecha pasa sin decisión conserva su retención; el tratamiento corresponde a la operación administrativa.
