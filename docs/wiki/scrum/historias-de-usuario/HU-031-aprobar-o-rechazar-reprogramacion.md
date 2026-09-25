---
id: HU-031
tipo: historia-de-usuario
titulo: "Aprobar o rechazar una reprogramación"
estado: Aprobada
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

La cita sigue en `APPROVED` en ambos casos. Aun así, la épica exige que cada decisión aparezca en el historial con origen `ADMIN`, por lo que esta HU registra la decisión en `appointment_status_history` además de en `reschedule_requests` (`decided_by_user_id`, `decided_at`, `decision_reason`). Tras un rechazo, el paciente decide qué hacer en [[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]].

## Alcance

- Endpoints REST de aprobación y de rechazo de una solicitud de reprogramación, restringidos a `ADMIN`.
- Validación de que la solicitud está `PENDING` y su cita sigue `APPROVED`.
- Aprobación: eliminación de las reservas `APPOINTMENT` antiguas, conversión de las reservas `RESCHEDULE_REQUEST` de la solicitud en reservas `APPOINTMENT` de la cita, actualización de fecha y horas de la cita y paso de la solicitud a `APPROVED`.
- Rechazo: motivo obligatorio, eliminación de las reservas `RESCHEDULE_REQUEST`, cita sin cambios y paso de la solicitud a `REJECTED`.
- Registro de decisor, fecha de decisión y motivo en la solicitud, y registro de la decisión en el historial de la cita con origen `ADMIN`.
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
  Descripción: Casos de uso que bloquean o versionan solicitud y cita, aplican la decisión, persisten solicitud, cita y reservas y registran el historial con origen `ADMIN` en una única transacción.

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

### CA-06 — Decisión registrada en el historial con origen ADMIN

**Dado** una aprobación y un rechazo realizados correctamente  
**Cuando** se consulta el historial de cada cita  
**Entonces** cada cita tiene exactamente un registro nuevo con estado `APPROVED`, origen `ADMIN`, el identificador del administrador como actor y la fecha y hora; en la aprobación el motivo nombra la franja anterior y la nueva, y en el rechazo contiene el motivo enviado (RF-19, D22).

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

- [ ] Los criterios CA-01 a CA-09 están validados con evidencia concreta.
- [ ] La aprobación conserva el identificador de la cita y está demostrada con una prueba que verifica fila a fila `slot_reservations` antes y después.
- [ ] Solicitud, cita, reservas e historial se modifican en una única transacción; ante cualquier fallo no hay resultados parciales.
- [ ] La PK `slot_id` de `slot_reservations` se mantiene como garantía de no-doble-reserva durante la transferencia; no se desactivan restricciones.
- [ ] Las decisiones son operaciones explícitas del dominio con validación de estado origen de solicitud y cita.
- [ ] El motivo obligatorio del rechazo se valida en el servidor y se persiste en `reschedule_requests.decision_reason` y en el historial.
- [ ] Existe control de concurrencia demostrado frente a decisión doble y frente a cancelación simultánea.
- [ ] No se crean migraciones salvo cambio de esquema justificado, en una migración Flyway posterior a V4.
- [ ] Los endpoints exigen rol `ADMIN` aplicando [[HU-005-autorizar-peticiones-por-rol-y-ownership]].
- [ ] La pantalla de `citas-web` muestra franja actual y propuesta y no permite rechazar sin motivo.
- [ ] Existen pruebas automatizadas de aprobación y rechazo con 30 y 60 minutos, motivo vacío, estados inválidos, concurrencia y rol, y pasan.
- [ ] El contrato de los endpoints de decisión de reprogramación está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
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
| CA-09 | Pendiente | — | — |
| DoD | Pendiente | — | — |

## Historial de validación

- 2026-09-25 — CA-06 ajustado a D22: el registro de historial lleva estado `APPROVED` y, en la aprobación, un motivo que nombra la franja anterior y la nueva.
- 2026-09-25 — CA-07 ajustado a D18: antes exigía que, entre aprobación y cancelación simultáneas, "la otra recibe 409" en cualquier orden. Con D18 y RF-14 eso solo vale si la cancelación va primero; si la aprobación va primero, la cancelación posterior es legítima sobre la cita ya movida. El criterio exige ahora serialización y estado final coherente en los dos órdenes.
- 2026-09-25 — Se añade CA-09 por D23 (franja propuesta ya pasada → 409 al aprobar), que ningún criterio cubría; la DoD pasa a CA-01 a CA-09.
- 2026-09-25 — Aprobada por **aprobación delegada** del usuario para S4 (D15, PLAN_RETOMA_S4.md). Alcance en `PLAN_RETOMA_S4.md` §3 (bloque «Operación administrativa», fase F5 / LOOP_02) y decisiones D15–D30 en [[dec-006-decisiones-s4-ciclo-de-vida]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- **Resuelta (D22, provisional bajo delegación):** N3 — la decisión sobre una reprogramación **sí escribe historial** aunque la cita siga `APPROVED`: una fila con estado `APPROVED`, origen `ADMIN` y un motivo que nombra la franja anterior y la nueva. RF-19 pide trazar el cambio, y el esquema lo admite sin migración porque `appointment_status_history` guarda solo el estado nuevo (`V3__schedule_and_appointments.sql:115`) ([[dec-006-decisiones-s4-ciclo-de-vida]]). CA-06 lo refleja. D22 no precisa si en el **rechazo** el motivo del historial debe nombrar también las franjas; CA-06 exige solo que contenga el motivo enviado por ADMIN. D22 afecta también a [[HU-032-auditar-cambios-de-estado-de-cita]] (`Completada`), que define el historial como registro de transiciones.
- **Resuelta (D21, provisional bajo delegación):** INC-031 (ver [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]): la franja propuesta puede estar en otra sede si el profesional atiende en ella; al aprobar, la cita actualiza también su sede ([[dec-006-decisiones-s4-ciclo-de-vida]]).
- Incógnita abierta **INC-035** (ver [[EP-008-operacion-administrativa-de-solicitudes]]): no hay reversión de decisiones. No la resuelve ninguna decisión D15–D30.
- **Resuelta (D23, provisional bajo delegación):** INC-036 (ver [[EP-008-operacion-administrativa-de-solicitudes]]): si la franja propuesta ya pasó, aprobar responde 409 y ADMIN debe rechazar con motivo, igual que D12 para las citas `REQUESTED` vencidas y RN-06 ([[dec-006-decisiones-s4-ciclo-de-vida]]). CA-09 lo cubre. D23 no trata el caso de que haya pasado solo la franja **original**; aprobar hacia una franja futura sigue siendo válido en ese caso.
- **Resuelta (D18, respondida por el usuario):** cancelar una cita con solicitud `PENDING` pasa la solicitud a `CANCELLED` y libera las dos franjas en la misma transacción. Queda reflejado en CA-09 de [[HU-026-cancelar-una-cita-futura]] y en CA-07 de esta HU.
