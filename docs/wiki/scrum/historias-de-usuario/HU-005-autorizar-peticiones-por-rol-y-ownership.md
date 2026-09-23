---
id: HU-005
tipo: historia-de-usuario
titulo: "Autorizar peticiones por rol y ownership"
estado: En validación
epica: "[[EP-001-identidad-y-acceso-seguro]]"
requisitos: [RF-02]
esfuerzo: "Alto"
sprint_sugerido: "Sprint 1"
dependencias:
  - "[[HU-002-iniciar-sesion-con-jwt]]"
relacionadas:
  - "[[HU-025-consultar-mis-citas-y-detalle]]"
  - "[[HU-020-consultar-agenda-de-citas-aprobadas]]"
  - "[[HU-029-consultar-bandeja-administrativa]]"
---

# HU-005 — Autorizar peticiones por rol y ownership

## Historia de usuario

**COMO** responsable de la seguridad del sistema
**QUIERO** que cada petición se autorice por el rol del usuario y por la propiedad del recurso
**PARA** que nadie acceda a datos o acciones que no le corresponden

> Como responsable de la seguridad del sistema, quiero que cada petición se autorice por el rol del usuario y por la propiedad del recurso para que nadie acceda a datos o acciones que no le corresponden.

## Contexto y descripción

RF-02 exige que los roles formen parte del contexto de autorización y el PRD §8 declara la autorización por rol y ownership como seguridad mínima obligatoria. [[HU-002-iniciar-sesion-con-jwt]] ya deja los roles disponibles en el contexto de seguridad de cada petición; esta HU define qué se hace con ellos.

Son dos comprobaciones distintas y complementarias. La primera, por rol, responde a "¿este tipo de actor puede ejecutar esta operación?": solo ADMIN gestiona catálogos, profesionales y decisiones administrativas; solo PROFESSIONAL gestiona su propia agenda; solo USER opera sus citas. La segunda, por ownership, responde a "¿este recurso concreto le pertenece a quien lo pide?": tener rol USER no habilita a leer las citas de otro USER, y RF-16 prohíbe expresamente que un PROFESSIONAL vea datos de usuarios ajenos a sus propias citas.

Esta es la HU transversal de autorización: describe el mecanismo reutilizable —la declaración de rol requerido por operación y la verificación de propiedad sobre el recurso— y no la protección concreta de cada endpoint, que cada HU funcional aplica en su propio alcance. Por eso el resto de épicas la referencian como dependencia.

También fija la distinción observable entre no estar autenticado y estar autenticado con rol o propiedad insuficientes, para que el frontend pueda reaccionar de forma diferente: en un caso llevar al login, en el otro informar de falta de permiso.

Pertenece al alcance de la sesión S2.

## Alcance

- Mecanismo de autorización por rol aplicable de forma declarativa sobre las operaciones de `citas-api`.
- Mecanismo de verificación de ownership sobre recursos que pertenecen a un usuario concreto.
- Regla de rol para ADMIN sobre catálogos configurables, gestión de profesionales y decisiones administrativas sobre solicitudes.
- Regla de rol para PROFESSIONAL sobre la gestión de su propia agenda y la consulta de sus propias citas.
- Regla de rol para USER sobre la operación de sus propias citas.
- Restricción de RF-16: un PROFESSIONAL no accede a datos de usuarios que no estén asociados a sus propias citas.
- Respuestas diferenciadas para petición no autenticada frente a rol o propiedad insuficientes.
- Manejo en `citas-web` de ambas respuestas y ocultación de las acciones que el rol del usuario no puede ejecutar.
- Denegación por defecto: una operación sin regla de autorización declarada queda inaccesible.

## Fuera de alcance

- La protección endpoint por endpoint de cada funcionalidad: cada HU funcional aplica este mecanismo dentro de su propio alcance y lo declara en su DoD.
- La autenticación y la emisión de tokens, que pertenecen a [[HU-002-iniciar-sesion-con-jwt]].
- Permisos de grano fino distintos del rol (por ejemplo permisos individuales por operación): el PRD solo define roles.
- Delegación o suplantación entre usuarios: no está en el PRD.
- Reglas de negocio sobre estados de cita, que viven en sus propias épicas aunque se apoyen en esta autorización.

