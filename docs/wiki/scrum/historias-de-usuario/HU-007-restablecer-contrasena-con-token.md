---
id: HU-007
tipo: historia-de-usuario
titulo: "Restablecer contraseña con token"
estado: En validación
epica: "[[EP-001-identidad-y-acceso-seguro]]"
requisitos: [RF-03]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 2"
dependencias:
  - "[[HU-006-solicitar-recuperacion-de-contrasena]]"
relacionadas:
  - "[[HU-002-iniciar-sesion-con-jwt]]"
---

# HU-007 — Restablecer contraseña con token

## Historia de usuario

**COMO** usuario que solicitó recuperar su contraseña
**QUIERO** definir una contraseña nueva presentando el token recibido
**PARA** volver a entrar al sistema

> Como usuario que solicitó recuperar su contraseña, quiero definir una contraseña nueva presentando el token recibido para volver a entrar al sistema.

## Contexto y descripción

RF-03 cierra el flujo de recuperación exigiendo que cambiar la contraseña invalide y consuma el token. Esta HU cubre esa segunda mitad: el usuario llega con el token generado en [[HU-006-solicitar-recuperacion-de-contrasena]], escribe una contraseña nueva y recupera el acceso.

La validación del token sigue el mismo patrón que el resto de la épica: se localiza el registro comparando contra el hash almacenado y se comprueba que no esté expirado ni consumido. La condición de un solo uso es verificable de forma directa, repitiendo la operación con el mismo token y comprobando que la segunda vez se rechaza.

La contraseña nueva se guarda con hash adaptativo, igual que en [[HU-001-registrar-cuenta-de-usuario]], y la prueba definitiva del éxito es de extremo a extremo: tras el cambio el usuario se autentica con la contraseña nueva y no con la anterior.

~~Queda abierta una decisión que el PRD no define: si restablecer la contraseña debe además revocar los refresh tokens vigentes del usuario. Es relevante porque, si alguien había robado la sesión, cambiar la contraseña sin revocar no le expulsa.~~
**Resuelta por D34 (2026-09-30):** sí se revocan, todas las familias, en la misma transacción del cambio. Ver notas al final.

Esta HU cubre la pantalla de cambio de contraseña, una de las pantallas obligatorias del PRD §6.

## Alcance

- Pantalla de cambio de contraseña en `citas-web`, accesible sin sesión, que recibe el token y la contraseña nueva.
- Endpoint público de restablecimiento en `citas-api`.
- Validación del token contra su hash almacenado, su fecha de expiración y su marca de consumo.
- Rechazo de token inexistente, expirado o ya usado.
- Consumo e invalidación del token al completar el cambio, comprobable reintentando.
- Almacenamiento de la contraseña nueva únicamente como hash adaptativo.
- Verificación de extremo a extremo de que el usuario se autentica con la contraseña nueva y no con la anterior.
- Validación server-side de la contraseña recibida.

## Fuera de alcance

- Generación y entrega del token de recuperación, que se cubre en [[HU-006-solicitar-recuperacion-de-contrasena]].
- ~~Revocación de los refresh tokens vigentes al restablecer: no está definida en el PRD; se registra como decisión pendiente en las notas.~~ **Resuelta por D34:** sí se revocan todas las familias del usuario. D34 no añade criterio de aceptación, solo prueba (ver notas).
- ~~Política de complejidad de contraseña, no definida en el PRD (INC-001).~~ Entra en alcance por D29 (ver notas y CA-08).
- Cambio de contraseña por un usuario autenticado que recuerda la actual: no está descrito en el PRD.
- Notificación al usuario de que su contraseña fue cambiada: el PRD no la exige.

## Reglas de negocio

- El cambio de contraseña se realiza presentando el token temporal recibido (RF-03).
- Cambiar la contraseña invalida y consume el token (RF-03).
- Un token inexistente, expirado o ya consumido no permite restablecer la contraseña (RF-03).
- El token se valida contra su hash almacenado; nunca se compara ni se guarda en claro (PRD §8).
- La contraseña nueva se almacena con hash adaptativo compatible con Spring Security y nunca en texto plano (RF-01, PRD §8).
- Ni la contraseña nueva ni el token aparecen en logs (PRD §8).
- La validación de los datos enviados se ejecuta también en el servidor (PRD §8).
- Tras el cambio, la contraseña anterior deja de ser válida para iniciar sesión.
- La contraseña nueva tiene al menos 8 caracteres, con al menos una letra y un número, validado en el servidor (D29).

