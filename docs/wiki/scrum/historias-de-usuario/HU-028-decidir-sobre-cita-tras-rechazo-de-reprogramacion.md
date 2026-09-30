---
id: HU-028
tipo: historia-de-usuario
titulo: "Decidir sobre la cita tras el rechazo de una reprogramación"
estado: En validación
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
- Nueva solicitud de reprogramación tras el rechazo: se realiza por [[HU-027-solicitar-reprogramacion-de-cita-aprobada]], que la admite una vez decidida la anterior (D20, CA-10 de HU-027).
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

- [x] Los criterios CA-01 a CA-06 están validados con evidencia concreta.
- [x] No se introduce ninguna transición de estado nueva: conservar no escribe y cancelar reutiliza el caso de uso de [[HU-026-cancelar-una-cita-futura]].
- [x] El motivo mostrado es el mismo dato persistido por [[HU-031-aprobar-o-rechazar-reprogramacion]], sin copia.
- [x] La ampliación del detalle mantiene la regla de ownership de [[HU-005-autorizar-peticiones-por-rol-y-ownership]].
- [x] No se crean migraciones salvo que se decida persistir la elección, en cuyo caso será una migración Flyway posterior a V4.
- [ ] El flujo de `citas-web` diferencia visualmente la franja vigente de la propuesta rechazada y exige confirmación para cancelar.
- [x] Existen pruebas automatizadas de detalle con rechazo, conservar sin cambios, cancelación posterior y acceso ajeno, y pasan.
- [x] La ampliación del contrato de detalle está reflejada en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [x] La trazabilidad de esta HU y de [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Ejecución de referencia del **2026-09-30** (iteración 2 del LOOP_02 de S4): el `backend-verifier`, agente independiente que no escribió el código, reejecutó la suite completa de `citas-api` → **480 pruebas, 0 fallos, 0 errores, `BUILD SUCCESS`** (`evidencias/s4/loops/LOOP-02/iter-2-verifier.json`). Abreviatura: **RDIT** = `src/test/java/com/fcv/citas/infrastructure/rest/RescheduleDecisionIntegrationTest.java`.

Los criterios de esta HU son observables por API, y todos cumplen. Lo que queda en `Pendiente` es la **interfaz**: el `backend-verifier` la clasificó `NO VERIFICABLE` porque `citas-web` está fuera de su repositorio, y el `frontend-verifier` dio PASS solo al alcance de la iteración 2, con defectos abiertos y sin la prueba manual en navegador de F10.

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | RDIT:321 `rejectingKeepsTheAppointmentAndReleasesTheProposal`, líneas 350–361: el detalle del titular devuelve `lastReschedule.status = REJECTED`, `statusName = "Rechazada"`, `proposed.startTime` (la franja rechazada), `previous.startTime` y `decisionReason` con el motivo del ADMIN, mientras la cita sigue `APPROVED` a las 08:00 —su franja vigente— y `reschedulable: true` | La franja anterior viaja en la propia solicitud desde `V10` (D31), así que el paciente ve las dos franjas sin depender del historial |
| CA-02 | Cumple | RDIT:321, líneas 351–364: consultar el detalle no cambia nada —`history(id)` es idéntico antes y después y la cita conserva sus dos reservas `APPOINTMENT`—; no existe ningún endpoint de "conservar" | "Conservar" no tiene camino de escritura en la API, que es la forma fuerte de cumplir el criterio. Que el botón de la interfaz tampoco escriba es lo que queda en `Pendiente` (el `frontend-verifier` anotó su defecto D-3: la prueba del camino "Entendido" lo afirma en un comentario y no lo asevera) |
| CA-03 | Cumple | RDIT:383 `cancellingAfterARejectionReleasesTheOriginalSlot` (tras el rechazo el paciente cancela: cita `CANCELLED`, cero reservas de la cita y de la solicitud, última fila de historial con estado `CANCELLED`, `source = USER` y el paciente como actor, y las franjas 08:00 y 09:00 vuelven a ofrecerse) | Reutiliza `CancelAppointmentUseCase`; no hay una cancelación propia de esta HU |
| CA-04 | Cumple | RDIT:321, líneas 344–346 (10:00 y 10:30 sin fila en `slot_reservations` y la franja vuelve a ofrecerse en la búsqueda) y RDIT:383, líneas 399–402 (tras cancelar tampoco queda ninguna reserva de la solicitud rechazada) | Se comprueba con las dos opciones del paciente, como pide el criterio |
| CA-05 | Cumple | RDIT:407 `aPastAppointmentWithARejectedRescheduleCannotBeCancelled` (cita de ayer con la solicitud `REJECTED` → 409 `APPOINTMENT_EXPIRED` y la cita sigue `APPROVED`) | Es el mismo error de [[HU-026-cancelar-una-cita-futura]]: la regla no se duplica |
| CA-06 | Cumple | RDIT:427 `anotherPatientCannotSeeTheRejection` (otro USER pide el detalle → 404 `NOT_FOUND` y `lastReschedule` ausente del cuerpo) | No se filtra el motivo ni por omisión ni por mensaje de error |
| DoD — CA-01 a CA-06 validados con evidencia concreta | Cumple | Filas CA-01 a CA-06 de esta tabla | — |
| DoD — Ninguna transición nueva: conservar no escribe y cancelar reutiliza el caso de uso de [[HU-026-cancelar-una-cita-futura]] | Cumple | No existe endpoint de "conservar" (RDIT:321, líneas 351–363: el historial no crece); la cancelación pasa por `application/appointment/CancelAppointmentUseCase#cancel`, cuya fila `CANCELLED`/`USER` se comprueba en RDIT:383 | — |
| DoD — El motivo mostrado es el mismo dato persistido por [[HU-031-aprobar-o-rechazar-reprogramacion]], sin copia | Cumple | `infrastructure/persistence/appointment/JdbcAppointmentQueries#lastReschedule` lee `rr.decision_reason` de `reschedule_requests`; RDIT:321 compara el valor de la columna (líneas 334–340) con el que devuelve el detalle (línea 361): es el mismo texto | No hay campo espejo en `appointments` ni en el historial (D39) |
| DoD — La ampliación del detalle mantiene el ownership de [[HU-005-autorizar-peticiones-por-rol-y-ownership]] | Cumple | RDIT:427 (404 para el no titular); la lectura del detalle pasa por el mismo componente de propiedad que el resto de `/api/patient/**` (`SecurityConfig:85` y `application/shared/Ownership`) | — |
| DoD — Sin migraciones salvo que se decida persistir la elección | Cumple | La elección no se persiste (`PLAN_RETOMA_S4.md` F5: «conservar (sin escritura)»); las migraciones de S4 son `V5`–`V10` y ninguna añade columnas para esta HU | — |
| DoD — El flujo de `citas-web` diferencia la franja vigente de la propuesta rechazada y exige confirmación para cancelar | Pendiente | Existen `citas-web/src/pages/patient/AppointmentDetailPage.tsx` y `PendingRescheduleMark.tsx`, con la suite de frontend en verde (218/218, typecheck 0, oxlint 0, build OK, reejecutada por el `frontend-verifier`) | `NO VERIFICABLE` para el `backend-verifier` (otro repositorio). El `frontend-verifier` dejó defectos abiertos en este mismo aviso: D-1 (el aviso de una solicitud `CANCELLED` descarta `decisionReason` y deja al paciente sin explicación cuando el profesional cerró la atención, D38) y D-4 (se pierde el foco al cerrar el aviso). Falta la prueba manual en navegador de F10 |
| DoD — Pruebas automatizadas de detalle con rechazo, conservar sin cambios, cancelación posterior y acceso ajeno, y pasan | Cumple | RDIT:321, RDIT:383, RDIT:407 y RDIT:427; suite completa 480/480, `BUILD SUCCESS`, reejecutada por el `backend-verifier` | — |
| DoD — La ampliación del contrato de detalle está reflejada en la documentación de [[HU-033-publicar-contrato-rest-documentado]] | Cumple | `citas-api/docs/wiki/llm-wiki/wiki/contrato-rest-citas.md`: tipo `RescheduleRequest`, `AppointmentDetail + lastReschedule` («la más reciente, en cualquier estado (HU-028)») y la nota que enumera los valores de `decisionReason` según el estado | Queda un hueco en ese documento que no es de esta HU: no lista el `decisionReason` que D38 introduce al cerrar la atención (hallazgo MEDIA n.º 3 del Verifier). `llm-wiki/` está fuera del límite de escritura de esta skill |
| DoD — Trazabilidad de esta HU y de [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]] actualizada | Cumple | Esta matriz y el historial de validación; EP-007 enlaza la HU y no mantiene estados por historia | — |