## Reglas de negocio

- Los roles del usuario forman parte del contexto de autorización de cada petición (RF-02).
- La autorización combina rol y ownership sobre el recurso (PRD §8).
- Solo ADMIN gestiona catálogos configurables, profesionales y decisiones administrativas sobre solicitudes y reprogramaciones (RF-06, RF-07, RF-12, RF-15, RF-18).
- Solo PROFESSIONAL gestiona su propia agenda y consulta su propio calendario (RF-08, RF-16).
- Solo USER opera sus propias citas (RF-11, RF-13, RF-14, RF-15).
- Un USER no lee ni modifica recursos pertenecientes a otro USER (PRD §8).
- Un PROFESSIONAL no accede a datos de usuarios fuera de sus propias citas (RF-16).
- Una petición sin autenticación válida y una petición autenticada con rol o propiedad insuficientes producen respuestas distinguibles entre sí.
- Toda operación que no declare explícitamente su regla de autorización se deniega por defecto.

## Dependencias y relaciones

- Épica: [[EP-001-identidad-y-acceso-seguro]]
- Dependencias: [[HU-002-iniciar-sesion-con-jwt]]
- Relacionadas: [[HU-025-consultar-mis-citas-y-detalle]], [[HU-020-consultar-agenda-de-citas-aprobadas]], [[HU-029-consultar-bandeja-administrativa]], [[HU-008-consultar-y-actualizar-perfil]]

## Esfuerzo

**Nivel:** Alto

**Justificación de dificultad:** Es una capacidad transversal que condiciona a todas las épicas posteriores. Exige decidir y montar dos mecanismos distintos —autorización declarativa por rol y verificación de propiedad sobre el recurso, esta última dependiente de consultar el dato antes de responder—, sin filtrar por el código de respuesta si el recurso existe. Añade la diferenciación entre no autenticado y no autorizado en toda la cadena de seguridad y su reflejo en la navegación y la interfaz del frontend. Un error aquí se propaga a cada funcionalidad construida encima.

## Tareas de desarrollo

- [ ] **T-01 — Definir el modelo de roles y permisos de operación**
  Dificultad: Medio
  Descripción: Enumerar en el dominio los roles del sistema y expresar qué familia de operaciones corresponde a cada uno, de forma que la regla sea consultable desde la aplicación sin depender del framework de seguridad.

- [ ] **T-02 — Expresar la regla de ownership en el dominio**
  Dificultad: Medio
  Descripción: Modelar qué significa que un recurso pertenece a un usuario y qué relación habilita a un PROFESSIONAL a ver datos de un paciente, es decir, la existencia de una cita propia que los vincule (RF-16).

- [ ] **T-03 — Configurar la autorización por rol en Spring Security**
  Dificultad: Alto
  Descripción: Establecer la política de denegación por defecto, declarar el rol requerido por familia de operaciones y asegurar que los roles del access token se traduzcan a autoridades reconocidas por la cadena de filtros.

- [ ] **T-04 — Implementar la verificación de ownership en la capa de aplicación**
  Dificultad: Alto
  Descripción: Componente reutilizable que, dado el usuario autenticado y el recurso solicitado, determina si existe propiedad o vínculo autorizado, y que los casos de uso invocan antes de devolver o modificar el recurso.

- [ ] **T-05 — Unificar el manejo de errores de autenticación y autorización**
  Dificultad: Medio
  Descripción: Manejadores que produzcan una respuesta de no autenticado cuando falta o es inválido el access token y una respuesta distinta de permiso insuficiente cuando el rol o la propiedad no alcanzan, ambas con cuerpo de error uniforme y sin detalles internos.

- [ ] **T-06 — Adaptar la navegación y la interfaz de citas-web a los roles**
  Dificultad: Medio
  Descripción: Restringir las rutas de la aplicación según el rol de la sesión, ocultar las acciones no permitidas y reaccionar de forma distinta a la respuesta de no autenticado (llevar al login) y a la de permiso insuficiente (informar sin cerrar la sesión).

