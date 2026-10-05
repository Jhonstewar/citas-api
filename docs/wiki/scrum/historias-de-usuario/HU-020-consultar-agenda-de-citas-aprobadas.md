---
id: HU-020
tipo: historia-de-usuario
titulo: "Consultar la agenda de citas aprobadas"
estado: Completada
epica: "[[EP-005-agenda-del-profesional]]"
requisitos: [RF-16]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 8"
dependencias:
  - "[[HU-023-agendar-cita-de-medicina-general]]"
  - "[[HU-030-aprobar-o-rechazar-cita-especializada]]"
relacionadas:
  - "[[HU-021-registrar-cierre-de-atencion]]"
  - "[[HU-005-autorizar-peticiones-por-rol-y-ownership]]"
---

# HU-020 — Consultar la agenda de citas aprobadas

## Historia de usuario

**COMO** PROFESSIONAL  
**QUIERO** consultar mis citas aprobadas por día o semana y por sede  
**PARA** prepararme para la atención del período

> Como PROFESSIONAL, quiero consultar mis citas aprobadas por día o semana y por sede para prepararme para la atención del período.

## Contexto y descripción

RF-16 define la agenda visible al profesional: sus citas en estado `APPROVED`, consultables por día o semana y por sede. Es una vista distinta del calendario de disponibilidad de [[HU-019-consultar-calendario-de-disponibilidad]]: aquella muestra la oferta publicada, esta muestra la demanda confirmada.

RF-16 añade una restricción explícita de privacidad: el profesional no puede ver datos de usuarios fuera de sus propias citas. Esto convierte la HU en un caso concreto de la autorización por rol y ownership exigida por PRD §8 y desarrollada en [[HU-005-autorizar-peticiones-por-rol-y-ownership]].

La agenda solo tiene contenido cuando existen citas aprobadas, que llegan por dos caminos: la aprobación automática de la cita general ([[HU-023-agendar-cita-de-medicina-general]]) y la decisión administrativa sobre la cita especializada ([[HU-030-aprobar-o-rechazar-cita-especializada]]). Por eso se sitúa después de ambas.

## Alcance

- Consulta de las citas en estado `APPROVED` del profesional autenticado (RF-16).
- Filtro por día y filtro por semana sobre la agenda (RF-16).
- Filtro por sede sobre la agenda (RF-16).
- Presentación de cada cita con su fecha y hora, duración, sede, especialidad y los datos del paciente estrictamente necesarios para la atención.
- Endpoint REST de agenda del profesional, restringido al rol `PROFESSIONAL` y a sus propias citas.
- Pantalla de agenda del profesional en `citas-web`.

## Fuera de alcance

- Cierre de la atención de la cita, que se cubre en [[HU-021-registrar-cierre-de-atencion]].
- Aprobación o rechazo de citas: el PRD lo asigna a ADMIN (PRD §2, RF-12).
- Consulta de citas en estados distintos de `APPROVED`, que no forma parte de RF-16.
- Creación, cancelación o reprogramación de citas por el profesional: no está en el PRD.
- Acceso del profesional a datos de usuarios con los que no tiene una cita (RF-16).

## Reglas de negocio

- La agenda lista únicamente citas en estado `APPROVED` (RF-16).
- La agenda lista únicamente citas del profesional autenticado (RF-16).
- El profesional no puede ver datos de usuarios fuera de sus propias citas (RF-16).
- Los filtros disponibles son día, semana y sede (RF-16).
- La autorización se aplica por rol y ownership, no solo por ocultación en la interfaz (PRD §8).

## Dependencias y relaciones

