---
id: HU-002
tipo: historia-de-usuario
titulo: "Iniciar sesión con JWT"
estado: Completada
epica: "[[EP-001-identidad-y-acceso-seguro]]"
requisitos: [RF-02]
esfuerzo: "Alto"
sprint_sugerido: "Sprint 1"
dependencias:
  - "[[HU-001-registrar-cuenta-de-usuario]]"
relacionadas:
  - "[[HU-003-renovar-sesion-con-refresh-token]]"
  - "[[HU-004-cerrar-sesion-revocando-refresh-token]]"
  - "[[HU-005-autorizar-peticiones-por-rol-y-ownership]]"
---

# HU-002 — Iniciar sesión con JWT

## Historia de usuario

**COMO** usuario registrado del sistema
**QUIERO** autenticarme con mi email y mi contraseña y recibir un access token y un refresh token
**PARA** acceder a las funciones que me corresponden según mi rol

> Como usuario registrado del sistema, quiero autenticarme con mi email y mi contraseña y recibir un access token y un refresh token para acceder a las funciones que me corresponden según mi rol.

## Contexto y descripción

RF-02 exige login por email y contraseña, emisión de un access token de corta duración y un refresh token separado, y que los roles formen parte del contexto de autorización. Es la HU que habilita cualquier operación autenticada del producto para los tres actores.

Pertenece al alcance de la sesión S2.

## Alcance

- Pantalla de login en `citas-web` con email y contraseña.
- Endpoint público de autenticación en `citas-api`.
- Verificación de la contraseña contra el hash almacenado.
- Emisión de un access token JWT de corta duración que incluye la identidad y los roles del usuario.
- Emisión de un refresh token con vida más larga, persistido únicamente como hash.
- Rechazo del login de cuentas inactivas.
- Almacenamiento del access token en el cliente y envío en las peticiones posteriores.

## Fuera de alcance

- Renovación del access token, que se cubre en [[HU-003-renovar-sesion-con-refresh-token]].
- Cierre de sesión y revocación, que se cubre en [[HU-004-cerrar-sesion-revocando-refresh-token]].
- Protección de endpoints por rol, que se cubre en [[HU-005-autorizar-peticiones-por-rol-y-ownership]].
- Doble factor y bloqueo por intentos fallidos: no están en el PRD.

## Reglas de negocio

- El login se realiza con email y contraseña (RF-02).
- Access token y refresh token son tokens separados con vidas distintas (RF-02, PRD §8).
- Los roles del usuario forman parte del contexto de autorización transportado por el access token (RF-02).
- El refresh token se persiste solo como hash, nunca en claro.
- El secreto de firma JWT proviene de variable de entorno y nunca del repositorio (PRD §8).
- Ni la contraseña ni los tokens se escriben en logs (PRD §8).
- Un usuario inactivo no puede iniciar sesión.

## Dependencias y relaciones

