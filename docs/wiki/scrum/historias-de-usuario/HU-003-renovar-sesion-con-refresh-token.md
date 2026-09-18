---
id: HU-003
tipo: historia-de-usuario
titulo: "Renovar sesión con refresh token"
estado: Completada
epica: "[[EP-001-identidad-y-acceso-seguro]]"
requisitos: [RF-02]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 1"
dependencias:
  - "[[HU-002-iniciar-sesion-con-jwt]]"
relacionadas:
  - "[[HU-004-cerrar-sesion-revocando-refresh-token]]"
  - "[[HU-033-publicar-contrato-rest-documentado]]"
---

# HU-003 — Renovar sesión con refresh token

## Historia de usuario

**COMO** usuario autenticado
**QUIERO** renovar mi access token expirado presentando mi refresh token
**PARA** seguir trabajando sin volver a escribir mis credenciales

> Como usuario autenticado, quiero renovar mi access token expirado presentando mi refresh token para seguir trabajando sin volver a escribir mis credenciales.

## Contexto y descripción

RF-02 exige que el sistema permita refresh además del login. El access token emitido en [[HU-002-iniciar-sesion-con-jwt]] es de corta duración; sin un mecanismo de renovación, el usuario quedaría expulsado a mitad de una gestión de citas y tendría que autenticarse de nuevo constantemente.

Esta HU añade el endpoint de renovación en `citas-api` y la renovación transparente en `citas-web`: cuando la API responde no autorizado por expiración del access token, el cliente intenta una renovación con el refresh token guardado y reintenta la petición original; si la renovación falla, la sesión termina y el usuario vuelve al login.

El refresh token se persiste únicamente como hash (regla heredada de HU-002), por lo que la validación consiste en localizar el registro correspondiente y comparar contra su hash, además de comprobar que no esté expirado, revocado ni ya consumido por una renovación anterior. Cada renovación rota el refresh token: el presentado queda consumido y se entrega uno nuevo de la misma sesión ([[dec-002-rotacion-refresh-tokens]]).

Pertenece al alcance de la sesión S2.

## Alcance

- Endpoint de renovación de sesión en `citas-api` que recibe el refresh token en el cuerpo de la petición.
- Validación del refresh token contra su hash almacenado, su fecha de expiración, su marca de revocación y su marca de consumo.
- Emisión de un nuevo access token con la identidad y los roles vigentes del usuario, junto con un refresh token nuevo que sustituye al presentado (rotación).
- Revocación de la sesión completa (familia de refresh tokens) cuando se presenta un refresh token ya consumido.
- Rechazo con la misma respuesta observable, sin filtración de información, para refresh token inexistente, expirado, revocado o ya consumido.
- Renovación transparente desde `citas-web` ante una respuesta de no autorizado por expiración, con reintento de la petición original.
- Finalización de la sesión en el cliente y redirección al login cuando la renovación es rechazada.

## Fuera de alcance

- Revocación explícita del refresh token por cierre de sesión, que se cubre en [[HU-004-cerrar-sesion-revocando-refresh-token]].
- Emisión inicial del par de tokens, que pertenece a [[HU-002-iniciar-sesion-con-jwt]].
- Autorización por rol y ownership de los endpoints renovados, que vive en [[HU-005-autorizar-peticiones-por-rol-y-ownership]].

## Reglas de negocio

- El refresh token permite obtener un nuevo access token sin volver a presentar credenciales (RF-02).
- Un refresh token inexistente, expirado, revocado o ya consumido no renueva la sesión (RF-02).
- Cada renovación correcta consume el refresh token presentado y entrega uno nuevo de la misma sesión; presentar de nuevo un refresh token ya consumido revoca la sesión completa a la que pertenece ([[dec-002-rotacion-refresh-tokens]], decisión `Provisional`).
- El refresh token se valida contra su hash almacenado; nunca se guarda ni se compara en claro (PRD §8).
- El nuevo access token conserva la corta duración y transporta los roles vigentes del usuario (RF-02).
- Ni el refresh token ni el access token emitido se escriben en logs (PRD §8).
- El refresh token no viaja en la URL ni en parámetros de consulta, para no quedar registrado en historiales ni en trazas de servidor (PRD §8).
- Un usuario desactivado no puede renovar su sesión aunque conserve un refresh token vigente.

