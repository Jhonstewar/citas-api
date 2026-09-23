---
id: HU-014
tipo: historia-de-usuario
titulo: "Asignar especialidades y especialidad primaria al profesional"
estado: Completada
epica: "[[EP-004-gestion-de-profesionales]]"
requisitos: [RF-07]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 3"
dependencias:
  - "[[HU-013-crear-profesional-con-datos-de-registro]]"
  - "[[HU-011-gestionar-especialidades-y-su-duracion]]"
relacionadas:
  - "[[HU-015-asignar-sedes-al-profesional]]"
  - "[[HU-022-buscar-disponibilidad-con-filtros]]"
  - "[[HU-023-agendar-cita-de-medicina-general]]"
  - "[[HU-024-solicitar-cita-especializada]]"
---

# HU-014 — Asignar especialidades y especialidad primaria al profesional

## Historia de usuario

**COMO** ADMIN  
**QUIERO** asignar a un profesional una o varias especialidades activas y marcar exactamente una como primaria  
**PARA** que el sistema sepa de qué puede atender cada profesional y ofrezca sus horarios solo para esas especialidades

> Como ADMIN, quiero asignar a un profesional una o varias especialidades activas y marcar exactamente una como primaria para que el sistema sepa de qué puede atender cada profesional y ofrezca sus horarios solo para esas especialidades.

## Contexto y descripción

RF-07 permite a ADMIN asignar una o varias especialidades a un profesional y marcar una de ellas como primaria. RN-08 convierte esa asociación en condición de reserva: una especialidad solo se reserva si está activa y asociada al profesional. Esta HU es, por tanto, la que define la combinación profesional-especialidad que filtrará la búsqueda de [[HU-022-buscar-disponibilidad-con-filtros]] y que validarán [[HU-023-agendar-cita-de-medicina-general]] y [[HU-024-solicitar-cita-especializada]]. Un profesional general es, en este modelo, un profesional asociado a la especialidad `Medicina General`.

El esquema ya existe en V2: `professional_specialties` es la relación N:M con el atributo `is_primary`, y la columna generada `primary_marker` con su índice único garantiza en el motor como máximo una primaria por profesional. La regla "exactamente una" (al menos una) no la puede garantizar la base de datos y corresponde al dominio.

## Alcance

- Endpoint REST para consultar y reemplazar el conjunto de especialidades de un profesional, restringido a `ADMIN`.
- Marcado de la especialidad primaria dentro del conjunto asignado.
- Validación server-side: especialidades existentes y activas, al menos una asignada, exactamente una primaria y la primaria incluida en el conjunto.
- Cambio de la especialidad primaria entre las ya asignadas.
- Sección de especialidades en la pantalla de gestión de profesionales de `citas-web`.

## Fuera de alcance

- CRUD de especialidades y su duración, que se cubre en [[HU-011-gestionar-especialidades-y-su-duracion]].
- Asignación de sedes, que se cubre en [[HU-015-asignar-sedes-al-profesional]].
- Efecto de retirar una especialidad sobre citas o solicitudes futuras ya existentes: pendiente de la incógnita INC-014.
- Autogestión de especialidades por el propio profesional: RF-07 la asigna a ADMIN.

## Reglas de negocio

- Un profesional puede tener una o varias especialidades (RF-07).
- Exactamente una de las especialidades asignadas es la primaria (RF-07); la base de datos impide dos primarias y el dominio impide cero.
- Solo se asignan especialidades existentes y activas (RN-08).
- Una especialidad no asociada al profesional no puede reservarse con él (RN-08).
- La duración de la cita pertenece a la especialidad; asignarla al profesional no la modifica (RF-09).
- Solo ADMIN gestiona esta asignación (RF-07, PRD §8).

## Dependencias y relaciones

