---
id: HU-011
tipo: historia-de-usuario
titulo: "Gestionar especialidades y su duración"
estado: En validación
epica: "[[EP-003-catalogos-del-sistema]]"
requisitos: [RF-06, RF-09]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 3"
dependencias:
  - "[[HU-010-consultar-catalogos-fijos-precargados]]"
  - "[[HU-005-autorizar-peticiones-por-rol-y-ownership]]"
relacionadas:
  - "[[HU-014-asignar-especialidades-y-especialidad-primaria]]"
  - "[[HU-022-buscar-disponibilidad-con-filtros]]"
---

# HU-011 — Gestionar especialidades y su duración

## Historia de usuario

**COMO** ADMIN
**QUIERO** crear, editar, activar y desactivar especialidades indicando su duración y su tipo de cita
**PARA** que la oferta clínica y la duración de cada cita tengan una única fuente de verdad.

> Como ADMIN, quiero crear, editar, activar y desactivar especialidades indicando su duración y su tipo de cita para que la oferta clínica y la duración de cada cita tengan una única fuente de verdad.

## Contexto y descripción

RF-06 declara la especialidad como catálogo configurable gestionado por ADMIN, y RF-09 le asigna una responsabilidad adicional decisiva: cada especialidad define una duración de 30 o 60 minutos y el profesional no puede sobrescribirla. La duración determina cuántos slots consecutivos necesita una cita (30 min = 1 slot, 60 min = 2 slots consecutivos), por lo que la especialidad es la fuente de verdad del cálculo de disponibilidad.

La especialidad también determina la política de aprobación. RN-02 y RN-03 distinguen citas generales, que se aprueban automáticamente, de citas especializadas, que requieren la intervención de ADMIN. Esa distinción no depende de la cita ni del profesional: depende del tipo de cita asociado a la especialidad, que es su única fuente de verdad.

Por último, RF-06 prohíbe el borrado físico de un catálogo referenciado por transacciones. Una especialidad ya usada por citas o asignada a profesionales se desactiva, nunca se elimina, para no romper el histórico exigido por RN-12.

## Alcance

- Migración Flyway de la tabla de especialidades con nombre, duración, tipo de cita y marca de activación.
- Creación de una especialidad por ADMIN indicando nombre, duración y tipo de cita.
- Edición de los datos de una especialidad existente.
- Activación y desactivación de una especialidad.
- Listado y consulta de especialidades, con filtro por estado de activación y por tipo de cita.
- Validación server-side de la duración permitida (30 o 60 minutos).
- Derivación de la política de aprobación a partir del tipo de cita de la especialidad.
- Exclusión de las especialidades desactivadas de la oferta para nuevas reservas.
- Pantalla de CRUD de especialidades en `citas-web`, restringida a ADMIN.

## Fuera de alcance

- Asignación de especialidades a un profesional y marcado de la primaria, que se cubre en [[HU-014-asignar-especialidades-y-especialidad-primaria]].
- Cálculo de slots y de disponibilidad a partir de la duración, que pertenece a la agenda y a la búsqueda de disponibilidad.
- Aprobación o rechazo de citas especializadas, que pertenece a la operación administrativa de solicitudes.
- Creación de tipos de cita nuevos: general y especializada son los dos valores que el PRD reconoce (RF-10).
- Tarifas, cupos o restricciones por especialidad: no están en el PRD.

## Reglas de negocio

- Solo ADMIN gestiona los catálogos configurables (RF-06, PRD §8 autorización por rol).
- Cada especialidad define una duración de 30 o 60 minutos, y solo esos dos valores son admisibles (RF-09).
- El profesional no sobrescribe la duración definida por la especialidad (RF-09).
- Cada especialidad se asocia a un tipo de cita general o especializada (RF-10).
- La política de aprobación deriva del tipo de cita de la especialidad: general se aprueba automáticamente (RN-02) y especializada requiere ADMIN (RN-03). La especialidad es la única fuente de verdad de esa política.
- No se permite borrar físicamente una especialidad referenciada por transacciones; se desactiva (RF-06).
- Una especialidad debe estar activa para poder reservarse (RN-08).
- Los nombres de especialidad del laboratorio son sintéticos y no reproducen la oferta real de FCV (PRD §9).

