---
id: HU-012
tipo: historia-de-usuario
titulo: "Gestionar EPS y planes"
estado: En validación
epica: "[[EP-003-catalogos-del-sistema]]"
requisitos: [RF-06]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 3"
dependencias:
  - "[[HU-010-consultar-catalogos-fijos-precargados]]"
  - "[[HU-005-autorizar-peticiones-por-rol-y-ownership]]"
relacionadas:
  - "[[HU-009-registrar-afiliacion-a-eps-y-plan]]"
---

# HU-012 — Gestionar EPS y planes

## Historia de usuario

**COMO** ADMIN
**QUIERO** crear, editar, activar y desactivar EPS y sus planes
**PARA** que los pacientes puedan declarar su afiliación sobre datos maestros vigentes.

> Como ADMIN, quiero crear, editar, activar y desactivar EPS y sus planes para que los pacientes puedan declarar su afiliación sobre datos maestros vigentes.

## Contexto y descripción

RF-06 incluye la EPS y sus planes entre los catálogos configurables que ADMIN gestiona mediante CRUD. Son los datos maestros sobre los que se apoya RF-04: un `USER` asocia su EPS, su plan y su régimen mediante una afiliación, y la aplicación debe evitar duplicar esa combinación dentro del usuario.

El plan es un catálogo dependiente: pertenece a una EPS concreta y se clasifica por un régimen tomado del catálogo fijo precargado en [[HU-010-consultar-catalogos-fijos-precargados]]. Esta jerarquía es la que permite que el formulario de afiliación ofrezca solo combinaciones coherentes.

Como el resto de catálogos configurables, una EPS o un plan ya referenciados por afiliaciones no se borran físicamente: se desactivan, para no romper la trazabilidad de los datos declarados por los pacientes. Todas las EPS y planes del laboratorio son de demostración y sintéticos, nunca entidades ni datos reales asociados a FCV (PRD §9).

## Alcance

- Migración Flyway de las tablas de EPS y de planes de EPS, con la referencia del plan a su EPS y a un régimen del catálogo fijo.
- Creación, edición, activación y desactivación de EPS por ADMIN.
- Creación, edición, activación y desactivación de planes por ADMIN, siempre dentro de una EPS.
- Listado y consulta de EPS y de sus planes, con filtro por estado de activación.
- Exclusión de EPS y planes desactivados de la oferta para declarar una afiliación nueva.
- Validación server-side de la pertenencia del plan a una EPS y a un régimen existente.
- Pantalla de CRUD de EPS y planes en `citas-web`, restringida a ADMIN.

## Fuera de alcance

- Declaración de la afiliación por parte del paciente, que se cubre en [[HU-009-registrar-afiliacion-a-eps-y-plan]].
- Gestión del catálogo de regímenes: es catálogo fijo de solo lectura (RF-05).
- Gestión de especialidades, que se cubre en [[HU-011-gestionar-especialidades-y-su-duracion]].
- Validación de la afiliación contra sistemas externos o reales: el PRD lo excluye (PRD §9).
- Coberturas, tarifas o autorizaciones por plan: no están en el PRD.

## Reglas de negocio

- Solo ADMIN gestiona los catálogos configurables de EPS y planes (RF-06, PRD §8 autorización por rol).
- Un plan pertenece a una EPS y a un régimen del catálogo fijo (RF-04, RF-05, RF-06).
- No se permite borrar físicamente una EPS o un plan referenciados por afiliaciones; se desactivan (RF-06).
- Un plan desactivado deja de ofrecerse al declarar una afiliación nueva.
- Las EPS y los planes del laboratorio son de demostración y sintéticos; nunca se usan datos reales (PRD §9).
- Toda validación se realiza también en el servidor (PRD §8).

## Dependencias y relaciones

