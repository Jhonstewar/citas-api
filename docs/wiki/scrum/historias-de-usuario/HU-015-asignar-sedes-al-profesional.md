---
id: HU-015
tipo: historia-de-usuario
titulo: "Asignar sedes al profesional"
estado: Completada
epica: "[[EP-004-gestion-de-profesionales]]"
requisitos: [RF-07]
esfuerzo: "Bajo"
sprint_sugerido: "Sprint 3"
dependencias:
  - "[[HU-013-crear-profesional-con-datos-de-registro]]"
  - "[[HU-010-consultar-catalogos-fijos-precargados]]"
relacionadas:
  - "[[HU-017-crear-bloques-de-disponibilidad-con-slots]]"
  - "[[HU-014-asignar-especialidades-y-especialidad-primaria]]"
  - "[[HU-022-buscar-disponibilidad-con-filtros]]"
---

# HU-015 — Asignar sedes al profesional

## Historia de usuario

**COMO** ADMIN  
**QUIERO** habilitar a un profesional en una o en ambas sedes del laboratorio  
**PARA** que solo pueda publicar agenda y recibir citas en las sedes donde realmente atiende

> Como ADMIN, quiero habilitar a un profesional en una o en ambas sedes del laboratorio para que solo pueda publicar agenda y recibir citas en las sedes donde realmente atiende.

## Contexto y descripción

RF-07 permite a ADMIN asignar una o ambas sedes fijas a un profesional: Hospital Internacional de Colombia (HIC) e Instituto Cardiovascular (ICV), precargadas como catálogo fijo por RF-05 y consultables desde [[HU-010-consultar-catalogos-fijos-precargados]]. RN-07 hace de esta asignación una barrera de agenda: un profesional solo publica bloques en sedes asignadas, lo que valida [[HU-017-crear-bloques-de-disponibilidad-con-slots]].

El esquema existe en V2 como la relación N:M `professional_sites`. Esta HU construye el caso de uso, el endpoint y la sección de pantalla que la gestionan.

## Alcance

- Endpoint REST para consultar y reemplazar el conjunto de sedes de un profesional, restringido a `ADMIN`.
- Validación server-side: al menos una sede, sedes existentes en el catálogo fijo y sin duplicados.
- Sección de sedes en la pantalla de gestión de profesionales de `citas-web`.

## Fuera de alcance

- Creación o edición de sedes: son catálogo fijo de solo lectura (RF-05).
- Creación de bloques en la sede, que se cubre en [[HU-017-crear-bloques-de-disponibilidad-con-slots]].
- Efecto de retirar una sede sobre bloques y citas futuras ya existentes en ella: pendiente de la incógnita INC-014.

## Reglas de negocio

- Un profesional puede trabajar en una o en ambas sedes (RF-07).
- Las sedes provienen del catálogo fijo HIC / ICV (PRD §3, RF-05).
- Un profesional solo publica agenda en sedes asignadas (RN-07).
- Solo ADMIN gestiona esta asignación (RF-07, PRD §8).

## Dependencias y relaciones

