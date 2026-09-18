---
id: HU-030
tipo: historia-de-usuario
titulo: "Aprobar o rechazar una cita especializada"
estado: Aprobada
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

- [ ] Los criterios CA-01 a CA-08 están validados con evidencia concreta.
- [ ] Aprobar y rechazar son operaciones explícitas del dominio con validación de estado origen; no existe una actualización genérica de estado expuesta.
- [ ] Cambio de estado, liberación de reservas e historial ocurren en una única transacción, sin resultados parciales.
- [ ] La liberación de reservas reutiliza la misma operación que la cancelación y no deja filas huérfanas en `slot_reservations`.
- [ ] El motivo obligatorio se valida en el servidor y se persiste en `appointment_status_history.reason`.
- [ ] Existe control de concurrencia demostrado con una prueba de dos decisiones simultáneas.
- [ ] No se crean migraciones salvo cambio de esquema justificado (por ejemplo una columna de versión), en una migración Flyway posterior a V4.
- [ ] Los endpoints exigen rol `ADMIN` aplicando [[HU-005-autorizar-peticiones-por-rol-y-ownership]].
- [ ] La pantalla de `citas-web` no permite enviar un rechazo sin motivo y refresca la bandeja ante un 409.
- [ ] Existen pruebas automatizadas de aprobación, rechazo con liberación, motivo vacío, transiciones inválidas, concurrencia y rol, y pasan.
- [ ] El contrato de los endpoints de decisión está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [ ] La trazabilidad de esta HU y de [[EP-008-operacion-administrativa-de-solicitudes]] está actualizada en `docs/wiki/scrum/`.

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
| DoD | Pendiente | — | — |

## Historial de validación

- 2026-09-18 — Estado `Aprobada` por **aprobación delegada** de S3: el usuario pidió continuar S3 dejando las decisiones de diseño a criterio del agente (`AGENTS.md` §6). Alcance y decisiones D5–D13 en `PLAN_RETOMA_S3.md` y [[dec-004-decisiones-s3-reserva]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- Incógnita abierta **INC-032** (ver [[EP-008-operacion-administrativa-de-solicitudes]]): no está definido qué ocurre si al aprobar la franja ya no es válida (bloque eliminado o profesional desactivado). Como [[HU-018-editar-y-eliminar-bloques-futuros]] impide eliminar bloques con citas comprometidas, el caso restante es el profesional desactivado; esta HU no lo bloquea hasta que se decida.
- Incógnita abierta **INC-033** (ver [[EP-008-operacion-administrativa-de-solicitudes]]): el motivo se trata como texto libre no vacío; la columna `reason` de V3 admite hasta 500 caracteres, que actúa como límite técnico hasta que se decida una longitud funcional.
- Incógnita abierta **INC-035** (ver [[EP-008-operacion-administrativa-de-solicitudes]]): no se ofrece reversión de decisiones; CA-04 lo hace explícito para `REJECTED` y `APPROVED`.
- Incógnita abierta **INC-036** (ver [[EP-008-operacion-administrativa-de-solicitudes]]): no está definido si puede aprobarse una solicitud cuya fecha ya pasó. Esta HU no añade esa restricción; si se decide aplicar RN-06, se incorporará como criterio adicional.
