---
id: HU-033
tipo: historia-de-usuario
titulo: "Publicar el contrato REST documentado"
estado: En desarrollo
epica: "[[EP-009-trazabilidad-y-contrato-rest]]"
requisitos: [RF-20]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 1"
dependencias: []
relacionadas:
  - "[[HU-001-registrar-cuenta-de-usuario]]"
  - "[[HU-002-iniciar-sesion-con-jwt]]"
  - "[[HU-005-autorizar-peticiones-por-rol-y-ownership]]"
  - "[[HU-032-auditar-cambios-de-estado-de-cita]]"
---

# HU-033 — Publicar el contrato REST documentado

## Historia de usuario

**COMO** desarrollador de `citas-web` que consume `citas-api`  
**QUIERO** disponer de un contrato REST documentado con endpoints, formatos, códigos de respuesta y formato de error uniforme, y de una URL de backend configurable por entorno  
**PARA** integrar el frontend directamente con la API sin capa intermedia y sin adivinar cómo responde cada operación

> Como desarrollador de `citas-web` que consume `citas-api`, quiero disponer de un contrato REST documentado con endpoints, formatos, códigos de respuesta y formato de error uniforme, y de una URL de backend configurable por entorno, para integrar el frontend directamente con la API sin capa intermedia y sin adivinar cómo responde cada operación.

## Contexto y descripción

RF-20 establece que el frontend consume directamente una API REST de Spring Boot, sin Express ni BFF, y que el contrato debe diseñarse y documentarse durante el proyecto. Las restricciones técnicas añaden que la URL del backend es configurable por entorno, y PRD §8 exige CORS explícito y que no se registren contraseñas ni tokens.

Esta HU se inaugura en el Sprint 1 porque todas las demás la referencian en su DoD ("el contrato del endpoint está reflejado en la documentación de HU-033") y porque varias decisiones transversales deben existir antes del primer endpoint: el formato de error uniforme, la distinción entre no autenticado y sin permiso que pide [[HU-005-autorizar-peticiones-por-rol-y-ownership]], el uso de 409 para conflictos de estado y de doble reserva, y el tratamiento de campos no editables que menciona [[HU-008-consultar-y-actualizar-perfil]]. Es un artefacto vivo: se crea con los endpoints de identidad y crece en cada sprint.

El repositorio ya contiene piezas de base: `app.cors.allowed-origins` en `application.yml` de `citas-api` leído de `FRONTEND_ORIGIN`, y `VITE_API_URL` en el `.env.example` de `citas-web`.

## Alcance

- Documento de contrato REST ubicado en `citas-api/docs/wiki/` y enlazado desde la wiki, con convenciones generales y un apartado por recurso.
- Convenciones: prefijo y estructura de rutas, JSON como formato, formato de fechas y horas, autenticación mediante access token, paginación y filtros cuando apliquen.
- Formato de error uniforme para toda la API, con los casos de validación, no autenticado, sin permiso, no encontrado, conflicto (409) y error interno.
- Tabla de códigos de respuesta de uso común, incluida la distinción no autenticado / sin permiso.
- Contrato de los endpoints de identidad del Sprint 1 ([[HU-001-registrar-cuenta-de-usuario]] a [[HU-005-autorizar-peticiones-por-rol-y-ownership]]).
- Manejador global de errores en `citas-api` que produce el formato de error uniforme.
- CORS explícito por orígenes configurados por entorno, sin comodín.
- Cliente HTTP de `citas-web` que lee la URL base de `VITE_API_URL` e interpreta el formato de error uniforme.
- Regla de mantenimiento: toda HU que publique o cambie un endpoint actualiza este contrato como parte de su DoD.

## Fuera de alcance

- Contratos de los endpoints de sprints posteriores: los añade cada HU funcional al publicarlos.
- Express, BFF o proxy de aplicación: prohibidos (RF-20).
- CI/CD de validación del contrato (PRD §9).
- Política de versionado del contrato entre repositorios: pendiente de INC-039.

## Reglas de negocio