- Épica: [[EP-001-identidad-y-acceso-seguro]]
- Dependencias: [[HU-001-registrar-cuenta-de-usuario]]
- Relacionadas: [[HU-003-renovar-sesion-con-refresh-token]], [[HU-004-cerrar-sesion-revocando-refresh-token]], [[HU-005-autorizar-peticiones-por-rol-y-ownership]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Alto

**Justificación de dificultad:** Introduce la cadena de filtros de Spring Security, la generación y firma de dos tipos de token, una nueva tabla persistida con hash, la gestión de sesión en el cliente y la configuración por entorno del secreto. Es un cambio transversal entre capas y repositorios con reglas de seguridad que no admiten aproximaciones.

## Tareas de desarrollo

- [ ] **T-01 — Definir los puertos de autenticación en el dominio y la aplicación**
  Dificultad: Medio
  Descripción: Caso de uso de autenticación que recibe credenciales, delega la verificación del hash y la emisión de tokens a puertos, y devuelve el par de tokens junto con la identidad y los roles.

- [ ] **T-02 — Crear la migración Flyway de refresh tokens**
  Dificultad: Bajo
  Descripción: Tabla de refresh tokens con referencia al usuario, hash del token, fecha de expiración y marca de revocación, más la familia, la marca de consumo y el enlace al reemplazo que necesita la rotación de [[HU-003-renovar-sesion-con-refresh-token]] ([[dec-002-rotacion-refresh-tokens]]).

- [ ] **T-03 — Implementar el adaptador de emisión y firma de JWT**
  Dificultad: Alto
  Descripción: Componente que emite el access token con identidad y roles como reclamaciones, y el refresh token, ambos firmados con el secreto leído de variable de entorno y con vidas configurables.

- [ ] **T-04 — Implementar el filtro de autenticación de Spring Security**
  Dificultad: Alto
  Descripción: Filtro que valida el access token de cada petición, reconstruye el contexto de seguridad con los roles y rechaza tokens ausentes, malformados, con firma inválida o expirados.

- [ ] **T-05 — Exponer el adaptador REST de login**
  Dificultad: Medio
  Descripción: Controlador público de autenticación con DTO validado, respuesta con el par de tokens, y respuesta de error uniforme e indistinguible para credenciales incorrectas.

- [ ] **T-06 — Construir la pantalla de login y la gestión de sesión en citas-web**
  Dificultad: Medio
  Descripción: Formulario de login, almacenamiento del par de tokens, envío automático del access token en cada petición, redirección según rol y estado de sesión compartido en la aplicación.

- [ ] **T-07 — Pruebas de autenticación**
  Dificultad: Medio
  Descripción: Pruebas del caso de uso y de integración REST para login correcto, credenciales inválidas, usuario inactivo, y acceso a un endpoint protegido con y sin access token.

## Criterios de aceptación

### CA-01 — Login correcto devuelve el par de tokens

**Dado** un usuario registrado y activo con email y contraseña conocidos
**Cuando** envía sus credenciales correctas al endpoint de login
**Entonces** la API responde con éxito devolviendo un access token y un refresh token distintos entre sí, y la respuesta no incluye la contraseña ni su hash.

### CA-02 — El access token transporta identidad y roles

**Dado** un login correcto de un usuario con rol `USER`
**Cuando** se decodifica el access token emitido
**Entonces** contiene el identificador del usuario y la lista de sus roles, y su tiempo de expiración es estrictamente menor que el del refresh token.

### CA-03 — Credenciales incorrectas rechazadas sin revelar la causa

**Dado** un email inexistente en un caso y un email existente con contraseña incorrecta en otro
**Cuando** se intenta iniciar sesión en ambos casos
**Entonces** la API responde en los dos con el mismo código de no autorizado y el mismo mensaje, sin indicar cuál de los dos datos era incorrecto y sin emitir ningún token.

### CA-04 — Usuario inactivo no puede iniciar sesión

**Dado** un usuario cuya cuenta está marcada como inactiva
**Cuando** envía sus credenciales correctas
**Entonces** la API responde con un error de autenticación y no emite ningún token.

### CA-05 — Refresh token persistido solo como hash

**Dado** un login correcto
**Cuando** se consulta el registro del refresh token en la base de datos
**Entonces** el valor almacenado no coincide con el refresh token entregado al cliente y corresponde a su hash.

### CA-06 — Endpoint protegido exige access token válido

**Dado** un endpoint de la API que requiere autenticación
**Cuando** se invoca sin cabecera de autorización, con un token malformado y con un token expirado
**Entonces** la API responde en los tres casos con un código de no autorizado y no ejecuta la operación.

### CA-07 — El secreto de firma proviene del entorno

**Dado** el repositorio `citas-api`
**Cuando** se busca el secreto de firma JWT en el código y en los archivos versionados
**Entonces** no aparece ningún valor real, la configuración lo lee de una variable de entorno y `.env.example` documenta la variable sin valor sensible.

### CA-08 — Ausencia de tokens y contraseñas en logs

**Dado** un login correcto y un login fallido
**Cuando** se inspecciona la salida de log de la aplicación para ambas peticiones
**Entonces** no aparece la contraseña ni ninguno de los dos tokens emitidos.

### CA-09 — Sesión utilizable desde el frontend

**Dado** un usuario que inicia sesión desde la pantalla de login de `citas-web`
**Cuando** navega a una vista que consulta un endpoint protegido
**Entonces** la petición incluye automáticamente el access token y la vista muestra los datos devueltos sin pedir credenciales de nuevo.

## Definition of Done

- [x] Los criterios CA-01 a CA-09 están validados con evidencia concreta.
- [x] Existe una migración Flyway versionada para la tabla de refresh tokens con hash y expiración.
- [x] La cadena de filtros de Spring Security valida el access token en todas las rutas salvo las declaradas explícitamente como públicas.
- [x] Las duraciones de access token y refresh token son configurables por entorno y están documentadas en `.env.example`.
- [x] El dominio y la aplicación acceden a la emisión y verificación de tokens a través de puertos, sin depender del adaptador JWT.
- [x] CORS está declarado explícitamente para el origen de `citas-web` y no se usa una configuración permisiva abierta.
- [x] Existen pruebas automatizadas de login correcto, credenciales inválidas y acceso a endpoint protegido sin token, y pasan.
- [x] El contrato de login está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [x] La trazabilidad de esta HU y de [[EP-001-identidad-y-acceso-seguro]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Verificación independiente del 2026-09-17 por `backend-verifier` y `frontend-verifier` (agentes que no implementaron el código), con prueba de mutación: backend `docker compose run --rm citas-api-dev mvn -B test` → **104 pruebas, 0 fallos** (11 mutantes, mueren los 11); frontend `npm test` en `citas-web` → **42 pruebas, 0 fallos**, con typecheck, lint y build en verde (29 mutantes, mueren todos salvo uno cosmético).
Condición del verificador frontend cumplida: la evidencia de `citas-web` está commiteada en `develop` (commit `f3e7989`, comprobado con `git log`). La evidencia de `citas-api` se versiona en `develop` en el mismo commit que el cambio de estado a `Completada`.

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | `AuthFlowIntegrationTest#loginReturnsBothTokensWithIdentityAndRoles`; `SessionUseCasesTest#loginIssuesDistinctTokensAndStoresOnlyRefreshHash` | Access token y refresh token distintos. Solo backend |
| CA-02 | Cumple | `AuthFlowIntegrationTest#loginReturnsBothTokensWithIdentityAndRoles` | `sub` = id del usuario, `roles` = `[USER]`, `exp` del access token menor que `expires_at` del refresh token; sin claim `email`. Solo backend |
| CA-03 | Cumple | Backend: `AuthFlowIntegrationTest#invalidCredentialsReturnSame401ForUnknownEmailAndWrongPassword`, `#loginWithPasswordOver72Utf8BytesReturnsTheSame401AsAWrongPassword` (×5); `SessionUseCasesTest#loginFailuresAreIndistinguishable`. Frontend (complemento): `citas-web/src/api/httpClient.test.ts > request — renovación ante 401 > no renueva ante el 401 de un login fallido` | Cuerpo completo y `Content-Type` idénticos en ambos casos; 0 refresh tokens emitidos. Una contraseña de más de 72 bytes recibe el mismo 401, y una que solo comparte con la real los primeros 72 bytes también se rechaza (`#loginRejectsPasswordThatOnlySharesTheFirst72BytesWithTheRealOne`, ×3) |
| CA-04 | Cumple | `AuthFlowIntegrationTest#inactiveUserCannotLogin`; `SessionUseCasesTest#inactiveUserCannotLogin` | Solo backend |
| CA-05 | Cumple | `AuthFlowIntegrationTest#loginReturnsBothTokensWithIdentityAndRoles` | `token_hash` = SHA-256 del token, distinto del valor entregado. Solo backend |
| CA-06 | Cumple | `AuthFlowIntegrationTest#protectedEndpointRejectsMissingMalformedAndExpiredTokens`, `#protectedEndpointRejectsWellFormedTokenSignedWithAnotherKeyOrIssuer`, `#protectedRoutesStillRejectAnInvalidBearer` | 401 sin cabecera, con token malformado y con token expirado; también con un token bien formado de otra clave o emisor. Solo backend |
| CA-07 | Cumple | `application.yml` (`${JWT_ACCESS_SECRET}` sin valor por defecto); `.env.example`; `JwtSecretValidationTest` (13 pruebas) | El mutante sobre `CHANGE_ME` muere. Solo backend |
| CA-08 | Cumple | `CapturedOutput` en `AuthFlowIntegrationTest#loginReturnsBothTokensWithIdentityAndRoles` (login correcto) y `#invalidCredentialsReturnSame401ForUnknownEmailAndWrongPassword` (login fallido) | El mutante que registra la contraseña en el login fallido muere. Solo backend. Ver riesgo R1 en [[HU-001-registrar-cuenta-de-usuario]] |
| CA-09 | Cumple | `citas-web/src/sessionFlow.test.tsx > flujo de sesión en la aplicación > HU-002 CA-09: tras el login, la vista protegida muestra lo que devuelve /api/me` | La aplicación real, con el backend simulado en `fetch`, envía `Bearer access-1` a `/api/me` tras el login, muestra los datos y no vuelve a pedir credenciales. La prueba contra el backend real en navegador queda `No verificable`; no la exige ni el CA ni la DoD (paso 3 de `PLAN_RETOMA_S2.md`) |
| DoD — CA-01 a CA-09 validados con evidencia concreta | Cumple | Filas CA-01 a CA-09 de esta tabla | — |
| DoD — Migración Flyway de refresh tokens con hash y expiración | Cumple | `V1__identity_and_fixed_catalogs.sql` (tabla `refresh_tokens`) | — |
| DoD — Filtros de Spring Security en todas las rutas salvo las públicas | Cumple | `SecurityConfig`; `AuthFlowIntegrationTest#protectedRoutesStillRejectAnInvalidBearer`, `#registerAndRefreshIgnoreAnInvalidBearer` | El mutante del resolver de Bearer muere |
| DoD — Duraciones configurables por entorno y documentadas en `.env.example` | Cumple | `application.yml` (`JWT_ACCESS_MINUTES`, `JWT_REFRESH_DAYS`); `.env.example` | Valores concretos pendientes de INC-002 |
| DoD — Emisión y verificación de tokens a través de puertos | Cumple | Puertos `AccessTokenIssuer` y `RefreshTokenRepository`; `HexagonalArchitectureTest` | — |
| DoD — CORS explícito para el origen de `citas-web` | Cumple | `AuthFlowIntegrationTest#corsPreflightAllowsFrontendOrigin`; `CorsConfig` | — |
| DoD — Pruebas de login correcto, credenciales inválidas y endpoint protegido sin token, en verde | Cumple | `AuthFlowIntegrationTest#loginReturnsBothTokensWithIdentityAndRoles`, `#invalidCredentialsReturnSame401ForUnknownEmailAndWrongPassword`, `#protectedEndpointRejectsMissingMalformedAndExpiredTokens` | Dentro de la suite de 104 pruebas, 0 fallos |
| DoD — Contrato de login documentado | Cumple | [[contrato-rest-identidad]] releído contra el código y las sondas HTTP por backend-verifier el 2026-09-17: sin divergencias | Incluye la corrección de D1 (cabecera `WWW-Authenticate` con `error_description` en inglés) y el 400 de login por cuerpo ilegible |
| DoD — Trazabilidad de esta HU y de [[EP-001-identidad-y-acceso-seguro]] actualizada | Cumple | Esta tabla y el historial de validación | — |

## Historial de validación

- 2026-09-17 — Estado `Completada`: matriz de evidencia completa y toda la DoD en `Cumple`, según la verificación independiente de backend y frontend (con prueba de mutación). Cierre dentro de la aprobación delegada de S2 (`AGENTS.md` §6).
- 2026-09-17 — Estado `En validación` (paso previo al cierre): backend-verifier relee [[contrato-rest-identidad]] contra el código y sus sondas HTTP tras la corrección de D1 y no encuentra divergencias; la fila "DoD — Contrato de login documentado" pasa de `Pendiente` a `Cumple`. Se comprueba con `git log` que la evidencia de `citas-web` está commiteada en `develop` (commit `f3e7989`). Matriz: 18 de 18 filas en `Cumple`. Se marcan los ítems de la DoD, cada uno respaldado por su fila de la matriz.
- 2026-09-17 — Tabla de evidencia reescrita a partir de la verificación independiente (backend-verifier y frontend-verifier, con prueba de mutación). Estado sin cambios (`Aprobada`). 18 filas (9 CA y 9 ítems de DoD): 17 `Cumple` y 1 `Pendiente` (contrato, a la espera de que el verificador confirme la corrección de D1). CA-09 pasa de `Parcial` a `Cumple` con la prueba de flujo de `citas-web`; la comprobación manual contra el backend real no la exige ni el CA ni la DoD.
- 2026-09-17 — Enmienda de especificación dentro de la **aprobación delegada** de S2 (`AGENTS.md` §6): la descripción de T-02 recoge las columnas de familia, consumo y reemplazo que la V1 ya crea para la rotación de [[dec-002-rotacion-refresh-tokens]]. No cambia ningún criterio de aceptación. Estado sin cambios (`Aprobada`).
- 2026-09-16 — Estado `Aprobada` por **aprobación delegada**: el usuario eligió ejecutar S2 en modo autónomo, autorizando al agente a asumir las aprobaciones de HU. Alcance: GOAL_01 (registro + login JWT + refresh + logout).
- Sesión S2 — HU creada y dejada en estado `Pendiente de aprobación` como candidata al alcance de S2.

## Notas y decisiones

- Incógnita abierta **INC-002**: las duraciones concretas de access y refresh token no están definidas en el PRD. CA-02 solo exige la relación de orden entre ambas hasta que se fijen valores.
- Incógnita abierta **INC-003**: si `ADMIN` y `PROFESSIONAL` usan este mismo formulario de login o una entrada separada. Esta HU asume un único endpoint de login para los tres roles.
- El mecanismo de almacenamiento del token en el cliente debe evitar exponerlo en logs o en la URL; la elección concreta queda a decisión del desarrollador.
- Riesgo no bloqueante (verificación del 2026-09-17, pregunta **S4** de [[sintesis-preguntas-abiertas]]): el refresh token vive en memoria de JavaScript, así que un XSS en el mismo origen puede leerlo.
- Riesgo no bloqueante: `/login` sigue accesible con una sesión abierta, y un segundo login no revoca la familia de refresh tokens de la sesión anterior.
- Riesgo no bloqueante: la pantalla `/recuperar-password`, enlazada desde el login y el registro, llama a `/api/auth/password-recovery`, que aún no existe; lo cubre [[HU-006-solicitar-recuperacion-de-contrasena]], fuera del alcance de S2.
