---
id: HU-021
tipo: historia-de-usuario
titulo: "Registrar el cierre de atención"
estado: En validación
epica: "[[EP-005-agenda-del-profesional]]"
requisitos: [RF-17, RF-19]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 8"
dependencias:
  - "[[HU-020-consultar-agenda-de-citas-aprobadas]]"
  - "[[HU-032-auditar-cambios-de-estado-de-cita]]"
relacionadas:
  - "[[HU-025-consultar-mis-citas-y-detalle]]"
---

# HU-021 — Registrar el cierre de atención

## Historia de usuario

**COMO** PROFESSIONAL  
**QUIERO** marcar una cita como atendida o como inasistencia  
**PARA** dejar cerrado el resultado de la atención con trazabilidad

> Como PROFESSIONAL, quiero marcar una cita como atendida o como inasistencia para dejar cerrado el resultado de la atención con trazabilidad.

## Contexto y descripción

RF-17 otorga al profesional la capacidad de cerrar el ciclo de una cita marcándola como `COMPLETED` cuando el paciente fue atendido o como `NO_SHOW` cuando no se presentó, y exige que el cambio quede registrado en el historial. Es la última transición del recorrido de una cita en el alcance del profesional.

El cierre es una transición de estado y, por tanto, queda sujeto a RN-11, que exige transiciones explícitas y verificables, y a RF-19, que obliga a registrar cita, estado nuevo, actor, fuente, fecha y motivo opcional en cada cambio. Aquí el actor es el profesional y la fuente es `PROFESSIONAL`, valor que D8 añadió en V5 (ver notas). La escritura del historial se apoya en el mecanismo de [[HU-032-auditar-cambios-de-estado-de-cita]], y el punto de entrada natural es la agenda de [[HU-020-consultar-agenda-de-citas-aprobadas]].

RF-17 describe la cita candidata como "pasada/aplicable" sin precisar la condición, lo que deja abierta la incógnita INC-018 de [[EP-005-agenda-del-profesional]]. Los criterios de esta HU se redactan sobre el mecanismo de la transición y no sobre un umbral temporal concreto.

## Alcance

- Transición de una cita propia a estado `COMPLETED` (RF-17).
- Transición de una cita propia a estado `NO_SHOW` (RF-17).
- Verificación de que la cita pertenece al profesional autenticado y de que su estado actual admite el cierre.
- Rechazo del cierre sobre citas que aún no son cerrables y sobre citas que ya están en un estado terminal.
- Registro en el historial de estados de cada cierre con estado nuevo, actor, fuente y fecha (RF-19, RN-11).
- Endpoint REST de cierre de atención restringido al rol `PROFESSIONAL`.
- Acción de cierre en la pantalla de agenda del profesional en `citas-web`.

## Fuera de alcance

- Registro clínico, diagnóstico, tratamiento o historia clínica (PRD §9).
- Reapertura o reversión de un cierre ya registrado: no está en el PRD y contradice RN-12.
- Cancelación de la cita por el paciente, que pertenece a [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]].
- Cierre masivo automático de citas no cerradas: no está en el PRD.
- Modificación directa del historial de auditoría (RN-12).
- **Consulta por el profesional de sus citas ya cerradas (`COMPLETED` o `NO_SHOW`): fuera del alcance de S4.** La única lectura de citas que tiene el profesional es la agenda de [[HU-020-consultar-agenda-de-citas-aprobadas]], que por su propio alcance —RF-16— lista solo `APPROVED`; en cuanto la cita se cierra desaparece de ella (`ProfessionalAgendaIntegrationTest:396` `aClosedAppointmentLeavesTheAgenda`, comportamiento deliberado, aclaración 6 del contrato S4). **No es un defecto**: ninguna HU pide esa lectura y RF-16 la excluye explícitamente. Es un **hueco de producto que ninguna HU cubre**: el profesional no puede releer lo que acaba de cerrar, ni revisar su actividad pasada. Si el usuario lo quiere, es alcance nuevo y necesita su propia HU, con su propia decisión sobre qué campos del paciente se exponen en una cita ya atendida (la proyección de D35 se decidió «para la atención»). Anotado antes de cerrar para que quede escrito, no para resolverlo aquí.

