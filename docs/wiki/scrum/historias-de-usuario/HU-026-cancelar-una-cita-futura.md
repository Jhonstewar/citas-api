---
id: HU-026
tipo: historia-de-usuario
titulo: "Cancelar una cita futura"
estado: Borrador
epica: "[[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]"
requisitos: [RF-14, RF-19]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 7"
dependencias:
  - "[[HU-025-consultar-mis-citas-y-detalle]]"
  - "[[HU-032-auditar-cambios-de-estado-de-cita]]"
relacionadas:
  - "[[HU-022-buscar-disponibilidad-con-filtros]]"
---

# HU-026 — Cancelar una cita futura

## Historia de usuario

**COMO** USER autenticado con una cita agendada
**QUIERO** cancelar una cita futura que ya no necesito
**PARA** liberar el horario y dejar constancia de la cancelación

> Como USER autenticado con una cita agendada, quiero cancelar una cita futura que ya no necesito para liberar el horario y dejar constancia de la cancelación.

## Contexto y descripción

RF-14 permite al paciente cancelar una cita futura que no esté en un estado terminal. La cancelación lleva la cita a `CANCELLED`, libera sus slots conforme a RN-09 y deja registro en el historial de estados conforme a RF-19. El PRD es explícito en que una cita cancelada no se reactiva directamente: si el paciente cambia de opinión debe agendar una cita nueva desde [[HU-022-buscar-disponibilidad-con-filtros]].

Esta HU es, junto a [[HU-030-aprobar-o-rechazar-cita-especializada]], una de las dos que devuelven oferta a la agenda. Su valor real no está en el cambio de estado sino en la liberación efectiva de los slots: una cancelación que deje el horario retenido convierte la franja en inservible para siempre. Por eso los criterios de aceptación verifican la disponibilidad reaparecida en la búsqueda, no solo el estado de la cita.

## Alcance

- Endpoint de cancelación de una cita propia en `citas-api`.
- Verificación de que la cita es futura y de que su estado no es terminal.
- Transición explícita de la cita a `CANCELLED`.
- Liberación de todos los slots que la cita tenía reservados o retenidos.
- Registro del cambio en el historial con actor y origen `USER`.
- Acción de cancelar en la pantalla de detalle de cita de `citas-web`, con confirmación previa.

## Fuera de alcance

- Cancelación de citas por ADMIN o por PROFESSIONAL: RF-14 asigna la cancelación al usuario y el PRD no describe otra vía.
- Reactivación de una cita cancelada: el PRD la prohíbe explícitamente (RF-14).
- Cancelación de una solicitud de reprogramación, que se trata en [[HU-027-solicitar-reprogramacion-de-cita-aprobada]] y en [[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]].
- Penalizaciones, cupos de cancelación o listas de espera: no están en el PRD.
- Notificación por correo de la cancelación: pertenece a la automatización posterior de PRD §10.

## Reglas de negocio

- Solo puede cancelarse una cita cuya fecha y hora son futuras (RF-14, RN-06).
- Solo puede cancelarse una cita en estado no terminal (RF-14).
- La cancelación lleva la cita al estado `CANCELLED` (RF-14).
- La cancelación libera las reservas de slots correspondientes, que vuelven a estar disponibles para cualquier paciente (RN-09).
- Una cita `CANCELLED` no se reactiva directamente (RF-14).
- Un usuario solo puede cancelar sus propias citas (PRD §8, ownership).
- El cambio de estado se registra en el historial con cita, estado nuevo, actor, origen `USER` y fecha y hora (RF-19).
- La transición a `CANCELLED` es explícita y verificable; no se alcanza por una escritura directa del estado (RN-11).

## Dependencias y relaciones