- El frontend consume directamente la API REST de Spring Boot; no existe Express ni BFF (RF-20).
- El contrato se diseña y se documenta durante el proyecto (RF-20).
- La URL del backend es configurable por entorno y no está escrita en el código (restricciones técnicas).
- CORS es explícito, con orígenes concretos y sin comodín (PRD §8).
- Ninguna respuesta, ejemplo de contrato ni log expone contraseñas ni tokens más allá de la emisión legítima en login y refresh (PRD §8).
- La validación es server-side; el contrato documenta los errores de validación que el servidor devuelve (PRD §8).

## Dependencias y relaciones

- Épica: [[EP-009-trazabilidad-y-contrato-rest]]
- Dependencias: ninguna
- Relacionadas: [[HU-001-registrar-cuenta-de-usuario]], [[HU-002-iniciar-sesion-con-jwt]], [[HU-003-renovar-sesion-con-refresh-token]], [[HU-004-cerrar-sesion-revocando-refresh-token]], [[HU-005-autorizar-peticiones-por-rol-y-ownership]], [[HU-008-consultar-y-actualizar-perfil]], [[HU-022-buscar-disponibilidad-con-filtros]], [[HU-023-agendar-cita-de-medicina-general]], [[HU-032-auditar-cambios-de-estado-de-cita]]. Todas las HU que publican endpoints enlazan a esta en su DoD.

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** El volumen inicial es acotado (convenciones y endpoints de identidad), pero fija decisiones transversales que condicionan a todas las HU posteriores y a ambos repositorios: formato de error, códigos, CORS y cliente HTTP. Un error de diseño aquí se propaga a cada endpoint, y dos incógnitas abiertas (formato de documentación y formato de error) deben resolverse para empezar.

## Tareas de desarrollo

- [ ] **T-01 — Redactar las convenciones generales del contrato**  
  Dificultad: Medio  
  Descripción: Estructura de rutas, formato JSON, formato de fechas y horas, cabecera de autenticación, paginación y filtros, y regla de mantenimiento del documento.

- [ ] **T-02 — Diseñar el formato de error uniforme y la tabla de códigos**  
  Dificultad: Medio  
  Descripción: Estructura del cuerpo de error con código, mensaje y detalle de campos, y correspondencia con validación, no autenticado, sin permiso, no encontrado, conflicto 409 (transición inválida, doble reserva, duplicado) y error interno.

- [ ] **T-03 — Implementar el manejador global de errores en citas-api**  
  Dificultad: Medio  
  Descripción: Adaptador de infraestructura que traduce excepciones de validación, de seguridad, de dominio y de persistencia al formato uniforme, sin filtrar trazas internas ni datos sensibles.

- [ ] **T-04 — Verificar la configuración CORS explícita**  
  Dificultad: Bajo  
  Descripción: Configuración de Spring Security que aplica `app.cors.allowed-origins` desde el entorno, sin comodín, con los métodos y cabeceras que el contrato usa.

- [ ] **T-05 — Documentar los endpoints de identidad del Sprint 1**  
  Dificultad: Medio  
  Descripción: Petición, respuesta, códigos y ejemplos sintéticos de registro, login, refresh, logout y respuesta de autorización denegada.

- [ ] **T-06 — Implementar el cliente HTTP de citas-web**  
  Dificultad: Medio  
  Descripción: Módulo de acceso a la API que lee `VITE_API_URL`, adjunta el access token e interpreta el formato de error uniforme para las pantallas.

- [ ] **T-07 — Pruebas del contrato transversal**  
  Dificultad: Medio  
  Descripción: Pruebas de integración REST que verifican el formato de error para validación, no autenticado, sin permiso, no encontrado y conflicto; prueba de CORS con origen permitido y no permitido; y typecheck/build de `citas-web` con la URL de entorno.

## Criterios de aceptación

### CA-01 — El contrato existe y es navegable

**Dado** el repositorio `citas-api`  
**Cuando** se consulta la documentación del contrato REST desde `citas-api/docs/wiki/`  
**Entonces** existe un documento con las convenciones generales, el formato de error, la tabla de códigos y los endpoints de identidad del Sprint 1, cada uno con método, ruta, autenticación requerida, cuerpo de petición, respuesta de éxito y respuestas de error.

### CA-02 — Formato de error uniforme