## Reglas de negocio

- El profesional puede marcar una cita aplicable como `COMPLETED` o como `NO_SHOW` (RF-17).
- Solo se cierran citas propias del profesional autenticado (RF-16, PRD §8).
- Las transiciones de estado son explícitas y verificables (RN-11).
- Una cita que ya está en un estado terminal no admite una nueva transición de cierre (RN-11).
- Cada cierre escribe una entrada de historial con la cita, el estado nuevo, el actor, la fuente y la fecha y hora (RF-19).
- Los datos de auditoría no se modifican como un CRUD normal (RN-12).

## Dependencias y relaciones

- Épica: [[EP-005-agenda-del-profesional]]
- Dependencias: [[HU-020-consultar-agenda-de-citas-aprobadas]], [[HU-032-auditar-cambios-de-estado-de-cita]]
- Relacionadas: [[HU-025-consultar-mis-citas-y-detalle]], [[HU-005-autorizar-peticiones-por-rol-y-ownership]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** La operación en sí es una transición de estado sobre un agregado existente, pero obliga a formalizar la máquina de estados de la cita y su verificabilidad, a integrar la escritura de auditoría en la misma transacción y a resolver una condición de aplicabilidad que el PRD no define. El impacto se concentra en el dominio y no en el esquema.

## Tareas de desarrollo

- [ ] **T-01 — Formalizar la máquina de estados de la cita en el dominio**  
  Dificultad: Medio  
  Descripción: Declarar de forma explícita las transiciones admitidas hacia `COMPLETED` y `NO_SHOW`, los estados de origen válidos y los estados terminales que no admiten nueva transición, de modo que la validez de un cierre sea consultable y verificable (RN-11).

- [ ] **T-02 — Implementar la condición de cita cerrable**  
  Dificultad: Medio  
  Descripción: Regla de dominio que determina si una cita es aplicable para cierre, combinando su estado actual con la condición temporal que se decida para INC-018, aislada tras un único punto de decisión para poder cambiarla sin tocar el resto del caso de uso.

- [ ] **T-03 — Implementar el caso de uso de cierre de atención**  
  Dificultad: Medio  
  Descripción: Caso de uso que resuelve la cita, verifica la titularidad contra el profesional autenticado, aplica la regla de cita cerrable, ejecuta la transición al estado solicitado y escribe la entrada de historial en la misma transacción.

- [ ] **T-04 — Integrar la escritura del historial de estados**  
  Dificultad: Bajo  
  Descripción: Uso del puerto de auditoría de [[HU-032-auditar-cambios-de-estado-de-cita]] para registrar cita, estado nuevo, actor, fuente, fecha y motivo opcional, sin exponer la escritura del historial como una operación independiente del cierre.

- [ ] **T-05 — Exponer el adaptador REST de cierre**  
  Dificultad: Bajo  
  Descripción: Endpoint restringido al rol `PROFESSIONAL` que recibe el desenlace solicitado, con errores diferenciados para cita ajena, cita no cerrable todavía y cita en estado terminal.

- [ ] **T-06 — Añadir la acción de cierre a la agenda en citas-web**  
  Dificultad: Medio  
  Descripción: Acción de marcar atendida o inasistencia en la vista de agenda, habilitada solo para las citas que la API indica como cerrables, con confirmación previa y actualización del estado mostrado tras la operación.

- [ ] **T-07 — Pruebas del cierre de atención y su auditoría**  
  Dificultad: Medio  
  Descripción: Pruebas de dominio de las transiciones admitidas y prohibidas, y pruebas de integración para el cierre correcto en ambos desenlaces, el rechazo sobre cita no aplicable, el rechazo sobre estado terminal, el intento sobre cita ajena y la presencia de la entrada de historial resultante.

## Criterios de aceptación

### CA-01 — Cierre como cita atendida

**Dado** una cita propia del profesional autenticado que la API señala como cerrable  
**Cuando** el profesional la marca como atendida  
**Entonces** la cita queda en estado `COMPLETED`, la respuesta refleja el nuevo estado y una consulta posterior de la cita devuelve `COMPLETED`.

### CA-02 — Cierre como inasistencia

**Dado** una cita propia del profesional autenticado que la API señala como cerrable  
**Cuando** el profesional la marca como inasistencia  
**Entonces** la cita queda en estado `NO_SHOW`, la respuesta refleja el nuevo estado y una consulta posterior de la cita devuelve `NO_SHOW`.

### CA-03 — Cierre solo sobre citas propias

**Dado** una cita cerrable perteneciente al profesional B  
**Cuando** el profesional A autenticado intenta marcarla como `COMPLETED` o como `NO_SHOW`  
**Entonces** la API responde con un error de autorización, el estado de la cita de B no cambia y no se escribe ninguna entrada de historial.

### CA-04 — Cita todavía no cerrable rechazada

**Dado** una cita propia en estado `APPROVED` cuya hora de inicio es posterior al instante actual, y otra cuya hora de inicio ya llegó pero cuya franja aún no ha terminado  
**Cuando** el profesional intenta cerrar cada una  
**Entonces** para la primera la API responde con un error que indica que la cita todavía no puede cerrarse, el estado no cambia y no se escribe ninguna entrada de historial; la segunda sí se cierra, porque la condición es la hora de inicio y no el final de la franja, y no existe plazo máximo (D19).

### CA-05 — Cita en estado terminal rechazada

**Dado** una cita propia ya cerrada como `COMPLETED` y otra en estado `CANCELLED`  
**Cuando** el profesional intenta cerrar cada una de ellas  
**Entonces** la API responde en ambos casos con un error que indica que el estado actual no admite la transición, y ninguno de los dos estados cambia.

### CA-06 — Cada cierre escribe historial

**Dado** una cita propia cerrable  
**Cuando** el profesional la cierra en cualquiera de los dos desenlaces  
**Entonces** se crea exactamente una entrada de historial para esa cita con el estado nuevo aplicado, el identificador del profesional como actor, la fuente del cambio y la fecha y hora del cierre (RF-19).

### CA-07 — La transición es explícita y verificable

**Dado** la definición de la máquina de estados de la cita en el dominio  
**Cuando** se consulta qué estados de origen admiten la transición a `COMPLETED` y a `NO_SHOW`  
**Entonces** la respuesta es determinista y consultable desde el propio dominio, y toda transición ejecutada por el caso de uso corresponde a una de las declaradas (RN-11).

### CA-08 — El cierre y su historial son atómicos

**Dado** un cierre de atención en curso  
**Cuando** la escritura de la entrada de historial falla  
**Entonces** la transición de estado de la cita tampoco se persiste, y una consulta posterior devuelve la cita en su estado anterior sin entrada de historial huérfana.

## Definition of Done

- [ ] Los criterios CA-01 a CA-08 están validados con evidencia concreta.
- [ ] La máquina de estados de la cita está declarada en el dominio, sin dependencias de Spring ni de JPA, y es la única fuente que autoriza una transición de cierre.
- [ ] La condición de cita cerrable está aislada en un único punto del dominio, de modo que la decisión de INC-018 pueda aplicarse sin cambiar el caso de uso.
- [ ] La transición de estado y la escritura de la entrada de historial ocurren en la misma transacción.
- [ ] El endpoint de cierre exige rol `PROFESSIONAL` y verifica la titularidad de la cita contra el usuario autenticado.
- [ ] El historial de estados no se expone como un recurso editable ni borrable por la API (RN-12).
- [ ] La acción de cierre en `citas-web` solo se ofrece sobre las citas que la API señala como cerrables, y el estado mostrado se actualiza tras la operación.
- [ ] Existen pruebas automatizadas de ambos desenlaces de cierre, del rechazo por estado terminal y de la escritura del historial, y pasan.
- [ ] El contrato del endpoint de cierre está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [ ] La trazabilidad de esta HU y de [[EP-005-agenda-del-profesional]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Ejecución de referencia del **2026-09-30**: el `backend-verifier`, agente independiente que no escribió el código, reejecutó la suite completa de `citas-api` → **484 pruebas, 0 fallos, 0 errores, `BUILD SUCCESS`**. El `frontend-verifier` reejecutó la de `citas-web` → **218 pruebas**, con typecheck, `oxlint` y build limpios, y dejó **14 hallazgos abiertos** (3 en reparación y 4 pruebas que faltan).

Ruta abreviada: **PAIT** = `src/test/java/com/fcv/citas/infrastructure/rest/ProfessionalAgendaIntegrationTest.java`. Los números de línea son los del árbol de trabajo del 2026-09-30.

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | PAIT:344 `completesAnOwnStartedAppointmentAndRecordsTheProfessional`: la agenda marca la cita con `closable = true`, `POST …/complete` responde 200 con `status = COMPLETED` y `closable = false`, la tabla `appointments` queda en `COMPLETED`, y una consulta posterior por otra vía (`GET /api/admin/appointments/{id}`) también la ve `COMPLETED` | La consulta posterior que exige el criterio se hace por un endpoint distinto del que cerró |
| CA-02 | Cumple | PAIT:379 `marksAnOwnStartedAppointmentAsNoShow`: `POST …/no-show` → 200 con `status = NO_SHOW` y `closable = false`; la tabla queda en `NO_SHOW` | — |
| CA-03 | Cumple | PAIT:419 `aProfessionalCannotCloseAnotherProfessionalsAppointment`: A intenta cerrar la cita de B como `COMPLETED` y como `NO_SHOW` → **404** (el mismo que un id inexistente, política de `application/shared/Ownership`), el estado de la cita de B no cambia y el recuento de historial queda igual | El criterio admite «un error de autorización»; el código responde 404 a propósito, para no revelar que la cita de otro existe |
| CA-04 | Cumple | PAIT:438 `closingDependsOnTheStartTimeNotTheEnd`: la cita futura → 409 `APPOINTMENT_NOT_STARTED` en los dos desenlaces, sin cambio de estado y **sin ninguna fila de historial**; la cita cuya hora de inicio ya llegó y cuya franja **no** ha terminado sí se cierra (D19) | Cubre exactamente las dos situaciones que el criterio contrapone |
| CA-05 | Cumple | PAIT:456 `nonApprovedAppointmentsCannotBeClosed`: `COMPLETED`, `CANCELLED`, `NO_SHOW`, `REJECTED` y `REQUESTED` → 409 `INVALID_TRANSITION` en los dos desenlaces, sin cambio de estado ni historial | El criterio nombra dos estados y la prueba cubre cinco |
| CA-06 | Cumple | PAIT:344: el cierre añade **exactamente una** fila a `appointment_status_history` con `status = COMPLETED`, `actor_user_id` = el usuario del profesional, `source = 'PROFESSIONAL'` (el valor que D8 añadió al ENUM en `V5`) y `changed_at` no nulo; PAIT:379 comprueba el mismo `source` para `NO_SHOW` | Se verifica contra la tabla, no solo contra la respuesta |
| CA-07 | Cumple | PAIT:471 `theClosingTransitionsAreDeclaredInTheDomain`: recorriendo `AppointmentStatus.values()`, el **único** estado de origen que admite `COMPLETED` y `NO_SHOW` es `APPROVED`; `AppointmentStatus:19-29` (`allowedNext()` y `canTransitionTo`), del que `isTerminal` se **deriva** para que no existan dos listas | La prueba consulta el dominio, sin Spring ni base de datos, que es lo que RN-11 pide de «consultable» |
| CA-08 | Cumple | PAIT:484 `aFailureWritingTheHistoryRollsBackTheStatusChange`: con un espía sobre el adaptador real se fuerza que la inserción del historial falle por la FK `fk_ash_actor` **después** de actualizar el estado; la respuesta es ≥ 400, la cita sigue `APPROVED` y el recuento de historial no cambia | El fallo se provoca en el punto exacto que el criterio describe, no con una excepción genérica antes de empezar |
| DoD — CA-01 a CA-08 validados con evidencia concreta | Cumple | Filas CA-01 a CA-08 de esta tabla | Los ocho son de backend y todos tienen prueba propia |
| DoD — Máquina de estados declarada en el dominio, sin Spring ni JPA, y única fuente que autoriza el cierre | Cumple | `domain/appointment/AppointmentStatus:19-29` (`allowedNext()`, `canTransitionTo`); `Appointment#complete:141` y `Appointment#noShow:146`, los dos por `canTransitionTo`; `HexagonalArchitectureTest`; `S3DebtIntegrationTest` comprueba que `is_terminal` del catálogo de `V4` coincide con lo derivado del enum | Una sola definición de estados terminales, compartida con [[HU-026-cancelar-una-cita-futura]] y [[HU-032-auditar-cambios-de-estado-de-cita]] |
| DoD — Condición de cita cerrable aislada en un único punto del dominio | Cumple | `Appointment#hasStartedAt(now)`, usado tanto por la regla de cierre como por el `closable` que la agenda publica: cambiar el umbral de D19 no toca el caso de uso; PAIT:438 y PAIT:344 (el `closable` de la respuesta y el comportamiento del cierre nunca se contradicen) | El mismo predicado alimenta la decisión y lo que ve la interfaz |
| DoD — Transición de estado y escritura del historial en la misma transacción | Cumple | `ProfessionalAppointmentsUseCase` ejecuta el cierre dentro de `tx.inTransaction`, con `AppointmentRepository#apply(Transition)` escribiendo estado e historial juntos; PAIT:484 | — |
| DoD — El endpoint exige rol `PROFESSIONAL` y verifica la titularidad contra el usuario autenticado | Cumple | `SecurityConfig:84`; PAIT:618 `onlyProfessionalsClose`; PAIT:419 (titularidad vía `Ownership`) | — |
| DoD — El historial no se expone como recurso editable ni borrable por la API (RN-12) | Cumple | Ningún controlador expone el historial como recurso: solo se lee incrustado en el detalle de cita (`JdbcAppointmentQueries:117`), y no hay `PUT`, `PATCH` ni `DELETE` sobre él; `StatusHistoryJpaEntity` no tiene ningún método de modificación; `V5__audit_history_append_only.sql` cambia `fk_ash_appointment` a `ON DELETE RESTRICT`, así que una cita con historial no se puede borrar físicamente; `HistoryWritersArchitectureTest` comprueba sobre el **bytecode** que la fila de historial solo la puede componer el dominio | Hueco declarado por el propio `HistoryWritersArchitectureTest`: un `INSERT` nativo escrito a mano contra la tabla no lo ve ArchUnit. No es una vía de la API |
| DoD — La acción de cierre en `citas-web` solo se ofrece sobre citas cerrables y el estado se actualiza tras la operación | Pendiente | Existen `citas-web/src/pages/professional/AppointmentsPanel.tsx` y `ConfirmDialog`, con las pruebas `src/professionalAppointments.test.tsx:169` (atendida tras confirmar, la cita sale de la lista), `:191` (inasistencia), `:204` (una cita que aún no empezó tiene los botones deshabilitados con el motivo visible), `:217` (409 `APPOINTMENT_NOT_STARTED`) y `:240` (409 `INVALID_TRANSITION`) | No se marca `Cumple`: la verificación de frontend criterio a criterio la hace el `frontend-verifier`, que dejó 14 hallazgos abiertos (3 en reparación, 4 pruebas que faltan), y falta la prueba manual en navegador de F10 |
| DoD — Pruebas de ambos desenlaces, del rechazo por estado terminal y de la escritura del historial, y pasan | Cumple | PAIT:344, :379, :456 y :484; más PAIT:396, :406, :438, :471, :548, :578, :597 y :618; suite completa 484/484 `BUILD SUCCESS` reejecutada por el `backend-verifier` | — |
| DoD — Contrato del endpoint de cierre reflejado en [[HU-033-publicar-contrato-rest-documentado]] | Cumple | `citas-api/docs/wiki/llm-wiki/wiki/contrato-rest-citas.md:251` (`POST /api/professional/appointments/{id}/complete` → 200 · 404 si no es suya · 409 `INVALID_TRANSITION` / `APPOINTMENT_NOT_STARTED`, y el efecto de **D38**) y `:252` (`…/no-show`) | `llm-wiki/` queda fuera del límite de escritura de esta skill: la evidencia se leyó, no se produjo aquí |
| DoD — Trazabilidad de esta HU y de [[EP-005-agenda-del-profesional]] actualizada | Cumple | Esta matriz, el historial de validación y las notas (D19, D38 e INC-017 registradas, y el hueco de las citas ya cerradas anotado); en [[EP-005-agenda-del-profesional]] las anotaciones de INC-017 e INC-018; `docs/wiki/scrum/README.md` | — |

## Historial de validación

- 2026-09-30 — **Matriz de evidencia recolectada del repositorio.** CA-01 a CA-08 y toda la DoD de backend, de contrato y de trazabilidad en `Cumple`. Estado: `Aprobada` → `En validación`. **No pasa a `Completada`**: el ítem de DoD de la acción de cierre en `citas-web` queda en `Pendiente` porque la verificación de frontend criterio a criterio y la prueba manual en navegador de F10 no se han hecho.
- 2026-09-30 — Anotado en "Fuera de alcance", con su causa, que el profesional **no puede consultar sus citas ya cerradas**: la agenda solo trae `APPROVED` por el alcance de RF-16 y de [[HU-020-consultar-agenda-de-citas-aprobadas]]. No es un defecto ni una regresión; es un hueco que ninguna HU cubre y que conviene que esté escrito antes de cerrar S4. No cambia ningún criterio ni la DoD.
- 2026-09-25 — CA-04 ajustado a D19: la condición de "todavía no cerrable" deja de ser genérica y pasa a ser "la hora de inicio aún no ha llegado"; se añade el caso de una cita ya empezada y no terminada, que sí se cierra. En el contexto se corrige la frase sobre la fuente del historial, que D8 ya fijó como `PROFESSIONAL`.
- 2026-09-25 — Aprobada por **aprobación delegada** del usuario para S4 (D15, PLAN_RETOMA_S4.md). Alcance en `PLAN_RETOMA_S4.md` §3 (bloque «Profesional», fase F4) y decisiones D15–D30 en [[dec-006-decisiones-s4-ciclo-de-vida]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- **Resuelta (D19, respondida por el usuario):** INC-018 (ver [[EP-005-agenda-del-profesional]]): una cita `APPROVED` se puede cerrar como `COMPLETED` o `NO_SHOW` **desde su hora de inicio**, sin esperar al final de la franja y **sin plazo máximo**. La condición vive en un único punto de decisión (T-02), para poder cambiarla sin tocar el caso de uso ([[dec-006-decisiones-s4-ciclo-de-vida]]). CA-04 lo refleja.
- Incógnita **INC-017** (ver [[EP-005-agenda-del-profesional]]): no la resuelve ninguna decisión D15–D30. El punto de corte de D19 debe usar el mismo reloj que las demás reglas de "futuro" del sistema (`PLAN_RETOMA_S4.md` §5: `America/Bogota`, ver [[riesgo-zona-horaria-columnas-time]]).
- **Resuelta antes de S4 (D8 de [[dec-004-decisiones-s3-reserva]]):** la fuente del historial para el cierre es `PROFESSIONAL`, añadida al ENUM `source` por `V5__audit_history_append_only.sql`. CA-06 debe verificarse con ese valor.
- RF-19 permite un motivo opcional en el historial. El PRD no exige motivo para el cierre de atención, a diferencia del rechazo administrativo de RN-04, por lo que esta HU no lo hace obligatorio.
- **Efecto añadido por D38 (2026-09-30), sin criterio propio en esta HU:** si la cita que se cierra tiene una solicitud de reprogramación `PENDING`, el cierre la cancela **en la misma transacción** (decisor = el profesional, motivo automático «Cita cerrada por el profesional») y libera su retención, reutilizando el mismo camino de liberación que D18. Sin eso la franja propuesta quedaba retenida sin salida, porque [[HU-031-aprobar-o-rechazar-reprogramacion]] CA-05 exige una cita `APPROVED` para poder decidir. La agenda publica `pendingReschedule` para que la pantalla pueda advertirlo antes de confirmar. Probado en `ProfessionalAgendaIntegrationTest:523`, `:548`, `:578` y `:597`, y documentado en `contrato-rest-citas.md:251-252` y `:260`. Cerrar **no** libera los slots de la propia cita, porque `COMPLETED` y `NO_SHOW` no liberan (`ProfessionalAgendaIntegrationTest:406`). D38 no añade criterio de aceptación aquí; si el usuario quiere que el cierre se **bloquee** en lugar de cancelar la solicitud, es un cambio de decisión y entonces sí haría falta un CA.
