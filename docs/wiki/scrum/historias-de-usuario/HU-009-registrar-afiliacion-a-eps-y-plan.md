---
id: HU-009
tipo: historia-de-usuario
titulo: "Registrar la afiliación a EPS y plan"
estado: En validación
epica: "[[EP-002-perfil-y-afiliacion-del-paciente]]"
requisitos: [RF-04]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 2"
dependencias:
  - "[[HU-008-consultar-y-actualizar-perfil]]"
  - "[[HU-012-gestionar-eps-y-planes]]"
relacionadas:
  - "[[HU-024-solicitar-cita-especializada]]"
---

# HU-009 — Registrar la afiliación a EPS y plan

## Historia de usuario

**COMO** USER autenticado
**QUIERO** asociar mi afiliación seleccionando un plan de EPS
**PARA** que mi cobertura quede registrada y pueda asociarse a mis citas

> Como USER autenticado, quiero asociar mi afiliación seleccionando un plan de EPS para que mi cobertura quede registrada y pueda asociarse a mis citas.

## Contexto y descripción

RF-04 permite al USER asociar su EPS, plan y régimen mediante una afiliación, y exige que la aplicación evite duplicar esa combinación dentro del usuario. Esta HU completa [[EP-002-perfil-y-afiliacion-del-paciente]] y depende del catálogo de EPS y planes que administra el ADMIN en [[HU-012-gestionar-eps-y-planes]].

La decisión de diseño que gobierna toda la historia es de normalización. La afiliación referencia un plan y nada más: la EPS a la que pertenece ese plan y el régimen asociado se conocen navegando desde el plan, y no se copian dentro del registro del usuario. Duplicarlos rompería la tercera forma normal exigida por las restricciones técnicas y abriría la puerta a que los datos del usuario contradijeran al catálogo.

De esa decisión se derivan dos consecuencias visibles. La primera es que la prevención de duplicados de RF-04 se resuelve impidiendo que un mismo usuario registre dos veces el mismo plan, porque el plan ya determina la EPS y el régimen. La segunda es que la selección disponible al usuario se limita a los planes activos del catálogo, en coherencia con la desactivación en lugar de borrado que exige RF-06.

~~Esta HU introduce la tabla de afiliaciones, por lo que requiere una migración Flyway propia.~~
**Corregido el 2026-09-23:** la tabla `affiliations` ya existe desde `V2__configurable_catalogs_and_professionals.sql:145-168`, con `is_current`, `started_on`, `ended_on`, la columna generada `current_marker` y las restricciones `uq_affiliations_user_plan` y `uq_affiliations_user_current`, igual que `eps` y `eps_plans`. El primer corte **no** llevó migración: solo faltaba el código (entidad, repositorio, caso de uso y endpoints).

**Ampliado el 2026-09-30:** el segundo corte sí llevó una migración, `V9__eps_names_and_affiliation_history.sql`, que por **D32** sustituye `uq_affiliations_user_plan` por `uq_affiliations_user_plan_current (user_id, eps_plan_id, current_marker)` para que el historial de D26 permita volver a un plan ya usado. Ver notas.

## Alcance

- Selección de un plan de EPS desde el catálogo para registrar la afiliación del usuario autenticado.
- Afiliación que referencia únicamente al plan, del que se derivan la EPS y el régimen.
- Consulta de la afiliación registrada por el propio usuario.
- Prevención de que un mismo usuario duplique la combinación de EPS, régimen y plan.
- Restricción de la oferta de selección a los planes activos del catálogo.
- Ownership estricto sobre la afiliación, apoyado en [[HU-005-autorizar-peticiones-por-rol-y-ownership]].
- Migración Flyway que crea la tabla de afiliaciones con la restricción que impide la duplicidad.
- Pantalla de afiliación en `citas-web` integrada con la de perfil.

### Recorte acordado el 2026-09-23 — primer corte: solo el registro

El usuario aprobó esta HU para desbloquear la afiliación **durante el registro público**, que es
un momento distinto al que describe la historia (allí el usuario ya está autenticado). Este primer
corte implementa únicamente:

- `GET /api/catalogs/insurance-plans`, **público**, con los planes activos de EPS activas. Es la
  única lectura de catálogo sin token, porque el formulario de registro no tiene sesión.
