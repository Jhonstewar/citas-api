---
id: HU-027
tipo: historia-de-usuario
titulo: "Solicitar la reprogramación de una cita aprobada"
estado: Aprobada
epica: "[[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]"
requisitos: [RF-15]
esfuerzo: "Alto"
sprint_sugerido: "Sprint 7"
dependencias:
  - "[[HU-025-consultar-mis-citas-y-detalle]]"
  - "[[HU-022-buscar-disponibilidad-con-filtros]]"
relacionadas:
  - "[[HU-031-aprobar-o-rechazar-reprogramacion]]"
  - "[[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]]"
---

# HU-027 — Solicitar la reprogramación de una cita aprobada

## Historia de usuario

**COMO** USER autenticado con una cita aprobada y futura
**QUIERO** solicitar mover una cita aprobada a otro horario disponible
**PARA** cambiar la fecha sin perder la cita que ya tengo

> Como USER autenticado con una cita aprobada y futura, quiero solicitar mover una cita aprobada a otro horario disponible para cambiar la fecha sin perder la cita que ya tengo.

## Contexto y descripción

RF-15 describe la reprogramación como una solicitud, no como un cambio inmediato. Solo una cita `APPROVED` y futura puede originarla, la solicitud conserva profesional y especialidad, nace en estado `PENDING` y la nueva franja se retiene mientras espera decisión. RN-10 añade la garantía que da sentido a toda la historia: la cita original conserva su franja hasta que ADMIN decida, de modo que el paciente nunca queda sin cita por haber intentado moverla.

La consecuencia directa es que mientras la solicitud está `PENDING` el sistema mantiene dos franjas retenidas para el mismo paciente: la original y la propuesta. Es una ocupación deliberada y temporal, y se resuelve en [[HU-031-aprobar-o-rechazar-reprogramacion]]. Esta HU introduce además la tabla de solicitudes de reprogramación mediante migración Flyway, con su estado y las fechas anterior y propuesta, porque la información no cabe en la propia cita sin destruir el dato original.

## Alcance

- Endpoint de creación de una solicitud de reprogramación sobre una cita propia en `citas-api`.
- Validación de que la cita origen está `APPROVED` y es futura.
- Conservación obligatoria del profesional y de la especialidad de la cita original.
- Creación de la solicitud en estado `PENDING`.
- Retención de la nueva franja propuesta mientras la solicitud está `PENDING`.
- Conservación intacta de la franja y del estado de la cita original.
- Validación de la nueva franja: no pasada, libre y con dos slots consecutivos cuando la duración es de 60 minutos.
- Migración Flyway de la tabla de solicitudes de reprogramación con estado, fecha y hora anterior y fecha y hora propuesta.
- Pantalla "solicitar reprogramación" en `citas-web` (PRD §6).

## Fuera de alcance

- Aprobación o rechazo de la solicitud, que se cubre en [[HU-031-aprobar-o-rechazar-reprogramacion]].
- Decisión del paciente tras un rechazo, que se cubre en [[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]].
- Cambio de profesional: RF-15 lo trata como una cita nueva, que se crea desde [[HU-022-buscar-disponibilidad-con-filtros]].
- Cambio de especialidad, por la misma razón.
- Reprogramación de citas `REQUESTED`, `CANCELLED`, `REJECTED`, `COMPLETED` o `NO_SHOW`: RF-15 la limita a `APPROVED`.
- Notificación por correo de la solicitud: pertenece a la automatización posterior de PRD §10.

## Reglas de negocio

- Solo una cita en estado `APPROVED` y con fecha futura admite solicitud de reprogramación (RF-15).
- La solicitud conserva el profesional y la especialidad de la cita original; cambiar de profesional se trata como una cita nueva (RF-15).
- La solicitud nace en estado `PENDING` (RF-15).
- La nueva franja queda retenida mientras la solicitud está `PENDING` y ninguna otra cita puede ocuparla (RF-15, RN-01).
- La cita original conserva su franja y su estado `APPROVED` hasta la decisión administrativa (RN-10).
- La nueva fecha y hora no pueden estar en el pasado (RN-06).
- Si la duración de la especialidad es de 60 minutos, la nueva franja exige dos slots de 30 minutos consecutivos y disponibles (RN-05, RF-09).
- La nueva franja no puede ocupar slots ya reservados o retenidos por otra cita o solicitud (RN-01).
- Un usuario solo puede solicitar la reprogramación de sus propias citas (PRD §8, ownership).