## Dependencias y relaciones

- Épica: [[EP-001-identidad-y-acceso-seguro]]
- Dependencias: [[HU-006-solicitar-recuperacion-de-contrasena]]
- Relacionadas: [[HU-002-iniciar-sesion-con-jwt]], [[HU-001-registrar-cuenta-de-usuario]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** El caso de uso reutiliza la tabla y el patrón de hash creados en la HU anterior y el codificador de contraseñas ya configurado, así que no introduce infraestructura nueva. La dificultad está en encadenar correctamente la validación, el cambio y el consumo del token como una sola operación consistente, y en montar la verificación de extremo a extremo que atraviesa recuperación y login.

## Tareas de desarrollo

- [ ] **T-01 — Modelar el consumo del token en el dominio**
  Dificultad: Bajo
  Descripción: Expresar en el dominio las condiciones que hacen utilizable un token de recuperación y la transición a consumido, junto con la regla de que un token consumido no vuelve a servir, sin dependencias de framework.

- [ ] **T-02 — Implementar el caso de uso de restablecimiento**
  Dificultad: Medio
  Descripción: Caso de uso de aplicación que localiza el token por su hash, comprueba vigencia y no consumo, valida la contraseña recibida, actualiza la credencial del usuario mediante el puerto de hashing y marca el token como consumido como parte de la misma operación consistente.

- [ ] **T-03 — Exponer el adaptador REST de restablecimiento**
  Dificultad: Bajo
  Descripción: Controlador público con DTO validado que recibe el token en el cuerpo de la petición y nunca en la ruta, responde sin devolver datos sensibles y emite un error uniforme ante cualquier token no utilizable.

- [ ] **T-04 — Declarar el endpoint como público en la configuración de seguridad**
  Dificultad: Bajo
  Descripción: Añadir la ruta de restablecimiento a las accesibles sin autenticación, manteniendo protegida el resto de la API y el CORS explícito hacia `citas-web`.

- [ ] **T-05 — Construir la pantalla de cambio de contraseña en citas-web**
  Dificultad: Medio
  Descripción: Pantalla accesible sin sesión que recoge el token y la contraseña nueva con confirmación, valida en cliente, consume el endpoint mediante la URL configurable por entorno, presenta los errores de token no utilizable y redirige al login tras el cambio.

- [ ] **T-06 — Verificar la ausencia de token y contraseña en logs**
  Dificultad: Bajo
  Descripción: Revisar la configuración de trazas del backend y del cliente para que ningún nivel de log imprima el token de recuperación ni la contraseña enviada.

- [ ] **T-07 — Pruebas de restablecimiento**
  Dificultad: Medio
  Descripción: Pruebas del caso de uso y de integración REST para cambio correcto, token inexistente, token expirado, token ya consumido y contraseña inválida, más una prueba de extremo a extremo que encadene solicitud, cambio y login con la contraseña nueva y con la anterior.

## Criterios de aceptación

### CA-01 — Restablecimiento correcto

**Dado** un usuario con un token de recuperación vigente y no consumido
**Cuando** envía ese token junto con una contraseña nueva válida
**Entonces** la API responde con éxito, la credencial del usuario queda actualizada y la respuesta no incluye la contraseña ni el token.

### CA-02 — El token queda consumido tras el cambio

**Dado** un token de recuperación con el que ya se completó un restablecimiento
**Cuando** se intenta usar el mismo token para fijar otra contraseña
**Entonces** la API rechaza la petición, no modifica la credencial del usuario y el registro del token permanece marcado como consumido.

### CA-03 — Token inexistente rechazado

**Dado** una cadena con forma de token de recuperación que no corresponde a ningún registro almacenado
**Cuando** se invoca el endpoint de restablecimiento con ella
**Entonces** la API responde con un error y no modifica ninguna credencial.

### CA-04 — Token expirado rechazado

**Dado** un token de recuperación cuyo registro tiene una fecha de expiración anterior al instante actual
**Cuando** se invoca el endpoint de restablecimiento con él
**Entonces** la API responde con un error y no modifica ninguna credencial.

### CA-05 — Autenticación con la contraseña nueva

**Dado** un usuario que completó el restablecimiento
**Cuando** intenta iniciar sesión con la contraseña nueva
**Entonces** el login de [[HU-002-iniciar-sesion-con-jwt]] tiene éxito y se emite el par de tokens.

### CA-06 — La contraseña anterior deja de servir

**Dado** un usuario que completó el restablecimiento
**Cuando** intenta iniciar sesión con la contraseña que tenía antes del cambio
**Entonces** la API responde con el error de credenciales inválidas y no emite ningún token.

### CA-07 — Contraseña nueva almacenada solo como hash

**Dado** un restablecimiento completado correctamente
**Cuando** se consulta la credencial del usuario en la base de datos
**Entonces** el valor almacenado no coincide con la contraseña enviada, corresponde a un hash adaptativo verificable por Spring Security y es distinto del hash que había antes del cambio.

### CA-08 — Contraseña ausente, vacía o fuera de política rechazada en el servidor

**Dado** peticiones de restablecimiento enviadas directamente a la API con un token válido y, en cada una, sin contraseña nueva, con ella vacía, con menos de 8 caracteres (`abc123`), sin ningún número (`abcdefgh`) o sin ninguna letra (`12345678`)
**Cuando** la API procesa cada petición
**Entonces** responde con un error de validación sobre el campo de contraseña, no modifica la credencial y el token no queda consumido (D29).

### CA-09 — Ausencia de token y contraseña en logs

**Dado** un restablecimiento correcto y otro rechazado por token no utilizable
**Cuando** se inspecciona la salida de log de la aplicación para ambas peticiones
**Entonces** no aparece el token de recuperación ni la contraseña enviada.

## Definition of Done

- [ ] Los criterios CA-01 a CA-09 están validados con evidencia concreta.
- [ ] Esta HU reutiliza la tabla de tokens de recuperación creada en [[HU-006-solicitar-recuperacion-de-contrasena]]; no introduce cambios de esquema y por tanto no requiere migración Flyway propia.
- [ ] La actualización de la credencial y el marcado del token como consumido ocurren de forma consistente: no existe un resultado en el que la contraseña cambie y el token siga utilizable, ni al revés.
- [ ] La contraseña nueva se cifra con el mismo codificador adaptativo configurado en [[HU-001-registrar-cuenta-de-usuario]].
- [ ] El dominio del token y de la credencial no depende de Spring ni de JPA, respetando la separación hexagonal.
- [ ] El endpoint de restablecimiento está declarado como público en la configuración de seguridad y el token viaja en el cuerpo de la petición, nunca en la ruta.
- [ ] Existe una prueba automatizada de extremo a extremo que encadena solicitud de recuperación, restablecimiento, login con la contraseña nueva y login fallido con la anterior, cubriendo la coherencia entre `citas-web` y `citas-api` en el flujo completo de recuperación.
- [ ] Existe una prueba automatizada que demuestra el rechazo del segundo uso del mismo token.
- [ ] La pantalla de cambio de contraseña de `citas-web` consume el endpoint usando la URL del backend leída de la configuración de entorno, cubriendo la pantalla obligatoria de recuperación/cambio de contraseña del PRD §6.
- [ ] El contrato del endpoint de restablecimiento está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [ ] La trazabilidad de esta HU y de [[EP-001-identidad-y-acceso-seguro]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Ejecución de referencia del **2026-09-30**: el `backend-verifier`, agente independiente que no escribió el código, reejecutó la suite completa de `citas-api` → **484 pruebas, 0 fallos, 0 errores, `BUILD SUCCESS`**. El `frontend-verifier` reejecutó la de `citas-web` → **218 pruebas**, con typecheck, `oxlint` y build limpios, y dejó **14 hallazgos abiertos** (3 en reparación y 4 pruebas que faltan).

Rutas abreviadas: **PRIT** = `src/test/java/com/fcv/citas/infrastructure/rest/PasswordRecoveryIntegrationTest.java`; **PRET** = `src/test/java/com/fcv/citas/infrastructure/rest/PasswordRecoveryExposedTokenIntegrationTest.java`; **PRUC** = `src/test/java/com/fcv/citas/application/auth/PasswordRecoveryUseCasesTest.java`; **PPT** = `src/test/java/com/fcv/citas/domain/auth/PasswordPolicyTest.java`.

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | PRIT:251 `resetChangesThePasswordAndOnlyTheNewOneWorks`: 204 con **cuerpo vacío** (ni contraseña ni token en la respuesta), el hash almacenado cambia y `passwordEncoder.matches(nuevaClave, hash)` es cierto; PRUC:122 sobre el caso de uso | El 204 sin cuerpo es la evidencia de que la respuesta no devuelve nada sensible |
| CA-02 | Cumple | PRIT:273 `everyUnusableTokenGetsTheSameAnswerAndChangesNothing` (el token ya usado recibe 400 `RESET_TOKEN_INVALID`, el hash de la credencial es idéntico al de después del primer cambio y `used_at` sigue puesto); PRET:115 (tercer intento con el mismo token → 400); PRUC:142 `aTokenServesOnlyOnce` | — |
| CA-03 | Cumple | PRIT:273 (un token aleatorio inexistente recibe **exactamente el mismo cuerpo** que el usado, el expirado y el revocado: se compara el JSON entero contra la primera respuesta); PRIT:235 (`Bearer` inválido + token inventado → 400 `RESET_TOKEN_INVALID`); PRUC:152 | La respuesta uniforme evita revelar si el token existe |
| CA-04 | Cumple | PRIT:273 (token sembrado con `Duration.ofMinutes(-1)` → 400 `RESET_TOKEN_INVALID`, `used_at` sigue nulo y la credencial no cambia); `PasswordResetToken:38-48` (`isUsable` exige `expiresAt` posterior a `now`); PRUC:152 | El filtro `isUsable` corre dentro de la transacción, sobre la fila bloqueada |
| CA-05 | Cumple | PRIT:251 (login con la contraseña nueva → 200 con `accessToken` no vacío); PRET:115 `endToEndRecoveryResetAndLogin` (recuperación → restablecimiento → login, todo por la API) | — |
| CA-06 | Cumple | PRIT:251 (login con la anterior → 401, `detail = "Credenciales inválidas"` y sin `accessToken`); PRET:115 | — |
| CA-07 | Cumple | PRIT:251: el hash nuevo es distinto del anterior, distinto de la contraseña enviada, empieza por `{bcrypt}$2` y lo verifica el `PasswordEncoder` de Spring Security | Se comprueban las tres condiciones del criterio, no solo que el valor cambió |
| CA-08 | Cumple | PRIT:307 `aPasswordOutsideThePolicyIsRejectedAndDoesNotConsumeTheToken`: ausente, vacía, `abc123` (corta), `abcdefgh` (sin dígito), `12345678` (sin letra) y una de más de 72 bytes → 400 con `fieldErrors.newPassword` y el mensaje propio de cada caso; el hash no cambia, `used_at` sigue nulo y **al final el mismo token todavía sirve**; PPT (política en el dominio); PRUC:167 | El criterio exige que el token no quede consumido, y la prueba lo demuestra usándolo después con éxito |
| CA-09 | Cumple | PRIT:361 `neitherTheTokenNorThePasswordReachTheLog` (con `OutputCaptureExtension`: ni el token válido, ni el inventado, ni ninguna de las tres contraseñas enviadas, **ni el SHA-256 del token**, aparecen en la salida, ni en el caso correcto ni en los dos rechazados); PRET:92 y PRET:115 | Cubre las dos ramas que pide el criterio (correcta y rechazada) |
| DoD — CA-01 a CA-09 validados con evidencia concreta | Cumple | Filas CA-01 a CA-09 de esta tabla | Los nueve son de backend y todos tienen prueba propia |
| DoD — Reutiliza la tabla de [[HU-006-solicitar-recuperacion-de-contrasena]]; sin cambios de esquema ni migración propia | Cumple | `V1__identity_and_fixed_catalogs.sql:198-212` es la única definición de `password_reset_tokens`; ninguna migración posterior (V2–V10) la toca; `ResetPasswordUseCase` solo lee y actualiza esa tabla, `users` y `refresh_tokens` | — |
| DoD — Credencial actualizada y token consumido de forma consistente, sin resultados a medias | Cumple | `ResetPasswordUseCase:61-73`: `findByTokenHashForUpdate` (bloqueo de la fila), `users.updatePasswordHash`, `resetTokens.save(token.consume(now))` y la revocación de refresh, **todo dentro de un único `tx.inTransaction`**; PRIT:307 (la validación falla antes de tocar nada: hash intacto y token vivo) y PRIT:273 (el token no utilizable no cambia la credencial) | El hash BCrypt se calcula **fuera** de la transacción y siempre, válido o no el token, para no filtrar por tiempo de respuesta si el token existe |
| DoD — La contraseña nueva se cifra con el mismo codificador adaptativo de [[HU-001-registrar-cuenta-de-usuario]] | Cumple | `ResetPasswordUseCase` usa el puerto `PasswordHasher`, el mismo que el registro; PRIT:251 comprueba el prefijo `{bcrypt}$2` y que el `PasswordEncoder` inyectado en la prueba lo valida | — |
| DoD — Dominio del token y de la credencial sin Spring ni JPA | Cumple | `domain/auth/PasswordResetToken`, `domain/auth/PasswordPolicy`, `domain/auth/PasswordHasher` y `domain/auth/PasswordResetTokenRepository` son Java puro; `HexagonalArchitectureTest` (cero dependencias de Spring, JPA, Jakarta o HTTP en `domain/` y `application/`) | — |
| DoD — Endpoint público y token en el cuerpo, nunca en la ruta | Cumple | `SecurityConfig:36-39` (`/api/auth/password-reset` en `PUBLIC_AUTH_POST`), `:74` (`permitAll`) y `:94` (`denyAll` por omisión); `PasswordRecoveryController` recibe el token en el DTO del cuerpo; PRIT:235 (un `Bearer` inválido no bloquea la ruta: responde 400 de token, no 401) | La ruta no lleva ningún segmento variable, así que el token no puede aparecer en ella |
| DoD — Prueba de extremo a extremo que encadena solicitud, restablecimiento, login con la nueva y login fallido con la anterior | Cumple | PRET:115 `endToEndRecoveryResetAndLogin`: el token se obtiene del `devToken` de la solicitud (D27), se restablece, se entra con la nueva (200), se rechaza la anterior (401) y el mismo token no sirve por tercera vez; **todo por la API, sin consultar la base** | Es exactamente el flujo que la pantalla de `citas-web` ejecuta con el enlace directo |
| DoD — Prueba del rechazo del segundo uso del mismo token | Cumple | PRIT:273; PRET:115 (tercer intento); PRUC:142 | — |
| DoD — Pantalla de cambio de contraseña de `citas-web` consumiendo la URL de entorno (pantalla obligatoria PRD §6) | Pendiente | Existen `citas-web/src/pages/RestablecerPasswordPage.tsx` y la ruta `/restablecer-password` (`src/App.tsx:47`); el token llega por `?token=…`, se copia al estado y se **quita de la barra de direcciones** sin añadir historial; `src/api/httpClient.ts` toma la base de `VITE_API_URL`; hay 7 pruebas en `src/profileAndPassword.test.tsx:198-286` | No se marca `Cumple`: la verificación de frontend criterio a criterio la hace el `frontend-verifier`, que dejó 14 hallazgos abiertos (3 en reparación, 4 pruebas que faltan), y falta la prueba manual en navegador de la fase F10 de `PLAN_RETOMA_S4.md` |
| DoD — Contrato del endpoint reflejado en [[HU-033-publicar-contrato-rest-documentado]] | Cumple | `citas-api/docs/wiki/llm-wiki/wiki/contrato-rest-identidad.md:353` (`POST /api/auth/password-reset` → 204 · 400 `VALIDATION` por política · 400 `RESET_TOKEN_INVALID` para inexistente, caducado, usado o revocado, con **una sola respuesta**) y `:482` | `llm-wiki/` queda fuera del límite de escritura de esta skill: la evidencia se leyó, no se produjo aquí |
| DoD — Trazabilidad de esta HU y de [[EP-001-identidad-y-acceso-seguro]] actualizada | Cumple | Esta matriz, el historial de validación y las notas (D34 registrada, nota del token manual retirada); en [[EP-001-identidad-y-acceso-seguro]] las anotaciones de INC-001, INC-002 y INC-005; `docs/wiki/scrum/README.md` | — |

## Historial de validación

- 2026-09-30 — **Matriz de evidencia recolectada del repositorio.** CA-01 a CA-09 y toda la DoD de backend, de contrato y de trazabilidad en `Cumple`. Estado: `Aprobada` → `En validación`. **No pasa a `Completada`**: el ítem de DoD de la pantalla de `citas-web` queda en `Pendiente` porque la verificación de frontend criterio a criterio y la prueba manual en navegador de F10 no se han hecho.
- 2026-09-30 — Retirada la nota que exigía que «la pantalla debe admitir que el usuario introduzca el token manualmente», y escrito que el envío por SMTP queda fuera de S4. **Decisión directa del usuario**, no delegada: sin correo real nadie tiene un token que teclear, y D27 ya entrega el enlace directo. No cambia ningún criterio de aceptación ni la DoD.
- 2026-09-30 — La nota «Decisión pendiente, **no cubierta por D15–D30**» sobre revocar los refresh tokens se sustituye por la decisión que la resolvió, **D34**, con su implementación (`ResetPasswordUseCase:70`) y su prueba (`PasswordRecoveryIntegrationTest:337`). El punto de "Fuera de alcance" queda tachado con la misma referencia. Mentía sobre el estado del trabajo: quien retomara podía volver a decidir algo ya decidido y ejecutado.
- 2026-09-25 — CA-08 ajustado a D29: además de ausente o vacía, rechaza una contraseña con menos de 8 caracteres, sin letra o sin número. El punto de "Fuera de alcance" sobre la política de contraseña queda tachado por la misma decisión.
- 2026-09-25 — Aprobada por **aprobación delegada** del usuario para S4 (D15, PLAN_RETOMA_S4.md). Alcance en `PLAN_RETOMA_S4.md` §3 (bloque «Cuenta», fase F7) y decisiones D15–D30 en [[dec-006-decisiones-s4-ciclo-de-vida]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- **Resuelta (D34, provisional bajo delegación):** la duda sobre si restablecer la contraseña debe revocar los refresh tokens vigentes del usuario. **D34** la registró y decidió que sí: restablecer revoca **todas** las familias de refresh del usuario, porque una contraseña comprometida no debe sobrevivir en sesiones abiertas (PRD §8, [[dec-002-rotacion-refresh-tokens]]). Implementado en `ResetPasswordUseCase:70` (`refreshTokens.revokeAllForUser(user.id(), now, RefreshToken.REASON_PASSWORD_RESET)`, dentro de la misma transacción) y probado en `PasswordRecoveryIntegrationTest:337` `resetRevokesEveryRefreshTokenFamilyOfTheUser`: dos sesiones de dos dispositivos quedan con `revoked_at` y `revoked_reason = 'PASSWORD_RESET'`, las dos familias son distintas y ninguna de las dos cookies `fcv_refresh` renueva (401). D34 decidió expresamente **no** añadir criterio de aceptación a esta HU, solo prueba, así que el punto de "Fuera de alcance" se conserva tachado abajo y la matriz no lleva una fila `CA` para esto.
- **Resuelta (D29, provisional bajo delegación):** INC-001 (ver [[EP-001-identidad-y-acceso-seguro]]): mínimo 8 caracteres con al menos una letra y un número, **también en el servidor**, igual que el cliente. Se aplica solo al fijar una contraseña; las cuentas existentes siguen entrando ([[dec-006-decisiones-s4-ciclo-de-vida]]). CA-08 lo refleja. La misma decisión obliga a revisar la matriz de [[HU-001-registrar-cuenta-de-usuario]] (`Completada`).
- **Resuelta (D27, provisional bajo delegación):** INC-002 (ver [[EP-001-identidad-y-acceso-seguro]]) en lo que toca al token de recuperación: vigencia de 30 minutos, configurable por entorno ([[dec-006-decisiones-s4-ciclo-de-vida]]). CA-04 se sigue apoyando en la fecha de expiración almacenada, no en el valor.
- **Cerrada por decisión directa del usuario (2026-09-30):** el token llega a la pantalla **solo por el enlace** (`/restablecer-password?token=…`), y la pantalla no ofrece un campo para teclearlo. Razón: sin SMTP nadie recibe un token que copiar, así que un campo manual no habilitaría ningún flujo; y con la variable de laboratorio de **D27** encendida, la pantalla de solicitud ya muestra el token marcado como dato de laboratorio con el enlace directo (`citas-web/src/pages/RecuperarPasswordPage.tsx:90-102`). `citas-web/docs/diseno/PANTALLAS_OBLIGATORIAS.md:55-64` documenta únicamente el flujo por enlace, que es lo implementado (`RestablecerPasswordPage.tsx:41-55`: el token se lee de la query, se copia al estado y se quita de la barra de direcciones sin añadir historial).
- El **envío real por SMTP queda fuera de S4** (PRD §9 lo declara opcional y RF-03 autoriza la exposición controlada en desarrollo). Si algún día se añade, el puerto `domain/auth/PasswordResetNotifier` admite un adaptador de correo sin tocar el caso de uso, y entonces conviene revisar si el enlace debe apuntar a un origen configurable distinto del de laboratorio.