- [ ] **T-07 — Verificar la ausencia de fugas de datos en las respuestas**
  Dificultad: Medio
  Descripción: Revisar que las consultas filtren por el usuario autenticado en origen y que la respuesta ante un recurso ajeno no permita deducir su existencia ni su contenido.

- [ ] **T-08 — Pruebas de autorización por rol y ownership**
  Dificultad: Alto
  Descripción: Pruebas de integración que recorran, para una operación representativa de cada rol, los casos de acceso permitido, acceso sin autenticación, acceso con rol insuficiente y acceso a un recurso de otro usuario, incluyendo el caso de PROFESSIONAL frente a un paciente sin cita propia.

## Criterios de aceptación

### CA-01 — Petición sin autenticación diferenciada de rol insuficiente

**Dado** una operación reservada a ADMIN
**Cuando** se invoca primero sin access token y después con el access token válido de un USER
**Entonces** la primera respuesta indica falta de autenticación y la segunda indica permiso insuficiente, son códigos distintos entre sí y en ninguno de los dos casos se ejecuta la operación.

### CA-02 — Solo ADMIN ejecuta operaciones administrativas

**Dado** una operación de gestión de catálogos configurables, de gestión de profesionales o de decisión administrativa sobre una solicitud
**Cuando** se invoca con el access token de un USER y con el de un PROFESSIONAL
**Entonces** ambas peticiones se rechazan por permiso insuficiente, y la misma operación invocada con el access token de un ADMIN se ejecuta.

### CA-03 — Solo PROFESSIONAL gestiona su agenda

**Dado** una operación de gestión de la agenda de un profesional
**Cuando** se invoca con el access token de un USER y con el de un ADMIN
**Entonces** ambas peticiones se rechazan por permiso insuficiente, y la misma operación invocada por el PROFESSIONAL propietario de esa agenda se ejecuta.

### CA-04 — Solo USER opera sus propias citas

**Dado** una operación sobre una cita de un paciente
**Cuando** se invoca con el access token de un PROFESSIONAL o de un ADMIN que no tienen atribuida esa operación
**Entonces** la petición se rechaza por permiso insuficiente, y la misma operación invocada por el USER titular de la cita se ejecuta.

### CA-05 — Un USER no accede a recursos de otro USER

**Dado** dos usuarios `USER` distintos, cada uno con recursos propios en el sistema
**Cuando** el primero invoca, con su access token válido, una lectura y luego una modificación de un recurso perteneciente al segundo
**Entonces** ambas peticiones se rechazan sin devolver ningún dato del recurso ajeno y sin modificarlo.

### CA-06 — Un PROFESSIONAL no ve datos de usuarios ajenos a sus citas

**Dado** un PROFESSIONAL y dos pacientes, uno con una cita asignada a ese profesional y otro sin ninguna cita con él
**Cuando** el profesional consulta datos de ambos pacientes
**Entonces** obtiene los datos del paciente vinculado por una cita propia y la consulta del paciente sin cita con él se rechaza sin devolver ningún dato (RF-16).

### CA-07 — Denegación por defecto

**Dado** un endpoint de la API que no declara explícitamente una regla de autorización
**Cuando** se invoca con un access token válido de cualquier rol
**Entonces** la petición se rechaza por permiso insuficiente en lugar de atenderse.

### CA-08 — La interfaz refleja el rol de la sesión

**Dado** usuarios autenticados en `citas-web` con rol USER, PROFESSIONAL y ADMIN respectivamente
**Cuando** cada uno navega por la aplicación
**Entonces** cada sesión solo muestra las rutas y las acciones correspondientes a su rol, y el acceso manual a una ruta de otro rol no presenta la vista.

### CA-09 — El frontend distingue sesión caducada de permiso insuficiente

**Dado** una sesión activa en `citas-web`
**Cuando** la API responde falta de autenticación en un caso y permiso insuficiente en otro
**Entonces** en el primero la aplicación lleva al usuario al login y en el segundo muestra un aviso de permiso insuficiente manteniendo la sesión abierta.

## Definition of Done