**Dado** peticiones que provocan un error de validación, falta de autenticación, permiso insuficiente, recurso no encontrado y conflicto  
**Cuando** la API responde a cada una  
**Entonces** todas las respuestas usan la misma estructura de cuerpo documentada, con códigos HTTP distintos y coherentes con la tabla del contrato.

### CA-03 — Conflictos documentados como 409

**Dado** el contrato publicado  
**Cuando** se consulta la tabla de códigos  
**Entonces** documenta 409 para transiciones de estado inválidas, doble reserva de slots y duplicados de datos únicos, y cualquier endpoint que produzca esos casos responde 409.

### CA-04 — Sin información sensible en errores

**Dado** una excepción interna no controlada o un error de login  
**Cuando** la API responde  
**Entonces** el cuerpo no contiene trazas de pila, sentencias SQL, contraseñas, hashes ni tokens, y el log no registra contraseñas ni tokens.

### CA-05 — URL del backend configurable por entorno

**Dado** `citas-web` construido con `VITE_API_URL` apuntando a dos URLs distintas  
**Cuando** se inspeccionan las peticiones emitidas por la aplicación  
**Entonces** cada construcción usa la URL configurada y el código fuente no contiene la URL del backend escrita de forma fija.

### CA-06 — CORS explícito

**Dado** `citas-api` configurado con un origen permitido  
**Cuando** recibe una petición con preflight desde ese origen y otra desde un origen no configurado  
**Entonces** la primera recibe las cabeceras CORS de permiso para ese origen concreto y la segunda no; en ningún caso se responde con un comodín.

### CA-07 — Consumo directo sin capa intermedia

**Dado** los repositorios `citas-web` y `citas-api`  
**Cuando** se inspeccionan dependencias y código de `citas-web`  
**Entonces** no existe Express, BFF ni proxy de aplicación, y las pantallas llaman a la API REST a través del cliente HTTP común.

### CA-08 — El contrato se mantiene con cada endpoint

**Dado** una HU que publica o modifica un endpoint y pasa a `Completada`  
**Cuando** se compara su endpoint implementado con el contrato  
**Entonces** el contrato refleja su ruta, petición, respuesta y errores, y la discrepancia bloquea el cierre de esa HU.

## Definition of Done

