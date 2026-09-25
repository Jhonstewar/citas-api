---
id: HU-021
tipo: historia-de-usuario
titulo: "Registrar el cierre de atención"
estado: Aprobada
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

- 2026-09-25 — CA-04 ajustado a D19: la condición de "todavía no cerrable" deja de ser genérica y pasa a ser "la hora de inicio aún no ha llegado"; se añade el caso de una cita ya empezada y no terminada, que sí se cierra. En el contexto se corrige la frase sobre la fuente del historial, que D8 ya fijó como `PROFESSIONAL`.
- 2026-09-25 — Aprobada por **aprobación delegada** del usuario para S4 (D15, PLAN_RETOMA_S4.md). Alcance en `PLAN_RETOMA_S4.md` §3 (bloque «Profesional», fase F4) y decisiones D15–D30 en [[dec-006-decisiones-s4-ciclo-de-vida]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- **Resuelta (D19, respondida por el usuario):** INC-018 (ver [[EP-005-agenda-del-profesional]]): una cita `APPROVED` se puede cerrar como `COMPLETED` o `NO_SHOW` **desde su hora de inicio**, sin esperar al final de la franja y **sin plazo máximo**. La condición vive en un único punto de decisión (T-02), para poder cambiarla sin tocar el caso de uso ([[dec-006-decisiones-s4-ciclo-de-vida]]). CA-04 lo refleja.
- Incógnita **INC-017** (ver [[EP-005-agenda-del-profesional]]): no la resuelve ninguna decisión D15–D30. El punto de corte de D19 debe usar el mismo reloj que las demás reglas de "futuro" del sistema (`PLAN_RETOMA_S4.md` §5: `America/Bogota`, ver [[riesgo-zona-horaria-columnas-time]]).
- **Resuelta antes de S4 (D8 de [[dec-004-decisiones-s3-reserva]]):** la fuente del historial para el cierre es `PROFESSIONAL`, añadida al ENUM `source` por `V5__audit_history_append_only.sql`. CA-06 debe verificarse con ese valor.
- RF-19 permite un motivo opcional en el historial. El PRD no exige motivo para el cierre de atención, a diferencia del rechazo administrativo de RN-04, por lo que esta HU no lo hace obligatorio.
