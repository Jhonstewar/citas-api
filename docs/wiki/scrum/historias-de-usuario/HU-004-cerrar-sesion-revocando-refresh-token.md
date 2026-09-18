---
id: HU-004
tipo: historia-de-usuario
titulo: "Cerrar sesión revocando el refresh token"
estado: Completada
epica: "[[EP-001-identidad-y-acceso-seguro]]"
requisitos: [RF-02]
esfuerzo: "Bajo"
sprint_sugerido: "Sprint 1"
dependencias:
  - "[[HU-002-iniciar-sesion-con-jwt]]"
  - "[[HU-003-renovar-sesion-con-refresh-token]]"
relacionadas:
  - "[[HU-033-publicar-contrato-rest-documentado]]"
---

# HU-004 — Cerrar sesión revocando el refresh token

## Historia de usuario

**COMO** usuario autenticado
**QUIERO** cerrar mi sesión y que mi refresh token deje de servir
**PARA** que nadie pueda reutilizar mi sesión desde el dispositivo que dejo

> Como usuario autenticado, quiero cerrar mi sesión y que mi refresh token deje de servir para que nadie pueda reutilizar mi sesión desde el dispositivo que dejo.

## Contexto y descripción

RF-02 exige permitir revocación y logout además del login y el refresh. Cerrar sesión solo en el navegador no es suficiente: mientras el refresh token siga vigente en la base de datos, quien lo obtenga puede seguir renovando access tokens indefinidamente mediante [[HU-003-renovar-sesion-con-refresh-token]].

Esta HU cierra ese hueco. El endpoint de logout revoca la familia del refresh token presentado —la cadena de refresh tokens nacida del login en ese dispositivo, incluido el propio token presentado— ([[dec-002-rotacion-refresh-tokens]]), y `citas-web` limpia el estado de sesión almacenado y devuelve al usuario a la pantalla de login. La comprobación decisiva es que, tras el logout, el mismo refresh token ya no renueva nada.

El logout es idempotente: repetirlo con un token ya revocado o inexistente no debe romper la experiencia ni revelar información sobre la existencia del token.

Pertenece al alcance de la sesión S2.

## Alcance

- Endpoint de cierre de sesión en `citas-api` que recibe el refresh token y revoca su familia (la sesión de ese dispositivo), incluido el registro presentado.
- Comportamiento idempotente del logout ante un refresh token ya revocado o inexistente.
- Verificación cruzada de que un refresh token revocado ya no permite renovar, apoyada en el endpoint de [[HU-003-renovar-sesion-con-refresh-token]].
- Acción de cerrar sesión en `citas-web` que invoca el endpoint, elimina el estado de sesión del cliente y redirige al login.
- Ausencia del refresh token en logs y en la URL durante el cierre de sesión.

## Fuera de alcance

- Invalidación anticipada del access token ya emitido: el PRD no la exige; el access token es de corta duración y expira por sí mismo. Queda registrado en las notas.
- Cierre de sesión en todos los dispositivos a la vez: el PRD no lo menciona. El logout solo revoca la familia del token presentado; las sesiones abiertas en otros dispositivos siguen vigentes.
- Revocación automática de refresh tokens al restablecer la contraseña, que se discute como decisión pendiente en [[HU-007-restablecer-contrasena-con-token]].
- Emisión y renovación de tokens, cubiertas en [[HU-002-iniciar-sesion-con-jwt]] y [[HU-003-renovar-sesion-con-refresh-token]].

## Reglas de negocio

- El sistema permite revocar la sesión mediante logout (RF-02).
- Un refresh token revocado no vuelve a ser utilizable para renovar la sesión (RF-02).
- La revocación se registra sobre los registros persistidos de la familia del token presentado, que se localiza por su hash; el valor en claro nunca se almacena (PRD §8).
- El cierre de sesión es idempotente: invocarlo varias veces con el mismo token no produce un fallo distinto ni un efecto adicional.
- La respuesta del logout no revela si el refresh token existía o ya estaba revocado (PRD §8).
- El refresh token no se escribe en logs ni viaja en la URL durante el cierre de sesión (PRD §8).

## Dependencias y relaciones

