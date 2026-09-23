---
id: HU-016
tipo: historia-de-usuario
titulo: "Activar o desactivar profesional"
estado: En validación
epica: "[[EP-004-gestion-de-profesionales]]"
requisitos: [RF-07]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 3"
dependencias:
  - "[[HU-013-crear-profesional-con-datos-de-registro]]"
relacionadas:
  - "[[HU-022-buscar-disponibilidad-con-filtros]]"
  - "[[HU-017-crear-bloques-de-disponibilidad-con-slots]]"
  - "[[HU-030-aprobar-o-rechazar-cita-especializada]]"
---

# HU-016 — Activar o desactivar profesional

## Historia de usuario

**COMO** ADMIN  
**QUIERO** desactivar a un profesional que deja de atender y reactivarlo cuando vuelva  
**PARA** retirar su oferta de agenda sin borrar sus datos ni perder la trazabilidad de sus citas

> Como ADMIN, quiero desactivar a un profesional que deja de atender y reactivarlo cuando vuelva para retirar su oferta de agenda sin borrar sus datos ni perder la trazabilidad de sus citas.

## Contexto y descripción

RF-07 incluye la activación y desactivación del profesional entre las capacidades de ADMIN. [[EP-004-gestion-de-profesionales]] fija que un profesional referenciado por transacciones se desactiva y no se borra, aplicando por analogía la regla de RF-06 sobre catálogos y la preservación de trazabilidad de RN-12. La tabla `professionals` de V2 ya tiene el indicador `active`.

El efecto observable mínimo que exige la épica es que un profesional desactivado no aparezca como oferta en la búsqueda de disponibilidad. Lo que ocurre con sus bloques futuros y con sus citas ya comprometidas no está definido en el PRD (INC-014), por lo que esta HU no modifica citas existentes.

## Alcance

- Endpoints REST de desactivación y de activación de un profesional, restringidos a `ADMIN`.
- Transición explícita del indicador `active` con respuesta idempotente o error claro ante una transición redundante.
- Exclusión del profesional inactivo de la oferta de disponibilidad y de nuevas reservas.
- Filtro por estado activo/inactivo en el listado de profesionales de ADMIN.
- Acción de activar/desactivar con confirmación en la pantalla de gestión de profesionales de `citas-web`.

## Fuera de alcance

- Borrado físico del profesional: no se ofrece.
- Cancelación, rechazo o reasignación automática de citas futuras del profesional desactivado: pendiente de INC-014.
- Bloqueo del login del profesional desactivado: el PRD no lo define (ver notas).

## Reglas de negocio

- ADMIN puede activar y desactivar profesionales (RF-07).
- Un profesional se desactiva, no se borra, para preservar la trazabilidad de sus citas (EP-004, RN-12).
- Un profesional inactivo no se ofrece en la búsqueda de disponibilidad ni puede recibir nuevas citas (criterio de completitud de EP-004, RN-08 por analogía).
- Solo ADMIN ejecuta la operación (PRD §8).

## Dependencias y relaciones