- Épica: [[EP-004-gestion-de-profesionales]]
- Dependencias: [[HU-013-crear-profesional-con-datos-de-registro]], [[HU-011-gestionar-especialidades-y-su-duracion]]
- Relacionadas: [[HU-015-asignar-sedes-al-profesional]], [[HU-016-activar-o-desactivar-profesional]], [[HU-022-buscar-disponibilidad-con-filtros]], [[HU-023-agendar-cita-de-medicina-general]], [[HU-024-solicitar-cita-especializada]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** La tabla y la restricción de primaria única ya existen, pero la operación de reemplazo del conjunto combina borrado e inserción de asociaciones con un invariante que la base de datos no cubre por completo (al menos una primaria) y con la restricción única que puede dispararse en estados intermedios de la actualización si el orden de escritura no es cuidadoso.

## Tareas de desarrollo

- [ ] **T-01 — Modelar la asignación de especialidades en el agregado profesional**  
  Dificultad: Medio  
  Descripción: Colección de especialidades asignadas en el dominio del profesional con las invariantes de no vacía, sin duplicados, exactamente una primaria y primaria contenida en el conjunto, sin dependencias de framework.

- [ ] **T-02 — Implementar el caso de uso de asignación**  
  Dificultad: Medio  
  Descripción: Caso de uso que verifica existencia y estado activo de cada especialidad, aplica las invariantes del agregado y persiste el nuevo conjunto en una única transacción.

- [ ] **T-03 — Implementar la persistencia sobre professional_specialties**  
  Dificultad: Medio  
  Descripción: Adaptador que sincroniza las filas de la relación N:M sin violar transitoriamente la restricción única de primaria, y traduce cualquier violación a un error de dominio.

- [ ] **T-04 — Exponer los endpoints REST de consulta y asignación**  
  Dificultad: Bajo  
  Descripción: Endpoints restringidos a `ADMIN` con DTO validado y errores diferenciados para especialidad inexistente, especialidad inactiva, conjunto vacío y primaria ausente, duplicada o no incluida.

- [ ] **T-05 — Añadir la gestión de especialidades en la pantalla de profesionales de citas-web**  
  Dificultad: Medio  
  Descripción: Selector múltiple de especialidades activas con marca de primaria única, y presentación de los errores devueltos por la API.

- [ ] **T-06 — Pruebas de asignación de especialidades**  
  Dificultad: Medio  
  Descripción: Pruebas de dominio para las invariantes y pruebas de integración para la asignación válida, el cambio de primaria, la especialidad inactiva y la restricción de rol.

## Criterios de aceptación

### CA-01 — Asignación de varias especialidades con una primaria

**Dado** un profesional existente y dos especialidades activas, `Medicina General` y otra especializada  
**Cuando** ADMIN asigna ambas marcando `Medicina General` como primaria  
**Entonces** la API responde con éxito y la consulta del profesional devuelve las dos especialidades, con exactamente una marcada como primaria.

### CA-02 — Conjunto sin primaria o con dos primarias rechazado

**Dado** un profesional existente  
**Cuando** ADMIN envía un conjunto de especialidades sin ninguna primaria, o con dos marcadas como primaria  
**Entonces** la API responde con un error de validación y la asignación previa del profesional permanece sin cambios.

### CA-03 — Primaria fuera del conjunto rechazada

**Dado** un profesional existente  
**Cuando** ADMIN marca como primaria una especialidad que no está incluida en el conjunto asignado  
**Entonces** la API responde con un error de validación y no se persiste ningún cambio.

### CA-04 — Especialidad inactiva o inexistente rechazada

**Dado** una especialidad desactivada y un identificador de especialidad inexistente  
**Cuando** ADMIN intenta asignar cualquiera de ellas a un profesional  
**Entonces** la API responde con un error que identifica la especialidad no válida y no se persiste ningún cambio (RN-08).

### CA-05 — Cambio de especialidad primaria

**Dado** un profesional con dos especialidades asignadas y la primera marcada como primaria  
**Cuando** ADMIN marca la segunda como primaria manteniendo ambas  
**Entonces** la consulta devuelve la segunda como única primaria y la primera sigue asignada como no primaria.

### CA-06 — Solo ADMIN asigna especialidades

**Dado** un usuario autenticado con rol `USER` o `PROFESSIONAL`  
**Cuando** intenta modificar las especialidades de cualquier profesional, incluido él mismo  
**Entonces** la API responde con un error de autorización y la asignación no cambia.

### CA-07 — La asignación determina la oferta reservable

**Dado** un profesional asociado solo a `Medicina General` con slots publicados  
**Cuando** un paciente busca disponibilidad de otra especialidad a la que el profesional no está asociado  
**Entonces** ese profesional no aparece en los resultados para esa especialidad (RN-08), verificado contra [[HU-022-buscar-disponibilidad-con-filtros]].

## Definition of Done

- [x] Los criterios CA-01 a CA-07 están validados con evidencia concreta.
- [x] La invariante "exactamente una primaria" está implementada en el dominio sin depender de Spring ni de JPA, y la restricción única de V2 actúa como segunda barrera.
- [x] La actualización del conjunto es atómica: ante cualquier error la asignación previa queda intacta.
- [x] No se crean migraciones nuevas salvo que se requiera un cambio de esquema, en cuyo caso es una migración Flyway posterior a V4.
- [x] Los endpoints exigen rol `ADMIN` aplicando [[HU-005-autorizar-peticiones-por-rol-y-ownership]].
- [x] La pantalla de `citas-web` solo ofrece especialidades activas y no permite marcar dos primarias.
- [x] Existen pruebas automatizadas de las invariantes, de la asignación válida, del cambio de primaria y de la especialidad inactiva, y pasan.
- [x] El contrato de los endpoints de especialidades del profesional está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [x] La trazabilidad de esta HU y de [[EP-004-gestion-de-profesionales]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Ejecución de referencia del **2026-09-23**: backend `docker compose run --rm citas-api-dev mvn -B test` → **242 pruebas, 0 fallos** en 22 clases; frontend `npm test` en `citas-web` → **88 pruebas, 0 fallos**, con `typecheck`, `lint` y `build` en verde. La verificación fue independiente (`backend-verifier` y `frontend-verifier`, agentes que no escribieron el código): `EVIDENCIAS_S3.md` §10. Las clases de prueba del backend cuelgan de `citas-api/src/test/java/com/fcv/citas/`.

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | `infrastructure/rest/ProfessionalAdminIntegrationTest#createsProfessionalWithUserRoleAndHashedPassword` (alta con `MEDICINA_GENERAL` primaria + una especializada, `specialties.length() = 2`); `#changesThePrimarySpecialty` (el detalle devuelve las dos, con exactamente una `primary: true`) | — |
| CA-02 | Cumple | `domain/professional/SpecialtyAssignmentTest#rejectsMissingPrimary` y `#rejectsEmptySet`; `ProfessionalAdminIntegrationTest#invalidPrimaryIsRejectedWithoutChanges` (400 con `fieldErrors.primarySpecialtyId` y el detalle sigue con sus 2 especialidades) | «Dos primarias» no es representable por construcción: el contrato lleva un único `primarySpecialtyId` (`AdminProfessionalController.SpecialtiesRequest`) y la base añade `uq_professional_specialties_primary` sobre la columna generada `primary_marker` (V2 líneas 107-109) |
| CA-03 | Cumple | `SpecialtyAssignmentTest#rejectsPrimaryOutsideTheSet`; `ProfessionalAdminIntegrationTest#invalidPrimaryIsRejectedWithoutChanges` | La invariante se evalúa en `SpecialtyAssignment.of(...)` antes de abrir la transacción, así que no hay escritura parcial |
| CA-04 | Cumple | `ProfessionalAdminIntegrationTest#inactiveSpecialtyCannotBeAssigned` (422 `SPECIALTY_INACTIVE`); `application/professional/ManageProfessionalsUseCase#requireActiveSpecialties` | La especialidad inexistente cae por la misma comprobación, con `fieldErrors.specialtyIds` y el id que falla; ese lado concreto se verifica por lectura del código, no por prueba dedicada |
| CA-05 | Cumple | `ProfessionalAdminIntegrationTest#changesThePrimarySpecialty` | Tras marcar la segunda, la aserción recorre las dos especialidades y exige `primary == (id == cardiología)`: la anterior sigue asignada, sin ser primaria |
| CA-06 | Cumple | `SecurityConfig` (`/api/admin/**` → `hasRole("ADMIN")`); `infrastructure/rest/AuthorizationIntegrationTest#anonymousGets401AndWrongRoleGets403OnAdminRoutes` y `#onlyAdminReachesAdminRoutes` (USER y PROFESSIONAL → 403) | La ruta de asignación es `PUT /api/admin/professionals/{id}/specialties`: un PROFESSIONAL tampoco puede tocar la suya |
| CA-07 | Cumple | `infrastructure/rest/VerificationGapsIntegrationTest#availabilityFiltersByAppointmentTypeWithoutSpecialty` (buscando `SPECIALIZED` no aparece el médico general); `infrastructure/rest/BookingIntegrationTest#specialtyNotAssignedToTheProfessionalIsRejected` (422 `SPECIALTY_NOT_ASSIGNED`); `JdbcAvailabilityQueries` une por `professional_specialties` | La asignación manda en los dos lados: la oferta y la reserva |
| DoD — CA-01 a CA-07 validados con evidencia concreta | Cumple | Filas CA-01 a CA-07 de esta tabla | — |
| DoD — «Exactamente una primaria» en el dominio, con la restricción única de V2 como segunda barrera | Cumple | `domain/professional/SpecialtyAssignment` (Java puro, 5 pruebas en `SpecialtyAssignmentTest`); `HexagonalArchitectureTest`; `V2__configurable_catalogs_and_professionals.sql` líneas 107-109 (`primary_marker` generada + `uq_professional_specialties_primary`) | — |
| DoD — Actualización atómica: ante cualquier error la asignación previa queda intacta | Cumple | `ManageProfessionalsUseCase#assignSpecialties` (`tx.inTransaction` con `replaceSpecialties`); `ProfessionalAdminIntegrationTest#invalidPrimaryIsRejectedWithoutChanges` comprueba el estado tras el rechazo | — |
| DoD — Sin migraciones nuevas salvo cambio de esquema | Cumple | Se usa `professional_specialties` de V2; las migraciones posteriores (V5, V6, V7) son de otras HU | — |
| DoD — Los endpoints exigen rol ADMIN aplicando [[HU-005-autorizar-peticiones-por-rol-y-ownership]] | Cumple | `SecurityConfig`; `AuthorizationIntegrationTest#onlyAdminReachesAdminRoutes` | — |
| DoD — La pantalla solo ofrece especialidades activas y no permite marcar dos primarias | Cumple | `citas-web/src/adminOperations.test.tsx` → «M11: el alta no ofrece especialidades inactivas» y «alta: envía especialidades con principal y sedes; un 409 DUPLICATE marca el campo»; `citas-web/src/pages/admin/professionalPickers.tsx` | La principal es un selector único sobre el conjunto elegido, no una casilla por especialidad |
| DoD — Pruebas de las invariantes, la asignación válida, el cambio de primaria y la especialidad inactiva | Cumple | `SpecialtyAssignmentTest` (5); `ProfessionalAdminIntegrationTest#invalidPrimaryIsRejectedWithoutChanges`, `#inactiveSpecialtyCannotBeAssigned`, `#changesThePrimarySpecialty` | — |
| DoD — Contrato de los endpoints de especialidades del profesional reflejado en [[HU-033-publicar-contrato-rest-documentado]] | Cumple | `citas-api/docs/wiki/llm-wiki/wiki/contrato-rest-citas.md`, sección «Profesionales — ADMIN (HU-013 a HU-016)» | — |
| DoD — Trazabilidad de esta HU y de [[EP-004-gestion-de-profesionales]] actualizada | Cumple | Esta tabla y el historial de validación | — |

## Historial de validación

- 2026-09-23 — Estado `Completada` (fase F11 de `PLAN_RETOMA_S3.md`): matriz de evidencia completa, los 7 criterios y los 9 ítems de DoD en `Cumple`. Cierre dentro de la aprobación delegada de S3 (`AGENTS.md` §6); el usuario puede reabrirla.
- 2026-09-23 — Estado `En validación` (skill `scrum-spec-orchestrator`, paso 10): se recolecta del repositorio la evidencia de cada criterio y de cada ítem de DoD.
- 2026-09-18 — Estado `En desarrollo` (skill `scrum-spec-orchestrator`, paso 9): se inicia la implementación en la fase F3 de `PLAN_RETOMA_S3.md`.
- 2026-09-18 — Estado `Aprobada` por **aprobación delegada** de S3: el usuario pidió continuar S3 dejando las decisiones de diseño a criterio del agente (`AGENTS.md` §6). Alcance y decisiones D5–D13 en `PLAN_RETOMA_S3.md` y [[dec-004-decisiones-s3-reserva]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- Incógnita abierta **INC-016** (ver [[EP-004-gestion-de-profesionales]]): el PRD no define si un profesional puede quedar transitoriamente sin primaria al retirarle la especialidad marcada. Esta HU exige que toda operación deje exactamente una primaria, de modo que retirar la primaria obliga a marcar otra en la misma petición.
- Incógnita abierta **INC-014** (ver [[EP-004-gestion-de-profesionales]]): el PRD no define qué pasa con citas `REQUESTED`/`APPROVED` futuras de una especialidad que se retira al profesional. Esta HU no bloquea ni altera esas citas; la retirada solo afecta a la oferta futura.
- Incógnita abierta **INC-009** (ver [[EP-003-catalogos-del-sistema]]): si `Medicina General` es una especialidad protegida condiciona cómo se identifica a un "profesional general" en [[HU-023-agendar-cita-de-medicina-general]]. Esta HU lo identifica por la asociación a una especialidad de tipo `GENERAL`.
- La utilidad de la especialidad primaria más allá de su marcado no está definida en el PRD (por ejemplo, orden de presentación en búsquedas). No se le atribuye ningún efecto funcional adicional.
