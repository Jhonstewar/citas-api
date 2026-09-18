---
id: HU-017
tipo: historia-de-usuario
titulo: "Crear bloques de disponibilidad con slots de 30 minutos"
estado: Aprobada
epica: "[[EP-005-agenda-del-profesional]]"
requisitos: [RF-08, RF-09]
esfuerzo: "Alto"
sprint_sugerido: "Sprint 4"
dependencias:
  - "[[HU-015-asignar-sedes-al-profesional]]"
  - "[[HU-005-autorizar-peticiones-por-rol-y-ownership]]"
relacionadas:
  - "[[HU-018-editar-y-eliminar-bloques-futuros]]"
  - "[[HU-022-buscar-disponibilidad-con-filtros]]"
---

# HU-017 — Crear bloques de disponibilidad con slots de 30 minutos

## Historia de usuario

**COMO** PROFESSIONAL  
**QUIERO** publicar varios bloques de atención por día indicando la sede de cada uno  
**PARA** que los pacientes puedan reservar en los horarios en que realmente atiendo

> Como PROFESSIONAL, quiero publicar varios bloques de atención por día indicando la sede de cada uno para que los pacientes puedan reservar en los horarios en que realmente atiendo.

## Contexto y descripción

RF-08 define el bloque de disponibilidad como la unidad con la que el profesional declara su horario de atención: una fecha, una franja horaria y una sede. El PRD ofrece un día válido de ejemplo con dos bloques en la misma sede, 08:00–12:00 HIC y 14:00–17:00 HIC, lo que confirma que un mismo día admite varios bloques no contiguos.

El bloque por sí solo no es reservable. RF-08 exige que se discretice en slots de 30 minutos, y RF-09 establece que la duración de la cita la fija la especialidad: 30 minutos consumen un slot y 60 minutos consumen dos slots consecutivos. El slot de 30 minutos es, por tanto, la unidad atómica sobre la que se construyen la búsqueda de disponibilidad ([[HU-022-buscar-disponibilidad-con-filtros]]) y la reserva ([[HU-023-agendar-cita-de-medicina-general]] y [[HU-024-solicitar-cita-especializada]]).

Esta HU inaugura el esquema de agenda del proyecto: tablas de bloques y de slots, con el estado del slot como base del control de doble reserva exigido por RN-01.

## Alcance

- Creación de uno o varios bloques de disponibilidad por día para el profesional autenticado (RF-08).
- Selección de la sede de cada bloque entre las sedes asignadas al profesional (RF-08, RN-07).
- Expansión automática del bloque en slots atómicos de 30 minutos al persistirlo (RF-08).
- Validación server-side de fecha no pasada, de no solapamiento con otros bloques del mismo profesional y de sede asignada.
- Endpoint REST de creación de bloque en `citas-api`, restringido al rol `PROFESSIONAL` y a sus propios bloques.
- Pantalla de gestión de bloques en `citas-web` con la creación del bloque y la vista de los slots generados.
- Migración Flyway que crea el esquema de bloques de disponibilidad y de slots.

## Fuera de alcance

- Edición y eliminación de bloques, que se cubre en [[HU-018-editar-y-eliminar-bloques-futuros]].
- Consulta del calendario propio, que se cubre en [[HU-019-consultar-calendario-de-disponibilidad]].
- Búsqueda de disponibilidad por parte del paciente, que se cubre en [[HU-022-buscar-disponibilidad-con-filtros]].
- Creación de bloques por parte de ADMIN en nombre del profesional: no está en el PRD.
- Bloques recurrentes o plantillas semanales de horario: no están en el PRD.
- Asociación del bloque a una especialidad concreta: pendiente de la incógnita INC-020 de [[EP-005-agenda-del-profesional]].

## Reglas de negocio

- Un mismo día admite múltiples bloques del mismo profesional, por ejemplo 08:00–12:00 HIC y 14:00–17:00 HIC (RF-08).
- El bloque se discretiza en slots de 30 minutos (RF-08).
- Una especialidad de 30 minutos consume un slot; una de 60 minutos consume dos slots consecutivos (RF-09, RN-05).
- No se permiten bloques en el pasado (RN-06).
- No se permite solapamiento entre bloques del mismo profesional (RF-08).
- El profesional debe estar habilitado en la sede del bloque (RN-07).
- El profesional solo publica y administra su propia agenda; la autorización es por rol y ownership (PRD §8).
- El profesional no sobrescribe la duración de la especialidad (RF-09).

## Dependencias y relaciones