- [x] Los criterios CA-01 a CA-08 están validados con evidencia concreta.
- [ ] El formato de documentación del contrato (INC-038) y el formato de error (INC-040) están decididos por el usuario del proyecto y registrados en la documentación.
- [x] El manejador global de errores de `citas-api` produce el formato documentado para todos los tipos de error, demostrado con pruebas de integración.
- [x] CORS se configura por entorno sin comodín y está cubierto por una prueba.
- [x] `citas-web` lee la URL base de `VITE_API_URL`, el `.env.example` no contiene secretos y el typecheck/build pasa.
- [x] Los ejemplos del contrato usan exclusivamente datos sintéticos y no contienen credenciales reales.
- [x] Los endpoints de identidad del Sprint 1 están documentados y coinciden con su implementación.
- [x] La trazabilidad de esta HU y de [[EP-009-trazabilidad-y-contrato-rest]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Ejecución de referencia del **2026-09-23**: backend `docker compose run --rm citas-api-dev mvn -B test` → **242 pruebas, 0 fallos** en 22 clases; frontend `npm test` en `citas-web` → **88 pruebas, 0 fallos**, con `typecheck`, `lint` y `build` en verde. Las clases de prueba del backend cuelgan de `citas-api/src/test/java/com/fcv/citas/`.

**Esta HU no se cierra, y no por un defecto: el contrato es un artefacto vivo por diseño.** CA-08 obliga a reflejar en él cada endpoint nuevo, así que la HU solo podría cerrarse cuando el producto deje de crecer. La matriz de abajo es el **corte de S3**.

**Qué cubre hoy el contrato.** Dos documentos enlazados desde `wiki/index.md`:

- [[contrato-rest-identidad]] — Sprint 1: `POST /api/auth/register`, `/login`, `/refresh`, `/logout`, `GET /api/me` y `GET /api/catalogs/insurance-plans` (añadido el 2026-09-23 con el primer corte de [[HU-009-registrar-afiliacion-a-eps-y-plan]]). Incluye convenciones, formato de error, tabla de códigos, CORS y rutas públicas.
- [[contrato-rest-citas]] — S3: catálogos (HU-010), especialidades (HU-011), profesionales (HU-013 a HU-016), agenda (HU-017 a HU-019), reserva y consulta del paciente (HU-022 a HU-025) y operación del ADMIN (HU-029, HU-030, HU-032). Con tipos compartidos, tabla de códigos de error ampliada y las reglas de ownership y de duración.

**Qué falta, y por eso sigue en `En desarrollo`.** Los endpoints que S3 no construyó: recuperación de contraseña (RF-03, [[HU-006-solicitar-recuperacion-de-contrasena]] y [[HU-007-restablecer-contrasena-con-token]]) —el frontend ya tiene la ruta `/api/auth/password-recovery` en `contracts.ts` y el backend no la publica, única discrepancia conocida entre los dos repos—, perfil ([[HU-008-consultar-y-actualizar-perfil]]) y la parte de consulta y edición de la afiliación (HU-009), CRUD de EPS y planes ([[HU-012-gestionar-eps-y-planes]]), agenda de citas aprobadas y cierre de atención ([[HU-020-consultar-agenda-de-citas-aprobadas]], [[HU-021-registrar-cierre-de-atencion]]) y cancelación y reprogramación ([[HU-026-cancelar-una-cita-futura]] a [[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]], [[HU-031-aprobar-o-rechazar-reprogramacion]]). Además, el ítem de DoD sobre INC-038 e INC-040 sigue abierto: el formato del contrato y el formato de error los decidió el agente bajo aprobación delegada, no el usuario.

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | [[contrato-rest-identidad]] y [[contrato-rest-citas]], ambos en `citas-api/docs/wiki/llm-wiki/wiki/` y enlazados desde `wiki/index.md` | Convenciones, formato de error, tabla de códigos y, para cada endpoint, método, ruta, autenticación, cuerpo, respuesta de éxito y respuestas de error. El de identidad cubre el Sprint 1 que pedía el criterio; el de S3 añade los 30 endpoints restantes |
| CA-02 | Cumple | `infrastructure/rest/AuthFlowIntegrationTest` (400 con `fieldErrors`, 401, 409); `infrastructure/rest/AuthorizationIntegrationTest#anonymousGets401AndWrongRoleGets403OnAdminRoutes` (401 y 403 con `title`); `infrastructure/rest/BookingIntegrationTest#patientSeesOnlyOwnAppointmentsWithDetail` (404); `#doubleBookingIsRejectedWith409` (409 con `code`); `infrastructure/rest/error/GlobalExceptionHandler` e `infrastructure/security/ProblemJsonSecurityHandlers` | Los cinco tipos de error del criterio, todos con la misma estructura `ProblemDetail` (RFC 9457) y códigos HTTP coherentes con la tabla del contrato |
| CA-03 | Cumple | Tabla de códigos de [[contrato-rest-citas]] (fila 409: `DUPLICATE`, `SLOT_TAKEN`, `BLOCK_OVERLAP`, `BLOCK_HAS_APPOINTMENTS`, `INVALID_TRANSITION`, `APPOINTMENT_EXPIRED`, `SPECIALTY_REFERENCED`, `PROTECTED_SPECIALTY`, `CONCURRENT_CHANGE`); pruebas: `BookingIntegrationTest#doubleBookingIsRejectedWith409` (doble reserva), `infrastructure/rest/AdminDecisionIntegrationTest#decidingANonRequestedAppointmentIs409` (transición inválida), `ProfessionalAdminIntegrationTest#duplicateCodeIsRejectedAtomically` (duplicado) | Los tres supuestos del criterio están documentados **y** probados |
| CA-04 | Cumple | `GlobalExceptionHandler#unexpected` (registra solo el tipo de excepción y devuelve un `detail` genérico); `application.yml` con `server.error.include-message: never`; `AuthFlowIntegrationTest#malformedJsonReturnsTheSame400ProblemWithoutParserDetails` y las comprobaciones con `CapturedOutput` del registro y del login; `infrastructure/security/JwtSecretValidationTest#startupFailureDoesNotEchoTheSecret` | Ningún cuerpo lleva traza de pila, SQL, hash ni token; los logs tampoco |
| CA-05 | Cumple | `citas-web/src/api/contracts.ts#resolveApiBaseUrl` lee `import.meta.env.VITE_API_URL` y **falla al cargar** si falta, sin valor por defecto; `citas-web/src/api/contracts.test.ts`: «toma VITE_API_URL y le quita la barra final» y «falla al cargar si VITE_API_URL está vacía, en vez de usar un valor por defecto» | Se corrigió el defecto que esta misma matriz registraba el 2026-09-17: ya no queda el literal `http://localhost:8080` en el código |
| CA-06 | Cumple | `AuthFlowIntegrationTest#corsPreflightAllowsFrontendOrigin`; `infrastructure/config/CorsConfig` sobre `CorsProperties` (`app.cors.allowed-origins`, sin comodín); `EVIDENCIAS_S3.md` §8 («preflight CORS desde el origen del frontend» contra el backend real) | El origen llega por entorno (`FRONTEND_ORIGIN`) |
| CA-07 | Cumple | `citas-web/package.json`: en `dependencies` solo `react`, `react-dom` y `react-router`; ningún Express, BFF ni proxy de aplicación; todas las pantallas pasan por `citas-web/src/api/httpClient.ts`, con `citas-web/src/api/httpClient.test.ts` | Consumo REST directo contra `citas-api`, como exigen las restricciones técnicas |
| CA-08 | Cumple | `EVIDENCIAS_S3.md` §8: `citas-web/src/api/contracts.ts` se contrastó ruta por ruta con los `@*Mapping` del backend y coinciden todas; la regla de mantenimiento está escrita en la sección «Reglas e invariantes» del contrato de identidad; en este cierre de S3, cada HU marcada `Completada` cita la sección del contrato que refleja sus endpoints | **Criterio de mantenimiento continuo, verificado al corte de S3.** La única discrepancia conocida es `/api/auth/password-recovery`, que el frontend declara y el backend no publica: pertenece a HU-006 (S4) y no bloquea ninguna HU cerrada aquí |
| DoD — CA-01 a CA-08 validados con evidencia concreta | Cumple | Filas CA-01 a CA-08 de esta tabla | Al corte de S3 |
| DoD — INC-038 (formato del contrato) e INC-040 (formato de error) decididos por el usuario y registrados | No cumple | Las dos decisiones existen y están registradas —Markdown mantenido a mano y `ProblemDetail` (RFC 9457), en `contrato-rest-identidad`— pero **las tomó el agente bajo aprobación delegada**, no el usuario | **Acción pendiente del usuario:** confirmar explícitamente ambas, o pedir el cambio (por ejemplo OpenAPI generado, que hoy no tiene dependencia en `pom.xml`). Es el único ítem de DoD abierto |
| DoD — El manejador global produce el formato documentado para todos los tipos de error, con pruebas | Cumple | `GlobalExceptionHandler` (14 manejadores, todos devuelven `ProblemDetail`); `ProblemJsonSecurityHandlers` para 401 y 403; fila CA-02 | — |
| DoD — CORS por entorno sin comodín y cubierto por prueba | Cumple | Fila CA-06 | — |
| DoD — `citas-web` lee la URL de `VITE_API_URL`, `.env.example` sin secretos y typecheck/build pasan | Cumple | Fila CA-05; `citas-web/.env.example` (solo marcadores, `.env` en `.gitignore`); `npm run typecheck`, `lint` y `build` en verde el 2026-09-23 | El escaneo de secretos del hook `pre-commit` (`EVIDENCIAS_S3.md` §1 a §3) es la red que lo mantiene |
| DoD — Los ejemplos del contrato usan datos sintéticos, sin credenciales reales | Cumple | Revisión de [[contrato-rest-identidad]] y [[contrato-rest-citas]]: correos `@example.com`, documentos y códigos inventados, contraseñas como marcadores; las dos sedes son la única información pública del PRD §3 | — |
| DoD — Los endpoints de identidad del Sprint 1 están documentados y coinciden con su implementación | Cumple | [[contrato-rest-identidad]], contrastado con `infrastructure/rest/auth/AuthController`, `infrastructure/rest/me/MeController` y `infrastructure/rest/catalog/CatalogController#insurancePlans`; `AuthFlowIntegrationTest` (35 pruebas) y `RegistrationAffiliationIntegrationTest` (12) | — |
| DoD — Trazabilidad de esta HU y de [[EP-009-trazabilidad-y-contrato-rest]] actualizada | Cumple | Esta tabla y el historial de validación | — |

## Historial de validación

- 2026-09-25 — **Se mantiene `En desarrollo`** (abierta por diseño). Se retoma en S4 con su corte en la fase **F9** (`PLAN_RETOMA_S4.md` §3 bloque «Contrato» y §4): `contrato-rest-citas.md` y `contrato-rest-identidad.md` con todos los endpoints nuevos de S4 (recuperación, perfil y afiliación, EPS y planes, agenda y cierre, cancelación y reprogramación), errores y `WWW-Authenticate` en español, y `API_ROUTES` sin rutas supuestas. La matriz se rehace con la evidencia del corte en **F10**.
- 2026-09-23 — **Se mantiene `En desarrollo` a propósito** (fase F11 de `PLAN_RETOMA_S3.md`). La matriz se rehace con la evidencia del **corte de S3**: los 8 criterios en `Cumple` y 7 de 8 ítems de DoD también. No se cierra por dos razones. **Primera:** el contrato es un artefacto vivo; CA-08 obliga a reflejar cada endpoint nuevo, así que la HU acompaña al producto hasta que deje de crecer. Hoy cubre identidad (Sprint 1, más `GET /api/catalogs/insurance-plans` de HU-009) y todo S3: catálogos, especialidades, profesionales, agenda, búsqueda, reserva general y especializada, consulta del paciente, bandeja, decisión administrativa e historial. Le falta lo que S3 no construyó: recuperación de contraseña (RF-03), perfil y el resto de la afiliación, CRUD de EPS y planes, agenda de citas aprobadas, cierre de atención, cancelación y reprogramación. **Segunda:** el ítem de DoD de INC-038 e INC-040 sigue en `No cumple` —Markdown a mano y `ProblemDetail` los decidió el agente bajo aprobación delegada, no el usuario—, y conviene confirmarlo explícitamente. Se corrige de paso la observación de CA-05 del 2026-09-17: `contracts.ts` ya no conserva ningún valor por defecto de la URL del backend.
- 2026-09-18 — Estado `En desarrollo` (skill `scrum-spec-orchestrator`, paso 9): se inicia la implementación en la fase F6 de `PLAN_RETOMA_S3.md`.
- 2026-09-18 — Estado `Aprobada` por **aprobación delegada** de S3: el usuario pidió continuar S3 dejando las decisiones de diseño a criterio del agente (`AGENTS.md` §6). Alcance y decisiones D5–D13 en `PLAN_RETOMA_S3.md` y [[dec-004-decisiones-s3-reserva]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- Incógnita abierta **INC-038** (ver [[EP-009-trazabilidad-y-contrato-rest]]): no está definido si el contrato se documenta con OpenAPI generado, con markdown mantenido a mano o con ambos. El `pom.xml` actual no incluye ninguna librería de generación OpenAPI; añadirla sería una decisión técnica nueva. CA-01 exige el contenido, no la herramienta.
- Incógnita abierta **INC-040** (ver [[EP-009-trazabilidad-y-contrato-rest]]): el formato de error no está definido. Spring Boot 3.5 ofrece soporte nativo del formato Problem Details (RFC 9457), que es una opción natural, pero la decisión corresponde al usuario del proyecto.
- Incógnita abierta **INC-039** (ver [[EP-009-trazabilidad-y-contrato-rest]]): sin política de versionado entre repositorios, CA-08 obliga al menos a mantener contrato e implementación sincronizados en cada HU.
- CA-08 es un criterio de mantenimiento continuo: esta HU puede completarse al cerrar el Sprint 1 con los endpoints de identidad, y la regla sigue aplicándose en la DoD de cada HU posterior.
- La ubicación exacta del documento dentro de `citas-api/docs/wiki/` (por ejemplo junto a `llm-wiki/`) no está fijada por las restricciones técnicas, que solo indican que los contratos pueden enlazarse desde la wiki.