## Dependencias y relaciones

- Épica: [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]
- Dependencias: [[HU-025-consultar-mis-citas-y-detalle]], [[HU-022-buscar-disponibilidad-con-filtros]]
- Relacionadas: [[HU-031-aprobar-o-rechazar-reprogramacion]], [[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]], [[HU-029-consultar-bandeja-administrativa]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Alto

**Justificación de dificultad:** Es la única operación del producto que mantiene dos franjas retenidas simultáneamente para la misma cita, y hacerlo sin romper RN-01 exige que la retención de la nueva franja compita correctamente con cualquier otra reserva concurrente. Añade además un agregado nuevo con su propia migración Flyway, un conjunto de validaciones que reutiliza las reglas de reserva de la épica de disponibilidad, y una pantalla que combina el detalle de la cita con la búsqueda de huecos del mismo profesional.

## Tareas de desarrollo

- [ ] **T-01 — Modelar el agregado de solicitud de reprogramación en el dominio**
  Dificultad: Alto
  Descripción: Entidad de dominio con referencia a la cita original, fecha y hora anterior, fecha y hora propuesta y estado, con las invariantes de cita `APPROVED` y futura, profesional y especialidad conservados, y fecha propuesta no pasada, sin dependencias de framework.

- [ ] **T-02 — Crear la migración Flyway de solicitudes de reprogramación**
  Dificultad: Medio
  Descripción: Migración que crea la tabla de solicitudes con referencia a la cita, estado de reprogramación tomado del catálogo fijo de RF-05, fecha y hora anterior, fecha y hora propuesta, motivo de la decisión y marcas temporales, con las claves foráneas correspondientes.

- [ ] **T-03 — Implementar el caso de uso de solicitud de reprogramación**
  Dificultad: Alto
  Descripción: Caso de uso que valida propiedad y estado de la cita, verifica la disponibilidad de la nueva franja, retiene sus slots, crea la solicitud en `PENDING` y deja intacta la cita original, todo en una única transacción.

- [ ] **T-04 — Implementar la retención de la nueva franja en persistencia**
  Dificultad: Alto
  Descripción: Adaptador que reserva los slots propuestos de forma que dos solicitudes o reservas concurrentes sobre la misma franja no puedan tener éxito a la vez, y que distingue la retención provisional de la ocupación definitiva.

- [ ] **T-05 — Exponer el adaptador REST de solicitud de reprogramación**
  Dificultad: Medio
  Descripción: Endpoint autenticado que recibe la cita y la nueva fecha y hora, con respuestas diferenciadas para cita no `APPROVED`, cita pasada, franja pasada, franja ocupada, ausencia de dos slots consecutivos e intento de cambio de profesional.

- [ ] **T-06 — Construir la pantalla de solicitar reprogramación en citas-web**
  Dificultad: Medio
  Descripción: Vista que parte del detalle de la cita, ofrece únicamente los huecos disponibles del mismo profesional y especialidad, impide seleccionar otro profesional, y muestra el estado `PENDING` resultante junto con la cita original que se conserva.

- [ ] **T-07 — Pruebas de solicitud y de doble retención**
  Dificultad: Alto
  Descripción: Pruebas de dominio para las invariantes, y pruebas de integración para la doble retención simultánea, la franja de 60 minutos con dos slots consecutivos, la franja pasada, la franja ya ocupada, la cita no aprobada y la cita ajena.

## Criterios de aceptación

### CA-01 — Solo una cita APPROVED y futura admite solicitud

**Dado** citas propias en estados `REQUESTED`, `CANCELLED`, `REJECTED` y `COMPLETED`, y una cita `APPROVED` con fecha ya pasada
**Cuando** el usuario intenta solicitar reprogramación sobre cada una de ellas
**Entonces** la API rechaza todas las peticiones con un error de regla de negocio y no crea ninguna solicitud ni retiene ninguna franja.

### CA-02 — La solicitud conserva profesional y especialidad

**Dado** una cita `APPROVED` y futura con un profesional y una especialidad concretos
**Cuando** el usuario envía una solicitud de reprogramación indicando un profesional distinto del de la cita original
**Entonces** la API rechaza la petición indicando que el cambio de profesional se trata como una cita nueva (RF-15), y la solicitud creada en el caso válido conserva exactamente el profesional y la especialidad de la cita original.

### CA-03 — La solicitud nace en estado PENDING

**Dado** una cita `APPROVED` y futura y una nueva franja válida del mismo profesional
**Cuando** el usuario envía la solicitud de reprogramación
**Entonces** la API responde con éxito y la solicitud queda persistida en estado `PENDING`, con la fecha y hora anterior y la fecha y hora propuesta registradas.

### CA-04 — La nueva franja queda retenida mientras la solicitud está PENDING

**Dado** una solicitud de reprogramación en estado `PENDING` sobre una franja concreta
**Cuando** otro paciente busca disponibilidad e intenta reservar esa misma franja
**Entonces** la franja no aparece como disponible y cualquier intento directo de reservarla se rechaza (RN-01).

### CA-05 — La cita original conserva su franja y su estado

**Dado** una solicitud de reprogramación en estado `PENDING`
**Cuando** se consulta la cita original y la ocupación de sus slots
**Entonces** la cita sigue en estado `APPROVED`, sus slots originales siguen ocupados por ella, y quedan retenidas simultáneamente la franja original y la franja propuesta (RN-10).

### CA-06 — La nueva franja no puede estar en el pasado

**Dado** una cita `APPROVED` y futura
**Cuando** el usuario propone como nueva fecha y hora un instante anterior al momento actual
**Entonces** la API responde con un error de regla de negocio, no se crea la solicitud y no se retiene ningún slot (RN-06).

### CA-07 — Duración de 60 minutos exige dos slots consecutivos

**Dado** una cita de una especialidad de 60 minutos y una franja propuesta cuyo slot siguiente está ocupado o no existe
**Cuando** el usuario envía la solicitud sobre esa franja
**Entonces** la API responde con un error de regla de negocio, no se crea la solicitud y no se retiene el primer slot; y cuando los dos slots consecutivos sí están libres, la solicitud se crea reteniendo ambos (RN-05).

### CA-08 — La nueva franja no puede pisar slots ocupados o retenidos

**Dado** una franja del mismo profesional ya ocupada por otra cita `APPROVED` o retenida por una cita `REQUESTED`
**Cuando** el usuario la propone como nueva fecha y hora
**Entonces** la API responde con un error de regla de negocio y no se crea la solicitud (RN-01).

### CA-09 — El esquema de solicitudes se crea por migración versionada

**Dado** una base de datos MySQL 8.4 con las migraciones previas aplicadas
**Cuando** se arranca `citas-api`
**Entonces** Flyway aplica la migración que crea la tabla de solicitudes de reprogramación con estado, fecha y hora anterior y fecha y hora propuesta, y el arranque finaliza sin error.

### CA-10 — Una sola solicitud sin decidir por cita

**Dado** una cita `APPROVED` y futura con una solicitud de reprogramación `PENDING`
**Cuando** el usuario envía una segunda solicitud sobre la misma cita
**Entonces** la API responde 409, no se crea la segunda solicitud y no se retiene ninguna franja nueva; y una vez que ADMIN decide la primera (aprobada o rechazada), una nueva solicitud sobre la misma cita, si sigue `APPROVED` y futura, sí se admite (D20).

## Definition of Done

- [ ] Los criterios CA-01 a CA-10 están validados con evidencia concreta.
- [ ] Existe una migración Flyway versionada para la tabla de solicitudes de reprogramación, aplicada sobre el esquema existente sin pérdida de datos.
- [ ] Está demostrado con una prueba que, con la solicitud en `PENDING`, la cita original conserva su franja y la franja propuesta está retenida al mismo tiempo (RN-10).
- [ ] La retención de la nueva franja usa el mismo mecanismo de reserva que la creación de citas, de modo que RN-01 se cumple frente a peticiones concurrentes.
- [ ] El agregado de solicitud de reprogramación vive en el dominio sin depender de Spring ni de JPA.
- [ ] La creación de la solicitud, la retención de slots y la persistencia ocurren en una única transacción, sin resultados parciales.
- [ ] La pantalla de `citas-web` no permite seleccionar un profesional distinto del de la cita original.
- [ ] Existen pruebas automatizadas de la solicitud válida, de la franja pasada, de la franja ocupada, de los dos slots consecutivos y del intento de cambio de profesional, y pasan.
- [ ] El contrato del endpoint de solicitud de reprogramación está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
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
| CA-09 | Pendiente | — | — |
| CA-10 | Pendiente | — | — |
| DoD | Pendiente | — | — |

## Historial de validación

- 2026-09-25 — Se añade CA-10 por D20 (una solicitud sin decidir por cita; tras la decisión se admite otra), que ningún criterio cubría; la DoD pasa a CA-01 a CA-10. Ningún criterio existente se reescribe. Queda anotada en notas una divergencia entre CA-03 / CA-09 y el esquema V3 que no resuelve ninguna decisión.
- 2026-09-25 — Aprobada por **aprobación delegada** del usuario para S4 (D15, PLAN_RETOMA_S4.md). Alcance en `PLAN_RETOMA_S4.md` §3 (bloque «Ciclo de vida del paciente», fase F5 / LOOP_02) y decisiones D15–D30 en [[dec-006-decisiones-s4-ciclo-de-vida]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- **Resuelta (D20, provisional bajo delegación):** INC-028 (ver [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]): **una** solicitud sin decidir por cita, que el esquema ya impone con `uq_reschedule_requests_active` (`V3__schedule_and_appointments.sql:162`); tras la decisión se puede pedir otra ([[dec-006-decisiones-s4-ciclo-de-vida]]). CA-10 lo cubre.
- **Resuelta (D20, provisional bajo delegación):** INC-029 (ver [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]): el paciente **no retira** su solicitud en S4; si ya no la quiere, puede cancelar la cita ([[HU-026-cancelar-una-cita-futura]], D18).
- **Resuelta (D21, provisional bajo delegación):** INC-031 (ver [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]): la nueva franja **puede estar en otra sede** si el profesional atiende en ella (RN-07); RF-15 solo obliga a conservar profesional y especialidad ([[dec-006-decisiones-s4-ciclo-de-vida]]). CA-02 no se amplía.
- **Resuelta (D18, respondida por el usuario):** N1 — si el paciente cancela la cita mientras la solicitud está `PENDING`, la solicitud pasa a `CANCELLED` y se liberan las dos franjas en la misma transacción. Lo verifica CA-09 de [[HU-026-cancelar-una-cita-futura]].
- **Divergencia con el esquema, sin decisión que la cubra:** `reschedule_requests` existe desde `V3__schedule_and_appointments.sql` (T-02 no crea tabla y CA-09 se valida con V3), pero solo guarda la franja **propuesta** (`proposed_date`, `proposed_start_time`, `proposed_end_time`); **no tiene columnas de fecha y hora anterior**. CA-03 y CA-09 piden la "fecha y hora anterior registradas" en la solicitud. Mientras la solicitud está `PENDING` la franja anterior es la de la cita, y tras aprobarla D22 la deja escrita en el motivo del historial; pero literalmente CA-03 y CA-09 no se cumplen con V3. No se reescriben en silencio: al implementar F5 hay que elegir entre ajustar ambos criterios a "la anterior se obtiene de la cita mientras está `PENDING` y queda en el historial al aprobar (D22)", o añadir columnas con una migración (el plan prevé escalamiento humano ante cualquier migración).
- El estado `PENDING` proviene del catálogo fijo de estados de reprogramación de RF-05 y no se define aquí.
