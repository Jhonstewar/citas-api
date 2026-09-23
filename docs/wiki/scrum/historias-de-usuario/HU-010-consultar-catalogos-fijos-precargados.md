---
id: HU-010
tipo: historia-de-usuario
titulo: "Consultar los catálogos fijos precargados"
estado: Completada
epica: "[[EP-003-catalogos-del-sistema]]"
requisitos: [RF-05]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 3"
dependencias:
  - "[[HU-002-iniciar-sesion-con-jwt]]"
relacionadas:
  - "[[HU-011-gestionar-especialidades-y-su-duracion]]"
  - "[[HU-022-buscar-disponibilidad-con-filtros]]"
---

# HU-010 — Consultar los catálogos fijos precargados

## Historia de usuario

**COMO** usuario autenticado del sistema
**QUIERO** consultar los catálogos fijos precargados
**PARA** que los formularios de búsqueda, agenda y afiliación ofrezcan siempre valores válidos y consistentes.

> Como usuario autenticado del sistema, quiero consultar los catálogos fijos precargados para que los formularios de búsqueda, agenda y afiliación ofrezcan siempre valores válidos y consistentes.

## Contexto y descripción

RF-05 define cinco catálogos fijos precargados y de solo lectura: roles, estados de cita, estados de reprogramación, regímenes y sedes. A diferencia de los catálogos configurables de RF-06, estos no se crean ni se modifican desde la aplicación: se cargan por seed y la API solo los expone para consulta.

Las dos sedes del laboratorio son fijas y quedan precargadas con su nombre y su dirección (PRD §3): Hospital Internacional de Colombia (HIC), Km 7 Autopista Bucaramanga–Piedecuesta, Valle de Menzulí, Santander; y Fundación Cardiovascular de Colombia / Instituto Cardiovascular (ICV), Calle 155A No. 23-58, Urbanización El Bosque, Floridablanca, Santander.

Esta HU es la base de datos maestros de todo el producto: la búsqueda de disponibilidad filtra por sede, la agenda del profesional selecciona sede por bloque, la afiliación del paciente escoge régimen, y el ciclo de vida de la cita y de la reprogramación se apoya en los catálogos de estado. Que el conjunto de estados sea cerrado y venga del seed es lo que hace verificable la exigencia de transiciones explícitas de RN-11.

## Alcance

- Migración Flyway que crea las tablas de los cinco catálogos fijos de RF-05 y su seed de valores.
- Seed de los roles `USER`, `PROFESSIONAL` y `ADMIN` (PRD §2), coordinado con el esquema de usuarios y roles de [[HU-001-registrar-cuenta-de-usuario]].
- Seed de los estados de cita usados por el producto: `REQUESTED`, `APPROVED`, `REJECTED`, `CANCELLED`, `COMPLETED` y `NO_SHOW`.
- Seed de los estados de reprogramación, incluido `PENDING` (RF-15).
- Seed de los regímenes utilizados por la afiliación del paciente (RF-04).
- Seed de las dos sedes fijas HIC e ICV con nombre, sigla y dirección (PRD §3).
- Endpoints de consulta autenticados para cada catálogo fijo.
- Consumo de estos catálogos desde `citas-web` para poblar selectores de sede, estado y régimen.

## Fuera de alcance

- Cualquier operación de escritura sobre estos catálogos: son de solo lectura (RF-05).
- Creación de sedes nuevas: el laboratorio trabaja con dos sedes fijas (PRD §3).
- Catálogos configurables de EPS, planes y especialidades, que se cubren en [[HU-011-gestionar-especialidades-y-su-duracion]] y [[HU-012-gestionar-eps-y-planes]].
- La lógica de transición entre estados de cita, que pertenece a las HU del ciclo de vida de la cita.
- Internacionalización o traducción de las etiquetas de los catálogos: no está en el PRD.

## Reglas de negocio

