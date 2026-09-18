---
id: HU-029
tipo: historia-de-usuario
titulo: "Consultar la bandeja administrativa"
estado: En desarrollo
epica: "[[EP-008-operacion-administrativa-de-solicitudes]]"
requisitos: [RF-18]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 6"
dependencias:
  - "[[HU-024-solicitar-cita-especializada]]"
  - "[[HU-005-autorizar-peticiones-por-rol-y-ownership]]"
relacionadas:
  - "[[HU-030-aprobar-o-rechazar-cita-especializada]]"
  - "[[HU-031-aprobar-o-rechazar-reprogramacion]]"
  - "[[HU-027-solicitar-reprogramacion-de-cita-aprobada]]"
---

# HU-029 — Consultar la bandeja administrativa

## Historia de usuario

**COMO** ADMIN  
**QUIERO** ver en una bandeja las citas especializadas `REQUESTED` y las reprogramaciones `PENDING`, filtrables por sede, profesional, especialidad y fecha  
**PARA** localizar rápidamente las solicitudes que esperan mi decisión y evitar que la agenda quede retenida sin resolver

> Como ADMIN, quiero ver en una bandeja las citas especializadas `REQUESTED` y las reprogramaciones `PENDING`, filtrables por sede, profesional, especialidad y fecha, para localizar rápidamente las solicitudes que esperan mi decisión y evitar que la agenda quede retenida sin resolver.

## Contexto y descripción

RF-18 define la bandeja administrativa con dos tipos de elementos y cuatro filtros. Es el punto de entrada de las decisiones de [[HU-030-aprobar-o-rechazar-cita-especializada]] y [[HU-031-aprobar-o-rechazar-reprogramacion]]: cada solicitud sin resolver retiene slots en `slot_reservations`, de modo que una bandeja incompleta equivale a agenda bloqueada.

Es una lectura. Las citas especializadas pendientes se obtienen de `appointments` en estado `REQUESTED` (índice `ix_appointments_admin_inbox` de V3) y las reprogramaciones de `reschedule_requests` en estado `PENDING` (índice `ix_reschedule_requests_status`). Se planifica en el Sprint 6 con las citas especializadas; los elementos de reprogramación empiezan a tener contenido cuando exista [[HU-027-solicitar-reprogramacion-de-cita-aprobada]] en el Sprint 7, y la consulta debe estar preparada para ellos desde el principio.

## Alcance

- Endpoint REST de bandeja administrativa en `citas-api`, restringido a `ADMIN`.
- Inclusión de citas especializadas en estado `REQUESTED` (RF-18).
- Inclusión de solicitudes de reprogramación en estado `PENDING` (RF-18).
- Filtros por sede, profesional, especialidad y fecha, combinables (RF-18).
- Distinción explícita del tipo de elemento (cita especializada o reprogramación) en cada entrada.
- Datos mínimos para decidir: sede, profesional, especialidad, fecha y horas, duración y, para reprogramaciones, fecha y horas actuales y propuestas.
- Pantalla "dashboard ADMIN" con la bandeja en `citas-web` (PRD §6), con acceso a las acciones de aprobar y rechazar.

## Fuera de alcance

- Las decisiones de aprobar o rechazar, que se cubren en [[HU-030-aprobar-o-rechazar-cita-especializada]] y [[HU-031-aprobar-o-rechazar-reprogramacion]].
- Citas generales: nacen `APPROVED` y no requieren decisión (RN-02).
- Solicitudes ya resueltas o históricas: RF-18 solo pide las pendientes.
- Restricción de la bandeja por sede del ADMIN: pendiente de INC-034.
- Datos personales del paciente más allá de los necesarios para identificar la solicitud (ver notas).

## Reglas de negocio

- La bandeja muestra citas especializadas `REQUESTED` y reprogramaciones `PENDING` (RF-18).
- Los filtros disponibles son sede, profesional, especialidad y fecha (RF-18).
- Una cita general nunca aparece en la bandeja (RN-02).
- Una solicitud deja de aparecer en cuanto se resuelve.
- Solo ADMIN accede a la bandeja (PRD §8).

## Dependencias y relaciones

