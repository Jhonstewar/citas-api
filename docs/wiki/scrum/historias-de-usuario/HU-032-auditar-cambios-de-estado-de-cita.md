---
id: HU-032
tipo: historia-de-usuario
titulo: "Auditar los cambios de estado de cita"
estado: Borrador
epica: "[[EP-009-trazabilidad-y-contrato-rest]]"
requisitos: [RF-19]
esfuerzo: "Alto"
sprint_sugerido: "Sprint 5"
dependencias:
  - "[[HU-005-autorizar-peticiones-por-rol-y-ownership]]"
relacionadas:
  - "[[HU-023-agendar-cita-de-medicina-general]]"
  - "[[HU-024-solicitar-cita-especializada]]"
  - "[[HU-026-cancelar-una-cita-futura]]"
  - "[[HU-030-aprobar-o-rechazar-cita-especializada]]"
  - "[[HU-031-aprobar-o-rechazar-reprogramacion]]"
  - "[[HU-021-registrar-cierre-de-atencion]]"
---

# HU-032 — Auditar los cambios de estado de cita

## Historia de usuario

**COMO** ADMIN responsable de la operación  
**QUIERO** que cada cambio de estado de una cita quede registrado con estado nuevo, actor, origen, fecha y hora y motivo, sin posibilidad de editarlo ni borrarlo  
**PARA** poder responder con evidencia quién cambió qué cita, cuándo y por qué

> Como ADMIN responsable de la operación, quiero que cada cambio de estado de una cita quede registrado con estado nuevo, actor, origen, fecha y hora y motivo, sin posibilidad de editarlo ni borrarlo, para poder responder con evidencia quién cambió qué cita, cuándo y por qué.

## Contexto y descripción

RF-19 exige que todo cambio de estado de cita guarde cita, estado nuevo, actor cuando existe, origen `SYSTEM`/`USER`/`ADMIN`, fecha y hora y motivo opcional. RN-11 exige que las transiciones sean explícitas y verificables, y RN-12 que los datos de auditoría no se modifiquen como CRUD normal.

La tabla ya existe en V3: `appointment_status_history`, append-only por diseño, con `actor_user_id` nulo permitido solo para origen `SYSTEM` (restricción `ck_ash_actor`). Esta HU construye las piezas transversales que el resto de HU usan: el modelo explícito de transiciones de estado de cita, el puerto de dominio que registra cada transición dentro de la misma transacción del cambio, la ausencia deliberada de operaciones de edición y borrado, y la consulta del historial.

Se adelanta al Sprint 5, antes de [[HU-023-agendar-cita-de-medicina-general]] y [[HU-024-solicitar-cita-especializada]], para que ninguna cita nazca sin historial.

## Alcance

- Máquina de estados de cita en el dominio con las transiciones permitidas del PRD: creación → `APPROVED` (general, origen `SYSTEM`), creación → `REQUESTED` (especializada, origen `USER`), `REQUESTED` → `APPROVED` / `REJECTED` (`ADMIN`), `REQUESTED` / `APPROVED` → `CANCELLED` (`USER`), `APPROVED` → `COMPLETED` / `NO_SHOW` (profesional).
- Estados terminales tomados del catálogo `appointment_statuses.is_terminal` (V4), compartidos con todas las HU.
- Puerto de dominio de registro de transición y adaptador de persistencia de solo inserción sobre `appointment_status_history`.
- Motivo obligatorio cuando la transición es un rechazo administrativo (RN-04).
- Endpoint REST de consulta del historial de una cita, en orden cronológico.
- Ausencia de endpoints y de métodos de repositorio de actualización o borrado del historial.
- Vista del historial en el detalle de cita del dashboard ADMIN de `citas-web`.

## Fuera de alcance

- Las transiciones concretas de cada flujo, que implementan [[HU-023-agendar-cita-de-medicina-general]], [[HU-024-solicitar-cita-especializada]], [[HU-026-cancelar-una-cita-futura]], [[HU-030-aprobar-o-rechazar-cita-especializada]], [[HU-031-aprobar-o-rechazar-reprogramacion]] y [[HU-021-registrar-cierre-de-atencion]].
- Auditoría de entidades distintas de la cita (usuarios, catálogos, bloques): RF-19 solo exige la cita.
- Workflows n8n que reaccionan a cambios de estado (PRD §10).
- Exportación del historial: no está en el PRD.

## Reglas de negocio