## Historial de validación

- 2026-09-30 — Estado `En validación` (skill `scrum-spec-orchestrator`, paso 10): se registra la matriz de evidencia recolectada del repositorio y de la verificación independiente del `backend-verifier` en la iteración 2 del LOOP_02 de S4 (480/480, `BUILD SUCCESS`). Los seis criterios cumplen. **No pasa a `Completada`:** la DoD del flujo de `citas-web` queda en `Pendiente` porque el `frontend-verifier` dejó abiertos dos defectos del propio aviso (D-1, el aviso de una solicitud `CANCELLED` por D38 no muestra el motivo; D-4, pérdida de foco) y falta la prueba manual en navegador de F10. Ningún criterio se reescribió: D39 no toca esta HU, que ya leía el motivo del rechazo de `reschedule_requests.decision_reason` y no del historial.
- 2026-09-25 — Aprobada por **aprobación delegada** del usuario para S4 (D15, PLAN_RETOMA_S4.md). Alcance en `PLAN_RETOMA_S4.md` §3 (bloque «Ciclo de vida del paciente», fase F5 / LOOP_02) y decisiones D15–D30 en [[dec-006-decisiones-s4-ciclo-de-vida]]. Ningún criterio contradice D15–D30. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- El PRD no define si la elección "conservar" debe quedar registrada. Sin persistencia, el aviso de rechazo reaparecerá en cada consulta del detalle mientras esa sea la última solicitud; esta HU lo acepta como comportamiento informativo. Si se decide persistir la elección, requiere una migración nueva y un criterio adicional. `PLAN_RETOMA_S4.md` F5 confirma «conservar (sin escritura)».
- **Resuelta (D20, provisional bajo delegación):** INC-028 (ver [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]): tras un rechazo el paciente **puede** volver a solicitar reprogramación, con una sola solicitud sin decidir a la vez ([[dec-006-decisiones-s4-ciclo-de-vida]]). Esta HU no añade un botón propio para ello; la vía es [[HU-027-solicitar-reprogramacion-de-cita-aprobada]].
- **Resuelta (D17, provisional bajo delegación):** INC-027 (ver [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]): la cancelación no tiene antelación mínima; basta con que la cita no haya empezado. CA-05 hereda esa regla de [[HU-026-cancelar-una-cita-futura]].