- [ ] Los criterios CA-01 a CA-09 están validados con evidencia concreta.
- [x] Esta HU no altera el esquema de datos y por tanto no incorpora migración Flyway; se apoya en las tablas de usuarios y roles creadas en [[HU-001-registrar-cuenta-de-usuario]].
- [x] La cadena de seguridad aplica denegación por defecto: ninguna ruta queda accesible por omisión de regla.
- [ ] Existe un componente reutilizable de verificación de ownership invocable desde cualquier caso de uso, documentado para que las HU funcionales posteriores lo usen en lugar de reimplementar la comprobación.
- [x] La regla de rol y la de ownership están expresadas en dominio y aplicación, sin que la lógica de propiedad quede únicamente en anotaciones del adaptador REST.
- [x] Las respuestas de no autenticado y de permiso insuficiente usan códigos distintos y un cuerpo de error uniforme que no expone detalles internos ni la existencia de recursos ajenos.
- [ ] La restricción de RF-16 sobre los datos de usuarios visibles al PROFESSIONAL está implementada y probada.
- [x] El control de rutas y acciones por rol en `citas-web` es coherente con las reglas aplicadas por `citas-api`: ninguna acción visible en el cliente es rechazada por rol en el servidor y ninguna acción oculta es ejecutable saltándose el cliente.
- [x] Existen pruebas automatizadas de acceso permitido, no autenticado, rol insuficiente y recurso de otro usuario, y pasan.
- [x] La trazabilidad de esta HU y de [[EP-001-identidad-y-acceso-seguro]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Ejecución de referencia del **2026-09-23**: backend `docker compose run --rm citas-api-dev mvn -B test` → **242 pruebas, 0 fallos** en 22 clases; frontend `npm test` en `citas-web` → **88 pruebas, 0 fallos**, con `typecheck`, `lint` y `build` en verde. La verificación fue independiente (`backend-verifier` y `frontend-verifier`, agentes que no escribieron el código): `EVIDENCIAS_S3.md` §10. Las clases de prueba del backend cuelgan de `citas-api/src/test/java/com/fcv/citas/`.

**La HU no se cierra.** Dos elementos no están respaldados y ambos dependen de trabajo fuera del alcance de S3: RF-16 (CA-06) y la mitad de escritura de CA-05.

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | `infrastructure/rest/AuthorizationIntegrationTest#anonymousGets401AndWrongRoleGets403OnAdminRoutes` | Sin token → 401; con token de USER sobre una ruta de ADMIN → 403 con `title: "Acceso denegado"`. Códigos distintos y en ninguno de los dos casos se ejecuta la operación |
| CA-02 | Cumple | `AuthorizationIntegrationTest#onlyAdminReachesAdminRoutes` (PROFESSIONAL → 403, ADMIN → 200); `infrastructure/rest/SpecialtyAdminIntegrationTest#nonAdminCannotWrite`; `infrastructure/rest/ProfessionalAdminIntegrationTest#onlyAdminCreates`; `infrastructure/rest/AdminDecisionIntegrationTest#onlyAdminDecides` | Las tres familias administrativas del criterio —catálogos, profesionales y decisión— tienen su propia prueba de rol |
| CA-03 | Cumple | `AuthorizationIntegrationTest#onlyProfessionalReachesProfessionalRoutes` (USER y ADMIN → 403); `infrastructure/rest/ScheduleIntegrationTest#blockExpandsIntoEightSlotsStoredWithTheSameLocalTimes` (el PROFESSIONAL titular → 201) y `#professionalCannotTouchAnotherProfessionalsBlock` (otro profesional → 404) | — |
| CA-04 | Cumple | `AuthorizationIntegrationTest#onlyUserReachesPatientRoutes` (PROFESSIONAL y ADMIN → 403, USER → 200); `infrastructure/rest/BookingIntegrationTest#onlyUsersBookAndAlwaysForThemselves` | — |
| CA-05 | No verificable | Lectura: `BookingIntegrationTest#patientSeesOnlyOwnAppointmentsWithDetail` (el detalle de una cita ajena responde 404 sin devolver ningún dato) y `#onlyUsersBookAndAlwaysForThemselves` (el `patientUserId` del cuerpo se ignora). Modificación: **sin evidencia** | La mitad de lectura está cubierta. La mitad de **modificación** no es ejercitable en S3: el paciente no tiene ninguna operación de escritura sobre una cita ya existente. Llega con [[HU-026-cancelar-una-cita-futura]] y [[HU-027-solicitar-reprogramacion-de-cita-aprobada]] (S4). Acción pendiente: al implementarlas, añadir la prueba de «un USER intenta modificar la cita de otro» |
| CA-06 | No cumple | No existe ningún endpoint por el que un PROFESSIONAL consulte datos de un paciente; `EVIDENCIAS_S3.md` §10 lo declara abierto a propósito | RF-16 se materializa con [[HU-020-consultar-agenda-de-citas-aprobadas]], fuera de S3. Hoy el calendario del profesional (`JdbcScheduleQueries`) **no** consulta `appointments` ni `users`, así que no hay fuga, pero tampoco hay regla de vínculo implementada ni probada. Acción pendiente: implementar y probar el vínculo «paciente con cita propia» al desarrollar HU-020 |
| CA-07 | Cumple | `AuthorizationIntegrationTest#undeclaredRoutesAreDeniedEvenWhenAuthenticated` (ruta no declarada con token de ADMIN → 403); `infrastructure/security/SecurityConfig` cierra la cadena con `anyRequest().denyAll()` | — |
| CA-08 | Cumple | `citas-web/src/roleNavigation.test.tsx`: «el USER entra a /paciente y solo ve la navegación de paciente», «el ADMIN entra a /admin con su panel y su navegación», «el PROFESSIONAL entra a /profesional y ve solo su navegación», «una ruta de otro rol muestra "Sin permiso" sin pedir datos ni cerrar la sesión» y «"/" lleva al inicio del rol» | `citas-web/src/auth/RequireRole.tsx` y `src/app/navigation.ts` |
| CA-09 | Cumple | `citas-web/src/roleNavigation.test.tsx`: «un 403 de la API muestra "Permiso insuficiente" y mantiene la sesión abierta» y «un 401 cuya renovación es rechazada lleva al login» | — |
| DoD — CA-01 a CA-09 validados con evidencia concreta | No cumple | CA-05 `No verificable` y CA-06 `No cumple` | Bloquea el cierre |
| DoD — No altera el esquema; se apoya en las tablas de [[HU-001-registrar-cuenta-de-usuario]] | Cumple | No hay migración asociada a esta HU; los roles salen de `users`, `roles` y `user_roles` de `V1__identity_and_fixed_catalogs.sql` | — |
| DoD — Denegación por defecto: ninguna ruta accesible por omisión | Cumple | `SecurityConfig` (`anyRequest().denyAll()`); `AuthorizationIntegrationTest#undeclaredRoutesAreDeniedEvenWhenAuthenticated` | — |
| DoD — Componente reutilizable de verificación de ownership, invocable desde cualquier caso de uso y documentado | No cumple | Cada caso de uso implementa su propia comprobación: `application/appointment/PatientAppointmentsUseCase#detail` filtra por `patient().id()`, `application/schedule/ManageScheduleUseCase#ownBlock` filtra por titular del bloque y `JdbcAppointmentQueries#findByPatient` filtra en SQL. Una búsqueda de `ownership` en `src/main` no devuelve ningún componente compartido | No hay divergencia observable hoy —las tres comprobaciones responden 404— pero tampoco existe la pieza reutilizable que la DoD exige, y cada HU nueva la reimplementa. Acción pendiente de desarrollo |
| DoD — Rol y ownership expresados en dominio y aplicación, no solo en anotaciones REST | Cumple | Ownership en la capa de aplicación (`PatientAppointmentsUseCase`, `ManageScheduleUseCase#ownBlock`) y en la consulta (`JdbcAppointmentQueries#findByPatient`); rol en `domain/user/Role`, traducido a autoridades por `JwtConfig` y aplicado por `SecurityConfig` | Ningún controlador decide la propiedad con una anotación |
| DoD — 401 y 403 con códigos distintos y cuerpo de error uniforme sin detalles internos | Cumple | `infrastructure/security/ProblemJsonSecurityHandlers` (punto de entrada y manejador de acceso denegado, ambos con `ProblemDetail`); `AuthorizationIntegrationTest#anonymousGets401AndWrongRoleGets403OnAdminRoutes`; `infrastructure/rest/error/GlobalExceptionHandler#unexpected` (solo el tipo de excepción al log, cuerpo genérico) | Las citas ajenas responden 404 y no 403, para no revelar su existencia |
| DoD — La restricción de RF-16 está implementada y probada | No cumple | Fila CA-06 | Bloquea el cierre |
| DoD — El control por rol de `citas-web` es coherente con el de `citas-api` | Cumple | `citas-web/src/roleNavigation.test.tsx` (5 pruebas de navegación y 2 de 401/403); `EVIDENCIAS_S3.md` §8, comprobación «USER en ruta ADMIN → 403» contra el backend real; `citas-web/src/api/contracts.ts` contrastado con los `@*Mapping` del backend | La prueba de humo recorre las mismas rutas que usa el cliente |
| DoD — Pruebas de acceso permitido, no autenticado, rol insuficiente y recurso ajeno | Cumple | `AuthorizationIntegrationTest` (9 pruebas); `BookingIntegrationTest#patientSeesOnlyOwnAppointmentsWithDetail`; `ScheduleIntegrationTest#professionalCannotTouchAnotherProfessionalsBlock` | Falta el caso «PROFESSIONAL frente a un paciente sin cita propia», que depende de CA-06 |
| DoD — Trazabilidad de esta HU y de [[EP-001-identidad-y-acceso-seguro]] actualizada | Cumple | Esta tabla y el historial de validación | — |

## Historial de validación

- 2026-09-23 — Estado `En validación` (fase F11 de `PLAN_RETOMA_S3.md`): matriz de evidencia registrada. 7 de 9 criterios en `Cumple`; **no se cierra**. Falta: (1) CA-06 / RF-16, el vínculo que permite a un PROFESSIONAL ver datos de sus propios pacientes, que no existe hasta [[HU-020-consultar-agenda-de-citas-aprobadas]]; (2) la mitad de escritura de CA-05, no ejercitable hasta que el paciente tenga operaciones sobre citas existentes ([[HU-026-cancelar-una-cita-futura]], [[HU-027-solicitar-reprogramacion-de-cita-aprobada]]); (3) el componente reutilizable de ownership que pide la DoD, hoy reimplementado caso de uso por caso de uso.
- 2026-09-18 — Estado `En desarrollo` (skill `scrum-spec-orchestrator`, paso 9): se inicia la implementación en la fase F2 de `PLAN_RETOMA_S3.md`.
- 2026-09-18 — Estado `Aprobada` por **aprobación delegada** de S3: el usuario pidió continuar S3 dejando las decisiones de diseño a criterio del agente (`AGENTS.md` §6). Alcance y decisiones D5–D13 en `PLAN_RETOMA_S3.md` y [[dec-004-decisiones-s3-reserva]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada y dejada en estado `Pendiente de aprobación` como candidata al alcance de S2.

## Notas y decisiones

- Esta HU describe el mecanismo transversal de autorización, no la protección de endpoints concretos. Cada HU funcional declara en su propia DoD que aplica este mecanismo sobre sus operaciones.
- Incógnita abierta: el PRD no define si un ADMIN puede consultar los datos personales completos de los pacientes o solo los necesarios para decidir sobre una solicitud (RF-12, RF-18). Los criterios de esta HU no atribuyen a ADMIN acceso a datos de perfil de paciente hasta que exista decisión humana.
- Incógnita abierta: el PRD no aclara si un ADMIN puede cancelar o reprogramar una cita en nombre de un USER. CA-04 se limita a comprobar que la operación del paciente exige ser su titular, sin cerrar la puerta a una atribución administrativa futura.
- Incógnita abierta **INC-004** (ver [[EP-001-identidad-y-acceso-seguro]]): al asumirse que la primera cuenta ADMIN se precarga por seed, las pruebas de rol administrativo dependen de esa carga inicial.
- El PRD no fija los códigos de respuesta concretos; los criterios solo exigen que no autenticado y permiso insuficiente sean distinguibles entre sí y coherentes en toda la API. La elección concreta debe quedar documentada en [[HU-033-publicar-contrato-rest-documentado]].