- `POST /api/auth/register` con `insurancePlanId` **opcional**: si viene, crea la afiliación en la
  misma transacción que el usuario; si no viene, el registro queda exactamente como estaba.
- Plan inexistente, inactivo o de EPS inactiva → `422` con `INSURANCE_PLAN_UNAVAILABLE`. Los tres
  casos comparten código para no revelar si el plan existe.

Queda para un corte posterior de esta misma HU: consultar y cambiar la afiliación desde el perfil
del usuario autenticado, que es lo que dependía de [[HU-008-consultar-y-actualizar-perfil]].

**El selector de plan vive solo en el registro público de pacientes.** El alta de profesionales que
hace el ADMIN no lo lleva: un profesional se da de alta por su rol, no por su cobertura.

### Dependencia de HU-012 sustituida por una semilla

La historia dependía de [[HU-012-gestionar-eps-y-planes]] para tener catálogo. El usuario decidió el
2026-09-23 que los datos se crean **por script**, no por CRUD: `scripts/seed-eps-plans.ps1` en la
raíz del workspace, con EPS y planes ficticios e idempotente. HU-012 sigue fuera de alcance.

### Segundo corte (S4, aprobación delegada del 2026-09-25) — afiliación desde el perfil

Amplía el alcance aprobado al resto de la historia: el USER autenticado **consulta, cambia y
quita** su afiliación desde el perfil ([[HU-008-consultar-y-actualizar-perfil]]), con la regla de
una sola afiliación vigente de D26 (CA-09 y CA-10). Fase F6 de `PLAN_RETOMA_S4.md`. En S4
[[HU-012-gestionar-eps-y-planes]] también entra en alcance, así que la semilla por script pasa a
ser un apoyo de laboratorio y no la única fuente del catálogo.

## Fuera de alcance

- Creación, edición, activación y desactivación de EPS y planes, que corresponde a ADMIN en [[HU-012-gestionar-eps-y-planes]].
- Uso de la afiliación como condición para solicitar una cita: depende de una decisión pendiente (INC-008) y se resolvería en la épica de reserva.
- Validación de la vigencia real de la afiliación contra sistemas externos: el PRD excluye la integración con sistemas clínicos (PRD §9).
- Gestión del catálogo de regímenes, que es catálogo fijo de solo lectura (RF-05).
- Datos personales del perfil, que se cubren en [[HU-008-consultar-y-actualizar-perfil]].

## Reglas de negocio

- El usuario puede asociar su EPS, plan y régimen mediante una afiliación (RF-04).
- La afiliación referencia un plan; desde el plan se conocen la EPS y el régimen, que no se almacenan repetidos dentro del usuario (RF-04, normalización 3FN de las restricciones técnicas).
- La aplicación impide duplicar EPS, régimen y plan dentro del mismo usuario (RF-04).
- Solo se ofrecen y se aceptan planes activos del catálogo (RF-06).
- El régimen procede del catálogo fijo de solo lectura (RF-05).
- El usuario solo consulta y modifica su propia afiliación (PRD §8).
- Toda validación de los datos enviados se ejecuta también en el servidor (PRD §8).

## Dependencias y relaciones