- Épica: [[EP-003-catalogos-del-sistema]]
- Dependencias: [[HU-010-consultar-catalogos-fijos-precargados]], [[HU-005-autorizar-peticiones-por-rol-y-ownership]]
- Relacionadas: [[HU-009-registrar-afiliacion-a-eps-y-plan]], [[HU-011-gestionar-especialidades-y-su-duracion]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** Son dos CRUD encadenados con una jerarquía padre-hijo y una referencia a catálogo fijo. La lógica es simple, pero la desactivación debe propagarse de forma coherente entre EPS y planes, y la comprobación de referencias en afiliaciones obliga a coordinar con un agregado que construye otra HU. El trabajo de frontend crece por tratarse de dos entidades anidadas en una misma pantalla.

## Tareas de desarrollo

- [ ] **T-01 — Modelar EPS y plan en el dominio**
  Dificultad: Bajo
  Descripción: Entidades de dominio de EPS y de plan con nombre, estado de activación y, en el plan, la referencia obligatoria a su EPS y a un régimen, con las invariantes de obligatoriedad expresadas sin dependencias de framework.

- [ ] **T-02 — Crear la migración Flyway de EPS y planes**
  Dificultad: Medio
  Descripción: Migración versionada que crea la tabla de EPS con nombre único, la tabla de planes con clave foránea a EPS y al régimen del catálogo fijo, unicidad del nombre del plan dentro de su EPS, y columnas de activación con valor por defecto activo.

- [ ] **T-03 — Implementar los casos de uso de CRUD de EPS**
  Dificultad: Medio
  Descripción: Casos de uso de creación, edición, activación y desactivación de EPS, con comprobación de referencias en afiliaciones para impedir el borrado físico de una EPS referenciada.

- [ ] **T-04 — Implementar los casos de uso de CRUD de planes**
  Dificultad: Medio
  Descripción: Casos de uso de creación, edición, activación y desactivación de planes, validando que la EPS y el régimen indicados existen, y con la misma comprobación de referencias en afiliaciones antes de cualquier borrado.

- [ ] **T-05 — Exponer los adaptadores REST de EPS y planes**
  Dificultad: Medio
  Descripción: Controladores con DTO validados, listados con filtro por activación, consulta de los planes de una EPS, y errores diferenciados para nombre duplicado, EPS o régimen inexistente y borrado no permitido por referencias.

- [ ] **T-06 — Restringir el CRUD a ADMIN en la configuración de seguridad**
  Dificultad: Bajo
  Descripción: Declarar las rutas de escritura de EPS y planes como accesibles solo con rol `ADMIN`, dejando la consulta disponible para el formulario de afiliación del `USER`.

- [ ] **T-07 — Construir la pantalla de CRUD de EPS y planes en citas-web**
  Dificultad: Medio
  Descripción: Vista React + TypeScript visible solo para ADMIN con listado de EPS, gestión de los planes de la EPS seleccionada, selector de régimen poblado desde el catálogo fijo, conmutación de activación y presentación de los errores de la API.

- [ ] **T-08 — Pruebas de dominio, aplicación e integración de EPS y planes**
  Dificultad: Medio
  Descripción: Pruebas de la obligatoriedad de EPS y régimen en el plan, del rechazo del borrado de un catálogo referenciado por afiliaciones, de la exclusión de los desactivados en la oferta de afiliación y de la restricción de rol en el adaptador REST.

## Criterios de aceptación

### CA-01 — Alta de una EPS y de sus planes

**Dado** una sesión autenticada con rol `ADMIN`
**Cuando** crea una EPS con un nombre no existente y a continuación crea un plan dentro de esa EPS indicando un régimen del catálogo fijo
**Entonces** la API responde con creación exitosa en ambos casos, la EPS y el plan quedan persistidos en estado activo, y la consulta de los planes de esa EPS devuelve el plan creado.

### CA-02 — Un plan exige EPS y régimen existentes

**Dado** una sesión autenticada con rol `ADMIN`
**Cuando** intenta crear un plan en una EPS inexistente, sin indicar régimen, con un régimen que no pertenece al catálogo fijo, o sin código o nombre (la EPS viaja en la ruta, no en el cuerpo, así que «sin indicar EPS» no existe como variante)
**Entonces** una EPS inexistente responde `404 NOT_FOUND`; los demás casos responden `400` con `fieldErrors` que identifica el campo inválido; y en ninguno se persiste un plan.

> Reformulado el 2026-10-04 por decisión directa del usuario: antes exigía un error de validación (400 con `fieldErrors`) también para la EPS inexistente.

### CA-03 — Una EPS o un plan referenciados no se borran físicamente

**Dado** una EPS con al menos un plan, y un plan declarado en al menos una afiliación de un usuario
**Cuando** ADMIN intenta borrarlos
**Entonces** la API rechaza ambas operaciones con un error de conflicto que explica la causa real de cada una —la EPS **tiene planes registrados**, el plan **está referenciado por afiliaciones**—, los registros siguen existiendo en la base de datos, y la única vía disponible es desactivarlos.

### CA-04 — Un plan desactivado deja de ofrecerse en una afiliación nueva

**Dado** un plan en estado inactivo
**Cuando** un `USER` abre el formulario de afiliación e intenta declarar ese plan enviando la petición directamente a la API
**Entonces** el plan no aparece entre las opciones ofrecidas y la API rechaza la declaración indicando que el plan no está activo.

### CA-05 — Desactivar una EPS retira su oferta sin perder las afiliaciones existentes

**Dado** una EPS activa con planes y con afiliaciones ya declaradas
**Cuando** ADMIN desactiva la EPS
**Entonces** la EPS y sus planes dejan de ofrecerse para declarar una afiliación nueva, ningún registro se elimina, y las afiliaciones ya declaradas siguen mostrando su EPS y su plan al consultarlas.

### CA-06 — Reactivación de una EPS o de un plan

**Dado** una EPS o un plan en estado inactivo
**Cuando** ADMIN los vuelve a activar
**Entonces** la API responde con éxito y vuelven a aparecer entre las opciones ofrecidas para declarar una afiliación nueva.

### CA-07 — Solo ADMIN opera el CRUD

**Dado** sesiones autenticadas con rol `USER` y con rol `PROFESSIONAL`
**Cuando** cada una intenta crear, editar, activar o desactivar una EPS o un plan
**Entonces** la API responde con un código de prohibido en todos los casos, no se modifica ningún registro, y la misma operación con rol `ADMIN` se ejecuta correctamente.

### CA-08 — Los datos cargados son sintéticos

**Dado** el conjunto de EPS y planes presentes en el entorno de laboratorio, ya provengan del seed o de altas de ADMIN
**Cuando** se revisan sus nombres y demás datos
**Entonces** todos corresponden a entidades de demostración inventadas para el ejercicio y ninguno reproduce una EPS, un plan o un dato real vinculado a FCV (PRD §9).

### CA-09 — Una EPS o un plan no referenciados se borran físicamente

**Dado** un plan sin ninguna afiliación que lo referencie, y una EPS sin planes ni afiliaciones que la referencien
**Cuando** ADMIN borra cada uno
**Entonces** la API responde con éxito, el registro deja de existir en la base de datos y deja de aparecer en los listados; y si el mismo borrado se intenta sobre un registro referenciado, la API responde 409 y ofrece desactivarlo, como en CA-03 (D28).

## Definition of Done

- [ ] Los criterios CA-01 a CA-09 están validados con evidencia concreta.
- [ ] Existe una migración Flyway versionada que crea las tablas de EPS y planes con la clave foránea del plan a su EPS y al régimen del catálogo fijo.
- [ ] El nombre de la EPS es único y el nombre del plan es único dentro de su EPS, con restricción a nivel de base de datos.
- [ ] El dominio de EPS y plan no depende de Spring ni de JPA, respetando la separación hexagonal.
- [ ] No existe ninguna ruta ni caso de uso que elimine físicamente una EPS o un plan referenciados por afiliaciones.
- [ ] Las consultas que alimentan el formulario de afiliación devuelven únicamente EPS y planes activos.
- [ ] Las rutas de escritura de EPS y planes exigen rol `ADMIN` en la configuración de Spring Security y están cubiertas por prueba.
- [ ] La pantalla de CRUD de EPS y planes de `citas-web` solo es accesible para ADMIN y consume la API mediante la URL configurable por entorno.
- [ ] Los datos de ejemplo cargados son sintéticos y ningún nombre real de EPS ni de FCV aparece en el repositorio.
- [ ] Existen pruebas automatizadas del rechazo del borrado con referencias y de la exclusión de los desactivados en la oferta de afiliación, y pasan.
- [ ] El contrato del CRUD de EPS y planes está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [ ] La trazabilidad de esta HU y de [[EP-003-catalogos-del-sistema]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Ejecución de referencia del **2026-09-30**: el `backend-verifier`, agente independiente que no escribió el código, reejecutó la suite completa de `citas-api` → **484 pruebas, 0 fallos, 0 errores, `BUILD SUCCESS`**. El `frontend-verifier` reejecutó la de `citas-web` → **218 pruebas**, con typecheck, `oxlint` y build limpios, y dejó **14 hallazgos abiertos** (3 en reparación y 4 pruebas que faltan).

Rutas abreviadas: **EAIT** = `src/test/java/com/fcv/citas/infrastructure/rest/EpsAdminIntegrationTest.java`; **AIT** = `src/test/java/com/fcv/citas/infrastructure/rest/AffiliationIntegrationTest.java`; **FMES** = `src/test/java/com/fcv/citas/infrastructure/persistence/FlywayMigratesEmptySchemaTest.java`.

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | EAIT:128 `createsAnEpsAndAPlanAndListsThem`: ADMIN crea una EPS (201, activa, `planCount = 0`) y dentro de ella un plan con `regimeCode = CONTRIBUTIVO` del catálogo fijo (201, activo, con la EPS y el régimen resueltos en la respuesta); `GET /api/admin/eps/{id}/plans` devuelve el plan, `GET /api/admin/eps/{id}` sube el `planCount` a 1, el listado lo refleja, y en la base ambos quedan con `active = 1`; el plan aparece además en el catálogo público | Se comprueba la persistencia real, no solo la respuesta |
| CA-02 | Cumple (PASS, 2026-10-04) | EAIT:251 `aPlanNeedsAKnownRegimeAndItsFields` (sin régimen, régimen fuera del catálogo fijo, sin código y sin nombre → 400 con el campo identificado, nada persiste); EAIT:273 `aPlanOfAMissingEpsIsNotFound` (la EPS del plan es la de la ruta; si no existe → 404 y no se crea nada); EAIT:283 `anEpsNeedsCodeAndName` | «Sin indicar EPS» se resuelve por diseño: la EPS viaja en la ruta, no en el cuerpo, así que la variante es «EPS inexistente» → 404 `NOT_FOUND` (EAIT:273). **Criterio reformulado el 2026-10-04 por decisión directa del usuario** para pedir 404 en lugar de 400 con `fieldErrors`; el código ya lo cumplía |
| CA-03 | Cumple | EAIT:296 `referencedEpsAndPlansAreNotDeleted`: el plan con afiliación **vigente** y el plan con afiliación **cerrada** → 409 `PLAN_REFERENCED` («El plan está referenciado por afiliaciones»), y la EPS con planes → 409 `EPS_REFERENCED` («La EPS tiene planes registrados»); los dos planes y la EPS siguen en la base, y la prueba termina desactivándolos, que es la vía que queda; `ManageEpsUseCase:87-97` (EPS) y `:142-152` (plan) | **Criterio ajustado el 2026-09-30:** exigía que el error «explique que están referenciados por afiliaciones» también para la EPS, y el mensaje real es «tiene planes registrados». Es la causa correcta: a una EPS nunca la referencia una afiliación, la referencia su plan. Ver historial |
| CA-04 | No verificable (2026-10-04) | **Servidor, PASS:** EAIT:343 `aDeactivatedPlanIsNoLongerOfferedUntilReactivated` (el plan inactivo sale del catálogo y la API rechaza afiliarse a él); AIT:200 `unavailablePlansAreRejected` (422 `INSURANCE_PLAN_UNAVAILABLE`) y AIT:212 `theCatalogOffersOnlyActivePlans`. **Frontend:** `citas-web/src/profileAndPassword.test.tsx:149`, `:166` y `:183` (422 `INSURANCE_PLAN_UNAVAILABLE`) | Oferta y aceptación comparten el predicado `InsurancePlanCatalog#isSelectable`, así que no pueden divergir. **Único ítem abierto:** que un plan inactivo no aparezca en la lista del perfil y del registro no se puede confirmar sin navegador. **Paso manual pendiente:** desactivar un plan y abrir `/paciente/perfil` y `/registro` |
| CA-05 | Cumple | EAIT:366 `deactivatingAnEpsWithdrawsItsPlansButKeepsAffiliations`: al desactivar la EPS sus planes dejan de ofrecerse, **ningún registro se elimina** y la afiliación ya declarada sigue devolviendo su EPS y su plan al consultarla; `JdbcAffiliationQueries:18-31` lee la afiliación **sin filtrar por activo**, precisamente para eso | El mecanismo elegido es **sin cascada** (ver notas): el plan conserva su estado y queda inaccesible por su EPS |
| CA-06 | Cumple | EAIT:343 (reactivar el plan lo devuelve a la oferta) y EAIT:366 (reactivar la EPS devuelve sus planes a la oferta) | Sin cascada, reactivar restaura la oferta con el estado propio de cada plan |
| CA-07 | Cumple | EAIT:407 `onlyAdminsManageEpsAndPlans`: `USER` y `PROFESSIONAL` reciben 403 en todo el CRUD y nada cambia; la misma operación con `ADMIN` se ejecuta; `SecurityConfig:83` (`/api/admin/**` exige `ROLE_ADMIN`) | — |
| CA-08 | Cumple | `scripts/seed-eps-plans.ps1` (4 EPS y 9 planes inventados: «Bienestar Andino EPS», «Salud Integral del Oriente», «Meridiano Salud», «Previsión Antigua EPS», con la advertencia explícita de que **todos son ficticios** y que el PRD solo admite como real las sedes); los nombres usados en las pruebas se generan con `EpsTestData` y tampoco reproducen entidades reales | Verificado por lectura del seed y de los datos de prueba; no hay ningún nombre real de EPS ni de FCV en el repositorio dentro del alcance de esta HU |
| CA-09 | Cumple | EAIT:320 `unreferencedPlansAndEpsAreDeletedPhysically`: un plan sin afiliaciones y una EPS sin planes se borran (204), dejan de existir en la base y desaparecen de los listados; el contraste con el borrado referenciado está en EAIT:296 (409 y oferta de desactivar), tal como exige la segunda mitad del criterio (D28) | — |
| DoD — CA-01 a CA-09 validados con evidencia concreta | No cumple (2026-10-04) | CA-01 a CA-03 y CA-05 a CA-09 en `Cumple`; **CA-04 `No verificable`** (falta el paso manual en navegador) | El ítem depende de los nueve; queda abierto por CA-04 |
| DoD — Migración Flyway versionada de EPS y planes, con la FK del plan a su EPS y a un régimen del catálogo fijo | Cumple | `V2__configurable_catalogs_and_professionals.sql` crea `eps` y `eps_plans` con `fk_eps_plans_eps` y `fk_eps_plans_regime`; FMES:98 `aplicaTodasLasMigracionesEnOrden` y FMES:125 | T-02 no crea tablas: existían desde V2, que es incremental sobre V1 |
| DoD — Nombre de la EPS único y nombre del plan único dentro de su EPS, con restricción de base de datos | Cumple | `V9__eps_names_and_affiliation_history.sql` (`uq_eps_name UNIQUE (name)` y `uq_eps_plans_eps_name UNIQUE (eps_id, name)`, con collation `utf8mb4_0900_ai_ci`, ejecutando **D33**); EAIT:205 `epsCodeAndNameAreUnique` y EAIT:225 `planCodeAndNameAreUniqueWithinTheirEps`; FMES:222 `losNombresDeEpsYDePlanSonUnicos` | La divergencia anotada en S4 —unicidad solo por código— quedó resuelta por D33, no por reescritura de la DoD |
| DoD — Dominio de EPS y plan sin Spring ni JPA | Cumple | `domain/eps/Eps` y `domain/eps/EpsPlan` con sus puertos de repositorio; `HexagonalArchitectureTest` (cero dependencias de Spring, JPA, Jakarta o HTTP en `domain/` y `application/`) | — |
| DoD — Ninguna ruta ni caso de uso elimina físicamente una EPS o un plan referenciados por afiliaciones | Cumple | `ManageEpsUseCase:87-97` (`hasPlans` → 409 `EPS_REFERENCED`) y `:142-152` (`isReferenced` → 409 `PLAN_REFERENCED`), las dos únicas vías de borrado; EAIT:296 cubre también la afiliación **cerrada**, no solo la vigente | Un plan con solo historial cerrado también está protegido: el conflicto no distingue vigencia |
| DoD — Las consultas que alimentan el formulario de afiliación devuelven solo EPS y planes activos | Cumple | `JdbcCatalogQueries:93` implementa `isSelectable` exigiendo plan activo **y** EPS activa; el mismo adaptador alimenta el listado público; AIT:212, AIT:200, EAIT:343 y EAIT:366 | El puerto documenta que lo implementa el mismo adaptador que lista, para que oferta y aceptación no divergan |
| DoD — Las rutas de escritura exigen rol `ADMIN` en Spring Security y están cubiertas por prueba | Cumple | `SecurityConfig:83`; EAIT:407 | — |
| DoD — La pantalla de CRUD de `citas-web` solo es accesible para ADMIN y consume la API por la URL de entorno | Cumple (2026-10-04) | `citas-web/src/pages/admin/EpsPage.tsx`, `EpsDetailPage.tsx`, `EpsFormModals.tsx` y `DeleteReferencedDialog.tsx`, con `RequireRole` en el enrutado y las pruebas de `src/adminS4.test.tsx:219-521` (alta, 409 `DUPLICATE`, 409 `EPS_REFERENCED` y `PLAN_REFERENCED` con oferta de desactivar, edición, reactivar, eliminar); `src/api/httpClient.ts` toma la base de `VITE_API_URL`; vitest 248/248, typecheck, lint y build limpios | PASS del `frontend-verifier` en F10. Las líneas se actualizaron al árbol del 2026-10-04 |
| DoD — Los datos de ejemplo son sintéticos y ningún nombre real de EPS ni de FCV aparece en el repositorio | Cumple | `scripts/seed-eps-plans.ps1` (ver CA-08); los datos de prueba se generan con `EpsTestData` | Revisado solo el alcance de esta HU (EPS y planes); no es una auditoría de todo el repositorio |
| DoD — Pruebas del rechazo del borrado con referencias y de la exclusión de los desactivados, y pasan | Cumple | EAIT:296 y EAIT:320 (borrado); EAIT:343, EAIT:366, AIT:200 y AIT:212 (exclusión); suite completa 484/484 `BUILD SUCCESS` reejecutada por el `backend-verifier` | — |
| DoD — Contrato del CRUD reflejado en [[HU-033-publicar-contrato-rest-documentado]] | Cumple | `citas-api/docs/wiki/llm-wiki/wiki/contrato-rest-citas.md:282-291` (los diez endpoints de EPS y planes con sus códigos: `DUPLICATE` con `field`, `EPS_REFERENCED`, `PLAN_REFERENCED`), `:329` (`GET /api/admin/eps/{id}`), `:359` (la unicidad por nombre de V9) y `:367` (orden de los listados) | `llm-wiki/` queda fuera del límite de escritura de esta skill: la evidencia se leyó, no se produjo aquí |
| DoD — Trazabilidad de esta HU y de [[EP-003-catalogos-del-sistema]] actualizada | Cumple | Esta matriz, el historial de validación y las notas (D28 y D33 registradas, la cascada resuelta); en [[EP-003-catalogos-del-sistema]] la anotación de INC-011; `docs/wiki/scrum/README.md` | — |

## Historial de validación

- 2026-10-04 — **Verificación independiente de F10.** Backend verificado leyendo código y pruebas (suite 513/513) y frontend con vitest 248/248, typecheck, lint y build limpios: todo en PASS salvo CA-04 en su mitad de interfaz (que un plan inactivo no aparezca en perfil y registro), que no se puede confirmar sin navegador (paso manual: desactivar un plan y abrir `/paciente/perfil` y `/registro`). **Se queda `En validación` por ese único ítem.** **Decisiones directas del usuario (2026-10-04):** (1) **CA-02 reformulado**: una EPS inexistente responde `404 NOT_FOUND` porque la EPS viaja en la ruta, y no se exige 400 con `fieldErrors`; el criterio pasa a PASS. (2) Confirma la reescritura de **CA-03** del 2026-09-30 (la causa real de cada conflicto).
- 2026-09-30 — **Matriz de evidencia recolectada del repositorio.** CA-01 a CA-09 y toda la DoD de backend, de esquema, de contrato y de trazabilidad en `Cumple`. Estado: `Aprobada` → `En validación`. **No pasa a `Completada`**: el ítem de DoD de la pantalla de CRUD de `citas-web` queda en `Pendiente` porque la verificación de frontend criterio a criterio y la prueba manual en navegador de F10 no se han hecho.
- 2026-09-30 — **CA-03 ajustado a la causa real del conflicto.** El criterio exigía un error que «explica que están referenciados por afiliaciones» para las dos operaciones. Para el plan eso es exacto (`PLAN_REFERENCED`: «El plan está referenciado por afiliaciones»), pero para la EPS el mensaje real es «La EPS tiene planes registrados» (`EPS_REFERENCED`), que es la causa correcta: **a una EPS nunca la referencia una afiliación, la referencia su plan**. El criterio pedía al código explicar una causa que no es la suya. Se reescribe nombrando la causa propia de cada operación. No se relaja nada: siguen exigidos el conflicto, la conservación de los registros y la desactivación como única vía. Aprobación **directa del usuario**.
- 2026-09-30 — Las dos notas que afirmaban decisiones pendientes se sustituyen por las decisiones que las resolvieron: la unicidad por nombre («al implementar F6 hay que decidir si la unicidad por código satisface la intención») la cerró **D33**, ejecutada por `V9`; y la cascada al desactivar una EPS («decisión pendiente del usuario») la resolvió el código **sin cascada**, con el contrato REST documentándolo. Ambas decían que había que decidir antes de F6, y F6 ya está implementada y probada.
- 2026-09-25 — Se añade CA-09 por D28 (borrado físico de lo no referenciado), que ningún criterio cubría; la DoD pasa a CA-01 a CA-09. CA-03 no cambia: ya exigía el rechazo del borrado referenciado.
- 2026-09-25 — Aprobada por **aprobación delegada** del usuario para S4 (D15, PLAN_RETOMA_S4.md). Alcance en `PLAN_RETOMA_S4.md` §3 (bloque «Catálogos», fase F6) y decisiones D15–D30 en [[dec-006-decisiones-s4-ciclo-de-vida]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- **Resuelta (D28, provisional bajo delegación):** INC-011 (ver [[EP-003-catalogos-del-sistema]]): una EPS o un plan que nada referencia se **borran físicamente**; si algo los referencia, 409 y se ofrece desactivar, igual que las especialidades de [[HU-011-gestionar-especialidades-y-su-duracion]] ([[dec-006-decisiones-s4-ciclo-de-vida]]). CA-03 y CA-09 lo cubren.
- **Resuelta (D33, provisional bajo delegación):** `eps` y `eps_plans` existen desde `V2__configurable_catalogs_and_professionals.sql`, así que T-02 no crea tablas; pero la unicidad del esquema era solo sobre el **código** (`uq_eps_code`, `uq_eps_plans_eps_code (eps_id, code)`), no sobre el **nombre** que pide el ítem de DoD. **D33** decidió que la unicidad por código **no** satisface la intención y la **ejecutó `V9__eps_names_and_affiliation_history.sql`**, que añade `uq_eps_name UNIQUE (name)` y `uq_eps_plans_eps_name UNIQUE (eps_id, name)`, con la collation `utf8mb4_0900_ai_ci` —la misma que usa la comprobación del caso de uso, así que «Salud Demo» y «SALUD DEMÓ» chocan igual en los dos sitios—. Misma solución que `V8` para especialidades: la garantía la da el motor, no solo la aplicación. La DoD no se reescribió: se cumplió. Probado en `EpsAdminIntegrationTest:205` y `:225`, y en `FlywayMigratesEmptySchemaTest:222` `losNombresDeEpsYDePlanSonUnicos`.
- **Resuelta en la implementación de F6 (2026-09-30): sin cascada.** El PRD no definía si desactivar una EPS debe desactivar sus planes o si los planes conservan su estado propio quedando inaccesibles por su EPS. El código eligió lo segundo: `ManageEpsUseCase#changeStatus` solo cambia el estado de la EPS, y la exclusión de la oferta se resuelve en la lectura, porque el predicado del catálogo exige que el plan esté activo **y** su EPS también (`InsurancePlanCatalog#isSelectable`). Consecuencia observable: reactivar la EPS devuelve sus planes a la oferta con el estado que cada uno tenía, sin haber perdido información. Documentado en `citas-api/docs/wiki/llm-wiki/wiki/contrato-rest-citas.md` (bloque de EPS y planes) y probado en `EpsAdminIntegrationTest:366` `deactivatingAnEpsWithdrawsItsPlansButKeepsAffiliations`. CA-05 y CA-06 solo exigen el resultado y son compatibles con este mecanismo; **ninguna decisión D15–D39 lo registró de forma explícita**, así que el usuario puede pedir la cascada, y hacerlo exigiría decidir además cómo se restauraría el estado previo al reactivar.
- El PRD no define qué ocurre con una afiliación ya declarada cuando su plan se desactiva: si sigue vigente o debe marcarse para actualización. CA-05 solo exige preservar el registro. Esta duda debe resolverse junto con [[HU-009-registrar-afiliacion-a-eps-y-plan]].
- RF-04 exige evitar duplicar EPS, régimen y plan dentro del usuario; esa restricción pertenece a la afiliación y no a este catálogo, por lo que no se cubre aquí.