- Épica: [[EP-005-agenda-del-profesional]]
- Dependencias: [[HU-015-asignar-sedes-al-profesional]], [[HU-005-autorizar-peticiones-por-rol-y-ownership]]
- Relacionadas: [[HU-018-editar-y-eliminar-bloques-futuros]], [[HU-019-consultar-calendario-de-disponibilidad]], [[HU-022-buscar-disponibilidad-con-filtros]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Alto

**Justificación de dificultad:** Introduce el núcleo del modelo de agenda del producto: dos tablas nuevas relacionadas, la primera lógica de expansión temporal del proyecto y tres validaciones de integridad que deben resolverse en el dominio y reforzarse en la base de datos. La expansión bloque-a-slots y la detección de solapamiento son algoritmos con casos límite reales (franjas contiguas, franjas no alineadas a la media hora) y todo lo que se construya después de esta HU depende de que el slot sea correcto.

## Tareas de desarrollo

- [ ] **T-01 — Modelar bloque de disponibilidad y slot en el dominio**  
  Dificultad: Alto  
  Descripción: Entidades y objetos de valor de bloque (profesional, sede, fecha, hora de inicio, hora de fin) y de slot (bloque, inicio, fin, estado), con las invariantes de franja válida, franja no pasada y alineación a la rejilla de 30 minutos, sin dependencias de framework.

- [ ] **T-02 — Implementar la expansión del bloque en slots de 30 minutos**  
  Dificultad: Alto  
  Descripción: Servicio de dominio que, dada la franja del bloque, genera la secuencia completa de slots atómicos de 30 minutos y expone su orden para que la regla de consecutividad de RN-05 pueda evaluarse después.

- [ ] **T-03 — Implementar la detección de solapamiento y la validación de sede**  
  Dificultad: Medio  
  Descripción: Regla de dominio que rechaza un bloque cuya franja se cruce con la de otro bloque del mismo profesional en la misma fecha, y verificación de que la sede solicitada pertenece a las sedes asignadas al profesional (RN-07).

- [ ] **T-04 — Crear la migración Flyway de bloques y slots**  
  Dificultad: Medio  
  Descripción: Migración versionada que crea la tabla de bloques de disponibilidad con sus claves foráneas a profesional y sede, la tabla de slots con clave foránea al bloque y su estado, y las restricciones de integridad e índices que soportan la consulta por profesional, sede y fecha.

- [ ] **T-05 — Implementar el caso de uso de creación de bloque**  
  Dificultad: Medio  
  Descripción: Caso de uso de aplicación que recibe el comando de creación, resuelve el profesional desde el contexto de autenticación, aplica las reglas de dominio, persiste el bloque con sus slots en una sola transacción y devuelve el bloque creado con sus slots.

- [ ] **T-06 — Exponer el adaptador REST de creación de bloque**  
  Dificultad: Medio  
  Descripción: Controlador con DTO validado, restricción al rol `PROFESSIONAL`, respuesta de creación con los slots generados y errores diferenciados para franja pasada, solapamiento y sede no asignada.

- [ ] **T-07 — Construir la pantalla de gestión de bloques en citas-web**  
  Dificultad: Medio  
  Descripción: Vista React + TypeScript que permite añadir varios bloques a un mismo día, seleccionar la sede entre las asignadas al profesional, muestra los slots resultantes de cada bloque y presenta los errores de validación devueltos por la API.

- [ ] **T-08 — Pruebas de dominio, aplicación e integración de la agenda**  
  Dificultad: Alto  
  Descripción: Pruebas de la expansión en slots para franjas de distinta longitud, de la detección de solapamiento incluyendo bloques contiguos, del rechazo de franja pasada y de sede no asignada, y pruebas de integración REST y de persistencia para el alta de un día con dos bloques.

## Criterios de aceptación

### CA-01 — Un día admite varios bloques

**Dado** un profesional habilitado en la sede HIC y una fecha futura sin bloques publicados  
**Cuando** crea el bloque 08:00–12:00 en HIC y a continuación el bloque 14:00–17:00 en HIC para esa misma fecha  
**Entonces** la API acepta ambas operaciones, los dos bloques quedan persistidos para esa fecha y la consulta de la agenda de ese día devuelve los dos.

### CA-02 — El bloque se expande en slots atómicos de 30 minutos

**Dado** un profesional habilitado en HIC y una fecha futura  
**Cuando** crea el bloque 08:00–12:00 en esa sede  
**Entonces** el sistema genera exactamente 8 slots de 30 minutos asociados al bloque, con inicios en 08:00, 08:30, 09:00, 09:30, 10:00, 10:30, 11:00 y 11:30, todos en estado disponible y sin huecos ni solapamientos entre ellos.

### CA-03 — Bloque en el pasado rechazado

**Dado** una fecha y hora ya transcurridas respecto del instante de la petición  
**Cuando** el profesional intenta crear un bloque sobre esa franja  
**Entonces** la API responde con un error de validación que identifica la franja como pasada, y no se persiste ningún bloque ni ningún slot (RN-06).

### CA-04 — Bloque solapado con otro del mismo profesional rechazado

**Dado** un profesional con el bloque 08:00–12:00 ya publicado en una fecha futura  
**Cuando** intenta crear en esa misma fecha un bloque 11:00–13:00  
**Entonces** la API responde con un error de conflicto que indica el solapamiento con un bloque existente, y no se persiste el nuevo bloque ni sus slots.

### CA-05 — Bloque en sede no asignada rechazado

**Dado** un profesional asignado únicamente a la sede HIC  
**Cuando** intenta crear un bloque en la sede ICV  
**Entonces** la API responde con un error que indica que el profesional no está habilitado en esa sede, y no se persiste ningún bloque (RN-07).

### CA-06 — El profesional solo crea bloques para sí mismo

**Dado** un profesional A autenticado y un profesional B distinto  
**Cuando** A envía una petición de creación de bloque indicando a B como titular del bloque  
**Entonces** la API responde con un error de autorización o ignora el titular recibido y atribuye el bloque a A, y en ningún caso queda persistido un bloque de B creado por A.

### CA-07 — Esquema de bloques y slots creado por migración versionada

**Dado** una base de datos MySQL 8.4 sin las tablas de agenda  
**Cuando** se arranca `citas-api`  
**Entonces** Flyway aplica la migración que crea las tablas de bloques de disponibilidad y de slots con sus claves foráneas a profesional, sede y bloque, y el arranque finaliza sin error.

### CA-08 — Los slots publicados quedan visibles como oferta

**Dado** un bloque recién creado con sus slots en estado disponible  
**Cuando** se consulta la disponibilidad del profesional para esa fecha y sede  
**Entonces** los slots generados aparecen como franjas ofrecidas y cada uno conserva su duración de 30 minutos y su orden dentro del bloque.

## Definition of Done

- [ ] Los criterios CA-01 a CA-08 están validados con evidencia concreta.
- [ ] Existe una migración Flyway versionada que crea las tablas de bloques de disponibilidad y de slots, y se aplica sobre una base sin ellas.
- [ ] La expansión de un bloque en slots de 30 minutos está implementada en el dominio, sin dependencias de Spring ni de JPA.
- [ ] Las reglas de franja no pasada, no solapamiento y sede asignada se evalúan en el servidor y no dependen de la validación del cliente.
- [ ] La creación del bloque y de sus slots ocurre en una única transacción: no queda ningún bloque persistido sin sus slots.
- [ ] El endpoint de creación de bloque exige rol `PROFESSIONAL` y atribuye el bloque al profesional autenticado, no al identificador recibido en el cuerpo.
- [ ] La pantalla de gestión de bloques de `citas-web` consume la API mediante la URL del backend leída de la configuración de entorno.
- [ ] Existen pruebas automatizadas que cubren la expansión en 8 slots de un bloque 08:00–12:00, el solapamiento, la franja pasada y la sede no asignada, y pasan.
- [ ] El contrato del endpoint de creación de bloque está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
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

- 2026-09-18 — Estado `Aprobada` por **aprobación delegada** de S3: el usuario pidió continuar S3 dejando las decisiones de diseño a criterio del agente (`AGENTS.md` §6). Alcance y decisiones D5–D13 en `PLAN_RETOMA_S3.md` y [[dec-004-decisiones-s3-reserva]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- Incógnita abierta **INC-017** (ver [[EP-005-agenda-del-profesional]]): el PRD no define la zona horaria de referencia del sistema. CA-03 se expresa como comparación de la franja con el instante de la petición, sin fijar una zona concreta, hasta que se decida.
- Incógnita abierta **INC-020** (ver [[EP-005-agenda-del-profesional]]): el PRD no indica si el bloque se publica para una especialidad concreta o queda abierto a todas las del profesional. Esta HU no asocia especialidad al bloque; si la decisión cambia, afecta al esquema creado en T-04.
- Incógnita abierta **INC-021** (ver [[EP-005-agenda-del-profesional]]): no hay horizonte máximo definido para publicar bloques hacia el futuro. Los CA solo exigen la prohibición del pasado.
- El PRD no define qué ocurre con una franja cuya longitud no sea múltiplo exacto de 30 minutos. La invariante de alineación de T-01 debe confirmarse con el usuario del proyecto antes de implementar.
