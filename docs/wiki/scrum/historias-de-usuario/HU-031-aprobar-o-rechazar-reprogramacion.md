---
id: HU-031
tipo: historia-de-usuario
titulo: "Aprobar o rechazar una reprogramación"
estado: Borrador
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
**Entonces** cada cita tiene exactamente un registro nuevo con origen `ADMIN`, el identificador del administrador como actor, la fecha y hora, y en el rechazo el motivo enviado (RF-19).

### CA-07 — Decisiones concurrentes o cancelación simultánea

**Dado** una solicitud `PENDING`  
**Cuando** dos ADMIN deciden a la vez sobre ella, o ADMIN la aprueba mientras el paciente cancela la cita  
**Entonces** exactamente una operación tiene éxito, la otra recibe 409, y el estado final es coherente: ningún slot queda con dos reservas, ninguna reserva queda huérfana y la cita no queda cancelada con la franja nueva ocupada.

### CA-08 — Solo ADMIN decide

**Dado** un usuario autenticado con rol `USER` (incluido el titular) o `PROFESSIONAL`  
**Cuando** intenta aprobar o rechazar una reprogramación  
**Entonces** la API responde con un error de autorización y nada cambia.

## Definition of Done

- [ ] Los criterios CA-01 a CA-08 están validados con evidencia concreta.
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
| DoD | Pendiente | — | — |

## Historial de validación

- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- RF-19 audita cambios de estado de cita, pero aprobar o rechazar una reprogramación no cambia el estado de la cita (sigue `APPROVED`). CA-06 sigue el criterio de completitud de [[EP-008-operacion-administrativa-de-solicitudes]] y registra la decisión en `appointment_status_history` con el estado vigente `APPROVED`. Debe confirmarse si ese registro es deseado o si basta con la decisión en `reschedule_requests`, porque [[HU-032-auditar-cambios-de-estado-de-cita]] define el historial como registro de transiciones.
- Incógnita abierta **INC-031** (ver [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]): si la franja propuesta está en otra sede, la aprobación debe actualizar también `appointments.site_id`. La HU lo admite siempre que el profesional esté habilitado en esa sede (RN-07).
- Incógnita abierta **INC-035** (ver [[EP-008-operacion-administrativa-de-solicitudes]]): no hay reversión de decisiones.
- Incógnita abierta **INC-036** (ver [[EP-008-operacion-administrativa-de-solicitudes]]): no está definido qué hacer si la franja propuesta o la original ya pasaron mientras la solicitud esperaba. Esta HU no añade esa restricción hasta que se decida.
- [[HU-026-cancelar-una-cita-futura]] no especifica qué ocurre con una solicitud `PENDING` y su retención cuando el paciente cancela la cita. El catálogo de V4 incluye el estado de reprogramación `CANCELLED`, lo que sugiere que la cancelación debería cerrar la solicitud y liberar su retención; debe decidirse y reflejarse en HU-026, porque CA-07 de esta HU depende de ello.
