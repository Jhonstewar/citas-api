---
id: HU-013
tipo: historia-de-usuario
titulo: "Crear profesional con sus datos de registro"
estado: Completada
epica: "[[EP-004-gestion-de-profesionales]]"
requisitos: [RF-07]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 3"
dependencias:
  - "[[HU-001-registrar-cuenta-de-usuario]]"
  - "[[HU-005-autorizar-peticiones-por-rol-y-ownership]]"
relacionadas:
  - "[[HU-014-asignar-especialidades-y-especialidad-primaria]]"
  - "[[HU-015-asignar-sedes-al-profesional]]"
  - "[[HU-016-activar-o-desactivar-profesional]]"
  - "[[HU-002-iniciar-sesion-con-jwt]]"
  - "[[HU-033-publicar-contrato-rest-documentado]]"
---

# HU-013 — Crear profesional con sus datos de registro

## Historia de usuario

**COMO** ADMIN  
**QUIERO** dar de alta un profesional ficticio creando su cuenta `PROFESSIONAL` junto con su código profesional y su matrícula sintética  
**PARA** disponer de profesionales que luego puedan configurarse con especialidades y sedes y publicar agenda

> Como ADMIN, quiero dar de alta un profesional ficticio creando su cuenta `PROFESSIONAL` junto con su código profesional y su matrícula sintética para disponer de profesionales que luego puedan configurarse con especialidades y sedes y publicar agenda.

## Contexto y descripción

RF-07 reserva a ADMIN la creación del usuario `PROFESSIONAL` y el registro de su código profesional y de su matrícula ficticia. PRD §2 es explícito en que el profesional no se autorregistra: lo crea ADMIN. El profesional es, por tanto, primero un usuario del sistema con rol `PROFESSIONAL` y, además, un perfil profesional con datos propios.

El esquema ya existe: la migración V2 define la tabla `professionals` en relación 1:1 con `users`, con `professional_code` y `license_number` únicos y el indicador `active`. Esta HU no crea esquema; construye el caso de uso, el endpoint y la pantalla que dan de alta ambas filas de forma coherente y atómica, reutilizando las reglas de unicidad de email y documento y el hash de contraseña de [[HU-001-registrar-cuenta-de-usuario]].

Es la primera de cuatro HU de [[EP-004-gestion-de-profesionales]]: un profesional recién creado todavía no tiene especialidades ([[HU-014-asignar-especialidades-y-especialidad-primaria]]) ni sedes ([[HU-015-asignar-sedes-al-profesional]]), y por eso aún no puede publicar agenda ni aparecer como oferta.

## Alcance

- Endpoint REST de creación de profesional en `citas-api`, restringido al rol `ADMIN`.
- Creación en una única transacción del usuario con rol `PROFESSIONAL` (datos mínimos de RF-01) y del perfil profesional con código y matrícula (RF-07).
- Validación server-side de unicidad de email, documento, código profesional y matrícula.
- Estado inicial del profesional creado: activo.
- Listado y consulta de detalle de profesionales para ADMIN (alcance de [[EP-004-gestion-de-profesionales]]).
- Pantalla "CRUD de profesionales" en `citas-web` con el formulario de alta y el listado (PRD §6).

## Fuera de alcance

- Asignación de especialidades y marcado de primaria, que se cubre en [[HU-014-asignar-especialidades-y-especialidad-primaria]].
- Asignación de sedes, que se cubre en [[HU-015-asignar-sedes-al-profesional]].
- Activación y desactivación, que se cubre en [[HU-016-activar-o-desactivar-profesional]].
- Autorregistro del profesional: el PRD lo excluye (PRD §2).
- Edición del código o de la matrícula de un profesional ya creado: pendiente de la incógnita INC-015.
- Borrado físico de profesionales: un profesional referenciado se desactiva, no se borra.
- Creación de cuentas `ADMIN`: pendiente de la incógnita INC-004 de [[EP-001-identidad-y-acceso-seguro]].

## Reglas de negocio

