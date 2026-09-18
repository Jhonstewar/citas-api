---
id: HU-023
tipo: historia-de-usuario
titulo: "Agendar cita de Medicina General"
estado: En desarrollo
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

- [ ] Los criterios CA-01 a CA-09 están validados con evidencia concreta.
- [ ] La no-doble-reserva descansa en la PK `slot_id` de `slot_reservations` y está demostrada con una prueba concurrente contra MySQL 8.4, no solo con un mock.
- [ ] La violación de la PK se traduce a 409 y la transacción revierte cita, reservas e historial: no quedan filas parciales.
- [ ] La creación de la cita general es una operación explícita del dominio que produce `APPROVED`; el estado no se asigna desde el adaptador REST.
- [ ] El historial se escribe mediante el puerto de [[HU-032-auditar-cambios-de-estado-de-cita]] en la misma transacción.
- [ ] Se reutiliza la regla de franja ofrecible de [[HU-022-buscar-disponibilidad-con-filtros]] para validar consecutividad y futuro.
- [ ] No se crean migraciones salvo cambio de esquema justificado, en una migración Flyway posterior a V4.
- [ ] El endpoint exige rol `USER` y toma al paciente del contexto de autenticación.
- [ ] El flujo de `citas-web` muestra la cita aprobada y trata el 409 con un mensaje comprensible y la opción de volver a buscar.
- [ ] Existen pruebas automatizadas de 30 y 60 minutos, concurrencia, pasado, especialidad no general, inactiva o no asociada y rol, y pasan.
- [ ] El contrato del endpoint de cita general está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
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

- Incógnita abierta **INC-008** (ver [[EP-002-perfil-y-afiliacion-del-paciente]]): el PRD no define si la afiliación es obligatoria. `appointments.affiliation_id` es nullable; esta HU no exige afiliación ni fija cómo se asocia la vigente.
- Incógnita abierta **INC-022** (ver [[EP-006-busqueda-de-disponibilidad-y-reserva]]): no hay antelación mínima; CA-06 solo prohíbe el pasado.
- Incógnita abierta **INC-025** (ver [[EP-006-busqueda-de-disponibilidad-y-reserva]]): el PRD no prohíbe que un mismo usuario tenga dos citas solapadas entre sí con profesionales distintos. Esta HU no lo impide.
- Incógnita abierta **INC-026** (ver [[EP-006-busqueda-de-disponibilidad-y-reserva]]): no hay asignación automática de profesional general.
- El registro de historial con origen `SYSTEM` no identifica actor (`actor_user_id` nulo, permitido por la restricción de V3). No está definido si además debe conservarse qué USER originó la reserva; el titular ya consta en `appointments.patient_user_id`.
- El PRD no define si dos slots consecutivos pueden pertenecer a bloques contiguos; se sigue la misma suposición que [[HU-022-buscar-disponibilidad-con-filtros]] (mismo bloque).