## Dependencias y relaciones

- Épica: [[EP-003-catalogos-del-sistema]]
- Dependencias: [[HU-010-consultar-catalogos-fijos-precargados]], [[HU-005-autorizar-peticiones-por-rol-y-ownership]]
- Relacionadas: [[HU-012-gestionar-eps-y-planes]], [[HU-014-asignar-especialidades-y-especialidad-primaria]], [[HU-022-buscar-disponibilidad-con-filtros]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** El CRUD en sí es rutinario, pero esta HU concentra tres reglas que el resto del producto da por ciertas: la duración cerrada a 30/60, la derivación de la política de aprobación desde el tipo de cita, y la desactivación en lugar del borrado cuando hay referencias. Detectar que una especialidad está referenciada obliga a consultar citas y asignaciones de profesional, lo que añade acoplamiento a agregados que otras HU construyen.

## Tareas de desarrollo

- [ ] **T-01 — Modelar la especialidad en el dominio**
  Dificultad: Medio
  Descripción: Entidad de dominio con nombre, duración como objeto de valor restringido a 30 o 60 minutos, tipo de cita general o especializada y estado de activación, con la invariante de duración validada en el constructor y sin dependencias de framework.

- [ ] **T-02 — Crear la migración Flyway de especialidades**
  Dificultad: Bajo
  Descripción: Migración versionada que crea la tabla de especialidades con nombre único, duración en minutos, referencia al tipo de cita y columna de activación con valor por defecto activo, y una restricción que solo admita las duraciones válidas.

- [ ] **T-03 — Implementar los casos de uso de creación y edición**
  Dificultad: Medio
  Descripción: Casos de uso de aplicación que validan nombre, duración y tipo de cita, comprueban la unicidad del nombre y persisten la especialidad a través de puertos de repositorio.

- [ ] **T-04 — Implementar los casos de uso de activación y desactivación**
  Dificultad: Medio
  Descripción: Casos de uso que cambian el estado de activación sin eliminar el registro, y comprobación de referencias en citas y en asignaciones de profesional para impedir el borrado físico de una especialidad referenciada.

- [ ] **T-05 — Exponer el adaptador REST del CRUD de especialidades**
  Dificultad: Medio
  Descripción: Controlador con DTO validados, listado con filtro por activación y por tipo de cita, respuestas de error diferenciadas para duración inválida, nombre duplicado y borrado no permitido por referencias existentes.

- [ ] **T-06 — Restringir el CRUD a ADMIN en la configuración de seguridad**
  Dificultad: Bajo
  Descripción: Declarar las rutas de escritura de especialidades como accesibles solo con rol `ADMIN`, dejando la consulta disponible para los roles que la necesitan en sus formularios.

- [ ] **T-07 — Construir la pantalla de CRUD de especialidades en citas-web**
  Dificultad: Medio
  Descripción: Vista React + TypeScript visible solo para ADMIN con listado, alta, edición y conmutación de activación, selector de duración limitado a 30 y 60 minutos, selector de tipo de cita y presentación de los errores devueltos por la API.

- [ ] **T-08 — Pruebas de dominio, aplicación e integración de especialidades**
  Dificultad: Medio
  Descripción: Pruebas de la invariante de duración, del rechazo de valores distintos de 30 y 60, de la desactivación frente al borrado con referencias, de la exclusión de inactivas en la oferta y de la restricción de rol en el adaptador REST.

## Criterios de aceptación

### CA-01 — Alta de especialidad con duración válida

**Dado** una sesión autenticada con rol `ADMIN`
**Cuando** crea una especialidad con nombre no existente, duración de 30 minutos y tipo de cita especializada
**Entonces** la API responde con creación exitosa, la especialidad queda persistida en estado activo y aparece en el listado de especialidades.

### CA-02 — Duración distinta de 30 o 60 minutos rechazada

**Dado** una sesión autenticada con rol `ADMIN`
**Cuando** intenta crear o editar una especialidad con duración de 45 minutos, de 0 minutos o con un valor negativo
**Entonces** la API responde en los tres casos con un error de validación sobre el campo duración, no persiste ni modifica ninguna especialidad, y el mensaje indica que solo se admiten 30 o 60 minutos.

### CA-03 — El tipo de cita determina la política de aprobación

**Dado** una especialidad con tipo de cita general y otra con tipo de cita especializada
**Cuando** se consulta cada una de ellas
**Entonces** la respuesta indica para la general que no requiere aprobación de ADMIN y para la especializada que sí la requiere, y ese dato proviene del tipo de cita de la especialidad y no de ninguna otra configuración.

### CA-04 — Una especialidad referenciada no se borra físicamente

**Dado** una especialidad asociada a al menos una cita o asignada a al menos un profesional
**Cuando** ADMIN intenta borrarla
**Entonces** la API rechaza la operación con un error que explica que la especialidad está referenciada, el registro sigue existiendo en la base de datos y la única vía disponible es desactivarla.

### CA-05 — Desactivación sin pérdida del histórico

**Dado** una especialidad activa con citas ya registradas
**Cuando** ADMIN la desactiva
**Entonces** la API responde con éxito, la especialidad queda marcada como inactiva sin eliminarse, y las citas existentes siguen mostrando su especialidad al consultarlas.

### CA-06 — Una especialidad desactivada deja de ofrecerse para nuevas reservas

**Dado** una especialidad desactivada
**Cuando** un `USER` consulta la oferta de especialidades disponibles para reservar o intenta solicitar una cita con ella
**Entonces** la especialidad no aparece entre las opciones ofrecidas y la solicitud directa contra la API se rechaza indicando que la especialidad no está activa (RN-08).

### CA-07 — Reactivación de una especialidad

**Dado** una especialidad en estado inactivo
**Cuando** ADMIN la vuelve a activar
**Entonces** la API responde con éxito y la especialidad vuelve a aparecer entre las opciones ofrecidas para nuevas reservas.

### CA-08 — Solo ADMIN opera el CRUD

**Dado** sesiones autenticadas con rol `USER` y con rol `PROFESSIONAL`
**Cuando** cada una intenta crear, editar, activar o desactivar una especialidad
**Entonces** la API responde con un código de prohibido en todos los casos, no se modifica ningún registro, y la misma operación con rol `ADMIN` se ejecuta correctamente.

### CA-09 — La duración de la cita proviene siempre de la especialidad

**Dado** una especialidad con duración de 60 minutos
**Cuando** se crea una cita de esa especialidad enviando adicionalmente una duración distinta en la petición
**Entonces** la cita resultante tiene la duración definida por la especialidad y el valor enviado en la petición se ignora o se rechaza, sin que el profesional pueda alterarla (RF-09).

## Definition of Done

- [x] Los criterios CA-01 a CA-09 están validados con evidencia concreta.
- [ ] Existe una migración Flyway versionada de la tabla de especialidades con nombre único y una restricción de base de datos que impide persistir duraciones distintas de 30 y 60 minutos.
- [x] La invariante de duración está expresada en el dominio y no solo en la validación del DTO ni solo en la base de datos.
- [x] El dominio de especialidad no depende de Spring ni de JPA, respetando la separación hexagonal.
- [x] No existe ninguna ruta ni caso de uso que elimine físicamente una especialidad referenciada por citas o por asignaciones de profesional.
- [x] Las rutas de escritura de especialidades exigen rol `ADMIN` en la configuración de Spring Security y están cubiertas por prueba.
- [x] La pantalla de CRUD de especialidades de `citas-web` solo es accesible para ADMIN y consume la API mediante la URL configurable por entorno.
- [x] Los nombres de especialidad cargados como ejemplo son sintéticos y no reproducen la oferta real de FCV.
- [x] Existen pruebas automatizadas del rechazo de duración inválida, de la desactivación con referencias y de la exclusión de inactivas en la oferta, y pasan.
- [x] El contrato del CRUD de especialidades está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [x] La trazabilidad de esta HU y de [[EP-003-catalogos-del-sistema]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Ejecución de referencia del **2026-09-23**: backend `docker compose run --rm citas-api-dev mvn -B test` → **242 pruebas, 0 fallos** en 22 clases; frontend `npm test` en `citas-web` → **88 pruebas, 0 fallos**, con `typecheck`, `lint` y `build` en verde. La verificación fue independiente (`backend-verifier` y `frontend-verifier`, agentes que no escribieron el código): `EVIDENCIAS_S3.md` §10. Las clases de prueba del backend cuelgan de `citas-api/src/test/java/com/fcv/citas/`.

**La HU no se cierra.** Los nueve criterios están respaldados, pero un ítem de DoD no: falta la restricción de unicidad del **nombre** en la base de datos.

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | `infrastructure/rest/SpecialtyAdminIntegrationTest#createsActiveSpecialtyWithApprovalPolicyFromType` | 201 con `active: true`, 60 minutos y `requiresAdminApproval: true`; el listado de ADMIN la devuelve |
| CA-02 | Cumple | `SpecialtyAdminIntegrationTest#rejectsDurationOtherThan30Or60`, parametrizada con 0, 45 y 90 → 400 con `fieldErrors.durationMinutes = "Solo se admiten 30 o 60 minutos"` y cero filas persistidas; `domain/catalog/Specialty` (constructor compacto) | El caso negativo no se puede enviar como entero válido distinto de los probados sin recorrer la misma rama del constructor; los tres valores de la prueba cubren por debajo, por el medio y por encima. `EVIDENCIAS_S3.md` §8 lo repite contra la API real («45 min → 400») |
| CA-03 | Cumple | `SpecialtyAdminIntegrationTest#createsActiveSpecialtyWithApprovalPolicyFromType` (`requiresAdminApproval: true` para una SPECIALIZED); `V4__seed_fixed_catalogs.sql` líneas 23-25 (`appointment_types.requires_admin_approval`: GENERAL `FALSE`, SPECIALIZED `TRUE`); `infrastructure/persistence/catalog/JpaSpecialtyRepositoryAdapter` deriva el dato del tipo, no de una columna propia de la especialidad | La política no se copia en `specialties`: es una clave foránea al tipo |
| CA-04 | Cumple | `application/catalog/ManageSpecialtiesUseCase#delete` rechaza con 409 `SPECIALTY_REFERENCED` si `specialties.isReferenced(id)`; `JpaSpecialtyRepositoryAdapter#isReferenced` cuenta `professional_specialties` + `appointments`; `infrastructure/rest/VerificationGapsIntegrationTest#typeOfASpecialtyInUseCannotChange` ejercita ese mismo predicado con una especialidad en uso → 409 `SPECIALTY_REFERENCED`; `SpecialtyAdminIntegrationTest#deletesAnUnreferencedSpecialty` (el lado no referenciado, 204); `citas-web/src/adminOperations.test.tsx`: «una especialidad en uso no se borra: 409 SPECIALTY_REFERENCED sugiere desactivarla» | Matiz: **no** hay prueba de integración que haga `DELETE` sobre una especialidad referenciada. El predicado `isReferenced` sí está ejercitado (por la ruta de edición) y el guardia del borrado es el mismo, pero esa combinación concreta se verifica por lectura de código |
| CA-05 | Cumple | `ManageSpecialtiesUseCase#setActive` solo escribe el indicador `active` (`Specialty#withActive`), sin tocar `appointments`; `infrastructure/persistence/appointment/JdbcAppointmentQueries.SELECT` une `specialties` por identificador y **sin** filtrar por `active`, así que una cita existente sigue mostrando su especialidad; `SpecialtyAdminIntegrationTest#deactivatedSpecialtyLeavesTheCatalogAndComesBackWhenReactivated` (la especialidad queda inactiva sin eliminarse) | La conservación del histórico se verifica por lectura de la consulta de lectura; no hay una prueba que desactive una especialidad con citas y vuelva a consultarlas |
| CA-06 | Cumple | `SpecialtyAdminIntegrationTest#deactivatedSpecialtyLeavesTheCatalogAndComesBackWhenReactivated` (sale de `GET /api/catalogs/specialties`); `VerificationGapsIntegrationTest#deactivatedSpecialtyIsNoLongerOffered` (la búsqueda pasa de 3 franjas a 0); `#bookingWithAnInactiveSpecialtyIsRejected` (422 `SPECIALTY_INACTIVE`, cero citas) | Las tres caras de RN-08: catálogo, oferta y reserva |
| CA-07 | Cumple | `SpecialtyAdminIntegrationTest#deactivatedSpecialtyLeavesTheCatalogAndComesBackWhenReactivated` | Al reactivarla vuelve a aparecer en el catálogo de especialidades ofrecibles |
| CA-08 | Cumple | `SpecialtyAdminIntegrationTest#nonAdminCannotWrite` (USER → 403); `infrastructure/rest/AuthorizationIntegrationTest#onlyAdminReachesAdminRoutes` (PROFESSIONAL → 403, ADMIN → 200) | Toda la escritura está bajo `/api/admin/specialties` |
| CA-09 | Cumple | `infrastructure/rest/appointment/PatientBookingController.BookingRequest` **no tiene** campo de duración (su comentario lo declara: «el cuerpo no lleva paciente ni duracion (RF-09)»); `application/appointment/BookAppointmentUseCase#book` calcula el fin con `specialty.durationMinutes()`; `infrastructure/rest/BookingIntegrationTest#specializedRequestRetainsTwoConsecutiveSlots` (`durationMinutes: 60` tomado de la especialidad) y `#onlyUsersBookAndAlwaysForThemselves` (un campo extra en el cuerpo se descarta sin error) | Un valor enviado de más se ignora, que es una de las dos salidas que admite el criterio. El profesional no tiene ninguna ruta para alterar la duración |
| DoD — CA-01 a CA-09 validados con evidencia concreta | Cumple | Filas CA-01 a CA-09 de esta tabla | — |
| DoD — Migración con nombre único y restricción de base de datos para 30/60 minutos | No cumple | `V2__configurable_catalogs_and_professionals.sql` líneas 63-65: existen `uq_specialties_code` y `ck_specialties_duration CHECK (duration_minutes IN (30, 60))`, pero **no** hay restricción única sobre `name`. La unicidad del nombre solo vive en la aplicación (`ManageSpecialtiesUseCase#requireUniqueName` sobre `SpecialtyRepository#existsByName`, probada por `VerificationGapsIntegrationTest#specialtyNameIsUniqueIgnoringCase`) | La mitad de duración sí cumple. Falta la barrera de base de datos para el nombre: dos altas simultáneas con el mismo nombre podrían colarse, igual que ocurría con el código antes de `uq_specialties_code`. **Acción pendiente de desarrollo:** migración posterior a V7 que añada un índice único sobre `name` (con el colado `utf8mb4_0900_ai_ci` la comparación ya ignora mayúsculas y acentos) y traducción de su violación a 409 `DUPLICATE` con `field = name`. Bloquea el cierre |
| DoD — La invariante de duración está en el dominio, no solo en el DTO ni solo en la base | Cumple | `domain/catalog/Specialty` (constructor compacto: `durationMinutes != 30 && != 60` lanza `InvalidRequestException.field("durationMinutes", …)`) y `#slotsRequired`; `ck_specialties_duration` como segunda barrera | — |
| DoD — Dominio de especialidad sin Spring ni JPA | Cumple | `HexagonalArchitectureTest` (`elNucleoNoDependeDeFrameworks`, `elNucleoNoDependeDeLaInfraestructura`) | — |
| DoD — Ninguna ruta ni caso de uso elimina físicamente una especialidad referenciada | Cumple | `ManageSpecialtiesUseCase#delete` (único camino de borrado; guardia `isProtected() || isReferenced(id)`); `SpecialtyAdminIntegrationTest#generalMedicineCannotBeDeactivatedNorDeleted`; fila CA-04 | — |
| DoD — Las rutas de escritura exigen rol ADMIN y están cubiertas por prueba | Cumple | `SecurityConfig`; `SpecialtyAdminIntegrationTest#nonAdminCannotWrite`; `AuthorizationIntegrationTest#onlyAdminReachesAdminRoutes` | — |
| DoD — La pantalla de CRUD solo es accesible para ADMIN y usa la URL configurable | Cumple | `citas-web/src/auth/RequireRole.tsx` con `citas-web/src/roleNavigation.test.tsx`; `citas-web/src/pages/admin/SpecialtiesPage.tsx` sobre `src/api/adminApi.ts` y `API_ROUTES`; `citas-web/src/api/contracts.test.ts` | — |
| DoD — Los nombres de especialidad de ejemplo son sintéticos | Cumple | `citas-api/src/test/java/com/fcv/citas/support/S3TestData` genera códigos con prefijo y sufijo aleatorio; las pruebas usan «Cardiología» y «Dermatología», nombres genéricos de la disciplina; `V6__seed_general_medicine.sql` solo siembra `MEDICINA_GENERAL` | No se reproduce la oferta real de FCV |
| DoD — Pruebas de duración inválida, desactivación con referencias y exclusión de inactivas | Cumple | `SpecialtyAdminIntegrationTest#rejectsDurationOtherThan30Or60` (3 casos); `VerificationGapsIntegrationTest#typeOfASpecialtyInUseCannotChange` (predicado de referencia) y `#deactivatedSpecialtyIsNoLongerOffered`; `SpecialtyAdminIntegrationTest#deactivatedSpecialtyLeavesTheCatalogAndComesBackWhenReactivated` | Con el matiz de las filas CA-04 y CA-05: el borrado de una referenciada y la conservación del histórico se apoyan en lectura de código |
| DoD — Contrato del CRUD reflejado en [[HU-033-publicar-contrato-rest-documentado]] | Cumple | `citas-api/docs/wiki/llm-wiki/wiki/contrato-rest-citas.md`, sección «Especialidades — ADMIN (HU-011)», con el tipo `Specialty` y los códigos `SPECIALTY_REFERENCED` y `PROTECTED_SPECIALTY` | — |
| DoD — Trazabilidad de esta HU y de [[EP-003-catalogos-del-sistema]] actualizada | Cumple | Esta tabla y el historial de validación | — |

## Historial de validación

- 2026-09-23 — Estado `En validación` (fase F11 de `PLAN_RETOMA_S3.md`): matriz de evidencia registrada. Los 9 criterios en `Cumple`, pero **no se cierra**: falta el ítem de DoD «migración con nombre único». `V2` trae `uq_specialties_code` y el `CHECK` de 30/60 minutos, pero la unicidad del **nombre** solo existe en la capa de aplicación. Acción pendiente de desarrollo: una migración posterior a V7 con índice único sobre `specialties.name` y su traducción a 409 `DUPLICATE` con `field = name`.
- 2026-09-18 — Estado `En desarrollo` (skill `scrum-spec-orchestrator`, paso 9): se inicia la implementación en la fase F2 de `PLAN_RETOMA_S3.md`.
- 2026-09-18 — Estado `Aprobada` por **aprobación delegada** de S3: el usuario pidió continuar S3 dejando las decisiones de diseño a criterio del agente (`AGENTS.md` §6). Alcance y decisiones D5–D13 en `PLAN_RETOMA_S3.md` y [[dec-004-decisiones-s3-reserva]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- Incógnita abierta **INC-009** (ver [[EP-003-catalogos-del-sistema]]): RF-11 asume la existencia de `Medicina General`, pero RF-06 permite gestionar el catálogo libremente. No está decidido si `Medicina General` es una especialidad protegida contra desactivación o una más del catálogo configurable. Esta HU no la protege; si se decide protegerla, deberá añadirse el criterio correspondiente.
- Incógnita abierta **INC-010** (ver [[EP-003-catalogos-del-sistema]]): el PRD no define qué ocurre con los bloques de disponibilidad y las citas futuras ya aprobadas cuando se desactiva una especialidad. CA-06 solo exige bloquear nuevas reservas y CA-05 solo exige preservar el histórico; el tratamiento de las citas futuras queda pendiente de decisión y no se resuelve por inferencia.
- Incógnita abierta **INC-011** (ver [[EP-003-catalogos-del-sistema]]): RF-06 solo prohíbe el borrado físico de un catálogo referenciado. No está decidido si una especialidad nunca referenciada puede borrarse físicamente o si también debe limitarse a desactivación. CA-04 solo cubre el caso referenciado.
- Incógnita abierta **INC-012** (ver [[EP-003-catalogos-del-sistema]]): el PRD no indica si el tipo de cita de una especialidad puede cambiarse cuando ya tiene citas asociadas, lo que alteraría retroactivamente la política de aprobación. CA-03 no cubre ese cambio.