- Solo ADMIN crea profesionales (RF-07, PRD §2).
- El profesional es un usuario con rol `PROFESSIONAL`; los nombres y el documento viven en el usuario, no se repiten en el perfil profesional.
- Email y documento son únicos en todo el sistema, igual que en el registro de USER (RF-01).
- Código profesional y matrícula son únicos (esquema V2) y sintéticos; nunca se usan datos reales de FCV (RF-07, PRD §8, §9).
- La contraseña nunca se almacena ni se registra en logs en texto plano (RF-01, PRD §8).
- Un usuario tiene como máximo un perfil profesional (1:1).

## Dependencias y relaciones

- Épica: [[EP-004-gestion-de-profesionales]]
- Dependencias: [[HU-001-registrar-cuenta-de-usuario]], [[HU-005-autorizar-peticiones-por-rol-y-ownership]]
- Relacionadas: [[HU-014-asignar-especialidades-y-especialidad-primaria]], [[HU-015-asignar-sedes-al-profesional]], [[HU-016-activar-o-desactivar-profesional]], [[HU-002-iniciar-sesion-con-jwt]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** El esquema ya existe y las reglas de unicidad y hash se reutilizan de la HU de registro, pero la operación coordina dos agregados (usuario y perfil profesional) que deben persistirse juntos o no persistirse, con cuatro restricciones de unicidad que deben traducirse a errores de negocio distinguibles y no a errores genéricos de base de datos. Añade además la primera pantalla administrativa de gestión de personas.

## Tareas de desarrollo

- [ ] **T-01 — Modelar el perfil profesional en el dominio**  
  Dificultad: Bajo  
  Descripción: Entidad de dominio del profesional con referencia al usuario, código profesional, matrícula y estado activo, con las invariantes de campos obligatorios, sin dependencias de framework.

- [ ] **T-02 — Implementar el caso de uso de alta de profesional**  
  Dificultad: Medio  
  Descripción: Caso de uso que valida unicidad de email, documento, código y matrícula, crea el usuario con rol `PROFESSIONAL` y contraseña hasheada reutilizando el servicio de registro, crea el perfil profesional activo y confirma todo en una única transacción.

- [ ] **T-03 — Implementar el adaptador de persistencia sobre el esquema V2**  
  Dificultad: Medio  
  Descripción: Mapeo JPA de `professionals` y su relación con `users` y roles, y traducción de las violaciones de las restricciones únicas a errores de dominio diferenciados.

- [ ] **T-04 — Exponer los endpoints REST de alta, listado y detalle**  
  Dificultad: Medio  
  Descripción: Controlador restringido al rol `ADMIN` con DTO validado, respuesta de creación sin contraseña ni hash, y errores diferenciados para email, documento, código o matrícula duplicados.

- [ ] **T-05 — Construir la pantalla de gestión de profesionales en citas-web**  
  Dificultad: Medio  
  Descripción: Vista React + TypeScript con formulario de alta y listado de profesionales, visible solo para ADMIN, que presenta los errores de validación y de duplicado devueltos por la API.

- [ ] **T-06 — Pruebas de alta de profesional**  
  Dificultad: Medio  
  Descripción: Pruebas de dominio y de aplicación para el alta válida y los cuatro duplicados, prueba de atomicidad, e integración REST para la restricción de rol y para el login posterior del profesional creado.

## Criterios de aceptación

### CA-01 — Alta de profesional válida

**Dado** un ADMIN autenticado y datos sintéticos válidos con email, documento, código profesional y matrícula no registrados  
**Cuando** envía la petición de alta de profesional  
**Entonces** la API responde con éxito, existe un usuario con rol `PROFESSIONAL` y un perfil profesional activo asociado a él con el código y la matrícula enviados, y la respuesta no contiene la contraseña ni su hash.

### CA-02 — El profesional creado puede autenticarse con su rol

**Dado** un profesional recién creado por ADMIN con credenciales conocidas  
**Cuando** inicia sesión por el flujo de [[HU-002-iniciar-sesion-con-jwt]]  
**Entonces** obtiene tokens cuyo contexto de autorización contiene el rol `PROFESSIONAL`.

### CA-03 — Código profesional o matrícula duplicados rechazados

**Dado** un profesional existente con un código profesional y una matrícula concretos  
**Cuando** ADMIN intenta crear otro profesional repitiendo el código o la matrícula  
**Entonces** la API responde con un error que identifica el campo duplicado y no se crea ni el usuario ni el perfil profesional.

### CA-04 — Email o documento duplicados rechazados

**Dado** un usuario existente, de cualquier rol, con un email y un documento concretos  
**Cuando** ADMIN intenta crear un profesional repitiendo el email o el documento  
**Entonces** la API responde con un error que identifica el campo duplicado y no se persiste ningún dato del intento.

### CA-05 — Solo ADMIN puede crear profesionales

**Dado** un usuario autenticado con rol `USER` o `PROFESSIONAL`, o una petición sin autenticar  
**Cuando** invoca el endpoint de alta de profesional  
**Entonces** la API responde con el error de autenticación o de autorización correspondiente y no se crea ningún registro.

### CA-06 — Alta atómica

**Dado** un alta en la que la creación del perfil profesional falla después de haber preparado el usuario  
**Cuando** la transacción termina  
**Entonces** no queda en base de datos ningún usuario `PROFESSIONAL` sin perfil profesional ni ningún perfil sin usuario.

### CA-07 — Contraseña almacenada con hash

**Dado** un profesional creado correctamente  
**Cuando** se inspecciona la fila del usuario en la base de datos y los logs de la operación  
**Entonces** la contraseña está almacenada con hash adaptativo y no aparece en texto plano en la base de datos ni en los logs.

### CA-08 — ADMIN consulta el listado de profesionales

**Dado** varios profesionales creados, activos e inactivos  
**Cuando** ADMIN consulta el listado  
**Entonces** la respuesta incluye cada profesional con nombres, código profesional, matrícula y estado activo, sin datos de credenciales.

## Definition of Done

- [x] Los criterios CA-01 a CA-08 están validados con evidencia concreta.
- [x] El alta reutiliza el esquema de V2 sin migración nueva; si se requiere algún cambio de esquema, existe una migración Flyway posterior a V4 y no se editan las migraciones existentes.
- [x] Usuario y perfil profesional se crean en una única transacción, demostrado con una prueba de atomicidad.
- [x] Las cuatro restricciones de unicidad producen errores de negocio distinguibles y no un error interno genérico.
- [x] Los endpoints de alta, listado y detalle exigen rol `ADMIN` aplicando el mecanismo de [[HU-005-autorizar-peticiones-por-rol-y-ownership]].
- [x] Ninguna respuesta ni log expone contraseñas, hashes ni tokens.
- [x] La pantalla de gestión de profesionales de `citas-web` consume la API mediante la URL leída de la configuración de entorno.
- [x] Existen pruebas automatizadas de dominio, aplicación e integración REST para el alta válida, los duplicados y la restricción de rol, y pasan.
- [x] El contrato de los endpoints de profesionales está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [x] La trazabilidad de esta HU y de [[EP-004-gestion-de-profesionales]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Ejecución de referencia del **2026-09-23**: backend `docker compose run --rm citas-api-dev mvn -B test` → **242 pruebas, 0 fallos** en 22 clases; frontend `npm test` en `citas-web` → **88 pruebas, 0 fallos**, con `typecheck`, `lint` y `build` en verde. La verificación fue independiente (`backend-verifier` y `frontend-verifier`, agentes que no escribieron el código): `EVIDENCIAS_S3.md` §10. Las clases de prueba del backend cuelgan de `citas-api/src/test/java/com/fcv/citas/`.

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | `infrastructure/rest/ProfessionalAdminIntegrationTest#createsProfessionalWithUserRoleAndHashedPassword` | 201 con código, matrícula, `active: true`, 2 especialidades y la sede; el cuerpo no trae `password` ni `passwordHash` y la aserción comprueba que la respuesta completa no contiene la contraseña enviada. En base: rol `PROFESSIONAL` en `user_roles` |
| CA-02 | Cumple | `ProfessionalAdminIntegrationTest#createdProfessionalCanLogInWithItsRole` | Login real por `POST /api/auth/login`; `GET /api/me` devuelve `roles[0] = PROFESSIONAL` y `GET /api/professional/me` responde 200 |
| CA-03 | Cumple | `ProfessionalAdminIntegrationTest#duplicateCodeIsRejectedAtomically` (409 `DUPLICATE`, `field = professionalCode`); `infrastructure/rest/VerificationGapsIntegrationTest#duplicateLicenseAndDocumentIdentifyTheirField` (`field = licenseNumber`) | El campo duplicado viaja en el cuerpo del ProblemDetail; la primera prueba comprueba además que no queda el usuario del intento |
| CA-04 | Cumple | `ProfessionalAdminIntegrationTest#duplicateEmailIsRejected` (`field = email`); `VerificationGapsIntegrationTest#duplicateLicenseAndDocumentIdentifyTheirField` (`field = documentNumber`) | La unicidad se comprueba contra `users`, sea cual sea el rol del titular previo |
| CA-05 | Cumple | `ProfessionalAdminIntegrationTest#onlyAdminCreates` (USER → 403 y 0 usuarios creados); `infrastructure/rest/AuthorizationIntegrationTest#anonymousGets401AndWrongRoleGets403OnAdminRoutes` y `#onlyAdminReachesAdminRoutes` (anónimo → 401, PROFESSIONAL → 403) | `SecurityConfig` protege todo `/api/admin/**` con `hasRole("ADMIN")` |
| CA-06 | Cumple | `ProfessionalAdminIntegrationTest#duplicateCodeIsRejectedAtomically` (0 filas en `users` para el email del intento fallido); `VerificationGapsIntegrationTest#concurrentProfessionalCreationsLeaveNoOrphanUsers` (4 altas simultáneas con el mismo código: 1 × 201, 3 × 409 y **un único** usuario creado) | El fallo al insertar el perfil deshace también el usuario: `ManageProfessionalsUseCase#create` envuelve las dos escrituras en un solo `tx.inTransaction` |
| CA-07 | Cumple | `ProfessionalAdminIntegrationTest#createsProfessionalWithUserRoleAndHashedPassword` (`password_hash` empieza por `{bcrypt}`); `infrastructure/rest/professional/AdminProfessionalController.CreateProfessionalRequest#toString` enmascara la contraseña (`password=***`) | El `toString` enmascarado es lo que impide que la contraseña llegue al log si Spring registra el DTO ante un error de validación |
| CA-08 | Cumple | `ProfessionalAdminIntegrationTest#listsProfessionalsWithoutCredentials` (la respuesta no contiene la cadena `password`); `#deactivatesAndReactivatesKeepingAssignments` (el listado filtra por `active`) | `ProfessionalResponse` expone nombres, código, matrícula, estado, especialidades y sedes; ningún campo de credencial |
| DoD — CA-01 a CA-08 validados con evidencia concreta | Cumple | Filas CA-01 a CA-08 de esta tabla | — |
| DoD — Reutiliza el esquema de V2 sin migración nueva | Cumple | `V2__configurable_catalogs_and_professionals.sql` (tablas `professionals`, `professional_specialties`, `professional_sites`); las migraciones posteriores son V5 (auditoría), V6 (Medicina General) y V7 (direcciones) | Ninguna migración existente se editó: `FlywayMigratesEmptySchemaTest#elEsquemaMigradoValidaContraLasMigraciones` detectaría un checksum alterado |
| DoD — Usuario y perfil en una única transacción, con prueba de atomicidad | Cumple | `application/professional/ManageProfessionalsUseCase#create`; `ProfessionalAdminIntegrationTest#duplicateCodeIsRejectedAtomically`; `VerificationGapsIntegrationTest#concurrentProfessionalCreationsLeaveNoOrphanUsers` | — |
| DoD — Las cuatro unicidades producen errores de negocio distinguibles | Cumple | `duplicateCodeIsRejectedAtomically`, `duplicateEmailIsRejected`, `duplicateLicenseAndDocumentIdentifyTheirField`; `domain/shared/DuplicateValueException` y `GlobalExceptionHandler#codedConflict` | 409 `DUPLICATE` con la extensión `field` en los cuatro casos, nunca un 500 genérico |
| DoD — Alta, listado y detalle exigen rol ADMIN aplicando [[HU-005-autorizar-peticiones-por-rol-y-ownership]] | Cumple | `SecurityConfig` (`/api/admin/**` → `hasRole("ADMIN")`); `onlyAdminCreates`; `AuthorizationIntegrationTest#onlyAdminReachesAdminRoutes` | — |
| DoD — Ninguna respuesta ni log expone contraseñas, hashes ni tokens | Cumple | `createsProfessionalWithUserRoleAndHashedPassword`; `listsProfessionalsWithoutCredentials`; `CreateProfessionalRequest#toString`; `HexagonalArchitectureTest.nadieEscribeEnLaSalidaEstandar` | — |
| DoD — La pantalla de profesionales de `citas-web` usa la URL del backend del entorno | Cumple | `citas-web/src/api/adminApi.ts` sobre `API_ROUTES` de `contracts.ts`; `citas-web/src/api/contracts.test.ts` → «falla al cargar si VITE_API_URL está vacía, en vez de usar un valor por defecto»; `citas-web/src/adminOperations.test.tsx` → «alta: envía especialidades con principal y sedes; un 409 DUPLICATE marca el campo» | Ninguna URL escrita en el código |
| DoD — Pruebas de dominio, aplicación e integración REST del alta, los duplicados y el rol | Cumple | Dominio: `domain/professional/ProfessionalTest`, `domain/professional/SpecialtyAssignmentTest`. Integración: `ProfessionalAdminIntegrationTest` (11), `VerificationGapsIntegrationTest#concurrentProfessionalCreationsLeaveNoOrphanUsers` y `#duplicateLicenseAndDocumentIdentifyTheirField` | — |
| DoD — Contrato de los endpoints de profesionales reflejado en [[HU-033-publicar-contrato-rest-documentado]] | Cumple | `citas-api/docs/wiki/llm-wiki/wiki/contrato-rest-citas.md`, sección «Profesionales — ADMIN (HU-013 a HU-016)» | — |
| DoD — Trazabilidad de esta HU y de [[EP-004-gestion-de-profesionales]] actualizada | Cumple | Esta tabla y el historial de validación | — |

## Historial de validación

- 2026-09-23 — Estado `Completada` (fase F11 de `PLAN_RETOMA_S3.md`): matriz de evidencia completa, los 8 criterios y los 10 ítems de DoD en `Cumple`. Cierre dentro de la aprobación delegada de S3 (`AGENTS.md` §6); el usuario puede reabrirla.
- 2026-09-23 — Estado `En validación` (skill `scrum-spec-orchestrator`, paso 10): se recolecta del repositorio la evidencia de cada criterio y de cada ítem de DoD.
- 2026-09-18 — Estado `En desarrollo` (skill `scrum-spec-orchestrator`, paso 9): se inicia la implementación en la fase F3 de `PLAN_RETOMA_S3.md`.
- 2026-09-18 — Estado `Aprobada` por **aprobación delegada** de S3: el usuario pidió continuar S3 dejando las decisiones de diseño a criterio del agente (`AGENTS.md` §6). Alcance y decisiones D5–D13 en `PLAN_RETOMA_S3.md` y [[dec-004-decisiones-s3-reserva]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- Incógnita abierta **INC-013** (ver [[EP-004-gestion-de-profesionales]]): el PRD no define cómo recibe el profesional su contraseña inicial (valor fijado por ADMIN, contraseña temporal o flujo de recuperación de RF-03). CA-02 exige solo que el profesional pueda autenticarse con credenciales conocidas; el mecanismo concreto debe decidirse antes de aprobar la HU, porque cambia el formulario y el contrato de alta.
- Incógnita abierta **INC-015** (ver [[EP-004-gestion-de-profesionales]]): el PRD no indica si código y matrícula son editables tras el alta. Esta HU no incluye edición.
- Incógnita abierta **INC-004** (ver [[EP-001-identidad-y-acceso-seguro]]): las pruebas de esta HU necesitan una cuenta ADMIN, que se asume precargada por seed.
- El PRD no define un formato para el código profesional ni para la matrícula más allá de ser sintéticos y únicos; los CA no imponen formato.