- Todo cambio de estado de cita guarda cita, estado nuevo, actor cuando existe, origen, fecha y hora y motivo opcional (RF-19).
- Las transiciones son explícitas y verificables; no se permiten cambios de estado arbitrarios (RN-11).
- Los datos de auditoría no se modifican ni se borran como CRUD normal (RN-12).
- El origen es `SYSTEM` cuando la transición no la ejecuta una persona, como la aprobación automática (RN-02); en ese caso el actor puede ser nulo.
- Con origen `USER` o `ADMIN` el actor es obligatorio (restricción de V3).
- Un rechazo administrativo exige motivo, que se conserva en el historial (RN-04).
- Un estado terminal no admite nuevas transiciones (catálogo de RF-05).
- Cambio de estado e historial se confirman juntos o no se confirman.

## Dependencias y relaciones

- Épica: [[EP-009-trazabilidad-y-contrato-rest]]
- Dependencias: [[HU-005-autorizar-peticiones-por-rol-y-ownership]]
- Relacionadas: [[HU-023-agendar-cita-de-medicina-general]], [[HU-024-solicitar-cita-especializada]], [[HU-025-consultar-mis-citas-y-detalle]], [[HU-026-cancelar-una-cita-futura]], [[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]], [[HU-030-aprobar-o-rechazar-cita-especializada]], [[HU-031-aprobar-o-rechazar-reprogramacion]], [[HU-021-registrar-cierre-de-atencion]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Alto

**Justificación de dificultad:** Es una pieza transversal de la que dependen seis HU: el modelo de transiciones debe ser único y correcto desde el principio, el registro debe participar en la transacción de cada caso de uso sin convertirse en una operación independiente, y la inmutabilidad debe garantizarse por diseño (sin rutas de escritura) y no solo por convención. Un error aquí contamina toda la trazabilidad del producto.

## Tareas de desarrollo

- [ ] **T-01 — Modelar la máquina de estados de cita en el dominio**  
  Dificultad: Alto  
  Descripción: Tipo de dominio con los estados del catálogo, la tabla de transiciones permitidas con su origen admisible y la validación que rechaza cualquier transición no declarada o desde un estado terminal. Sin dependencias de framework.

- [ ] **T-02 — Definir el evento de transición y el puerto de auditoría**  
  Dificultad: Medio  
  Descripción: Objeto de valor con cita, estado nuevo, actor opcional, origen, instante y motivo, con las invariantes de actor obligatorio salvo `SYSTEM` y motivo obligatorio en rechazo; y puerto de salida "registrar transición".

- [ ] **T-03 — Implementar el adaptador de solo inserción**  
  Dificultad: Medio  
  Descripción: Adaptador JPA sobre `appointment_status_history` que solo expone inserción y lectura, participa en la transacción del caso de uso invocante y no ofrece métodos de actualización ni borrado.

- [ ] **T-04 — Implementar el caso de uso y el endpoint de consulta del historial**  
  Dificultad: Medio  
  Descripción: Consulta cronológica del historial de una cita con estado, actor, origen, fecha y hora y motivo, restringida a los perfiles autorizados.

- [ ] **T-05 — Mostrar el historial en citas-web**  
  Dificultad: Bajo  
  Descripción: Sección de historial en el detalle de cita del dashboard ADMIN, en orden cronológico.

- [ ] **T-06 — Pruebas de transiciones y auditoría**  
  Dificultad: Alto  
  Descripción: Pruebas de dominio de todas las transiciones permitidas y prohibidas, integración del registro dentro de la transacción con reversión conjunta, verificación de la restricción de actor, y prueba de ausencia de rutas de edición o borrado.

## Criterios de aceptación

### CA-01 — Cada transición deja un registro completo

**Dado** una transición de estado de cita válida ejecutada por cualquier flujo  
**Cuando** se consulta el historial de la cita  
**Entonces** existe exactamente un registro nuevo con la cita, el estado nuevo, el actor cuando existe, el origen, la fecha y hora del cambio y el motivo cuando se aportó (RF-19).

### CA-02 — Origen SYSTEM sin actor; USER y ADMIN con actor

**Dado** una aprobación automática de cita general, una solicitud especializada de un USER y una decisión de un ADMIN  
**Cuando** se registran sus transiciones  
**Entonces** la primera tiene origen `SYSTEM` y puede no tener actor, y las otras dos tienen origen `USER` y `ADMIN` respectivamente con el identificador del actor; un intento de registrar origen `USER` o `ADMIN` sin actor se rechaza.

### CA-03 — Transiciones no permitidas rechazadas

**Dado** citas en estados terminales (`REJECTED`, `CANCELLED`, `COMPLETED`, `NO_SHOW`) y una cita `APPROVED`  
**Cuando** se intenta llevar una terminal a cualquier otro estado, o la `APPROVED` a `REQUESTED` o `REJECTED`  
**Entonces** el dominio rechaza la transición, la API responde 409, el estado no cambia y no se crea registro de historial (RN-11).

### CA-04 — Rechazo administrativo sin motivo no se registra

**Dado** una transición a `REJECTED` con origen `ADMIN`  
**Cuando** se intenta registrar sin motivo o con motivo vacío  
**Entonces** la operación se rechaza con un error de validación y ni el estado de la cita ni el historial cambian (RN-04).

### CA-05 — Estado e historial son atómicos

**Dado** un caso de uso de transición en el que la inserción del historial falla  
**Cuando** la transacción termina  
**Entonces** la cita conserva su estado anterior y no existe ningún cambio parcial; e inversamente, no existe ningún registro de historial para un cambio de estado que no se confirmó.

### CA-06 — El historial no se edita ni se borra por la API

**Dado** registros de historial existentes  
**Cuando** se inspeccionan los endpoints publicados y se intenta invocar métodos `PUT`, `PATCH` o `DELETE` sobre recursos de historial  
**Entonces** no existe ninguna operación que los modifique o elimine, y las peticiones reciben un error de método o recurso no soportado sin alterar datos (RN-12).

### CA-07 — Consulta cronológica del historial

**Dado** una cita que pasó por `REQUESTED`, `APPROVED` y `CANCELLED`  
**Cuando** un ADMIN consulta su historial  
**Entonces** recibe los tres registros en orden cronológico con estado, actor, origen, fecha y hora y motivo.

### CA-08 — Acceso al historial restringido

**Dado** una cita de un paciente  
**Cuando** consultan su historial un usuario no autenticado y un USER que no es titular de la cita  
**Entonces** ambos reciben el error de autenticación o de autorización correspondiente y no obtienen registros.

## Definition of Done

- [ ] Los criterios CA-01 a CA-08 están validados con evidencia concreta.
- [ ] Existe una única definición de transiciones permitidas y estados terminales en el dominio, sin dependencias de Spring ni de JPA, y las HU de transición la reutilizan.
- [ ] El adaptador de historial no expone actualización ni borrado, verificable por inspección del código y por prueba.
- [ ] El registro de historial se ejecuta dentro de la transacción del caso de uso, demostrado con una prueba de reversión conjunta.
- [ ] Se usa `appointment_status_history` de V3; cualquier refuerzo de inmutabilidad en base de datos se añade con una migración Flyway posterior a V4.
- [ ] El endpoint de consulta aplica [[HU-005-autorizar-peticiones-por-rol-y-ownership]].
- [ ] Ningún registro ni log de auditoría contiene contraseñas ni tokens.
- [ ] Existen pruebas automatizadas de transiciones permitidas y prohibidas, actor por origen, motivo obligatorio, atomicidad y ausencia de edición, y pasan.
- [ ] El contrato del endpoint de historial y el modelo de estados están reflejados en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [ ] La trazabilidad de esta HU y de [[EP-009-trazabilidad-y-contrato-rest]] está actualizada en `docs/wiki/scrum/`.

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

- Incógnita abierta **INC-037** (ver [[EP-009-trazabilidad-y-contrato-rest]]): no está definido quién consulta el historial. CA-07 y CA-08 solo fijan que ADMIN puede y que un no titular no puede; el acceso del paciente titular y del profesional asignado debe decidirse antes de aprobar la HU.
- RF-19 solo admite los orígenes `SYSTEM`, `USER` y `ADMIN`, y la columna `source` de V3 es un ENUM con esos tres valores. El PRD no indica qué origen corresponde al cierre de atención ejecutado por un `PROFESSIONAL` ([[HU-021-registrar-cierre-de-atencion]]). Añadir un valor `PROFESSIONAL` exigiría una migración nueva; debe decidirse con el usuario del proyecto.
- [[HU-031-aprobar-o-rechazar-reprogramacion]] registra en el historial decisiones que no cambian el estado de la cita, siguiendo el criterio de completitud de EP-008. Esta HU define el historial como registro de transiciones; debe acordarse si se admiten registros "sin cambio de estado" o si la decisión de reprogramación basta con `reschedule_requests`.
- La clave foránea `fk_ash_appointment` de V3 tiene `ON DELETE CASCADE`: un borrado físico de una cita borraría su historial. Ningún flujo del PRD borra citas; la DoD exige no exponer esa ruta, y endurecerlo en base de datos sería una migración nueva.
- La inclusión de `REQUESTED → CANCELLED` en la máquina de estados depende de **INC-030** (ver [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]); la condición exacta de `APPROVED → COMPLETED/NO_SHOW` depende de **INC-018** (ver [[EP-005-agenda-del-profesional]]).