- Épica: [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]
- Dependencias: [[HU-025-consultar-mis-citas-y-detalle]], [[HU-032-auditar-cambios-de-estado-de-cita]]
- Relacionadas: [[HU-022-buscar-disponibilidad-con-filtros]], [[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** La regla es corta pero toca tres cosas a la vez: el modelo de transiciones de estado, la ocupación de slots y el historial de auditoría. La dificultad está en que la liberación de slots y el cambio de estado deben ocurrir en la misma unidad de trabajo, de modo que no exista un estado intermedio en el que la cita esté cancelada y su franja siga retenida, ni al revés.

## Tareas de desarrollo

- [ ] **T-01 — Modelar la transición de cancelación en el dominio**
  Dificultad: Medio
  Descripción: Método de dominio sobre la cita que valida que la fecha es futura y que el estado actual admite cancelación, produce la transición a `CANCELLED` y devuelve los slots que deben liberarse, sin dependencias de framework.

- [ ] **T-02 — Implementar el caso de uso de cancelación**
  Dificultad: Medio
  Descripción: Caso de uso que recupera la cita, comprueba la propiedad del solicitante, invoca la transición de dominio, libera los slots y solicita el registro de historial con origen `USER`, todo en una única transacción.

- [ ] **T-03 — Implementar la liberación de slots en persistencia**
  Dificultad: Medio
  Descripción: Adaptador que marca como disponibles los slots asociados a la cita cancelada, sea cual sea su número, garantizando que no queda ninguna retención huérfana.

- [ ] **T-04 — Exponer el adaptador REST de cancelación**
  Dificultad: Bajo
  Descripción: Endpoint autenticado de cancelación sobre una cita concreta, con respuestas diferenciadas para cita ajena, cita pasada, cita en estado terminal y cancelación correcta, siguiendo el formato de error uniforme de [[HU-033-publicar-contrato-rest-documentado]].

- [ ] **T-05 — Añadir la acción de cancelar en citas-web**
  Dificultad: Bajo
  Descripción: Botón de cancelación en el detalle de cita, visible solo cuando la cita es futura y no terminal, con diálogo de confirmación, y refresco del estado y del listado tras la respuesta.

- [ ] **T-06 — Pruebas de cancelación y liberación**
  Dificultad: Medio
  Descripción: Pruebas de dominio para las transiciones permitidas y prohibidas, y pruebas de integración para la liberación efectiva de uno y de dos slots, la reaparición de la franja en la búsqueda de disponibilidad, la cita pasada, la cita terminal y la cita ajena.

## Criterios de aceptación

### CA-01 — Cancelación de una cita futura no terminal

**Dado** una cita propia del usuario, con fecha y hora futuras y en un estado no terminal
**Cuando** el usuario solicita cancelarla
**Entonces** la API responde con éxito y la cita queda en estado `CANCELLED`.

### CA-02 — Los slots se liberan y vuelven a ofrecerse

**Dado** una cita futura de 60 minutos que ocupa dos slots consecutivos
**Cuando** el usuario la cancela
**Entonces** los dos slots quedan marcados como libres y una búsqueda de disponibilidad para ese profesional, esa sede y esa fecha vuelve a ofrecer esa franja a cualquier paciente (RN-09).

### CA-03 — Una cita cancelada no puede reactivarse

**Dado** una cita en estado `CANCELLED`
**Cuando** se intenta llevarla de vuelta a `APPROVED` o a `REQUESTED` por cualquier vía expuesta por la API
**Entonces** la operación se rechaza, la cita permanece en `CANCELLED` y no se crea ningún registro de historial.

### CA-04 — Cancelación de una cita pasada rechazada

**Dado** una cita propia cuya fecha y hora ya han pasado
**Cuando** el usuario intenta cancelarla
**Entonces** la API responde con un error de regla de negocio, la cita conserva su estado anterior y sus slots no se liberan.

### CA-05 — Cancelación de una cita terminal rechazada

**Dado** una cita propia en un estado terminal, como `CANCELLED`, `REJECTED`, `COMPLETED` o `NO_SHOW`
**Cuando** el usuario intenta cancelarla
**Entonces** la API responde con un error de regla de negocio y no se produce ningún cambio de estado ni ningún registro de historial.

### CA-06 — Un usuario no puede cancelar la cita de otro

**Dado** el identificador de una cita futura no terminal que pertenece a otro usuario
**Cuando** un `USER` autenticado intenta cancelarla
**Entonces** la API responde con un error de autorización o de recurso no encontrado, la cita ajena conserva su estado y sus slots siguen ocupados.

### CA-07 — El cambio queda registrado con actor y origen USER

**Dado** una cancelación realizada correctamente por el paciente
**Cuando** se consulta el historial de esa cita
**Entonces** existe exactamente un registro nuevo con la cita, el estado nuevo `CANCELLED`, el identificador del usuario como actor, el origen `USER` y la fecha y hora del cambio (RF-19).

### CA-08 — Atomicidad entre el estado y los slots

**Dado** una cancelación en la que la liberación de slots falla por un error de persistencia
**Cuando** la transacción termina
**Entonces** la cita conserva su estado anterior, sus slots siguen ocupados y no queda ningún registro de historial, es decir, no existe un resultado parcial.

## Definition of Done

- [ ] Los criterios CA-01 a CA-08 están validados con evidencia concreta.
- [ ] La transición a `CANCELLED` está implementada como una operación explícita del dominio y no como una asignación directa del campo de estado (RN-11).
- [ ] El cambio de estado, la liberación de slots y la escritura del historial ocurren en una única transacción.
- [ ] Una búsqueda de disponibilidad posterior a la cancelación ofrece de nuevo la franja liberada, verificado contra [[HU-022-buscar-disponibilidad-con-filtros]].
- [ ] No existe ningún endpoint que devuelva una cita `CANCELLED` a un estado activo.
- [ ] La acción de cancelar de `citas-web` solo se ofrece sobre citas futuras no terminales y exige confirmación explícita del usuario.
- [ ] Existen pruebas automatizadas de la cancelación correcta con uno y con dos slots, de la cita pasada, de la cita terminal y de la cita ajena, y pasan.
- [ ] El contrato del endpoint de cancelación está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [ ] La trazabilidad de esta HU y de [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]] está actualizada en `docs/wiki/scrum/`.

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

- Incógnita abierta **INC-027** (ver [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]): el PRD no define una antelación mínima para cancelar; RF-14 solo exige que la cita sea futura. Hasta que se decida, CA-01 y CA-04 usan únicamente la condición "futura" y no una ventana de horas previas.
- Incógnita abierta **INC-030** (ver [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]): el PRD no dice explícitamente si una cita `REQUESTED` puede cancelarse. La redacción "futura no terminal" de RF-14 la incluiría, y esta HU asume que sí; debe confirmarse con el usuario del proyecto antes de implementar, porque cambia el conjunto de estados válidos de CA-01.
- La lista concreta de estados terminales de CA-05 depende del modelo de estados de cita fijado en [[HU-032-auditar-cambios-de-estado-de-cita]]; ambas HU deben compartir la misma definición y no mantener dos listas distintas.
