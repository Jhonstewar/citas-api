---
id: HU-028
tipo: historia-de-usuario
titulo: "Decidir sobre la cita tras el rechazo de una reprogramación"
estado: Borrador
epica: "[[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]"
requisitos: [RF-15, RF-14]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 7"
dependencias:
  - "[[HU-031-aprobar-o-rechazar-reprogramacion]]"
  - "[[HU-026-cancelar-una-cita-futura]]"
relacionadas:
  - "[[HU-027-solicitar-reprogramacion-de-cita-aprobada]]"
  - "[[HU-025-consultar-mis-citas-y-detalle]]"
---

# HU-028 — Decidir sobre la cita tras el rechazo de una reprogramación

## Historia de usuario

**COMO** USER autenticado cuya solicitud de reprogramación fue rechazada  
**QUIERO** conocer el motivo del rechazo y elegir entre conservar mi cita original o cancelarla  
**PARA** decidir con información si mantengo el horario que tenía o lo libero

> Como USER autenticado cuya solicitud de reprogramación fue rechazada, quiero conocer el motivo del rechazo y elegir entre conservar mi cita original o cancelarla para decidir con información si mantengo el horario que tenía o lo libero.

## Contexto y descripción

RF-15 termina con una regla orientada al paciente: después de un rechazo de reprogramación, el usuario puede conservar la cita o cancelarla. Gracias a RN-10, la cita original sigue `APPROVED` con su franja cuando [[HU-031-aprobar-o-rechazar-reprogramacion]] rechaza la solicitud, así que "conservar" no requiere ninguna transición: la cita ya está conservada. "Cancelar" es la cancelación de [[HU-026-cancelar-una-cita-futura]], con su liberación de slots e historial con origen `USER`.

El valor propio de esta HU es cerrar el ciclo de información: el paciente debe ver que su solicitud fue rechazada, por qué (motivo guardado en `reschedule_requests.decision_reason`), qué cita conserva, y tener ambas opciones al alcance, sin tener que interpretar por su cuenta que su cita sigue vigente.

## Alcance

- Exposición en el detalle de cita de `citas-api` de la última solicitud de reprogramación de la cita con su estado, franja propuesta y motivo de rechazo, solo para el paciente titular.
- Aviso en el detalle de cita de `citas-web` cuando la última solicitud está `REJECTED`, con el motivo y la franja original vigente.
- Opción "conservar mi cita": cierra el aviso sin cambiar estado, franja, reservas ni historial.
- Opción "cancelar mi cita": ejecuta la cancelación de [[HU-026-cancelar-una-cita-futura]] con confirmación previa.

## Fuera de alcance

- La decisión administrativa, que se cubre en [[HU-031-aprobar-o-rechazar-reprogramacion]].
- Nueva solicitud de reprogramación tras el rechazo: se realiza por [[HU-027-solicitar-reprogramacion-de-cita-aprobada]] y su número máximo está pendiente de INC-028.
- Persistencia de la elección "conservar": el PRD no la exige (ver notas).
- Notificación por correo del rechazo: pertenece a la automatización posterior de PRD §10.

## Reglas de negocio

- Tras un rechazo, la cita original se mantiene con su franja (RF-15, RN-10).
- Después del rechazo, el usuario puede conservar la cita o cancelarla (RF-15).
- Cancelar aplica todas las reglas de RF-14: cita futura, no terminal, liberación de slots e historial con origen `USER` (RF-14, RN-09, RF-19).
- Conservar no produce ningún cambio de estado (RN-11: no hay transiciones implícitas).
- El usuario solo ve las solicitudes y motivos de sus propias citas (PRD §8, ownership).

## Dependencias y relaciones