- Épica: [[EP-004-gestion-de-profesionales]]
- Dependencias: [[HU-013-crear-profesional-con-datos-de-registro]], [[HU-010-consultar-catalogos-fijos-precargados]]
- Relacionadas: [[HU-017-crear-bloques-de-disponibilidad-con-slots]], [[HU-014-asignar-especialidades-y-especialidad-primaria]], [[HU-016-activar-o-desactivar-profesional]], [[HU-022-buscar-disponibilidad-con-filtros]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Bajo

**Justificación de dificultad:** Relación N:M sobre un catálogo fijo de dos elementos, con tabla ya existente y validaciones simples. Reutiliza el patrón de reemplazo de conjunto de [[HU-014-asignar-especialidades-y-especialidad-primaria]] sin su invariante de primaria.

## Tareas de desarrollo

- [ ] **T-01 — Modelar las sedes asignadas en el agregado profesional**  
  Dificultad: Bajo  
  Descripción: Conjunto de sedes del profesional con las invariantes de no vacío y sin duplicados, y operación de consulta "está habilitado en la sede" que reutilizará la agenda.

- [ ] **T-02 — Implementar el caso de uso y la persistencia sobre professional_sites**  
  Dificultad: Bajo  
  Descripción: Caso de uso que verifica que cada sede existe en el catálogo fijo y sincroniza las filas de la relación en una única transacción.

- [ ] **T-03 — Exponer los endpoints REST de consulta y asignación de sedes**  
  Dificultad: Bajo  
  Descripción: Endpoints restringidos a `ADMIN` con errores diferenciados para conjunto vacío y sede inexistente.

- [ ] **T-04 — Añadir la gestión de sedes en la pantalla de profesionales de citas-web**  
  Dificultad: Bajo  
  Descripción: Selección de una o ambas sedes a partir del catálogo fijo consultado a la API, con presentación de errores.

- [ ] **T-05 — Pruebas de asignación de sedes**  
  Dificultad: Bajo  
  Descripción: Pruebas de dominio del invariante y de integración para la asignación de una y de dos sedes, el conjunto vacío, la sede inexistente y la restricción de rol.

## Criterios de aceptación

### CA-01 — Asignación de una o ambas sedes

**Dado** un profesional existente  
**Cuando** ADMIN le asigna solo HIC y, en otra petición, HIC e ICV  
**Entonces** ambas peticiones responden con éxito y la consulta del profesional devuelve exactamente el conjunto enviado en la última petición.

### CA-02 — Conjunto vacío rechazado

**Dado** un profesional con la sede HIC asignada  
**Cuando** ADMIN envía un conjunto de sedes vacío  
**Entonces** la API responde con un error de validación y el profesional conserva HIC.

### CA-03 — Sede inexistente rechazada

**Dado** un identificador o código de sede que no pertenece al catálogo fijo  
**Cuando** ADMIN intenta asignarlo a un profesional  
**Entonces** la API responde con un error de validación y no se persiste ningún cambio.

### CA-04 — La sede asignada habilita la publicación de agenda

**Dado** un profesional asignado únicamente a HIC  
**Cuando** intenta crear un bloque en ICV y, a continuación, en HIC  
**Entonces** el bloque en ICV se rechaza y el bloque en HIC se acepta (RN-07), verificado contra [[HU-017-crear-bloques-de-disponibilidad-con-slots]].

### CA-05 — Solo ADMIN asigna sedes

**Dado** un usuario autenticado con rol `USER` o `PROFESSIONAL`  
**Cuando** intenta modificar las sedes de cualquier profesional, incluido él mismo  
**Entonces** la API responde con un error de autorización y las sedes no cambian.

## Definition of Done

- [x] Los criterios CA-01 a CA-05 están validados con evidencia concreta.
- [x] La operación usa la tabla `professional_sites` de V2; cualquier cambio de esquema se hace con una migración Flyway posterior a V4.
- [x] La consulta "profesional habilitado en sede" vive en el dominio y es la misma que usa la creación de bloques.
- [x] Los endpoints exigen rol `ADMIN` aplicando [[HU-005-autorizar-peticiones-por-rol-y-ownership]].
- [x] La pantalla de `citas-web` obtiene las sedes del catálogo fijo de la API y no las tiene escritas en el código.
- [x] Existen pruebas automatizadas de la asignación válida, del conjunto vacío, de la sede inexistente y del rol, y pasan.
- [x] El contrato de los endpoints de sedes del profesional está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [x] La trazabilidad de esta HU y de [[EP-004-gestion-de-profesionales]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Ejecución de referencia del **2026-09-23**: backend `docker compose run --rm citas-api-dev mvn -B test` → **242 pruebas, 0 fallos** en 22 clases; frontend `npm test` en `citas-web` → **88 pruebas, 0 fallos**, con `typecheck`, `lint` y `build` en verde. La verificación fue independiente (`backend-verifier` y `frontend-verifier`, agentes que no escribieron el código): `EVIDENCIAS_S3.md` §10. Las clases de prueba del backend cuelgan de `citas-api/src/test/java/com/fcv/citas/`.

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | `infrastructure/rest/ProfessionalAdminIntegrationTest#replacesSitesAndRejectsEmptySet` (alta con HIC; `PUT …/sites` con HIC + ICV → 200 y `sites[*].code` contiene exactamente ambas); `#createsProfessionalWithUserRoleAndHashedPassword` (`sites[0].code = HIC`) | La operación reemplaza el conjunto completo, no añade |
| CA-02 | Cumple | `ProfessionalAdminIntegrationTest#replacesSitesAndRejectsEmptySet` (conjunto vacío → 400 y el detalle conserva las 2 sedes previas); `domain/professional/ProfessionalTest#rejectsEmptyOrNullSites` | La invariante «al menos una sede» vive en `Professional.requireSites(...)` |
| CA-03 | Cumple | `ProfessionalAdminIntegrationTest#replacesSitesAndRejectsEmptySet` (`siteIds: [9999]` → 400 y el detalle sigue con 2 sedes); `application/professional/ManageProfessionalsUseCase#requireSites` sobre `domain/catalog/SiteCatalog#allActive` | — |
| CA-04 | Cumple | `infrastructure/rest/ScheduleIntegrationTest#blockAtUnassignedSiteIsRejected` (ICV → 422 `SITE_NOT_ASSIGNED`, 0 bloques) y `#blockExpandsIntoEightSlotsStoredWithTheSameLocalTimes` (HIC → 201); `infrastructure/rest/VerificationGapsIntegrationTest#siteNoLongerAssignedIsNeitherOfferedNorBookable` | Verificado contra [[HU-017-crear-bloques-de-disponibilidad-con-slots]]. La última prueba añade el caso de quitar la sede después: deja de ofrecerse y deja de admitir reservas |
| CA-05 | Cumple | `SecurityConfig` (`/api/admin/**` → `hasRole("ADMIN")`); `infrastructure/rest/AuthorizationIntegrationTest#anonymousGets401AndWrongRoleGets403OnAdminRoutes` y `#onlyAdminReachesAdminRoutes` (USER y PROFESSIONAL → 403) | La ruta es `PUT /api/admin/professionals/{id}/sites`: un PROFESSIONAL tampoco cambia las suyas |
| DoD — CA-01 a CA-05 validados con evidencia concreta | Cumple | Filas CA-01 a CA-05 de esta tabla | — |
| DoD — Usa `professional_sites` de V2; sin migración nueva | Cumple | `V2__configurable_catalogs_and_professionals.sql` (tabla `professional_sites`); `infrastructure/persistence/professional/ProfessionalSiteJpaEntity` | — |
| DoD — «Profesional habilitado en sede» vive en el dominio y la usa la creación de bloques | Cumple | `domain/professional/Professional#worksAt`, invocado por `application/schedule/ManageScheduleUseCase#validated` y por `application/appointment/BookAppointmentUseCase#book` | Una sola definición para publicar agenda y para reservar; `HexagonalArchitectureTest` garantiza que no arrastra framework |
| DoD — Los endpoints exigen rol ADMIN aplicando [[HU-005-autorizar-peticiones-por-rol-y-ownership]] | Cumple | `SecurityConfig`; `AuthorizationIntegrationTest#onlyAdminReachesAdminRoutes` | — |
| DoD — La pantalla de `citas-web` toma las sedes del catálogo de la API | Cumple | `citas-web/src/api/catalogApi.ts#getSites`; `citas-web/src/pages/admin/professionalPickers.tsx`; `citas-web/src/adminOperations.test.tsx` → «alta: envía especialidades con principal y sedes; un 409 DUPLICATE marca el campo»; `citas-web/src/professionalAgenda.test.tsx` → «crea un bloque: solo sedes asignadas…» | Ninguna sede escrita en el código del frontend |
| DoD — Pruebas de asignación válida, conjunto vacío, sede inexistente y rol | Cumple | `ProfessionalAdminIntegrationTest#replacesSitesAndRejectsEmptySet`; `ProfessionalTest#rejectsEmptyOrNullSites` y `#acceptsAnAlreadyImmutableSiteSet`; `AuthorizationIntegrationTest#onlyAdminReachesAdminRoutes` | — |
| DoD — Contrato de los endpoints de sedes reflejado en [[HU-033-publicar-contrato-rest-documentado]] | Cumple | `citas-api/docs/wiki/llm-wiki/wiki/contrato-rest-citas.md`, sección «Profesionales — ADMIN (HU-013 a HU-016)» | — |
| DoD — Trazabilidad de esta HU y de [[EP-004-gestion-de-profesionales]] actualizada | Cumple | Esta tabla y el historial de validación | — |

## Historial de validación

- 2026-09-23 — Estado `Completada` (fase F11 de `PLAN_RETOMA_S3.md`): matriz de evidencia completa, los 5 criterios y los 8 ítems de DoD en `Cumple`. Cierre dentro de la aprobación delegada de S3 (`AGENTS.md` §6); el usuario puede reabrirla.
- 2026-09-23 — Estado `En validación` (skill `scrum-spec-orchestrator`, paso 10): se recolecta del repositorio la evidencia de cada criterio y de cada ítem de DoD.
- 2026-09-18 — Estado `En desarrollo` (skill `scrum-spec-orchestrator`, paso 9): se inicia la implementación en la fase F3 de `PLAN_RETOMA_S3.md`.
- 2026-09-18 — Estado `Aprobada` por **aprobación delegada** de S3: el usuario pidió continuar S3 dejando las decisiones de diseño a criterio del agente (`AGENTS.md` §6). Alcance y decisiones D5–D13 en `PLAN_RETOMA_S3.md` y [[dec-004-decisiones-s3-reserva]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- Incógnita abierta **INC-014** (ver [[EP-004-gestion-de-profesionales]]): el PRD no define qué ocurre con los bloques futuros y las citas ya comprometidas en una sede que se retira al profesional. Esta HU no toca bloques ni citas existentes; hasta que se decida, conviene que la búsqueda de [[HU-022-buscar-disponibilidad-con-filtros]] no ofrezca slots de bloques en sedes que el profesional ya no tiene asignadas.
- CA-02 asume que un profesional siempre tiene al menos una sede, por la redacción "una o ambas sedes" de RF-07. Si se admite un profesional sin sede (por ejemplo recién creado), la regla solo aplica a la operación de reemplazo y no al alta de [[HU-013-crear-profesional-con-datos-de-registro]].