## Dependencias y relaciones

- Épica: [[EP-001-identidad-y-acceso-seguro]]
- Dependencias: [[HU-002-iniciar-sesion-con-jwt]]
- Relacionadas: [[HU-004-cerrar-sesion-revocando-refresh-token]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** El caso de uso del backend es acotado y reutiliza la tabla y el adaptador de tokens ya introducidos por HU-002. La dificultad real está en el cliente: interceptar la respuesta de no autorizado, disparar una única renovación aunque varias peticiones fallen a la vez y reintentar sin duplicar efectos es un comportamiento delicado de implementar y de probar.

## Tareas de desarrollo

- [ ] **T-01 — Modelar la validación del refresh token en el dominio**
  Dificultad: Bajo
  Descripción: Expresar en el dominio las condiciones que hacen utilizable un refresh token —existente, no expirado, no revocado y no consumido— y el resultado de la renovación, sin dependencias de framework.

- [ ] **T-02 — Implementar el caso de uso de renovación**
  Dificultad: Medio
  Descripción: Caso de uso de aplicación que localiza el refresh token por su hash mediante un puerto de repositorio, comprueba vigencia y revocación, verifica que el usuario siga activo y solicita al puerto de tokens un nuevo access token con los roles actuales y un refresh token nuevo de la misma familia, dejando el presentado consumido; si el presentado ya estaba consumido, revoca su familia.

- [ ] **T-03 — Exponer el adaptador REST de renovación**
  Dificultad: Bajo
  Descripción: Controlador que recibe el refresh token en el cuerpo de la petición, nunca en la ruta ni en la cadena de consulta, devuelve el nuevo access token y responde con un error de no autorizado uniforme ante cualquier token no utilizable.

- [ ] **T-04 — Ajustar la configuración de seguridad para el endpoint de renovación**
  Dificultad: Bajo
  Descripción: Declarar el endpoint de renovación como accesible sin access token válido pero sujeto a la validación del refresh token, manteniendo el resto de rutas protegidas y el CORS explícito hacia `citas-web`.

- [ ] **T-05 — Implementar la renovación transparente en citas-web**
  Dificultad: Alto
  Descripción: Interceptor del cliente REST que, ante una respuesta de no autorizado por expiración, lanza una sola renovación compartida entre las peticiones concurrentes, reintenta la petición original con el nuevo access token y, si la renovación se rechaza, limpia la sesión y redirige al login.

- [ ] **T-06 — Verificar la ausencia del refresh token en logs y URL**
  Dificultad: Bajo
  Descripción: Revisar y ajustar la configuración de trazas del backend y del cliente para que ningún nivel de log imprima el refresh token y para que no se construya ninguna ruta que lo incluya.

- [ ] **T-07 — Pruebas de renovación**
  Dificultad: Medio
  Descripción: Pruebas del caso de uso con dobles de prueba y pruebas de integración REST para renovación correcta con rotación, token inexistente, token expirado, token revocado, reuso de un token consumido y usuario desactivado.

## Criterios de aceptación

### CA-01 — Renovación correcta emite un par de tokens nuevo y consume el refresh presentado

**Dado** un usuario activo que inició sesión y conserva su refresh token vigente, no revocado y no consumido por una renovación anterior
**Cuando** invoca el endpoint de renovación presentando ese refresh token
**Entonces** la API responde con éxito devolviendo un access token nuevo, distinto del anterior, y un refresh token nuevo, distinto del presentado
**Y** si ha transcurrido al menos un segundo desde la emisión del access token sustituido, la expiración del nuevo access token es estrictamente posterior a la de aquel
**Y** el refresh token presentado deja de servir: una nueva renovación con él no emite ningún token (ver CA-09).

### CA-02 — El access token renovado sirve para operar

**Dado** un access token obtenido mediante renovación
**Cuando** se invoca con él un endpoint protegido de la API
**Entonces** la petición se atiende y el contexto de seguridad contiene la misma identidad y los mismos roles vigentes del usuario.

### CA-03 — Refresh token inexistente rechazado

**Dado** una cadena con forma de refresh token que no corresponde a ningún registro almacenado
**Cuando** se invoca el endpoint de renovación con ella
**Entonces** la API responde con un código de no autorizado, no emite ningún token y el mensaje no revela si el token existió alguna vez.

### CA-04 — Refresh token expirado rechazado

**Dado** un refresh token cuyo registro tiene una fecha de expiración anterior al instante actual
**Cuando** se invoca el endpoint de renovación con él
**Entonces** la API responde con un código de no autorizado y no emite ningún access token.

### CA-05 — Refresh token revocado rechazado

**Dado** un refresh token cuyo registro está marcado como revocado
**Cuando** se invoca el endpoint de renovación con él
**Entonces** la API responde con un código de no autorizado y no emite ningún access token.

### CA-06 — Renovación transparente desde el frontend

**Dado** un usuario con sesión iniciada en `citas-web` cuyo access token ha expirado y cuyo refresh token sigue vigente
**Cuando** la aplicación ejecuta una petición a un endpoint protegido y recibe una respuesta de no autorizado por expiración
**Entonces** el cliente renueva el access token, reintenta la misma petición, muestra el resultado y en ningún momento pide credenciales al usuario.

### CA-07 — Fallo de renovación termina la sesión en el cliente

**Dado** un usuario con sesión iniciada en `citas-web` cuyo refresh token está revocado o expirado
**Cuando** la aplicación intenta renovar tras una respuesta de no autorizado
**Entonces** la renovación es rechazada, el cliente elimina el estado de sesión almacenado y redirige a la pantalla de login.

### CA-08 — El refresh token no aparece en logs ni en la URL

**Dado** una renovación correcta y una renovación rechazada
**Cuando** se inspeccionan la salida de log de `citas-api`, la salida de consola de `citas-web` y las rutas invocadas
**Entonces** el valor del refresh token no aparece en ninguna traza y ninguna petición lo transporta en la ruta ni en la cadena de consulta.

### CA-09 — El reuso de un refresh token consumido revoca la sesión

**Dado** un refresh token ya consumido por una renovación correcta y el refresh token que se entregó en su lugar
**Cuando** se invoca el endpoint de renovación presentando de nuevo el token consumido
**Entonces** la API responde con el mismo código de no autorizado que ante cualquier otro refresh token no utilizable y no emite ningún token
**Y** a partir de ese momento el refresh token entregado en su lugar, y cualquier otro de la misma sesión, tampoco permite renovar.

## Definition of Done

- [x] Los criterios CA-01 a CA-09 están validados con evidencia concreta.
- [x] La renovación reutiliza la tabla de refresh tokens creada en [[HU-002-iniciar-sesion-con-jwt]]; esta HU no introduce cambios de esquema y por tanto no requiere una migración Flyway adicional.
- [x] El caso de uso de renovación vive en la capa de aplicación y accede al repositorio de tokens y al emisor de JWT a través de puertos, sin depender del adaptador concreto.
- [x] Las tres causas de rechazo —token inexistente, expirado y revocado— producen la misma respuesta observable desde fuera, sin distinguir el motivo al cliente.
- [x] El interceptor de `citas-web` no dispara renovaciones simultáneas duplicadas cuando varias peticiones fallan a la vez por expiración.
- [x] La URL del backend usada por el interceptor proviene de la configuración de entorno de `citas-web`, no está escrita en el código.
- [x] Existen pruebas automatizadas de renovación correcta y de los tres casos de rechazo, y pasan.
- [x] El contrato del endpoint de renovación está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [x] La trazabilidad de esta HU y de [[EP-001-identidad-y-acceso-seguro]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Verificación independiente del 2026-09-17 por `backend-verifier` y `frontend-verifier` (agentes que no implementaron el código), con prueba de mutación: backend `docker compose run --rm citas-api-dev mvn -B test` → **104 pruebas, 0 fallos** (11 mutantes, mueren los 11); frontend `npm test` en `citas-web` → **42 pruebas, 0 fallos**, con typecheck, lint y build en verde (29 mutantes, mueren todos salvo uno cosmético).
Condición del verificador frontend cumplida: la evidencia de `citas-web` está commiteada en `develop` (commit `f3e7989`, comprobado con `git log`). La evidencia de `citas-api` se versiona en `develop` en el mismo commit que el cambio de estado a `Completada`.

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | Backend: `AuthFlowIntegrationTest#refreshRotatesTokenAndRenewedAccessTokenWorks`, `#refreshInTheSameInstantStillIssuesADistinctAccessToken`; `NimbusAccessTokenIssuerTest#expirationHasOneSecondGranularity`. Frontend: `citas-web/src/auth/sessionManager.test.ts > sessionManager — renovación > renueva con el refresh token vigente y deja listo el access token nuevo` | Con el reloj congelado y avanzado 1 min, la `exp` nueva es la anterior + 60 s (`isAfter` estricto); rotación con `used_at` y `replaced_by`. En el mismo instante el access token sigue siendo distinto (`NimbusAccessTokenIssuerTest#twoTokensIssuedAtTheSameInstantForTheSameUserAreDistinct`). Que el refresh presentado deja de servir lo prueba `#reusingOldRefreshTokenReturns401AndRevokesFamily` (CA-09). El cliente guarda el refresh rotado. El mutante del reloj de CA-01 muere |
| CA-02 | Cumple | `AuthFlowIntegrationTest#refreshRotatesTokenAndRenewedAccessTokenWorks` | Invoca `/api/me` con el access token renovado: misma identidad y mismos roles. Solo backend |
| CA-03 | Cumple | `AuthFlowIntegrationTest#unknownAndExpiredRefreshTokensReturnSame401`; `SessionUseCasesTest#unknownRefreshIsRejected` | Solo backend |
| CA-04 | Cumple | `AuthFlowIntegrationTest#unknownAndExpiredRefreshTokensReturnSame401`; `SessionUseCasesTest#refreshIsRejectedExactlyAtExpiry`, `#refreshStillWorksOneSecondBeforeExpiry` | Borde fijado por los dos lados (también `SessionUseCasesTest#refreshIsRejectedAfterExpiry`). Solo backend |
| CA-05 | Cumple | `AuthFlowIntegrationTest#logoutRevokesRefreshTokenAndIsIdempotent`; `SessionUseCasesTest#revokedRefreshIsRejected` | Solo backend |
| CA-06 | Cumple | `citas-web/src/sessionFlow.test.tsx > flujo de sesión en la aplicación > HU-003 CA-06: ante un 401 renueva, reintenta y muestra el resultado sin pedir credenciales`; `citas-web/src/api/httpClient.test.ts > request — renovación ante 401 > CA-06: renueva, reintenta con el token nuevo y devuelve el resultado`; `… > CA-06: el reintento conserva método y cuerpo de la petición original` | Solo frontend (la parte de API la cubren CA-01 y CA-02) |
| CA-07 | Cumple | `citas-web/src/sessionFlow.test.tsx > flujo de sesión en la aplicación > HU-003 CA-07: si la renovación es rechazada, cierra la sesión y vuelve al login`; `citas-web/src/auth/sessionManager.test.ts > sessionManager — renovación > CA-07: si el servidor rechaza el refresh, la sesión termina`; `citas-web/src/api/httpClient.test.ts > request — renovación ante 401 > CA-07: si la renovación es rechazada, termina la sesión y propaga el error` | Solo frontend. Robustez adicional, sin CA propio: un 5xx o un corte de red durante la renovación no cierran la sesión, y el 401 de una sesión ya cerrada no se reintenta con la sesión nueva |
| CA-08 | Cumple | Backend: `CapturedOutput` en `AuthFlowIntegrationTest#refreshRotatesTokenAndRenewedAccessTokenWorks` y `#reusingOldRefreshTokenReturns401AndRevokesFamily`; el token llega en `@RequestBody`. Frontend: la prueba `HU-003 CA-06` de `citas-web/src/sessionFlow.test.tsx` exige el cuerpo `{ refreshToken: 'refresh-1' }` | Renovación correcta y rechazada. Rutas estáticas en `contracts.ts` y `authApi.ts`; sin `console.*` en `citas-web/src` (inspección del verificador). El mutante que registra el refresh token muere |
| CA-09 | Cumple | `AuthFlowIntegrationTest#reusingOldRefreshTokenReturns401AndRevokesFamily`; `SessionUseCasesTest#reusingConsumedTokenRevokesWholeFamily` | 401, dos filas con `REUSE_DETECTED` y el refresh entregado en su lugar deja de servir. Solo backend |
| DoD — CA-01 a CA-09 validados con evidencia concreta | Cumple | Filas CA-01 a CA-09 de esta tabla | — |
| DoD — Sin cambios de esquema ni migración nueva | Cumple | Migraciones V1–V4 intactas; se reutiliza `refresh_tokens` de V1 | — |
| DoD — Caso de uso en aplicación, a través de puertos | Cumple | `RefreshSessionUseCase`; `HexagonalArchitectureTest` | — |
| DoD — Inexistente, expirado y revocado con la misma respuesta observable | Cumple | `AuthFlowIntegrationTest#unknownAndExpiredRefreshTokensReturnSame401`; punto único de lanzamiento del rechazo en `RefreshSessionUseCase` | Inexistente y expirado se comparan en la misma prueba; el revocado sale del mismo punto de lanzamiento (inspección del verificador) |
| DoD — Sin renovaciones simultáneas duplicadas en el interceptor | Cumple | `citas-web/src/auth/sessionManager.test.ts > sessionManager — renovación > comparte una sola renovación entre las peticiones que fallan a la vez`; `citas-web/src/auth/singleFlight.test.ts > createSingleFlight > comparte una única ejecución entre las llamadas concurrentes`; `citas-web/src/api/httpClient.test.ts > request — renovación ante 401 > un 401 que llega con un token ya renovado reintenta sin rotar otra vez` | Con rotación, dos renovaciones simultáneas se leerían como reuso y revocarían la familia |
| DoD — URL del backend del interceptor desde el entorno | Cumple | `citas-web/src/api/contracts.test.ts > API_BASE_URL > toma VITE_API_URL y le quita la barra final`; `… > falla al cargar si VITE_API_URL está vacía, en vez de usar un valor por defecto` | — |
| DoD — Pruebas de renovación correcta y de los tres rechazos, en verde | Cumple | `AuthFlowIntegrationTest#refreshRotatesTokenAndRenewedAccessTokenWorks`, `#unknownAndExpiredRefreshTokensReturnSame401`; `SessionUseCasesTest#revokedRefreshIsRejected` | Dentro de la suite de 104 pruebas, 0 fallos |
| DoD — Contrato de renovación documentado | Cumple | Sección de renovación de [[contrato-rest-identidad]] | Coincide con lo implementado según el verificador backend |
| DoD — Trazabilidad de esta HU y de [[EP-001-identidad-y-acceso-seguro]] actualizada | Cumple | Esta tabla y el historial de validación | — |

## Historial de validación

- 2026-09-17 — Estado `Completada`: matriz de evidencia completa y toda la DoD en `Cumple`, según la verificación independiente de backend y frontend (con prueba de mutación). Cierre dentro de la aprobación delegada de S2 (`AGENTS.md` §6).
- 2026-09-17 — Estado `En validación` (paso previo al cierre): se comprueba con `git log` que la evidencia de `citas-web` está commiteada en `develop` (commit `f3e7989`), condición que había puesto el verificador frontend. La matriz no cambia: 18 de 18 filas en `Cumple`. Se marcan los ítems de la DoD, cada uno respaldado por su fila de la matriz.
- 2026-09-17 — Tabla de evidencia reescrita a partir de la verificación independiente (backend-verifier y frontend-verifier, con prueba de mutación). Estado sin cambios (`Aprobada`). 18 filas (9 CA y 9 ítems de DoD), todas `Cumple`; CA-01 y CA-09 se validan ya contra su redacción enmendada. Matiz: la prueba de robustez `citas-web/src/api/httpClient.test.ts > request — renovación ante 401 > sin estado de sesión instalado, un 401 cuyo token ya no existe no se reintenta ni renueva` cerró después el mutante f7; que lo mata lo confirmó por mutación el implementador, no el verificador. No respalda ningún CA.
- 2026-09-17 — Ajuste de redacción dentro de la **aprobación delegada**: en el alcance, "Rechazo diferenciado" pasa a "Rechazo con la misma respuesta observable", porque contradecía la DoD y CA-03. No cambia ningún criterio.
- 2026-09-17 — Enmienda de especificación dentro de la **aprobación delegada** de S2 (`AGENTS.md` §6), para que el texto deje de contradecir lo implementado. CA-01 cubre ahora la rotación (refresh token nuevo entregado y el presentado inutilizable) y precisa que la expiración del access token renovado solo es estrictamente posterior si ha transcurrido al menos un segundo desde la emisión del sustituido, porque `iat`/`exp` del JWT van en segundos enteros; sin comprometer duraciones (INC-002). Se añade CA-09 (el reuso de un refresh token consumido revoca la sesión completa) y la DoD pasa a CA-01 a CA-09. La rotación sale de "Fuera de alcance" y se refleja en contexto, alcance, reglas y tareas. Las dos incógnitas sobre reutilización del refresh token y detección de reuso se sustituyen por el comportamiento de [[dec-002-rotacion-refresh-tokens]], que sigue `Provisional`. Estado sin cambios (`Aprobada`); la tabla de evidencia no se modifica y queda pendiente de la verificación independiente.
- 2026-09-16 — Estado `Aprobada` por **aprobación delegada**: el usuario eligió ejecutar S2 en modo autónomo, autorizando al agente a asumir las aprobaciones de HU. Alcance: GOAL_01 (registro + login JWT + refresh + logout).
- Sesión S2 — HU creada y dejada en estado `Pendiente de aprobación` como candidata al alcance de S2.

## Notas y decisiones

- Incógnita abierta **INC-002** (ver [[EP-001-identidad-y-acceso-seguro]]): el PRD no fija la duración concreta de access ni de refresh token. CA-01 solo exige que el access token renovado expire después del sustituido, sin comprometer valores. La condición de "al menos un segundo desde la emisión del sustituido" se debe a que `iat` y `exp` del JWT se expresan en segundos enteros: dos access tokens emitidos en el mismo segundo son distintos pero comparten `exp`. En el flujo real la renovación se dispara con el access token ya expirado, muy por encima de ese segundo.
- Rotación del refresh token (**implementada**, [[dec-002-rotacion-refresh-tokens]]): cada renovación correcta consume el refresh token presentado, lo enlaza al que lo sustituye y entrega uno nuevo dentro de la misma familia —la cadena de refresh tokens nacida de un login, es decir, la sesión de un dispositivo—. Lo cubre CA-01. La decisión sigue `Provisional` hasta que el usuario la confirme; si la rechazara, CA-01 y CA-09 deberán revisarse.
- Detección de reuso (**implementada**, misma decisión y mismo estado `Provisional`): presentar un refresh token ya consumido se trata como posible robo y revoca la familia completa, incluido el refresh token vigente que lo sustituyó; las sesiones del mismo usuario en otros dispositivos (otras familias) no se ven afectadas. Lo cubre CA-09. Por eso la única renovación en vuelo que exige la DoD no es solo eficiencia: dos renovaciones simultáneas con el mismo refresh token se leerían como reuso.
- El mecanismo de almacenamiento del refresh token en el cliente se hereda de [[HU-002-iniciar-sesion-con-jwt]] y debe mantener la restricción de no exponerlo en logs ni en la URL; con la rotación, el cliente sustituye el refresh token guardado por el recibido en cada renovación.