- Épica: [[EP-004-gestion-de-profesionales]]
- Dependencias: [[HU-013-crear-profesional-con-datos-de-registro]]
- Relacionadas: [[HU-014-asignar-especialidades-y-especialidad-primaria]], [[HU-015-asignar-sedes-al-profesional]], [[HU-017-crear-bloques-de-disponibilidad-con-slots]], [[HU-022-buscar-disponibilidad-con-filtros]], [[HU-023-agendar-cita-de-medicina-general]], [[HU-024-solicitar-cita-especializada]], [[HU-030-aprobar-o-rechazar-cita-especializada]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** El cambio de estado en sí es sencillo, pero su efecto es transversal: la búsqueda de disponibilidad y los casos de uso de reserva deben respetar el indicador, y la decisión abierta sobre bloques y citas futuras obliga a acotar con cuidado lo que la HU hace y lo que deja intacto.

## Tareas de desarrollo

- [ ] **T-01 — Modelar las transiciones de activación en el dominio**  
  Dificultad: Bajo  
  Descripción: Operaciones explícitas de activar y desactivar sobre el agregado profesional, con la regla de transición redundante definida, sin dependencias de framework.

- [ ] **T-02 — Implementar los casos de uso de activación y desactivación**  
  Dificultad: Bajo  
  Descripción: Casos de uso que recuperan el profesional, aplican la transición y persisten el nuevo estado sin tocar usuario, especialidades, sedes, bloques ni citas.

- [ ] **T-03 — Exponer los endpoints REST y el filtro por estado**  
  Dificultad: Bajo  
  Descripción: Endpoints restringidos a `ADMIN` para activar y desactivar, y parámetro de filtro por estado en el listado de profesionales.

- [ ] **T-04 — Publicar la condición "profesional activo" para la oferta y la reserva**  
  Dificultad: Medio  
  Descripción: Consulta o especificación reutilizable que la búsqueda de disponibilidad y los casos de uso de reserva aplicarán para excluir profesionales inactivos.

- [ ] **T-05 — Añadir la acción de activar/desactivar en citas-web**  
  Dificultad: Bajo  
  Descripción: Botón con diálogo de confirmación en la gestión de profesionales, indicador visual de estado y filtro por estado.

- [ ] **T-06 — Pruebas de activación y desactivación**  
  Dificultad: Medio  
  Descripción: Pruebas de dominio de las transiciones e integración para la desactivación, la reactivación, la conservación de citas existentes y la restricción de rol.

## Criterios de aceptación

### CA-01 — Desactivación de un profesional activo

**Dado** un profesional activo  
**Cuando** ADMIN lo desactiva  
**Entonces** la API responde con éxito, el profesional figura como inactivo en el listado y su usuario, especialidades y sedes siguen existiendo.

### CA-02 — Reactivación de un profesional inactivo

**Dado** un profesional inactivo  
**Cuando** ADMIN lo activa  
**Entonces** la API responde con éxito y el profesional vuelve a figurar como activo con sus especialidades y sedes previas.

### CA-03 — Un profesional inactivo no se ofrece como disponibilidad

**Dado** un profesional con slots libres publicados en una fecha futura  
**Cuando** ADMIN lo desactiva y un paciente busca disponibilidad con filtros que lo incluirían  
**Entonces** ningún slot de ese profesional aparece en los resultados, verificado contra [[HU-022-buscar-disponibilidad-con-filtros]].

### CA-04 — Un profesional inactivo no recibe nuevas citas

**Dado** un profesional inactivo y el identificador de uno de sus slots libres futuros  
**Cuando** un paciente intenta reservar directamente ese slot  
**Entonces** la API rechaza la reserva con un error de regla de negocio y no se crea cita ni reserva de slot.

### CA-05 — Las citas existentes se conservan

**Dado** un profesional con citas y registros de historial existentes  
**Cuando** ADMIN lo desactiva  
**Entonces** ninguna cita cambia de estado, no se libera ningún slot reservado y no se borra ningún registro de historial.

### CA-06 — No existe borrado físico

**Dado** cualquier profesional  
**Cuando** se inspeccionan los endpoints publicados de gestión de profesionales  
**Entonces** no existe ninguna operación que elimine físicamente al profesional o a su usuario.

### CA-07 — Solo ADMIN activa o desactiva

**Dado** un usuario autenticado con rol `USER` o `PROFESSIONAL`  
**Cuando** invoca la activación o desactivación de cualquier profesional  
**Entonces** la API responde con un error de autorización y el estado no cambia.

## Definition of Done

- [x] Los criterios CA-01 a CA-07 están validados con evidencia concreta.
- [ ] Activar y desactivar son operaciones explícitas del dominio, no una actualización genérica del campo `active`.
- [x] La condición de profesional activo está centralizada y la reutilizan la búsqueda y la reserva, sin copias divergentes.
- [x] No se introduce borrado físico ni se alteran citas, reservas de slot ni historial.
- [x] Los endpoints exigen rol `ADMIN` aplicando [[HU-005-autorizar-peticiones-por-rol-y-ownership]].
- [x] La acción de `citas-web` requiere confirmación explícita.
- [ ] Existen pruebas automatizadas de desactivación, reactivación, conservación de citas y rol, y pasan; CA-03 y CA-04 se verifican cuando existan [[HU-022-buscar-disponibilidad-con-filtros]] y [[HU-023-agendar-cita-de-medicina-general]].
- [x] El contrato de los endpoints de activación está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [x] La trazabilidad de esta HU y de [[EP-004-gestion-de-profesionales]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Ejecución de referencia del **2026-09-23**: backend `docker compose run --rm citas-api-dev mvn -B test` → **242 pruebas, 0 fallos** en 22 clases; frontend `npm test` en `citas-web` → **88 pruebas, 0 fallos**, con `typecheck`, `lint` y `build` en verde. La verificación fue independiente (`backend-verifier` y `frontend-verifier`, agentes que no escribieron el código): `EVIDENCIAS_S3.md` §10. Las clases de prueba del backend cuelgan de `citas-api/src/test/java/com/fcv/citas/`.

**La HU no se cierra.** Los siete criterios están respaldados, pero dos ítems de DoD no lo están.

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | `infrastructure/rest/ProfessionalAdminIntegrationTest#deactivatesAndReactivatesKeepingAssignments` | `PATCH …/status` con `active: false` → 200 con `active: false` y sus 2 especialidades intactas; el listado `?active=true` deja de incluirlo. El usuario asociado no se toca |
| CA-02 | Cumple | `ProfessionalAdminIntegrationTest#deactivatesAndReactivatesKeepingAssignments` | Al reactivar, `active: true` y la sede sigue asignada |
| CA-03 | Cumple | `infrastructure/rest/BookingIntegrationTest#inactiveProfessionalIsNeitherOfferedNorBookable` (tras desactivarlo, ninguna franja suya aparece en la búsqueda); `infrastructure/persistence/appointment/JdbcAvailabilityQueries` (`JOIN professionals p … AND p.active`) | Verificado contra [[HU-022-buscar-disponibilidad-con-filtros]] |
| CA-04 | Cumple | `BookingIntegrationTest#inactiveProfessionalIsNeitherOfferedNorBookable` (reserva directa sobre su slot → 422 `PROFESSIONAL_INACTIVE`); `application/appointment/BookAppointmentUseCase#book` | No se crea cita ni reserva de slot |
| CA-05 | Cumple | `application/professional/ManageProfessionalsUseCase#setActive` y `infrastructure/persistence/professional/JpaProfessionalRepositoryAdapter#setActive`: la única escritura es el indicador `active` del perfil; no hay ninguna referencia a `appointments`, `slot_reservations` ni `appointment_status_history` en ese camino. `infrastructure/persistence/FlywayMigratesEmptySchemaTest#elHistorialEsAppendOnlyYAdmiteOrigenProfesional` confirma que la clave foránea del historial es `RESTRICT`, no `CASCADE` (V5). `ProfessionalAdminIntegrationTest#deactivatesAndReactivatesKeepingAssignments` comprueba que especialidades y sedes sobreviven | Verificado por **lectura del código**, no por una prueba que desactive un profesional con citas y vuelva a consultarlas. Ver el ítem de DoD correspondiente |
| CA-06 | Cumple | `infrastructure/rest/professional/AdminProfessionalController` publica `GET`, `POST`, `PUT` y `PATCH`, y **ningún** `@DeleteMapping`: no existe ruta de borrado de profesional ni de su usuario. `infrastructure/rest/AdminDecisionIntegrationTest#historyAndAppointmentsCannotBeDeletedThroughTheApi` muestra el patrón equivalente para citas (405 en un `DELETE` no declarado) | La desactivación es la única baja posible (decisión D11) |
| CA-07 | Cumple | `SecurityConfig` (`/api/admin/**` → `hasRole("ADMIN")`); `infrastructure/rest/AuthorizationIntegrationTest#anonymousGets401AndWrongRoleGets403OnAdminRoutes` y `#onlyAdminReachesAdminRoutes` (USER y PROFESSIONAL → 403) | — |
| DoD — CA-01 a CA-07 validados con evidencia concreta | Cumple | Filas CA-01 a CA-07 de esta tabla | — |
| DoD — Activar y desactivar son operaciones explícitas del dominio, no una actualización genérica del campo `active` | No cumple | `domain/professional/Professional` **no** tiene ninguna operación de activación: no existen `activate()`, `deactivate()` ni `withActive(...)`. El cambio se hace con el puerto genérico `ProfessionalRepository#setActive(long, boolean)`, que `JpaProfessionalRepositoryAdapter#setActive` traduce a `entity.setActive(active)`; el caso de uso es `ManageProfessionalsUseCase#setActive(id, active)` y el contrato es `PATCH …/status` con un booleano | Es exactamente lo que la DoD descarta: una actualización genérica del campo. Contraste dentro del propio proyecto: `domain/catalog/Specialty#withActive` sí es una operación de dominio y por eso puede proteger Medicina General. **Acción pendiente de desarrollo:** llevar activar y desactivar al dominio (por ejemplo `Professional#activate()` / `#deactivate()`), de modo que futuras reglas —como impedir desactivar a un profesional con citas próximas, si el usuario lo decide— tengan dónde vivir. Bloquea el cierre |
| DoD — La condición de profesional activo está centralizada y la reutilizan búsqueda y reserva | Cumple | Origen único: la columna `professionals.active` (`V2__configurable_catalogs_and_professionals.sql`). La búsqueda la lee en `JdbcAvailabilityQueries` (`JOIN professionals p … AND p.active`) y la reserva en `BookAppointmentUseCase#book` a través de `Professional#active()`; `BookingIntegrationTest#inactiveProfessionalIsNeitherOfferedNorBookable` comprueba los dos lados en la misma prueba | No hay regla derivada que pueda divergir: es el mismo indicador leído en los dos caminos, y la prueba fija su coherencia |
| DoD — No se introduce borrado físico ni se alteran citas, reservas de slot ni historial | Cumple | Fila CA-06 (ninguna ruta de borrado) y fila CA-05 (la desactivación solo escribe `active`); `V5__audit_history_append_only.sql` quitó el borrado en cascada del historial | — |
| DoD — Los endpoints exigen rol ADMIN aplicando [[HU-005-autorizar-peticiones-por-rol-y-ownership]] | Cumple | `SecurityConfig`; `AuthorizationIntegrationTest#onlyAdminReachesAdminRoutes` | — |
| DoD — La acción de `citas-web` requiere confirmación explícita | Cumple | `citas-web/src/adminOperations.test.tsx`: «filtra por estado en el backend y busca por texto; desactivar pide confirmación»; `citas-web/src/components/ConfirmDialog.tsx` | — |
| DoD — Pruebas de desactivación, reactivación, conservación de citas y rol | No cumple | Existen las de desactivación y reactivación (`ProfessionalAdminIntegrationTest#deactivatesAndReactivatesKeepingAssignments`), las de rol (`AuthorizationIntegrationTest`) y las de exclusión de la oferta y la reserva (`BookingIntegrationTest#inactiveProfessionalIsNeitherOfferedNorBookable`). **No existe** ninguna prueba de conservación de citas: ninguna desactiva a un profesional que ya tenga cita e historial para comprobar después que el estado, los slots retenidos y las filas de historial siguen igual | CA-05 se apoya hoy solo en lectura de código. **Acción pendiente de desarrollo:** prueba de integración que cree una cita `APPROVED` con su reserva, desactive al profesional y afirme estado, `COUNT(*)` de `slot_reservations` y `COUNT(*)` de `appointment_status_history` sin cambios. Bloquea el cierre |
| DoD — Contrato de los endpoints de activación reflejado en [[HU-033-publicar-contrato-rest-documentado]] | Cumple | `citas-api/docs/wiki/llm-wiki/wiki/contrato-rest-citas.md`, sección «Profesionales — ADMIN (HU-013 a HU-016)» | — |
| DoD — Trazabilidad de esta HU y de [[EP-004-gestion-de-profesionales]] actualizada | Cumple | Esta tabla y el historial de validación | — |

## Historial de validación

- 2026-09-23 — Estado `En validación` (fase F11 de `PLAN_RETOMA_S3.md`): matriz de evidencia registrada. Los 7 criterios en `Cumple`, pero **no se cierra**: dos ítems de DoD quedan en `No cumple`. (1) Activar y desactivar no son operaciones del dominio: el cambio pasa por el puerto genérico `ProfessionalRepository#setActive(long, boolean)`. (2) No hay prueba de conservación de citas: CA-05 se sostiene hoy solo por lectura del código.
- 2026-09-18 — Estado `En desarrollo` (skill `scrum-spec-orchestrator`, paso 9): se inicia la implementación en la fase F3 de `PLAN_RETOMA_S3.md`.
- 2026-09-18 — Estado `Aprobada` por **aprobación delegada** de S3: el usuario pidió continuar S3 dejando las decisiones de diseño a criterio del agente (`AGENTS.md` §6). Alcance y decisiones D5–D13 en `PLAN_RETOMA_S3.md` y [[dec-004-decisiones-s3-reserva]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- Incógnita abierta **INC-014** (ver [[EP-004-gestion-de-profesionales]]): el PRD no define qué ocurre con bloques futuros y citas `REQUESTED` o `APPROVED` de un profesional desactivado. CA-05 fija el comportamiento conservador (no se toca nada) hasta que exista decisión humana.
- Incógnita abierta **INC-032** (ver [[EP-008-operacion-administrativa-de-solicitudes]]): si ADMIN desactiva a un profesional con citas `REQUESTED`, no está definido si esas solicitudes pueden aprobarse después. Afecta a [[HU-030-aprobar-o-rechazar-cita-especializada]].
- El PRD no define si un profesional inactivo puede seguir iniciando sesión, consultar su agenda o cerrar atenciones pasadas. Esta HU no modifica la autenticación; debe decidirse antes de aprobarla.
- CA-03 y CA-04 dependen de HU planificadas en sprints posteriores; la HU puede validarse en su sprint con los demás criterios y completar esos dos cuando existan la búsqueda y la reserva, o bien planificarse su validación final en el Sprint 5.