- Épica: [[EP-008-operacion-administrativa-de-solicitudes]]
- Dependencias: [[HU-024-solicitar-cita-especializada]], [[HU-005-autorizar-peticiones-por-rol-y-ownership]]
- Relacionadas: [[HU-030-aprobar-o-rechazar-cita-especializada]], [[HU-031-aprobar-o-rechazar-reprogramacion]], [[HU-027-solicitar-reprogramacion-de-cita-aprobada]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** Es una consulta de solo lectura con índices ya previstos, pero unifica dos orígenes con forma distinta (cita y solicitud de reprogramación) bajo los mismos cuatro filtros, y la semántica del filtro de fecha y de sede para una reprogramación no es trivial porque existen una franja actual y una propuesta.

## Tareas de desarrollo

- [ ] **T-01 — Definir el modelo de lectura de la bandeja**  
  Dificultad: Bajo  
  Descripción: Proyección de aplicación con tipo de elemento, identificadores, sede, profesional, especialidad, fecha y horas, duración y, para reprogramaciones, franja actual y propuesta.

- [ ] **T-02 — Implementar las consultas de pendientes en persistencia**  
  Dificultad: Medio  
  Descripción: Adaptadores de lectura sobre `appointments` con estado `REQUESTED` y tipo especializado, y sobre `reschedule_requests` con estado `PENDING` unidos a su cita, aplicando los cuatro filtros en base de datos.

- [ ] **T-03 — Implementar el caso de uso de bandeja**  
  Dificultad: Medio  
  Descripción: Caso de uso que valida los filtros, combina ambos orígenes en un resultado ordenado de forma estable y documenta cómo se aplican fecha y sede a las reprogramaciones.

- [ ] **T-04 — Exponer el adaptador REST de bandeja**  
  Dificultad: Bajo  
  Descripción: Endpoint restringido a `ADMIN` con parámetros de filtro opcionales validados.

- [ ] **T-05 — Construir la bandeja en el dashboard ADMIN de citas-web**  
  Dificultad: Medio  
  Descripción: Vista con los cuatro filtros alimentados por catálogos y profesionales, listado que distingue visualmente los dos tipos de elemento y acceso a las acciones de decisión.

- [ ] **T-06 — Pruebas de la bandeja**  
  Dificultad: Medio  
  Descripción: Pruebas de integración con citas en todos los estados y reprogramaciones en todos los estados, verificando inclusión, exclusión, cada filtro y la restricción de rol.

## Criterios de aceptación

### CA-01 — La bandeja contiene solo lo pendiente

**Dado** citas especializadas en estados `REQUESTED`, `APPROVED`, `REJECTED` y `CANCELLED`, citas generales `APPROVED`, y reprogramaciones en estados `PENDING`, `APPROVED` y `REJECTED`  
**Cuando** ADMIN consulta la bandeja sin filtros  
**Entonces** el resultado contiene exactamente las citas especializadas `REQUESTED` y las reprogramaciones `PENDING`, y cada entrada indica su tipo.

### CA-02 — Filtro por sede

**Dado** solicitudes pendientes en HIC y en ICV  
**Cuando** ADMIN filtra por HIC  
**Entonces** todas las entradas devueltas corresponden a HIC y no se omite ninguna solicitud pendiente de HIC.

### CA-03 — Filtros por profesional, especialidad y fecha combinables

**Dado** solicitudes pendientes de varios profesionales, especialidades y fechas  
**Cuando** ADMIN combina los filtros de profesional, especialidad y fecha  
**Entonces** cada entrada devuelta cumple todos los filtros enviados simultáneamente, y ninguna solicitud pendiente que los cumpla queda fuera.

### CA-04 — Datos suficientes para decidir

**Dado** una cita especializada `REQUESTED` y una reprogramación `PENDING` en la bandeja  
**Cuando** ADMIN inspecciona cada entrada  
**Entonces** la cita muestra sede, profesional, especialidad, fecha, horas y duración, y la reprogramación muestra además la franja actual de la cita y la franja propuesta.

### CA-05 — Una solicitud resuelta sale de la bandeja

**Dado** una cita especializada `REQUESTED` visible en la bandeja  
**Cuando** ADMIN la aprueba o la rechaza y vuelve a consultar la bandeja  
**Entonces** la cita ya no aparece.

### CA-06 — Solo ADMIN accede

**Dado** un usuario autenticado con rol `USER` o `PROFESSIONAL`, o una petición sin autenticar  
**Cuando** invoca el endpoint de bandeja  
**Entonces** la API responde con el error de autenticación o autorización correspondiente y no devuelve ninguna solicitud.

## Definition of Done

- [ ] Los criterios CA-01 a CA-06 están validados con evidencia concreta.
- [ ] La bandeja es de solo lectura y no modifica citas, solicitudes ni reservas.
- [ ] Los filtros se aplican en base de datos apoyándose en los índices de V3; cualquier índice adicional va en una migración Flyway posterior a V4.
- [ ] La semántica de los filtros de fecha y sede para reprogramaciones está documentada en el contrato.
- [ ] El endpoint exige rol `ADMIN` aplicando [[HU-005-autorizar-peticiones-por-rol-y-ownership]].
- [ ] La vista de `citas-web` distingue los dos tipos de elemento y consume la API mediante la URL de entorno.
- [ ] Existen pruebas automatizadas de inclusión/exclusión por estado y tipo, de cada filtro y del rol, y pasan.
- [ ] El contrato del endpoint de bandeja está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
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
| DoD | Pendiente | — | — |

## Historial de validación

- 2026-09-18 — Estado `En desarrollo` (skill `scrum-spec-orchestrator`, paso 9): se inicia la implementación en la fase F6 de `PLAN_RETOMA_S3.md`.
- 2026-09-18 — Estado `Aprobada` por **aprobación delegada** de S3: el usuario pidió continuar S3 dejando las decisiones de diseño a criterio del agente (`AGENTS.md` §6). Alcance y decisiones D5–D13 en `PLAN_RETOMA_S3.md` y [[dec-004-decisiones-s3-reserva]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- Incógnita abierta **INC-034** (ver [[EP-008-operacion-administrativa-de-solicitudes]]): no está definido si la bandeja se restringe por sede del ADMIN; esta HU asume que todo ADMIN ve todas las sedes.
- Incógnita abierta **INC-036** (ver [[EP-008-operacion-administrativa-de-solicitudes]]): no está definido si las solicitudes cuya fecha ya pasó siguen apareciendo; esta HU las incluye mientras sigan pendientes, para que no queden retenciones invisibles.
- El PRD no define si el filtro de fecha de una reprogramación se aplica a la franja actual o a la propuesta, ni cómo se trata el filtro de sede si la franja propuesta está en otra sede (INC-031). Esta HU propone aplicar ambos filtros a la franja propuesta y mostrar las dos; debe confirmarse antes de aprobarla.
- El PRD no define qué datos del paciente ve ADMIN en la bandeja (ver notas de [[HU-005-autorizar-peticiones-por-rol-y-ownership]]). CA-04 no exige datos personales del paciente.
- El PRD no fija paginación ni orden; la HU solo exige un orden estable.