- Épica: [[EP-005-agenda-del-profesional]]
- Dependencias: [[HU-023-agendar-cita-de-medicina-general]], [[HU-030-aprobar-o-rechazar-cita-especializada]]
- Relacionadas: [[HU-019-consultar-calendario-de-disponibilidad]], [[HU-021-registrar-cierre-de-atencion]], [[HU-005-autorizar-peticiones-por-rol-y-ownership]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** Es una consulta de solo lectura, pero cruza cita, paciente, especialidad y sede, y la restricción de privacidad de RF-16 obliga a decidir y justificar qué campos del paciente se exponen. Los tres filtros de RF-16 deben resolverse en la consulta a la base de datos y probarse de forma combinada.

## Tareas de desarrollo

- [ ] **T-01 — Definir la proyección de agenda en la capa de aplicación**  
  Dificultad: Medio  
  Descripción: Caso de uso de consulta que resuelve el profesional desde el contexto de autenticación, acepta los filtros de día, semana y sede, y devuelve una proyección de cita con únicamente los campos que RF-16 permite exponer al profesional.

- [ ] **T-02 — Implementar el adaptador de persistencia de la agenda**  
  Dificultad: Medio  
  Descripción: Consulta sobre citas filtrada por profesional, estado `APPROVED`, rango temporal y sede, resolviendo especialidad y datos mínimos del paciente sin cargar el agregado completo de usuario.

- [ ] **T-03 — Exponer el adaptador REST de agenda del profesional**  
  Dificultad: Bajo  
  Descripción: Endpoint restringido al rol `PROFESSIONAL`, con parámetros de día, semana y sede validados, que devuelve siempre las citas del usuario autenticado y rechaza cualquier intento de consultar por otro profesional.

- [ ] **T-04 — Construir la pantalla de agenda del profesional en citas-web**  
  Dificultad: Medio  
  Descripción: Vista React + TypeScript con conmutador entre día y semana, selector de sede, listado ordenado por hora de inicio y detalle de cada cita limitado a los campos devueltos por la API.

- [ ] **T-05 — Pruebas de la agenda y de su aislamiento**  
  Dificultad: Medio  
  Descripción: Pruebas de integración REST para el filtrado por día, por semana y por sede, para la exclusión de citas en estados distintos de `APPROVED`, para la ausencia de citas de otros profesionales y para la ausencia de campos de paciente no autorizados en la respuesta.

## Criterios de aceptación

### CA-01 — La agenda lista solo citas APPROVED del profesional autenticado

**Dado** un profesional con citas propias en estados `APPROVED`, `REQUESTED`, `REJECTED` y `CANCELLED` en la misma fecha  
**Cuando** consulta su agenda para esa fecha  
**Entonces** la respuesta contiene únicamente las citas en estado `APPROVED` y ninguna de los otros tres estados.

### CA-02 — Filtro por día

**Dado** un profesional con citas aprobadas en tres fechas distintas de la misma semana  
**Cuando** consulta su agenda filtrando por una de esas fechas  
**Entonces** la respuesta contiene exclusivamente las citas aprobadas de esa fecha, ordenadas por hora de inicio.

### CA-03 — Filtro por semana

**Dado** un profesional con citas aprobadas dentro de una semana y otras citas aprobadas en la semana siguiente  
**Cuando** consulta su agenda filtrando por la primera semana  
**Entonces** la respuesta contiene todas las citas aprobadas de esa semana y ninguna de la semana siguiente.

### CA-04 — Filtro por sede

**Dado** un profesional habilitado en HIC y en ICV con citas aprobadas en ambas sedes el mismo día  
**Cuando** consulta su agenda de ese día filtrando por la sede HIC  
**Entonces** la respuesta contiene solo las citas cuya sede es HIC y ninguna de ICV.

### CA-05 — Un profesional no accede a citas de otro

**Dado** el profesional A autenticado y el profesional B con citas aprobadas  
**Cuando** A consulta la agenda indicando el identificador de B en la petición  
**Entonces** la API responde con un error de autorización o devuelve exclusivamente las citas de A, y en ningún caso aparece una cita de B.

### CA-06 — No se exponen datos de usuarios fuera de las propias citas

**Dado** un profesional autenticado consultando su agenda  
**Cuando** se inspecciona el cuerpo completo de la respuesta  
**Entonces** solo aparecen datos de los pacientes que tienen una cita aprobada con ese profesional, y para cada uno solo los campos necesarios para la atención, sin contraseñas, hashes, ni datos de afiliación o de contacto no requeridos por RF-16.

### CA-07 — Agenda vacía en un período sin citas

**Dado** un profesional autenticado y una fecha en la que no tiene ninguna cita aprobada  
**Cuando** consulta su agenda para esa fecha  
**Entonces** la API responde con éxito y una colección vacía, y la pantalla muestra el estado sin citas en lugar de un error.

## Definition of Done

- [ ] Los criterios CA-01 a CA-07 están validados con evidencia concreta.
- [ ] El endpoint de agenda exige rol `PROFESSIONAL` y resuelve el titular desde el contexto de autenticación, no desde un parámetro de la petición.
- [ ] El filtro de estado `APPROVED` se aplica en la consulta a la base de datos y no como filtrado posterior en el cliente.
- [ ] Los filtros de día, semana y sede están implementados en el servidor y son combinables entre sí.
- [ ] La lista de campos de paciente expuestos está decidida explícitamente y documentada, y no se serializa la entidad de usuario completa.
- [ ] La pantalla de agenda de `citas-web` consume la API mediante la URL del backend leída de la configuración de entorno.
- [ ] Existen pruebas automatizadas del filtrado por estado, de los tres filtros de RF-16 y del aislamiento entre profesionales, y pasan.
- [ ] El contrato del endpoint de agenda está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [ ] La trazabilidad de esta HU y de [[EP-005-agenda-del-profesional]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Ejecución de referencia del **2026-09-30**: el `backend-verifier`, agente independiente que no escribió el código, reejecutó la suite completa de `citas-api` → **484 pruebas, 0 fallos, 0 errores, `BUILD SUCCESS`**. El `frontend-verifier` reejecutó la de `citas-web` → **218 pruebas**, con typecheck, `oxlint` y build limpios, y dejó **14 hallazgos abiertos** (3 en reparación y 4 pruebas que faltan).

Ruta abreviada: **PAIT** = `src/test/java/com/fcv/citas/infrastructure/rest/ProfessionalAgendaIntegrationTest.java`. Los números de línea son los del árbol de trabajo del 2026-09-30.

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | PAIT:202 `theAgendaListsOnlyApprovedAppointments`: sembradas seis citas del mismo profesional en la misma fecha —`APPROVED`, `REQUESTED`, `REJECTED`, `CANCELLED`, `COMPLETED` y `NO_SHOW`—, la respuesta contiene **exactamente** la `APPROVED`; `JdbcAppointmentQueries:133-135` filtra `st.code = 'APPROVED'` en el SQL | El criterio nombra cuatro estados y la prueba cubre seis: añade los dos de cierre de [[HU-021-registrar-cierre-de-atencion]] |
| CA-02 | Cumple | PAIT:217 `theDayFilterReturnsOnlyThatDateOrderedByStartTime`: con citas en tres fechas de la misma semana, `from = to = miércoles` devuelve solo las dos de ese día y en orden de hora de inicio (08:30 antes de 11:00); comprueba además fecha, hora de inicio y fin, duración, sede y especialidad de cada elemento; `JdbcAppointmentQueries:140` ordena por `scheduled_date, start_time, id` | El orden se asevera con `containsExactly`, que es sensible a la secuencia |
| CA-03 | Cumple | PAIT:238 `theWeekFilterExcludesTheFollowingWeek`: `from` = lunes y `to` = domingo devuelve las tres citas de esa semana —lunes, miércoles y domingo— y **no** la del lunes siguiente; el lunes de partida es de una semana futura, así que la semana entera queda por delante | La semana empieza en **lunes** y la calcula el cliente enviando `from`/`to`; la API recibe un rango (D35) |
| CA-04 | Cumple | PAIT:251 `theSiteFilterKeepsOnlyThatSite`: con citas aprobadas en HIC y en ICV el mismo día, `siteId = HIC` devuelve solo la de HIC y `siteId = ICV` solo la de ICV; el filtro se combina con el de fecha en la misma petición; `JdbcAppointmentQueries:136-139` añade `AND a.site_id = :site` al SQL | Se prueban las dos sedes, no solo la del criterio |
| CA-05 | Cumple | PAIT:264 `aProfessionalNeverSeesAnotherProfessionalsAppointments`: A ve solo su cita; A enviando `professionalId` de B sigue viendo solo la suya y **nunca** la de B; B con su token ve la suya; `ProfessionalAppointmentsUseCase` resuelve el profesional desde el token y `JdbcAppointmentQueries:132` filtra por `a.professional_id = :professional` | El parámetro ajeno se ignora en lugar de rechazarse, que es una de las dos salidas que el criterio admite |
| CA-06 | Cumple | PAIT:280 `theAgendaExposesOnlyTheMinimumPatientData`: la lista de campos del elemento es **cerrada** (`id, status, statusName, date, startTime, endTime, durationMinutes, site, specialty, patient, pendingReschedule, closable`) y la de `patient` también (`fullName, documentType, documentNumber`, D35); el cuerpo crudo **no** contiene el email ni el teléfono reales del paciente, ni `password`, `hash` o `affiliation`, ni el documento de un paciente con cita `REQUESTED` con el mismo profesional; `ProfessionalAppointmentsUseCase:40` declara la proyección `ProfessionalPatientRef` en la capa de aplicación, no en el adaptador REST | La lista cerrada es la evidencia de que no se serializa la entidad de usuario: cualquier campo nuevo rompería la prueba |
| CA-07 | Cumple | Servidor: PAIT:312 `anEmptyPeriodAnswersAnEmptyList` devuelve 200 con longitud 0 para una fecha sin citas. Frontend: `citas-web/src/professionalAppointments.test.tsx:162` («un día sin citas muestra el estado vacío, no un error») y el componente `EmptyState`; verificación independiente del 2026-10-04 (vitest 248/248) | Verificado por el `frontend-verifier` criterio a criterio en F10 (PASS) |
| DoD — CA-01 a CA-07 validados con evidencia concreta | Cumple | CA-01 a CA-07 en `Cumple` | Cierra con la verificación independiente de F10 del 2026-10-04 |
| DoD — El endpoint exige rol `PROFESSIONAL` y resuelve el titular desde el contexto de autenticación | Cumple | `SecurityConfig:84` (`/api/professional/**` exige `ROLE_PROFESSIONAL`); PAIT:333 `onlyProfessionalsReadTheAgenda` (paciente y ADMIN → 403, sin token → 401); PAIT:264 (el titular sale del token, no del parámetro) | — |
| DoD — El filtro `APPROVED` se aplica en la consulta a la base de datos, no como filtrado posterior | Cumple | `JdbcAppointmentQueries:133-135` (`AND st.code = 'APPROVED'` dentro del `WHERE`); PAIT:202 | — |
| DoD — Los filtros de día, semana y sede están en el servidor y son combinables | Cumple | `JdbcAppointmentQueries:129-141` (`BETWEEN :from AND :to` más el `AND a.site_id` opcional, en el mismo SQL); PAIT:217, :238 y :251 (este último combina fecha y sede); PAIT:321 `theRangeIsValidated` (rango obligatorio, ordenado y de 62 días como máximo) | Día y semana son el mismo parámetro de rango: `from = to` para el día. Ver la nota sobre INC-017 |
| DoD — La lista de campos de paciente expuestos está decidida explícitamente y documentada, y no se serializa la entidad completa | Cumple | **D35** fija nombre completo, tipo y número de documento, y excluye email y teléfono; `ProfessionalAppointmentsUseCase:40` (`record ProfessionalPatientRef`) con el comentario que lo justifica; `citas-api/docs/wiki/llm-wiki/wiki/contrato-rest-citas.md:250`; PAIT:280 | Era la incógnita que la nota anterior daba por abierta (ver historial) |
| DoD — La pantalla de agenda de `citas-web` consume la API por la URL del backend de la configuración de entorno | Cumple | `citas-web/src/pages/professional/AgendaPage.tsx` y `AppointmentsPanel.tsx`, con las pruebas `src/professionalAppointments.test.tsx:105`, `:124` (hora, duración, especialidad, sede y paciente; conmutación a semana de lunes a domingo; filtro por sede) y `:162`; `src/api/httpClient.ts` toma la base de `VITE_API_URL`; verificación independiente del 2026-10-04 | PASS del `frontend-verifier` en F10 |
| DoD — Pruebas del filtrado por estado, de los tres filtros de RF-16 y del aislamiento entre profesionales, y pasan | Cumple | PAIT:202, :217, :238, :251, :264 y :333; suite completa 484/484 `BUILD SUCCESS` reejecutada por el `backend-verifier` | — |
| DoD — Contrato del endpoint de agenda reflejado en [[HU-033-publicar-contrato-rest-documentado]] | Cumple | `citas-api/docs/wiki/llm-wiki/wiki/contrato-rest-citas.md:250` (`GET /api/professional/appointments` con `from`/`to` obligatorios, máximo 62 días, `siteId` opcional, y «solo `APPROVED` y propias, por fecha y hora») | `llm-wiki/` queda fuera del límite de escritura de esta skill: la evidencia se leyó, no se produjo aquí |
| DoD — Trazabilidad de esta HU y de [[EP-005-agenda-del-profesional]] actualizada | Cumple | Esta matriz, el historial de validación y las notas (D35 registrada, INC-017 conservada); en [[EP-005-agenda-del-profesional]] las anotaciones de INC-017 e INC-018; `docs/wiki/scrum/README.md` | — |

## Historial de validación

- 2026-10-04 — **Verificación independiente de F10.** Backend verificado leyendo código y pruebas (suite 513/513) y frontend con vitest 248/248, typecheck, lint y build limpios: CA-01 a CA-07 y toda la DoD en `Cumple` (PASS). Estado: `En validación` → `Completada`. Sigue abierta, con su razón escrita, INC-017 (zona horaria), que no es criterio de esta HU.
- 2026-09-30 — **Matriz de evidencia recolectada del repositorio.** CA-01 a CA-06 y toda la DoD de backend, de contrato y de trazabilidad en `Cumple`. Estado: `Aprobada` → `En validación`. **No pasa a `Completada`**: CA-07 (estado vacío en pantalla) y el ítem de DoD de la pantalla de agenda quedan en `Pendiente`, y el ítem «CA-01 a CA-07 validados» en `No cumple` por arrastre. Falta la verificación de frontend criterio a criterio y la prueba manual en navegador de F10.
- 2026-09-30 — La nota «Ninguna decisión D15–D30 resuelve las incógnitas de esta HU. **Siguen abiertas**» se sustituye por **D35**, que fijó la proyección del paciente (nombre completo, tipo y número de documento; sin email ni teléfono) y el **lunes** como inicio de semana, y que está implementada y probada. Mentía sobre el estado del trabajo. **INC-017 (zona horaria) sí sigue abierta** en sentido estricto y se conserva con su razón escrita. Ningún criterio de aceptación cambia.
- 2026-09-25 — Aprobada por **aprobación delegada** del usuario para S4 (D15, PLAN_RETOMA_S4.md). Alcance en `PLAN_RETOMA_S4.md` §3 (bloque «Profesional», fase F4) y decisiones D15–D30 en [[dec-006-decisiones-s4-ciclo-de-vida]]. Ninguna decisión D15–D30 afecta a sus criterios. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- **Resueltas (D35, provisional bajo delegación):** las dos incógnitas propias de esta HU que ninguna decisión D15–D30 cubría. **D35** fija que el profesional ve del paciente **nombre completo, tipo y número de documento**, y **no** su email ni su teléfono —el mínimo que identifica al paciente en la atención (RF-16)—; y que **la semana empieza en lunes** y la calcula el frontend, porque la API recibe un rango `from`/`to` y no un número de semana ([[dec-006-decisiones-s4-ciclo-de-vida]]). Implementado en `ProfessionalAppointmentsUseCase:40` (`record ProfessionalPatientRef`, declarado en la capa de aplicación y no en el adaptador REST) y probado en `ProfessionalAgendaIntegrationTest:280` (listas de campos cerradas) y `:238` (semana de lunes a domingo). Ningún criterio se reescribió: CA-03 y CA-06 estaban redactados sobre el principio de mínimo necesario y sobre «esa semana», y siguen valiendo. La exigencia de la DoD de documentar la proyección se cumple con `contrato-rest-citas.md:250`.
- Esta HU cierra además CA-06 de [[HU-005-autorizar-peticiones-por-rol-y-ownership]] (el profesional ve solo datos de sus pacientes), según `PLAN_RETOMA_S4.md` F4.

- **Incógnita abierta, sigue abierta en sentido estricto: INC-017** (ver [[EP-005-agenda-del-profesional]]). D35 **no** la resuelve. Los filtros por día y por semana de CA-02 y CA-03 quedan expresados sobre la fecha almacenada de la cita, sin fijar el desplazamiento aplicado. En la práctica el sistema usa `America/Bogota` en JDBC, Hibernate y Jackson y las pruebas usan `SystemZone.ZONE` (`PLAN_RETOMA_S4.md` §5, [[riesgo-zona-horaria-columnas-time]]), pero eso es una convención de configuración, no una decisión registrada del producto: nadie ha decidido qué debe ver un profesional que consulta desde otro desplazamiento horario. Se conserva la incógnita hasta que el usuario la resuelva.
- ~~RF-16 prohíbe exponer datos de usuarios fuera de las propias citas, pero no enumera qué campos del paciente sí puede ver el profesional dentro de sus citas. El conjunto exacto es una decisión humana pendiente; CA-06 se redacta sobre el principio de mínimo necesario y no sobre una lista inventada.~~ **Resuelta por D35** (ver arriba). CA-06 se conserva tal cual: sigue redactado sobre el principio, y la lista concreta vive en la decisión y en el contrato.
- ~~El PRD no define el día de inicio de la semana para el filtro semanal de RF-16. Debe confirmarse con el usuario del proyecto antes de implementar.~~ **Resuelta por D35:** la semana empieza en **lunes** y el rango lo calcula el cliente.