- Épica: [[EP-002-perfil-y-afiliacion-del-paciente]]
- Dependencias: [[HU-008-consultar-y-actualizar-perfil]], [[HU-012-gestionar-eps-y-planes]]
- Relacionadas: [[HU-024-solicitar-cita-especializada]], [[HU-005-autorizar-peticiones-por-rol-y-ownership]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** Añade una tabla nueva con su migración y una restricción de unicidad que debe sostenerse tanto en base de datos como en la validación de la aplicación. Obliga a modelar correctamente la derivación de EPS y régimen desde el plan sin desnormalizar, y a coordinar el frontend con el catálogo para ofrecer solo planes activos. No hay lógica algorítmica compleja, pero sí varias piezas que deben encajar entre catálogo, perfil y persistencia.

## Tareas de desarrollo

- [ ] **T-01 — Modelar la afiliación en el dominio**
  Dificultad: Medio
  Descripción: Definir la afiliación como referencia del usuario a un plan, con la invariante de no duplicidad dentro del usuario y la derivación de EPS y régimen a partir del plan, sin replicar esos datos ni depender de framework.

- [ ] **T-02 — Crear la migración Flyway de afiliaciones**
  Dificultad: Medio
  Descripción: Migración versionada que crea la tabla de afiliaciones con referencia al usuario y al plan, claves foráneas hacia ambos y una restricción de unicidad que impide repetir el mismo plan para un mismo usuario.

- [ ] **T-03 — Implementar el caso de uso de registro de afiliación**
  Dificultad: Medio
  Descripción: Caso de uso que valida que el plan exista y esté activo, comprueba que el usuario no tenga ya una afiliación a ese plan y persiste la afiliación asociada al usuario autenticado.

- [ ] **T-04 — Implementar el caso de uso de consulta de la afiliación propia**
  Dificultad: Bajo
  Descripción: Caso de uso que devuelve la afiliación del usuario autenticado resolviendo, a partir del plan referenciado, los datos de EPS y régimen para presentarlos sin haberlos duplicado en almacenamiento.

- [ ] **T-05 — Exponer los adaptadores REST de afiliación**
  Dificultad: Bajo
  Descripción: Controladores de registro y consulta que resuelven el usuario desde el contexto de seguridad, con DTO validado, respuesta que incluye EPS y régimen derivados, y error de conflicto ante la duplicidad.

- [ ] **T-06 — Exponer la consulta de planes activos para la selección**
  Dificultad: Bajo
  Descripción: Operación de lectura que devuelve los planes activos del catálogo con su EPS y su régimen, para alimentar la selección del usuario sin exponer los planes desactivados.

- [ ] **T-07 — Construir la pantalla de afiliación en citas-web**
  Dificultad: Medio
  Descripción: Vista integrada con el perfil que permite seleccionar un plan entre los activos, muestra la EPS y el régimen derivados al elegirlo, presenta la afiliación registrada y comunica el error de duplicidad.

- [ ] **T-08 — Pruebas de afiliación**
  Dificultad: Medio
  Descripción: Pruebas del caso de uso y de integración REST y de persistencia para alta correcta, intento de duplicar el mismo plan, intento con un plan desactivado, intento con un plan inexistente y consulta de la afiliación de otro usuario.

## Criterios de aceptación

### CA-01 — Registro correcto de la afiliación

**Dado** un usuario `USER` autenticado sin afiliación registrada y un plan activo del catálogo
**Cuando** registra su afiliación seleccionando ese plan
**Entonces** la API responde con éxito y la afiliación queda persistida asociada al usuario y al plan elegido.

### CA-02 — EPS y régimen derivados del plan, no duplicados

**Dado** una afiliación registrada
**Cuando** se consulta la afiliación del usuario y se inspecciona su registro en la base de datos
**Entonces** la respuesta incluye la EPS y el régimen correspondientes al plan, y el registro almacenado referencia únicamente al plan sin columnas propias que repitan la EPS ni el régimen.

### CA-03 — Reenviar el plan vigente es idempotente y no duplica la afiliación

**Dado** un usuario `USER` autenticado con una afiliación vigente a un plan concreto
**Cuando** vuelve a enviar ese mismo plan
**Entonces** la API responde con éxito devolviendo la afiliación vigente —el mismo identificador y el mismo plan—, no se crea un segundo registro y el usuario sigue teniendo exactamente una afiliación vigente.

> Reescrito el 2026-09-30 por **decisión directa del usuario**. Ver el historial de validación para el motivo; la prohibición de dos afiliaciones vigentes al mismo plan vive en CA-04, a nivel de esquema.

### CA-04 — Duplicidad impedida a nivel de esquema

**Dado** la tabla de afiliaciones con las restricciones de unicidad vigentes
**Cuando** se intenta insertar directamente una segunda fila vigente del mismo usuario, al mismo plan o a un plan distinto
**Entonces** la base de datos rechaza la inserción, y una fila **cerrada** del mismo plan sí puede convivir con una vigente, para que el historial de afiliaciones se conserve (D26, D32).

### CA-05 — Solo se ofrecen planes activos

**Dado** un catálogo con al menos un plan activo y un plan desactivado
**Cuando** el usuario abre la selección de planes en `citas-web`
**Entonces** la lista presentada contiene el plan activo y no contiene el desactivado.

### CA-06 — Plan desactivado o inexistente rechazado en el servidor

**Dado** una petición de registro de afiliación enviada directamente a la API referenciando un plan desactivado en un caso y un plan inexistente en otro
**Cuando** la API procesa cada petición
**Entonces** responde con un error de validación en ambos casos y no crea ninguna afiliación.

### CA-07 — Ownership sobre la afiliación

**Dado** dos usuarios `USER` distintos, cada uno con su afiliación registrada
**Cuando** el primero, con su access token válido, intenta consultar o modificar la afiliación del segundo
**Entonces** la API rechaza la petición y no devuelve ni altera ningún dato del segundo usuario.

### CA-08 — Esquema creado por migración versionada

**Dado** una base de datos MySQL 8.4 con las migraciones anteriores aplicadas
**Cuando** se arranca `citas-api`
**Entonces** Flyway aplica la migración que crea la tabla de afiliaciones con sus claves foráneas hacia usuario y plan y la restricción de unicidad, y el arranque finaliza sin error.

### CA-09 — Cambiar de plan cierra la afiliación vigente

**Dado** un usuario `USER` autenticado con una afiliación vigente al plan A y un plan activo B distinto de A
**Cuando** cambia su afiliación al plan B desde el perfil
**Entonces** la afiliación al plan A deja de estar vigente y tiene `ended_on` con la fecha del cambio, el usuario tiene exactamente una afiliación vigente, la del plan B, y la consulta de su afiliación devuelve el plan B con su EPS y su régimen (D26).

### CA-10 — Quitar la afiliación la cierra sin reemplazo

**Dado** un usuario `USER` autenticado con una afiliación vigente
**Cuando** la quita desde el perfil
**Entonces** el registro de la afiliación sigue existiendo, deja de estar vigente y tiene `ended_on` con la fecha de la baja, el usuario no tiene ninguna afiliación vigente y la consulta de su afiliación indica que no hay ninguna (D26).

## Definition of Done

- [ ] Los criterios CA-01 a CA-10 están validados con evidencia concreta.
- [ ] Existe una migración Flyway versionada que crea la tabla de afiliaciones y se aplica de forma incremental sobre el esquema existente.
- [ ] La tabla de afiliaciones no contiene columnas que repliquen la EPS ni el régimen: ambos se obtienen navegando desde el plan, y esta decisión queda justificada en el análisis de normalización del proyecto.
- [ ] La prevención de duplicados está garantizada en dos niveles: la validación del caso de uso y la restricción de unicidad del esquema.
- [ ] El dominio de la afiliación no depende de Spring ni de JPA, respetando la separación hexagonal.
- [ ] Las operaciones de afiliación resuelven el usuario desde el contexto de seguridad y aplican el ownership de [[HU-005-autorizar-peticiones-por-rol-y-ownership]].
- [ ] La lista de planes que `citas-web` presenta contiene exclusivamente planes activos y coincide con lo que `citas-api` acepta, sin divergencia entre cliente y servidor.
- [ ] Existen pruebas automatizadas de alta correcta, duplicidad rechazada, plan desactivado rechazado y acceso a la afiliación de otro usuario, y pasan.
- [ ] El contrato de los endpoints de afiliación y de consulta de planes activos está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [ ] La trazabilidad de esta HU y de [[EP-002-perfil-y-afiliacion-del-paciente]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Ejecución de referencia del **2026-09-30**: el `backend-verifier`, agente independiente que no escribió el código, reejecutó la suite completa de `citas-api` → **484 pruebas, 0 fallos, 0 errores, `BUILD SUCCESS`**. El `frontend-verifier` reejecutó la de `citas-web` → **218 pruebas**, con typecheck, `oxlint` y build limpios, y dejó **14 hallazgos abiertos** (3 en reparación y 4 pruebas que faltan).

Rutas abreviadas: **AIT** = `src/test/java/com/fcv/citas/infrastructure/rest/AffiliationIntegrationTest.java`; **RAIT** = `src/test/java/com/fcv/citas/infrastructure/rest/RegistrationAffiliationIntegrationTest.java` (primer corte); **AT** = `src/test/java/com/fcv/citas/domain/affiliation/AffiliationTest.java`; **FMES** = `src/test/java/com/fcv/citas/infrastructure/persistence/FlywayMigratesEmptySchemaTest.java`. Los números de línea son los del árbol de trabajo del 2026-09-30.

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | AIT:134 `registersTheFirstAffiliation`: `PUT /api/me/affiliation` con un plan activo → 200 y la fila persistida con el `user_id` del token y el `eps_plan_id` elegido; AT:16 `aNewAffiliationIsCurrentAndOpen`; el primer corte (afiliación durante el registro) en RAIT | Cubre los dos momentos: registro público y perfil autenticado |
| CA-02 | Cumple | AIT:134 (la respuesta trae `plan`, `plan.eps` y `plan.regime` derivados) y AIT:157 `theAffiliationStoresOnlyThePlan`, que enumera las columnas de `affiliations` contra `information_schema`: contiene `eps_plan_id` y **no** contiene `eps_id` ni `regime_id`; `JdbcAffiliationQueries:18-31` resuelve EPS y régimen con `JOIN` desde el plan | La 3FN se asevera contra el catálogo del motor, no leyendo el DDL |
| CA-03 | Cumple | AIT:170 `theSamePlanAgainChangesNothing`: el segundo `PUT` con el plan vigente responde **200 con el mismo `id` y el mismo `plan.id`**, `affiliations` sigue con una sola fila y el usuario con exactamente una vigente; `ManageAffiliationUseCase#change` sale con el id actual sin crear ni cerrar nada cuando `current.isFor(insurancePlanId)`; AIT:242 documenta la variante deliberada (repetir el plan vigente se acepta incluso si el ADMIN lo desactivó después, mientras cambiar a **otro** plan no ofrecible sigue siendo 422) | **Criterio reescrito el 2026-09-30 por decisión directa del usuario**: antes exigía 409 y el código responde 200. Ver el historial para el motivo. La prohibición de duplicidad quedó en CA-04 |
| CA-04 | Cumple | AIT:182 `theDatabaseRejectsTwoCurrentRowsForTheSamePlan`: un `INSERT` directo de una segunda fila vigente del mismo plan lanza `DataIntegrityViolationException`; también la de un plan **distinto** (`uq_affiliations_user_current`); y una cerrada más una vigente del mismo plan **sí** conviven (D32); `V2:157-158` y `V9` (`uq_affiliations_user_plan_current`, `uq_affiliations_user_current`); FMES:234 `laUnicaDeAfiliacionPermiteVolverAUnPlanYaUsado` | La restricción se prueba por inserción directa, sin pasar por la aplicación |
| CA-05 | Pendiente | La parte de servidor **cumple**: AIT:212 `theCatalogOffersOnlyActivePlans` comprueba que `GET /api/catalogs/insurance-plans` contiene los dos planes activos y **no** contiene el desactivado ni el de una EPS inactiva; en frontend existen `citas-web/src/lib/insurancePlans.ts` y las pruebas `src/registroAfiliacion.test.tsx` y `src/profileAndPassword.test.tsx:138` | No se marca `Cumple` porque el criterio está redactado sobre lo que el usuario ve en `citas-web` («la lista presentada»), y la verificación de frontend criterio a criterio la hace el `frontend-verifier`, que dejó 14 hallazgos abiertos (3 en reparación, 4 pruebas que faltan); falta la prueba manual en navegador de F10 |
| CA-06 | Cumple | AIT:200 `unavailablePlansAreRejected`: plan desactivado, plan de EPS inactiva y plan inexistente → 422 `INSURANCE_PLAN_UNAVAILABLE` (los tres el mismo código, para no revelar si el plan existe); sin `insurancePlanId` → 400 con `fieldErrors.insurancePlanId`; ninguna fila creada; AIT:221 `aRejectedChangeKeepsTheCurrentAffiliation` (la vigente queda intacta) | El predicado «ofrecible» es el mismo del catálogo público (`InsurancePlanCatalog#isSelectable`), así que cliente y servidor no pueden divergir |
| CA-07 | Cumple | AIT:273 `aUserNeverTouchesAnotherUsersAffiliation`: no existe ruta a la afiliación de otro (`GET`/`DELETE /api/users/{otroId}/affiliation` → 403 por `SecurityConfig:94`), un `userId` ajeno en el cuerpo del `PUT` se ignora y la operación toca la propia, y tras `PUT` + `DELETE` del primer usuario las filas del segundo quedan idénticas; AIT:293 `onlyUsersManageTheirAffiliation` (PROFESSIONAL y ADMIN → 403, anónimo → 401); `SecurityConfig:88` (`/api/me/affiliation` exige `ROLE_USER`) | El titular sale siempre del token (`MeController:96`), nunca del cuerpo ni de la ruta |
| CA-08 | Cumple | `V2__configurable_catalogs_and_professionals.sql:145-168` crea `affiliations` con las FK a `users` y a `eps_plans`, el `CHECK` de fechas y las dos restricciones de unicidad; `V9` ajusta una de ellas por D32; FMES:98 `aplicaTodasLasMigracionesEnOrden`, FMES:120 y FMES:125 aplican V1–V10 sobre un esquema vacío desechable y validan el resultado | La tabla nace en V2, no en una migración propia de esta HU: V2 sí es incremental sobre V1 |
| CA-09 | Cumple | AIT:310 `changingThePlanClosesTheCurrentOne`: cambiar de A a B deja dos filas, la de A con `is_current = false` y `ended_on = hoy` (`America/Bogota`) y su `started_on` intacto, exactamente una vigente, y `GET /api/me` devuelve el plan B con su EPS y su régimen; AT:28 y AT:43; AIT:335 `returningToAPreviouslyUsedPlanWorks` (A → B → A el mismo día, tres filas, una vigente) | `ManageAffiliationUseCase#change` cierra y abre bajo `lockCurrent`, en una sola transacción |
| CA-10 | Cumple | AIT:349 `removingClosesItWithoutReplacement`: `DELETE /api/me/affiliation` → 204, la fila **sigue existiendo** con `is_current = false` y `ended_on = hoy`, ninguna vigente, y `GET /api/me` omite `affiliation`; sin vigente el `DELETE` vuelve a responder 204; AT:50 `onlyACurrentAffiliationCanBeClosed` | La baja es idempotente por diseño, sin error cuando no hay nada que cerrar |
| DoD — CA-01 a CA-10 validados con evidencia concreta | No cumple | CA-01 a CA-04 y CA-06 a CA-10 en `Cumple`; **CA-05 en `Pendiente`** | El ítem depende de los diez; queda abierto por CA-05 |
| DoD — Migración Flyway versionada que crea la tabla, aplicada de forma incremental sobre el esquema existente | Cumple | `V2:145-168` (creación, incremental sobre V1) y `V9` (ajuste de la unicidad por D32, incremental sobre V8); FMES:98 y FMES:125; `application.yml` con `ddl-auto: validate` | — |
| DoD — La tabla no replica EPS ni régimen: ambos se navegan desde el plan, con justificación de normalización | Cumple | AIT:157 (las columnas reales no incluyen `eps_id` ni `regime_id`); `JdbcAffiliationQueries:18-31`; `V2:138-144` documenta la dependencia funcional `affiliation_id → eps_plan_id → eps_id / regime_id`; `citas-api/docs/wiki/llm-wiki/wiki/datos-modelo-3fn.md` | — |
| DoD — Prevención de duplicados en dos niveles: caso de uso y restricción de unicidad del esquema | Cumple | Caso de uso: `ManageAffiliationUseCase#change` nunca crea una segunda fila vigente —bloquea la vigente, sale sin cambios si es el mismo plan y la cierra antes de abrir la nueva si es otro—, probado en AIT:170 y AIT:310. Esquema: `uq_affiliations_user_current` y `uq_affiliations_user_plan_current` (`V2`, `V9`), probadas por inserción directa en AIT:182 | En el caso de uso la prevención toma la forma de **idempotencia**, no de error, desde que D26 convirtió la operación en un `PUT` que reemplaza. El invariante protegido es el mismo: a lo sumo una afiliación vigente por usuario |
| DoD — Dominio de la afiliación sin Spring ni JPA | Cumple | `domain/affiliation/Affiliation`, `AffiliationRepository`, `InsurancePlanCatalog` e `InsurancePlanUnavailableException` son Java puro; AT (5 pruebas de dominio sin framework); `HexagonalArchitectureTest` | — |
| DoD — Las operaciones resuelven el usuario desde el contexto de seguridad y aplican el ownership de [[HU-005-autorizar-peticiones-por-rol-y-ownership]] | Cumple | `MeController:96` (`PUT`) y el `DELETE` del mismo controlador, ambos con `CurrentUser.id(auth)`; AIT:273 y AIT:293 | No hay `Ownership.requireOwned` aquí porque no existe ninguna ruta que reciba un id de afiliación ajena: el recurso no es direccionable, que es la forma más fuerte del mismo resultado |
| DoD — La lista de planes de `citas-web` contiene solo activos y coincide con lo que acepta `citas-api` | Pendiente | El servidor ya lo garantiza con un único predicado compartido (`InsurancePlanCatalog#isSelectable`, usado por el catálogo público, por el registro y por el cambio desde el perfil), probado en AIT:212 y AIT:200 | La parte de servidor está cerrada; falta comprobar en `citas-web` que la lista mostrada es exactamente la del catálogo. Misma causa que CA-05 |
| DoD — Pruebas de alta correcta, duplicidad rechazada, plan desactivado rechazado y acceso ajeno, y pasan | Cumple | AIT (14 pruebas de integración), RAIT (primer corte) y AT (5 de dominio); suite completa 484/484 `BUILD SUCCESS` reejecutada por el `backend-verifier` | «Duplicidad rechazada» se cubre con AIT:182 (esquema) y AIT:170 (el caso de uso no crea la segunda fila) |
| DoD — Contrato de los endpoints de afiliación y del catálogo de planes reflejado en [[HU-033-publicar-contrato-rest-documentado]] | Cumple | `citas-api/docs/wiki/llm-wiki/wiki/contrato-rest-identidad.md:342` (`PUT /api/me/affiliation`), `:343` (`DELETE`), `:370` («el plan que ya está vigente responde 200 con la afiliación vigente»), `:386` y `:393` (V9 y la unicidad nueva), y `:482` (el catálogo público de planes entre las rutas sin token) | El contrato documenta el 200 idempotente que CA-03 ahora exige. `llm-wiki/` queda fuera del límite de escritura de esta skill |
| DoD — Trazabilidad de esta HU y de [[EP-002-perfil-y-afiliacion-del-paciente]] actualizada | Cumple | Esta matriz, el historial de validación y las notas (D26 y D32 registradas, pregunta abierta cerrada); en [[EP-002-perfil-y-afiliacion-del-paciente]] las anotaciones de INC-006 e INC-007; `docs/wiki/scrum/README.md` | — |

## Historial de validación

- 2026-09-30 — **Matriz de evidencia recolectada del repositorio.** CA-01 a CA-04 y CA-06 a CA-10 en `Cumple`, junto con toda la DoD de backend, de contrato y de trazabilidad. Estado: `Aprobada` → `En validación`. **No pasa a `Completada`**: CA-05 y el ítem de DoD sobre la lista de planes en `citas-web` quedan en `Pendiente`, y el ítem «CA-01 a CA-10 validados» en `No cumple` por arrastre de CA-05. Falta la verificación de frontend criterio a criterio y la prueba manual en navegador de F10.
- 2026-09-30 — **CA-03 reescrito. Decisión directa del usuario, no delegada.** El criterio exigía 409 al reenviar el plan que ya está vigente; el código responde 200 sin cambios y el contrato lo documenta así (`contrato-rest-identidad.md:370`). **Manda el código: el criterio estaba mal.** Razón: CA-03 se escribió en S2 para un `POST` que *creaba* la afiliación, donde repetir el plan sí duplicaba; **D26** lo convirtió en un `PUT` que *reemplaza* la vigente, y un `PUT` cuyo cuerpo describe el estado actual debe ser idempotente. Además el invariante que CA-03 protegía —a lo sumo una afiliación vigente, y nunca dos al mismo plan— ya lo imponen `uq_affiliations_user_current` y `uq_affiliations_user_plan_current` (`V9`), y **CA-04 lo verifica** con un `INSERT` directo. CA-03 pasa a exigir el 200 idempotente, la ausencia de un segundo registro y la unicidad de la afiliación vigente; CA-04 se reformula sobre el esquema y recoge además la convivencia de una fila cerrada con una vigente que D32 hizo posible. **No se relaja el requisito de RF-04:** sigue siendo imposible duplicar EPS, régimen y plan dentro del usuario.
- 2026-09-30 — La nota «Pregunta abierta que D26 no resuelve» sobre `uq_affiliations_user_plan` se sustituye por la decisión que la resolvió, **D32**, ejecutada por `V9`. Decía que había que decidirlo «antes de implementar F6», y F6 ya está implementada y probada: quien retomara podía bloquearse sin motivo.
- 2026-09-25 — Se añaden CA-09 y CA-10 por D26 (cambio y baja de la afiliación), que no tenían ningún criterio que los hiciera verificables; la DoD pasa a CA-01 a CA-10. Ningún criterio existente se reescribe.
- 2026-09-25 — Estado sin cambios (`Aprobada`). Alcance **ampliado** al segundo corte —consultar, cambiar y quitar la afiliación desde el perfil— por **aprobación delegada** del usuario para S4 (D15, PLAN_RETOMA_S4.md). Decisiones en [[dec-006-decisiones-s4-ciclo-de-vida]]; fase F6 del plan. El primer corte sigue siendo aprobación directa del usuario; solo la ampliación es delegada.
- 2026-09-23 — **el usuario la aprueba** y la adelanta fuera del Sprint 2 para cubrir la afiliación opcional durante el registro. Aprobación directa del usuario, no delegada. Se recorta el alcance a ese primer corte y se sustituye la dependencia de [[HU-012-gestionar-eps-y-planes]] por una semilla por script.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- **Resuelta (D26, provisional bajo delegación):** INC-007 (ver [[EP-002-perfil-y-afiliacion-del-paciente]]): **una sola afiliación vigente** por usuario, que el esquema ya impone con `uq_affiliations_user_current` (`V2__configurable_catalogs_and_professionals.sql:158`). Cambiar de plan cierra la anterior con `ended_on` y abre una nueva; quitarla la cierra sin reemplazo ([[dec-006-decisiones-s4-ciclo-de-vida]]). CA-09 y CA-10 lo cubren. La nota sobre modificación y baja "que requieren decisión humana previa" queda resuelta por esta decisión.
- **Resuelta (D32, provisional bajo delegación):** `V2:157` declaraba `uq_affiliations_user_plan UNIQUE (user_id, eps_plan_id)` sin condición de vigencia, lo que con el historial de D26 impedía **volver a un plan ya usado**: la fila cerrada de A chocaba con la nueva. **D32** eligió la unicidad sobre la afiliación vigente y la **ejecutó `V9__eps_names_and_affiliation_history.sql`**, que sustituye esa restricción por `uq_affiliations_user_plan_current UNIQUE (user_id, eps_plan_id, current_marker)` —`current_marker` vale 1 en la vigente y `NULL` en las cerradas, y MySQL admite varios `NULL` en un índice único—. Dos afiliaciones **vigentes** al mismo plan siguen prohibidas, y además ya lo estaban por `uq_affiliations_user_current`. Probado en `AffiliationIntegrationTest:295` `returningToAPreviouslyUsedPlanWorks`, `AffiliationIntegrationTest:182` (los tres casos de la restricción) y `FlywayMigratesEmptySchemaTest:234` `laUnicaDeAfiliacionPermiteVolverAUnPlanYaUsado`. CA-04 lo refleja desde el 2026-09-30.
- Incógnita abierta **INC-008** (ver [[EP-002-perfil-y-afiliacion-del-paciente]]): el PRD no define si la afiliación es obligatoria para solicitar una cita. Ningún criterio de esta HU bloquea el agendamiento; si se decidiera que es obligatoria, la regla se escribiría en la épica de reserva y afectaría a [[HU-024-solicitar-cita-especializada]].
- ~~El PRD no indica si el usuario puede eliminar o cambiar una afiliación ya registrada. Esta HU solo cubre registrar y consultar; la modificación y la baja requieren decisión humana previa.~~ Resuelta por D26 (ver arriba).
- La decisión de no duplicar EPS ni régimen dentro de la afiliación se apoya en la exigencia de 3FN de las restricciones técnicas y debe quedar reflejada en la justificación de claves y dependencias funcionales del modelo de datos.