- Épica: [[EP-001-identidad-y-acceso-seguro]]
- Dependencias: [[HU-002-iniciar-sesion-con-jwt]], [[HU-003-renovar-sesion-con-refresh-token]]
- Relacionadas: [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Bajo

**Justificación de dificultad:** La operación consiste en marcar la revocación en los registros de refresh token ya existentes de una sesión y limpiar el estado del cliente. No introduce esquema nuevo, ni reglas de negocio complejas, ni componentes de seguridad adicionales; reutiliza por completo las piezas creadas en las dos HU de las que depende.

## Tareas de desarrollo

- [ ] **T-01 — Modelar la revocación en el dominio**
  Dificultad: Bajo
  Descripción: Añadir al modelo de refresh token del dominio la transición a revocado y la regla de que un token revocado deja de ser utilizable, sin dependencias de framework.

- [ ] **T-02 — Implementar el caso de uso de cierre de sesión**
  Dificultad: Bajo
  Descripción: Caso de uso de aplicación que localiza el refresh token por su hash mediante el puerto de repositorio, revoca la familia a la que pertenece si lo encuentra sin revocar y resuelve con el mismo resultado observable cuando no existe o ya estaba revocado.

- [ ] **T-03 — Exponer el adaptador REST de logout**
  Dificultad: Bajo
  Descripción: Controlador que recibe el refresh token en el cuerpo de la petición, nunca en la ruta ni en la cadena de consulta, y devuelve una respuesta uniforme sin contenido sensible en todos los casos.

- [ ] **T-04 — Implementar el cierre de sesión en citas-web**
  Dificultad: Bajo
  Descripción: Acción disponible desde la interfaz que invoca el endpoint de logout, elimina el access token, el refresh token y cualquier dato de sesión guardado en el cliente, y redirige a la pantalla de login.

- [ ] **T-05 — Pruebas de cierre de sesión y revocación**
  Dificultad: Medio
  Descripción: Pruebas del caso de uso y pruebas de integración REST que cubran el logout correcto, el intento de renovación posterior con el mismo token, el logout repetido y el logout con un token inexistente.

## Criterios de aceptación

### CA-01 — Logout revoca el refresh token

**Dado** un usuario autenticado que conserva un refresh token vigente y no revocado
**Cuando** invoca el endpoint de cierre de sesión presentando ese refresh token
**Entonces** la API responde con éxito y el registro correspondiente en la base de datos queda marcado como revocado.

### CA-02 — Un refresh token revocado ya no renueva

**Dado** un refresh token sobre el que se ha ejecutado el cierre de sesión
**Cuando** se invoca con él el endpoint de renovación de [[HU-003-renovar-sesion-con-refresh-token]]
**Entonces** la API responde con un código de no autorizado y no emite ningún access token.

### CA-03 — Logout idempotente sobre un token ya revocado

**Dado** un refresh token que ya fue revocado por un cierre de sesión anterior
**Cuando** se vuelve a invocar el endpoint de cierre de sesión con el mismo token
**Entonces** la API responde exactamente igual que en el primer cierre de sesión y el estado del registro permanece revocado, sin error ni cambio adicional.

### CA-04 — Logout con un token inexistente no revela información

**Dado** una cadena con forma de refresh token que no corresponde a ningún registro almacenado
**Cuando** se invoca el endpoint de cierre de sesión con ella
**Entonces** la API responde con el mismo código y el mismo mensaje que ante un cierre de sesión correcto, y no se crea ni modifica ningún registro.

### CA-05 — El cliente queda sin sesión tras cerrar

**Dado** un usuario con sesión iniciada en `citas-web`
**Cuando** selecciona la acción de cerrar sesión
**Entonces** la aplicación muestra la pantalla de login, no queda ningún token almacenado en el cliente y al intentar navegar a una vista protegida se le exige autenticarse de nuevo.

### CA-06 — El refresh token no aparece en logs ni en la URL

**Dado** un cierre de sesión correcto y un cierre de sesión con token inexistente
**Cuando** se inspeccionan la salida de log de `citas-api` y las rutas invocadas por `citas-web`
**Entonces** el valor del refresh token no aparece en ninguna traza y ninguna petición lo transporta en la ruta ni en la cadena de consulta.

## Definition of Done

- [x] Los criterios CA-01 a CA-06 están validados con evidencia concreta.
- [x] La revocación se apoya en la columna de marca de revocación ya creada por la migración Flyway de [[HU-002-iniciar-sesion-con-jwt]]; esta HU no modifica el esquema y no requiere migración nueva.
- [x] Existe una prueba automatizada que encadena login, logout e intento de renovación, demostrando que la revocación surte efecto sobre el flujo de [[HU-003-renovar-sesion-con-refresh-token]].
- [x] El caso de uso de cierre de sesión vive en la capa de aplicación y usa el puerto de repositorio de tokens, sin acoplarse al adaptador JPA.
- [x] Las cuatro situaciones —token vigente, token revocado, token inexistente y repetición del logout— producen la misma respuesta observable desde fuera.
- [x] La acción de cerrar sesión está disponible en la interfaz de `citas-web` para los tres roles y deja el cliente sin ningún token almacenado.
- [x] El contrato del endpoint de cierre de sesión está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [x] La trazabilidad de esta HU y de [[EP-001-identidad-y-acceso-seguro]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Verificación independiente del 2026-09-17 por `backend-verifier` y `frontend-verifier` (agentes que no implementaron el código), con prueba de mutación: backend `docker compose run --rm citas-api-dev mvn -B test` → **104 pruebas, 0 fallos** (11 mutantes, mueren los 11); frontend `npm test` en `citas-web` → **42 pruebas, 0 fallos**, con typecheck, lint y build en verde (29 mutantes, mueren todos salvo uno cosmético).
Condición del verificador frontend cumplida: la evidencia de `citas-web` está commiteada en `develop` (commit `f3e7989`, comprobado con `git log`). La evidencia de `citas-api` se versiona en `develop` en el mismo commit que el cambio de estado a `Completada`.

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | `AuthFlowIntegrationTest#logoutRevokesRefreshTokenAndIsIdempotent`, `#logoutWithGarbageBearerStillRevokesTheFamily`; `SessionUseCasesTest#logoutRevokesFamilyAndIsIdempotent` | 204 y revocación con motivo `LOGOUT`; un Bearer basura no impide revocar la familia. Solo backend |
| CA-02 | Cumple | `AuthFlowIntegrationTest#logoutRevokesRefreshTokenAndIsIdempotent` | Login → logout → renovación con el mismo token = 401. Solo backend |
| CA-03 | Cumple | `AuthFlowIntegrationTest#logoutRevokesRefreshTokenAndIsIdempotent` | El segundo logout responde otra vez 204 y `revoked_at` no cambia. Solo backend |
| CA-04 | Cumple | `AuthFlowIntegrationTest#logoutWithUnknownTokenAnswersLikeARealLogoutAndTouchesNoRow` | Misma respuesta que un logout real y ninguna fila tocada. Solo backend |
| CA-05 | Cumple | `citas-web/src/sessionFlow.test.tsx > flujo de sesión en la aplicación > HU-004 CA-05: cerrar sesión revoca el refresh token y vuelve al login`; `citas-web/src/auth/sessionManager.test.ts > sessionManager — expire > cierra la sesión si el token rechazado es el vigente`; `citas-web/src/sessionFlow.test.tsx > flujo de sesión en la aplicación > un logout mientras hay una renovación en vuelo no se deshace cuando esta responde` | Tras cerrar sesión se muestra el login y volver a `/` exige autenticarse de nuevo, sin otra llamada a `/api/me`. Solo frontend |
| CA-06 | Cumple | Backend: `CapturedOutput` en `AuthFlowIntegrationTest#logoutRevokesRefreshTokenAndIsIdempotent` y `#logoutWithUnknownTokenAnswersLikeARealLogoutAndTouchesNoRow`. Frontend: la prueba `HU-004 CA-05` de `citas-web/src/sessionFlow.test.tsx` exige el cuerpo `{ refreshToken: 'refresh-1' }` y ninguna cabecera `Authorization` | Logout correcto y con token inexistente; el token nunca va en la ruta |
| DoD — CA-01 a CA-06 validados con evidencia concreta | Cumple | Filas CA-01 a CA-06 de esta tabla | — |
| DoD — Revocación sobre la columna de V1, sin migración nueva | Cumple | Migraciones V1–V4 intactas; `refresh_tokens` de `V1__identity_and_fixed_catalogs.sql` | — |
| DoD — Prueba encadenada login, logout e intento de renovación | Cumple | `AuthFlowIntegrationTest#logoutRevokesRefreshTokenAndIsIdempotent` | Es la misma prueba de CA-02 |
| DoD — Caso de uso en aplicación con el puerto de repositorio | Cumple | `LogoutUseCase` usa el puerto `RefreshTokenRepository`; `HexagonalArchitectureTest` | — |
| DoD — Vigente, revocado, inexistente y repetición con la misma respuesta | Cumple | `AuthFlowIntegrationTest#logoutRevokesRefreshTokenAndIsIdempotent` (vigente, repetición y ya revocado), `#logoutWithUnknownTokenAnswersLikeARealLogoutAndTouchesNoRow` (inexistente) | 204 en las cuatro situaciones |
| DoD — Cerrar sesión disponible para los tres roles y cliente sin tokens | Cumple | `/` no filtra por rol (`App.tsx`, `RequireAuth.tsx`); la prueba `HU-004 CA-05` de `citas-web/src/sessionFlow.test.tsx` | Cumple por construcción según el verificador: solo se ha probado con `USER`. Al añadir pantallas por rol hay que confirmar que la acción sigue en las tres (INC-003) |
| DoD — Contrato de cierre de sesión documentado | Cumple | [[contrato-rest-identidad]] releído contra el código y las sondas HTTP por backend-verifier el 2026-09-17: sin divergencias | Incluye la corrección de D2 (máximo de 256 caracteres para `refreshToken`, `@Size(max = 256)` en `RefreshTokenRequest`) |
| DoD — Trazabilidad de esta HU y de [[EP-001-identidad-y-acceso-seguro]] actualizada | Cumple | Esta tabla y el historial de validación | — |

## Historial de validación

- 2026-09-17 — Estado `Completada`: matriz de evidencia completa y toda la DoD en `Cumple`, según la verificación independiente de backend y frontend (con prueba de mutación). Cierre dentro de la aprobación delegada de S2 (`AGENTS.md` §6).
- 2026-09-17 — Estado `En validación` (paso previo al cierre): backend-verifier relee [[contrato-rest-identidad]] contra el código y sus sondas HTTP tras la corrección de D2 y no encuentra divergencias; la fila "DoD — Contrato de cierre de sesión documentado" pasa de `Pendiente` a `Cumple`. Se comprueba con `git log` que la evidencia de `citas-web` está commiteada en `develop` (commit `f3e7989`). Matriz: 14 de 14 filas en `Cumple`. Se marcan los ítems de la DoD, cada uno respaldado por su fila de la matriz.
- 2026-09-17 — Tabla de evidencia reescrita a partir de la verificación independiente (backend-verifier y frontend-verifier, con prueba de mutación). Estado sin cambios (`Aprobada`). 14 filas (6 CA y 8 ítems de DoD): 13 `Cumple` y 1 `Pendiente` (contrato, a la espera de que el verificador confirme la corrección de D2). CA-02 se apoya ahora en la prueba de integración encadenada, no solo en la del caso de uso. La DoD de la acción para los tres roles pasa de `Parcial` a `Cumple` por construcción (la ruta `/` no filtra por rol), aunque solo se ha probado con `USER`.
- 2026-09-17 — Enmienda de especificación dentro de la **aprobación delegada** de S2 (`AGENTS.md` §6), para que el texto deje de contradecir lo implementado: el logout revoca la familia completa del refresh token presentado (la sesión de ese dispositivo), no solo ese registro ([[dec-002-rotacion-refresh-tokens]], `Provisional`). Se ajustan contexto, alcance, fuera de alcance, reglas de negocio, justificación de esfuerzo, T-02 y la nota correspondiente. Ningún criterio de aceptación contradecía la revocación por familia, así que CA-01 a CA-06 no cambian. Estado sin cambios (`Aprobada`); la tabla de evidencia no se modifica y queda pendiente de la verificación independiente.
- 2026-09-16 — Estado `Aprobada` por **aprobación delegada**: el usuario eligió ejecutar S2 en modo autónomo, autorizando al agente a asumir las aprobaciones de HU. Alcance: GOAL_01 (registro + login JWT + refresh + logout).
- Sesión S2 — HU creada y dejada en estado `Pendiente de aprobación` como candidata al alcance de S2.

## Notas y decisiones

- El PRD no exige invalidar el access token ya emitido al cerrar sesión. Esta HU asume que el access token caduca por sí solo gracias a su corta duración (RF-02); si se decidiera invalidarlo de inmediato haría falta una lista de tokens revocados, que no está contemplada.
- Alcance de la revocación (**implementado**, [[dec-002-rotacion-refresh-tokens]]): el PRD no indica si el logout debe revocar todos los refresh tokens del usuario o solo el presentado. El logout revoca la familia completa del refresh token presentado —la cadena de refresh tokens nacida de un login, es decir, la sesión de ese dispositivo—, no solo ese registro y no las sesiones de otros dispositivos. Es coherente con cerrar sesión en un dispositivo concreto y, con la rotación de [[HU-003-renovar-sesion-con-refresh-token]], asegura que ningún refresh token anterior o posterior de esa sesión siga sirviendo aunque el cliente presente uno ya rotado. La decisión sigue `Provisional` hasta que el usuario la confirme.
- Incógnita abierta **INC-003** (ver [[EP-001-identidad-y-acceso-seguro]]): al no estar decidido si `ADMIN` y `PROFESSIONAL` usan la misma entrada de sesión, la acción de cerrar sesión se describe común para los tres roles y deberá revisarse si se separan las entradas.