- Épica: [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]
- Dependencias: [[HU-031-aprobar-o-rechazar-reprogramacion]], [[HU-026-cancelar-una-cita-futura]]
- Relacionadas: [[HU-027-solicitar-reprogramacion-de-cita-aprobada]], [[HU-025-consultar-mis-citas-y-detalle]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** No introduce transiciones nuevas: reutiliza la cancelación existente y la cita ya queda conservada por RN-10. La dificultad está en ampliar la proyección de detalle de cita con la información de la solicitud rechazada respetando ownership, y en construir un flujo de interfaz que distinga con claridad la cita vigente de la franja propuesta rechazada.

## Tareas de desarrollo

- [ ] **T-01 — Ampliar la proyección de detalle de cita con la última reprogramación**  
  Dificultad: Medio  
  Descripción: Consulta de lectura que añade al detalle de la cita del titular la última solicitud de reprogramación con estado, franja propuesta, fecha de decisión y motivo, sin exponer datos del ADMIN decisor más allá de lo definido en el contrato.

- [ ] **T-02 — Exponer la información en el adaptador REST de detalle**  
  Dificultad: Bajo  
  Descripción: Extensión del contrato de detalle de [[HU-025-consultar-mis-citas-y-detalle]], manteniendo la restricción de ownership.

- [ ] **T-03 — Construir el aviso y las dos opciones en citas-web**  
  Dificultad: Medio  
  Descripción: Aviso en el detalle de cita con motivo de rechazo y franja vigente, botón "conservar" que cierra el aviso sin llamada de escritura, y botón "cancelar" que reutiliza la acción y la confirmación de cancelación existentes.

- [ ] **T-04 — Pruebas del flujo tras rechazo**  
  Dificultad: Medio  
  Descripción: Integración del detalle con solicitud rechazada, verificación de que conservar no altera nada, cancelación posterior con liberación de slots e historial, y acceso de un usuario ajeno.

## Criterios de aceptación

### CA-01 — El paciente ve el rechazo y su motivo

**Dado** una cita `APPROVED` propia cuya última solicitud de reprogramación fue rechazada con un motivo  
**Cuando** el paciente consulta el detalle de la cita  
**Entonces** ve que la solicitud está `REJECTED`, la franja propuesta rechazada, el motivo registrado por ADMIN y la franja original como franja vigente de la cita.

### CA-02 — Conservar no cambia nada

**Dado** una cita en la situación de CA-01  
**Cuando** el paciente elige conservar su cita  
**Entonces** la cita sigue `APPROVED` con la misma fecha, horas y reservas en `slot_reservations`, y no se crea ningún registro de historial.

### CA-03 — Cancelar libera la franja original y registra el historial

**Dado** una cita futura en la situación de CA-01  
**Cuando** el paciente elige cancelarla y confirma  
**Entonces** la cita queda `CANCELLED`, sus slots originales se liberan y vuelven a ofrecerse, y existe un registro de historial con estado `CANCELLED`, el paciente como actor y origen `USER` (RF-14, RF-19).

### CA-04 — La franja propuesta rechazada ya no está retenida

**Dado** una solicitud de reprogramación rechazada  
**Cuando** el paciente consulta su cita y cualquier usuario busca disponibilidad en la franja propuesta  
**Entonces** la franja propuesta aparece disponible y no existe ninguna reserva `RESCHEDULE_REQUEST` de esa solicitud, sea cual sea la opción elegida por el paciente.

### CA-05 — Cancelación sujeta a las reglas de RF-14

**Dado** una cita cuya reprogramación fue rechazada y cuya fecha ya pasó  
**Cuando** el paciente intenta cancelarla  
**Entonces** la API rechaza la operación con el mismo error de regla de negocio de [[HU-026-cancelar-una-cita-futura]] y la cita no cambia.

### CA-06 — Ownership sobre la información del rechazo

**Dado** una cita ajena con una reprogramación rechazada  
**Cuando** un USER autenticado distinto del titular solicita su detalle  
**Entonces** la API responde con error de autorización o recurso no encontrado y no expone el motivo de rechazo.

## Definition of Done

- [ ] Los criterios CA-01 a CA-06 están validados con evidencia concreta.
- [ ] No se introduce ninguna transición de estado nueva: conservar no escribe y cancelar reutiliza el caso de uso de [[HU-026-cancelar-una-cita-futura]].
- [ ] El motivo mostrado es el mismo dato persistido por [[HU-031-aprobar-o-rechazar-reprogramacion]], sin copia.
- [ ] La ampliación del detalle mantiene la regla de ownership de [[HU-005-autorizar-peticiones-por-rol-y-ownership]].
- [ ] No se crean migraciones salvo que se decida persistir la elección, en cuyo caso será una migración Flyway posterior a V4.
- [ ] El flujo de `citas-web` diferencia visualmente la franja vigente de la propuesta rechazada y exige confirmación para cancelar.
- [ ] Existen pruebas automatizadas de detalle con rechazo, conservar sin cambios, cancelación posterior y acceso ajeno, y pasan.
- [ ] La ampliación del contrato de detalle está reflejada en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
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
| DoD | Pendiente | — | — |

## Historial de validación

- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- El PRD no define si la elección "conservar" debe quedar registrada. Sin persistencia, el aviso de rechazo reaparecerá en cada consulta del detalle mientras esa sea la última solicitud; esta HU lo acepta como comportamiento informativo. Si se decide persistir la elección, requiere una migración nueva y un criterio adicional.
- Incógnita abierta **INC-028** (ver [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]): no está definido si tras un rechazo el paciente puede volver a solicitar reprogramación ni cuántas veces. Esta HU no lo impide ni lo ofrece explícitamente.
- Incógnita abierta **INC-027** (ver [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]): la cancelación hereda la ausencia de antelación mínima de HU-026.