- Los catálogos fijos se precargan por seed y son de solo lectura desde la aplicación (RF-05).
- Los catálogos fijos son roles, estados de cita, estados de reprogramación, regímenes y sedes (RF-05).
- Las sedes del laboratorio son exactamente dos: HIC e ICV (PRD §3).
- Los catálogos fijos se cargan por seed versionado, no por inserciones manuales (restricciones técnicas, base de datos).
- La consulta de catálogos requiere sesión autenticada; no expone datos sensibles de personas.
- Los valores de estado de cita cubren el ciclo completo que el producto usa: `REQUESTED` (RF-12), `APPROVED` (RF-11, RF-12), `REJECTED` (RF-12), `CANCELLED` (RF-14), `COMPLETED` y `NO_SHOW` (RF-17).
- El catálogo de estados de reprogramación incluye `PENDING`, el estado en que nace toda solicitud de reprogramación (RF-15).

## Dependencias y relaciones

- Épica: [[EP-003-catalogos-del-sistema]]
- Dependencias: [[HU-002-iniciar-sesion-con-jwt]]
- Relacionadas: [[HU-011-gestionar-especialidades-y-su-duracion]], [[HU-012-gestionar-eps-y-planes]], [[HU-015-asignar-sedes-al-profesional]], [[HU-022-buscar-disponibilidad-con-filtros]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** Las reglas son simples y no hay lógica de negocio compleja, pero la HU fija el vocabulario de estados de todo el producto y crea cinco tablas de referencia con su seed versionado. Un error aquí se propaga a la cita, la reprogramación, la agenda y la afiliación, por lo que la dificultad está en acertar el modelo y en dejar el seed reproducible sobre base vacía, no en el código.

## Tareas de desarrollo

- [ ] **T-01 — Modelar los catálogos fijos en el dominio**
  Dificultad: Bajo
  Descripción: Representar en la capa de dominio los conjuntos cerrados de rol, estado de cita, estado de reprogramación y régimen, y la entidad sede con su nombre, sigla y dirección, sin dependencias de framework y sin operaciones de modificación.

- [ ] **T-02 — Crear la migración Flyway de los catálogos fijos**
  Dificultad: Medio
  Descripción: Migración versionada que crea las tablas de roles, estados de cita, estados de reprogramación, regímenes y sedes, con clave natural única por código, e inserta el seed completo, incluidas las dos sedes fijas con su dirección literal del PRD §3.

- [ ] **T-03 — Implementar los casos de uso de consulta de catálogos**
  Dificultad: Bajo
  Descripción: Casos de uso de aplicación que devuelven cada catálogo completo a través de puertos de lectura, sin exponer ninguna operación de creación, edición o borrado.

- [ ] **T-04 — Exponer los adaptadores REST de consulta**
  Dificultad: Bajo
  Descripción: Controladores de solo lectura para cada catálogo fijo, con respuesta que incluya código y etiqueta legible, y en el caso de sedes también sigla y dirección.

- [ ] **T-05 — Restringir la escritura en la configuración de seguridad**
  Dificultad: Bajo
  Descripción: Declarar las rutas de catálogos fijos como accesibles solo a usuarios autenticados y asegurar que no exista ningún manejador de método de escritura sobre ellas.

- [ ] **T-06 — Consumir los catálogos fijos desde citas-web**
  Dificultad: Medio
  Descripción: Capa de acceso a datos en React + TypeScript que obtiene los catálogos mediante la URL configurable por entorno, los tipa y los reutiliza en los selectores de sede, estado y régimen de las pantallas del producto.

- [ ] **T-07 — Pruebas de seed y de consulta de catálogos**
  Dificultad: Medio
  Descripción: Pruebas de integración que arrancan sobre una base vacía, verifican el contenido del seed de los cinco catálogos y comprueban que los endpoints de consulta responden autenticados y que las peticiones de escritura no están soportadas.

## Criterios de aceptación

### CA-01 — Seed aplicado por migración sobre base vacía

**Dado** una base de datos MySQL 8.4 completamente vacía
**Cuando** se arranca `citas-api`
**Entonces** Flyway crea las tablas de roles, estados de cita, estados de reprogramación, regímenes y sedes, inserta sus valores, y el arranque finaliza sin error y sin requerir ninguna carga manual.

### CA-02 — Las dos sedes fijas quedan precargadas con su dirección

**Dado** el seed aplicado
**Cuando** se consulta el catálogo de sedes
**Entonces** devuelve exactamente dos sedes: Hospital Internacional de Colombia (HIC) con dirección «Km 7 Autopista Bucaramanga–Piedecuesta, Valle de Menzulí, Santander» y Fundación Cardiovascular de Colombia / Instituto Cardiovascular (ICV) con dirección «Calle 155A No. 23-58, Urbanización El Bosque, Floridablanca, Santander».

### CA-03 — El catálogo de estados de cita cubre el ciclo completo

**Dado** el seed aplicado
**Cuando** se consulta el catálogo de estados de cita
**Entonces** contiene los valores `REQUESTED`, `APPROVED`, `REJECTED`, `CANCELLED`, `COMPLETED` y `NO_SHOW`.

### CA-04 — El catálogo de estados de reprogramación incluye PENDING

**Dado** el seed aplicado
**Cuando** se consulta el catálogo de estados de reprogramación
**Entonces** contiene el valor `PENDING` como estado inicial de toda solicitud de reprogramación, junto con los estados de resolución que el producto usa.

### CA-05 — Roles y regímenes precargados y consultables

**Dado** el seed aplicado
**Cuando** se consultan los catálogos de roles y de regímenes
**Entonces** el de roles contiene `USER`, `PROFESSIONAL` y `ADMIN`, y el de regímenes devuelve al menos un valor utilizable por el formulario de afiliación de [[HU-009-registrar-afiliacion-a-eps-y-plan]].

### CA-06 — No existen endpoints de escritura sobre catálogos fijos

**Dado** la API en ejecución con una sesión `ADMIN` válida
**Cuando** se intenta crear, editar o borrar un elemento de cualquiera de los cinco catálogos fijos mediante las rutas de catálogo
**Entonces** la API responde con un error de método o de ruta no soportada en todos los casos, y el contenido de los catálogos permanece idéntico al del seed.

### CA-07 — La consulta exige autenticación

**Dado** la API en ejecución
**Cuando** se consulta cualquier catálogo fijo sin cabecera de autorización
**Entonces** la API responde con un código de no autorizado, y con un access token válido de cualquiera de los tres roles responde con el catálogo completo.

### CA-08 — Los selectores del frontend se pueblan desde la API

**Dado** un usuario autenticado en `citas-web`
**Cuando** abre una pantalla que ofrece selección de sede, de estado de cita o de régimen
**Entonces** las opciones mostradas provienen de la respuesta de la API y no de valores escritos en el código del frontend.

## Definition of Done

- [x] Los criterios CA-01 a CA-08 están validados con evidencia concreta.
- [x] Existe una migración Flyway versionada que crea las cinco tablas de catálogo fijo y su seed, y se aplica correctamente sobre una base vacía sin pasos manuales.
- [x] Las dos sedes precargadas reproducen el nombre, la sigla y la dirección literales del PRD §3.
- [x] El código no contiene ninguna operación de escritura, ni en aplicación ni en adaptador REST, sobre roles, estados de cita, estados de reprogramación, regímenes o sedes.
- [x] El dominio que representa los catálogos no depende de Spring ni de JPA, respetando la separación hexagonal.
- [x] `citas-web` obtiene los catálogos mediante la URL del backend leída de la configuración de entorno y no duplica sus valores en constantes locales.
- [x] Existen pruebas automatizadas que verifican el contenido del seed y el acceso autenticado de consulta, y pasan.
- [x] Los contratos de consulta de catálogos están reflejados en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [x] La trazabilidad de esta HU y de [[EP-003-catalogos-del-sistema]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Ejecución de referencia del **2026-09-23**: backend `docker compose run --rm citas-api-dev mvn -B test` → **242 pruebas, 0 fallos** en 22 clases; frontend `npm test` en `citas-web` → **88 pruebas, 0 fallos**, con `typecheck`, `lint` y `build` en verde. La verificación fue independiente (`backend-verifier` y `frontend-verifier`, agentes que no escribieron el código): `EVIDENCIAS_S3.md` §10. Las clases de prueba del backend cuelgan de `citas-api/src/test/java/com/fcv/citas/`.

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | `infrastructure/persistence/FlywayMigratesEmptySchemaTest#aplicaTodasLasMigracionesEnOrden`, `#dejaElModeloCompletoDe24Tablas`, `#elEsquemaMigradoValidaContraLasMigraciones`, `#siembraLosCatalogosFijos` | Esquema desechable vacío: V1–V7 aplicadas en orden y marcadas `success`, 24 tablas, `flyway.validate()` sin excepción y los cinco catálogos fijos con filas |
| CA-02 | Cumple | `FlywayMigratesEmptySchemaTest#siembraLasDosSedesConSuDireccion`; `V4__seed_fixed_catalogs.sql` líneas 51-57 y `V7__site_addresses_as_in_prd.sql`; `infrastructure/persistence/catalog/JdbcCatalogQueries#sites`; `infrastructure/rest/AuthorizationIntegrationTest#anyAuthenticatedRoleReadsCatalogs` (`$.length()` = 2) | Exactamente dos sedes, con su código y su nombre completo. Ciudad y departamento están en columnas propias (`city`, `department`), así que `address` guarda el tramo de calle y la sede completa reproduce el literal del PRD §3. V7 corrigió la raya de «Bucaramanga–Piedecuesta» tras la verificación independiente |
| CA-03 | Cumple | `V4__seed_fixed_catalogs.sql` líneas 29-35; `domain/appointment/AppointmentStatus`; `infrastructure/rest/catalog/CatalogController#appointmentStatuses` (`GET /api/catalogs/appointment-statuses`) | Los seis códigos `REQUESTED`, `APPROVED`, `REJECTED`, `CANCELLED`, `COMPLETED` y `NO_SHOW`, con `is_terminal` y `releases_slots`. El enum del dominio declara exactamente los mismos seis |
| CA-04 | Cumple | `infrastructure/rest/VerificationGapsIntegrationTest#rescheduleStatusesCatalogIncludesPending`; `V4__seed_fixed_catalogs.sql` líneas 38-42 | Devuelve `PENDING` más `APPROVED`, `REJECTED` y `CANCELLED` como estados de resolución |
| CA-05 | Cumple | `V4__seed_fixed_catalogs.sql` (roles líneas 17-20, regímenes líneas 45-48); `CatalogController#roles` y `#regimes`; `infrastructure/rest/RegistrationAffiliationIntegrationTest#insurancePlanExposesItsEpsAndRegimeDerivedFromThePlan` | Roles `USER`, `PROFESSIONAL` y `ADMIN`; tres regímenes. El formulario de afiliación de [[HU-009-registrar-afiliacion-a-eps-y-plan]] recibe el régimen derivado del plan, que sale de esta misma tabla |
| CA-06 | Cumple | `AuthorizationIntegrationTest#fixedCatalogsRejectWrites` (POST y DELETE → 405); `CatalogController` solo declara `@GetMapping`; `application/catalog/CatalogQueries` no expone ninguna escritura | `SecurityConfig` declara `/api/catalogs/**` sin método para que la petición llegue a MVC y responda 405, no 401 |
| CA-07 | Cumple | `AuthorizationIntegrationTest#catalogsRequireAuthentication` (401 sin cabecera) y `#anyAuthenticatedRoleReadsCatalogs` (USER, PROFESSIONAL y ADMIN → 200) | Excepción documentada y ajena a esta HU: `/api/catalogs/insurance-plans` es pública desde [[HU-009-registrar-afiliacion-a-eps-y-plan]] y no es un catálogo fijo |
| CA-08 | Cumple | `citas-web/src/patientBooking.test.tsx` → «filtra por estado con las opciones del catálogo de la API (HU-010 CA-08, HU-025 CA-02)»; `citas-web/src/professionalAgenda.test.tsx` → «crea un bloque: solo sedes asignadas, cuenta las franjas y muestra el 409 en el formulario»; `citas-web/src/api/catalogApi.ts` | Los selectores de estado de cita y de sede se pueblan con la respuesta de la API |
| DoD — CA-01 a CA-08 validados con evidencia concreta | Cumple | Filas CA-01 a CA-08 de esta tabla | — |
| DoD — Migración Flyway con las cinco tablas de catálogo fijo y su seed, aplicada sobre base vacía | Cumple | `V1__identity_and_fixed_catalogs.sql` (tablas) y `V4__seed_fixed_catalogs.sql` (seed); `FlywayMigratesEmptySchemaTest` (7 pruebas) | Sin pasos manuales: la prueba crea un esquema desechable y lo migra desde cero |
| DoD — Sedes con nombre, sigla y dirección literales del PRD §3 | Cumple | `FlywayMigratesEmptySchemaTest#siembraLasDosSedesConSuDireccion`; `V7__site_addresses_as_in_prd.sql` | — |
| DoD — Sin operaciones de escritura sobre roles, estados, regímenes ni sedes | Cumple | `CatalogController` (solo `@GetMapping`); `JdbcCatalogQueries` (solo `SELECT`); `AuthorizationIntegrationTest#fixedCatalogsRejectWrites` | — |
| DoD — Dominio de catálogos sin Spring ni JPA | Cumple | `HexagonalArchitectureTest` (ArchUnit sobre bytecode: `elNucleoNoDependeDeFrameworks`, `elNucleoNoDependeDeLaInfraestructura`) | `domain/catalog/SiteCatalog` y `domain/user/DocumentTypeCatalog` son puertos en Java puro |
| DoD — `citas-web` lee los catálogos por la URL de entorno y no duplica sus valores | Cumple | `citas-web/src/api/contracts.test.ts` → «toma VITE_API_URL y le quita la barra final» y «falla al cargar si VITE_API_URL está vacía, en vez de usar un valor por defecto»; `citas-web/src/api/catalogApi.ts` | Matiz registrado: `citas-web/src/lib/status.ts` mantiene `STATUS_FALLBACK_LABEL` con las seis etiquetas en español. No alimenta ningún selector —las opciones vienen de la API— y solo actúa si el backend omite `statusName` |
| DoD — Pruebas del contenido del seed y del acceso autenticado de consulta | Cumple | `FlywayMigratesEmptySchemaTest` (7); `AuthorizationIntegrationTest` (9); `VerificationGapsIntegrationTest#rescheduleStatusesCatalogIncludesPending` | — |
| DoD — Contratos de consulta reflejados en [[HU-033-publicar-contrato-rest-documentado]] | Cumple | `citas-api/docs/wiki/llm-wiki/wiki/contrato-rest-citas.md`, sección «Catálogos — cualquier rol autenticado (HU-010)» | Las ocho rutas de catálogo con su respuesta y el 405 de las escrituras |
| DoD — Trazabilidad de esta HU y de [[EP-003-catalogos-del-sistema]] actualizada | Cumple | Esta tabla y el historial de validación | — |

## Historial de validación

- 2026-09-23 — Estado `Completada` (fase F11 de `PLAN_RETOMA_S3.md`): matriz de evidencia completa, los 8 criterios y los 9 ítems de DoD en `Cumple`. Cierre dentro de la aprobación delegada de S3 (`AGENTS.md` §6); el usuario puede reabrirla.
- 2026-09-23 — Estado `En validación` (skill `scrum-spec-orchestrator`, paso 10): se recolecta del repositorio la evidencia de cada criterio y de cada ítem de DoD.
- 2026-09-18 — Estado `En desarrollo` (skill `scrum-spec-orchestrator`, paso 9): se inicia la implementación en la fase F2 de `PLAN_RETOMA_S3.md`.
- 2026-09-18 — Estado `Aprobada` por **aprobación delegada** de S3: el usuario pidió continuar S3 dejando las decisiones de diseño a criterio del agente (`AGENTS.md` §6). Alcance y decisiones D5–D13 en `PLAN_RETOMA_S3.md` y [[dec-004-decisiones-s3-reserva]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- El PRD no enumera explícitamente los valores del catálogo de estados de cita en una lista única; CA-03 los deriva de los estados que RF-11, RF-12, RF-14 y RF-17 exigen usar. Si el usuario del proyecto necesita estados adicionales, debe decidirse antes de fijar el seed.
- El PRD no enumera los valores concretos del catálogo de regímenes ni los estados de resolución de una reprogramación distintos de `PENDING`; CA-04 y CA-05 solo exigen los valores que el PRD sí obliga a usar, y el resto queda a confirmación del usuario del proyecto.
- El catálogo de roles se comparte con el esquema creado en [[HU-001-registrar-cuenta-de-usuario]]; debe evitarse duplicar la tabla o el seed entre ambas migraciones.
- El PRD no define un catálogo de tipos de documento dentro de RF-05, pese a que RF-01 exige tipo de documento; esta HU no lo incluye y la duda queda registrada en [[HU-001-registrar-cuenta-de-usuario]].
