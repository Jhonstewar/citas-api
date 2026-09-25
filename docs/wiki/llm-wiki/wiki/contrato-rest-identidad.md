---
titulo: "Contrato REST — identidad y acceso"
tipo: contrato
estado: Vigente
actualizado: 2026-09-25
fuentes:
  [
    "citas-api/src/main/java/com/fcv/citas/infrastructure/rest/auth/AuthController.java",
    "citas-api/src/main/java/com/fcv/citas/infrastructure/rest/auth/RefreshTokenCookies.java",
    "citas-api/src/main/java/com/fcv/citas/infrastructure/rest/auth/PasswordRecoveryController.java",
    "citas-api/src/main/java/com/fcv/citas/domain/auth/PasswordPolicy.java",
    "citas-api/src/main/java/com/fcv/citas/infrastructure/config/CorsConfig.java",
    "citas-api/src/main/java/com/fcv/citas/infrastructure/rest/me/MeController.java",
    "citas-api/src/main/java/com/fcv/citas/infrastructure/rest/catalog/CatalogController.java",
    "citas-api/src/main/java/com/fcv/citas/infrastructure/rest/error/GlobalExceptionHandler.java",
    "citas-api/src/main/java/com/fcv/citas/infrastructure/security/SecurityConfig.java",
    "citas-api/src/main/java/com/fcv/citas/infrastructure/security/ProblemJsonSecurityHandlers.java",
    "citas-api/src/main/resources/application.yml",
    "PRD.md §8",
    "HU-033 (docs/wiki/scrum/)",
  ]
tags: [contrato, rest, auth, HU-033]
---

# Contrato REST — identidad y acceso (Sprint 1)

## Qué es

El contrato de los endpoints de identidad de `citas-api` que consume `citas-web` directamente,
sin Express ni BFF (RF-20). Cubre [[HU-001-registrar-cuenta-de-usuario]] a
[[HU-004-cerrar-sesion-revocando-refresh-token]] más `GET /api/me`. Es el artefacto que pide
[[HU-033-publicar-contrato-rest-documentado]] y crece con cada HU que publique un endpoint.

Cada entrada de este documento se contrastó contra el código: `AuthController.java`,
`MeController.java`, los DTO de `infrastructure/rest/auth/`, `GlobalExceptionHandler.java`,
`ProblemJsonSecurityHandlers.java` y `SecurityConfig.java`.

## Convenciones generales

| Aspecto | Regla |
|---|---|
| Prefijo | Todas las rutas cuelgan de `/api`. Autenticación bajo `/api/auth`. |
| Formato | JSON UTF-8 en petición y respuesta. `Content-Type: application/json`. |
| Nulos | Jackson omite las propiedades nulas (`default-property-inclusion: non_null`): un campo ausente equivale a nulo. |
| Zona horaria | `America/Bogota` en Jackson, Hibernate y la conexión MySQL. |
| Idioma | Los errores de los flujos de identidad van en español: locale fijo `es_CO` (`spring.web.locale-resolver: fixed`) y `Accept-Language` se ignora. Excepciones en inglés: la cabecera `WWW-Authenticate` del 401, el 403 del preflight CORS y los errores que Spring genera fuera de los manejadores propios (ver "Formato de error uniforme" y "Preguntas abiertas"). |
| Autenticación | `Authorization: Bearer <accessToken>`. La API es *stateless* (`SessionCreationPolicy.STATELESS`): sin sesión de servidor. Desde D36 hay **una** cookie, `fcv_refresh`, que solo transporta el refresh token hacia `/api/auth` (ver §S4 "refresh token en cookie"). |
| CSRF | Deshabilitado de forma consciente: el access token va en cabecera, y la única cookie es `HttpOnly; SameSite=Strict; Path=/api/auth` con CORS de orígenes exactos (justificación en `SecurityConfig`). |
| Refresh token | Desde D36 viaja SOLO en la cookie `HttpOnly` `fcv_refresh`; NUNCA en el cuerpo, la ruta ni la cadena de consulta (HU-003 CA-08). |
| Paginación | Todavía no aplica: ningún endpoint de identidad devuelve colecciones paginadas. |

## Formato de error uniforme

Los errores usan **`ProblemDetail` (RFC 9457)**, con `Content-Type:
application/problem+json`. Decisión registrada abajo (INC-040). Una sola excepción: el `403` de
un preflight CORS desde un origen no permitido, que Spring (`DefaultCorsProcessor`) responde con
texto plano `Invalid CORS request` y sin `Content-Type` (ver CORS).

```json
{
  "type": "about:blank",
  "title": "Datos inválidos",
  "status": 400,
  "detail": "La petición contiene campos inválidos",
  "instance": "/api/auth/register",
  "fieldErrors": {
    "email": "no debe estar vacío"
  }
}
```

- `title` es la categoría del error; `detail` es el mensaje legible que `citas-web` muestra;
  `instance` es la ruta invocada y aparece en todos los errores `ProblemDetail` (no en el 403 de
  CORS).
- `fieldErrors` es una **extensión propia**, presente solo en los 400 de validación de campos: un
  mapa `campo → mensaje`, siempre en español. Nunca incluye el valor rechazado.
- Un cuerpo ausente, que no es JSON o con un tipo incompatible también da `400 Datos inválidos`,
  con `detail: "El cuerpo de la petición falta o no es un JSON válido"` y **sin** `fieldErrors`. El
  mensaje interno del parser no se devuelve ni se registra.
- Ningún cuerpo de error lleva traza de pila, SQL, contraseñas, hashes ni tokens. El 500 registra
  en log únicamente el **tipo** de la excepción, no su mensaje (HU-033 CA-04).

### Tabla de códigos

| Código | Título | Cuándo | Ejemplo en identidad |
|---|---|---|---|
| 200 | — | Operación correcta con cuerpo | `login`, `refresh`, `GET /api/me` |
| 201 | — | Recurso creado | `register` |
| 204 | — | Correcta sin cuerpo | `logout` |
| 400 | `Datos inválidos` | Validación server-side (con `fieldErrors`) o cuerpo ausente/ilegible (sin `fieldErrors`) | campo vacío, email mal formado, `documentType` desconocido, JSON roto |
| 401 | `No autenticado` | Sin credenciales válidas, o access/refresh token inválido o expirado | credenciales incorrectas, refresh inexistente/expirado/revocado |
| 403 | `Acceso denegado` | Autenticado pero sin rol u *ownership* | reservado para [[HU-005-autorizar-peticiones-por-rol-y-ownership]] |
| 404 | `No encontrado` | El recurso no existe | usuario del token ya borrado |
| 409 | `Conflicto` | Choque con datos únicos o de estado | email o documento ya registrados |
| 422 | `Regla de negocio` | Petición bien formada que una regla del PRD rechaza. Lleva la extensión `code`, estable, para que el cliente decida | `INSURANCE_PLAN_UNAVAILABLE` al registrar con un plan de EPS no seleccionable (HU-009) |
| 500 | `Error interno` | Fallo no controlado | cuerpo genérico, sin detalles |

**401 frente a 403**: 401 significa "no sé quién eres o tu token ya no sirve" y es lo que dispara
la renovación automática del frontend; 403 significa "sé quién eres y no puedes". El cliente HTTP
de `citas-web` solo renueva ante 401.

**Dos orígenes del 401**, con el mismo `title` y formato:

| Origen | `detail` | Cabecera |
|---|---|---|
| Filtro de seguridad: falta el access token | `Se requiere un access token válido` | `WWW-Authenticate: Bearer` |
| Filtro de seguridad: token mal formado, mal firmado, caducado o de otro emisor | `Se requiere un access token válido` | `WWW-Authenticate: Bearer error="invalid_token", error_description="…", error_uri="…"` |
| Caso de uso de login | `Credenciales inválidas` | — |
| Caso de uso de refresh | `La sesión no es válida o ha expirado` | — |

El cuerpo es idéntico en todos los casos del filtro, pero la cabecera no. La pone el
`BearerTokenAuthenticationEntryPoint` estándar de Spring (RFC 6750), al que delega
`ProblemJsonSecurityHandlers`, y su `error_description` va **en inglés** y deja ver el motivo del
rechazo: `Jwt expired at …`, `Invalid signature`, `The iss claim is not valid`, `Malformed token`.
Ver la pregunta abierta al final.

El `403` lo produce el mismo manejador de la cadena de seguridad, con `detail: "No tiene permisos
para realizar esta operación"`.

## Endpoints

### `POST /api/auth/register` — registrar cuenta (HU-001)

Público. No inicia sesión: no devuelve tokens.

**Petición**

```json
{
  "firstNames": "Ana María",
  "lastNames": "Pérez Gómez",
  "documentType": "CC",
  "documentNumber": "1098765432",
  "email": "ana.perez@ejemplo.test",
  "phone": "3001234567",
  "password": "Clave-Secreta#2026",
  "insurancePlanId": 3
}
```

Alias aceptados por compatibilidad: `firstName`, `lastName`, `documentTypeCode`.

| Campo | Regla |
|---|---|
| `firstNames`, `lastNames` | obligatorio, máx. 100 |
| `documentType` | obligatorio, máx. 10; código del catálogo `document_types` (`CC`, `TI`, `CE`, `PA`, `RC`) |
| `documentNumber` | obligatorio, máx. 20 |
| `email` | obligatorio, formato email, máx. 160 |
| `phone` | obligatorio, máx. 30 |
| `password` | obligatorio y conforme a la **política D29** (§S4): mín. 8 caracteres, al menos una letra Unicode y un dígito `0-9`, máx. **72 bytes UTF-8** (límite de BCrypt), no 72 caracteres: la ñ y las vocales con tilde ocupan 2 bytes y los emojis 4 |
| `insurancePlanId` | **opcional** (HU-009), entero. Ausente o `null` = cuenta sin afiliación. Si viene, debe ser el `id` de un plan del catálogo público `GET /api/catalogs/insurance-plans` |

Una contraseña de más de 72 bytes da `400` con `fieldErrors.password = "no debe superar 72 bytes
en UTF-8 (la ñ y las vocales con tilde ocupan 2 bytes; los emojis, 4)"`.

**201** → `UserResponse`:

```json
{
  "id": 42,
  "firstNames": "Ana María",
  "lastNames": "Pérez Gómez",
  "documentType": "CC",
  "documentNumber": "1098765432",
  "email": "ana.perez@ejemplo.test",
  "phone": "3001234567",
  "roles": ["USER"]
}
```

La respuesta **no cambia** por llevar o no `insurancePlanId`: no incluye la afiliación. Si el
plan es válido, la afiliación se crea en la **misma transacción** que la cuenta
(`is_current = 1`, `started_on` = hoy en `America/Bogota`, sin número de afiliado ni fecha de
fin). Si no lo es, no se crea ni la afiliación ni el usuario.

**Errores**: `400` validación (con `fieldErrors`) · `409` `El email ya está registrado` /
`El documento ya está registrado` · `422` `code: INSURANCE_PLAN_UNAVAILABLE`, `detail: "El plan
de EPS seleccionado no está disponible"`, cuando el plan **no existe**, está **inactivo** o
pertenece a una **EPS inactiva**. Los tres casos dan la misma respuesta: no revela si el plan
existe.

### `POST /api/auth/login` — iniciar sesión (HU-002)

Público.

```json
{ "email": "ana.perez@ejemplo.test", "password": "Clave-Secreta#2026" }
```

**200** → `TokenResponse` (desde D36, **sin** `refreshToken` en el cuerpo):

```json
{
  "accessToken": "<JWT HS256>",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

y la cabecera:

```
Set-Cookie: fcv_refresh=<valor opaco>; Path=/api/auth; Max-Age=604800; Expires=<fecha>; Secure; HttpOnly; SameSite=Strict
```

- `expiresIn` en **segundos** de vida del access token (900 = 15 min, `JWT_ACCESS_MINUTES`).
- El access token es un JWT HS256 con `iss: citas-api`, `sub: <id de usuario>`, el claim `roles`
  (sin prefijo `ROLE_`; el prefijo lo añade el conversor del servidor), `iat`/`exp` en segundos
  enteros y un `jti` aleatorio. No lleva email ni otros datos personales.
- El `jti` garantiza que dos tokens emitidos en el mismo segundo para el mismo usuario sean
  distintos: sin él serían idénticos byte a byte.
- El refresh token es **opaco**, no un JWT: un valor aleatorio del que solo se guarda el hash
  SHA-256 (ver [[dec-002-rotacion-refresh-tokens]]). Vive 7 días (`JWT_REFRESH_DAYS`).

| Campo | Regla |
|---|---|
| `email` | obligatorio, máx. 160 |
| `password` | obligatorio; sin tope de longitud en la validación |

**Errores**: `400` solo si falta un campo o el email supera 160 (con `fieldErrors`), o si el
cuerpo falta o no es JSON (sin `fieldErrors`) · `401` `Credenciales inválidas`,
**idéntico** para email inexistente, contraseña incorrecta, usuario desactivado y contraseña de
más de 72 bytes, para no revelar qué emails existen ni describir la regla. Una contraseña de más
de 72 bytes nunca coincide, aunque sus primeros 72 bytes sean los de la real (ver
[[riesgo-spring-security-65-trampas]], trampa 5).

### `POST /api/auth/refresh` — renovar sesión (HU-003)

Público: se autentica con la cookie `fcv_refresh` (D36) e **ignora** la cabecera
`Authorization`, así que un access token caducado no impide renovar. **Sin cuerpo**: si llega uno
(el cliente anterior a D36 enviaba `{ "refreshToken": … }`), se ignora y, sin cookie, la respuesta
es 401.

**200** → `TokenResponse`, con access token nuevo, y un `Set-Cookie` con el **refresh token
nuevo**: la renovación **rota** el token. El presentado queda consumido y enlazado a su
reemplazo, dentro de la misma familia.

El access token renovado siempre es distinto del anterior (`jti`). Su `exp` es posterior al del
sustituido siempre que haya pasado al menos un segundo desde la emisión anterior: `exp` tiene
granularidad de segundos.

**Errores**: `401` para cookie ausente, vacía o de más de 256 caracteres, y para token
inexistente, expirado, revocado, ya consumido o de usuario desactivado; todos con el **mismo
cuerpo** (`detail: "La sesión no es válida o ha expirado"`), sin distinguir el motivo, y con un
`Set-Cookie: fcv_refresh=; Path=/api/auth; Max-Age=0; …` que la borra. Ya no hay `400`: no hay
cuerpo que validar.

> **Reuso**: presentar un refresh token ya consumido revoca la **familia completa** —también el
> token legítimo más reciente— y obliga a iniciar sesión otra vez. Por eso `citas-web` mantiene
> una sola renovación en vuelo: dos renovaciones simultáneas con el mismo token se leerían como
> un robo.

### `POST /api/auth/logout` — cerrar sesión (HU-004)

**Sin cuerpo** (D36): lee la cookie `fcv_refresh`; un cuerpo, si llega, se ignora y no revoca
nada.

**204** sin cuerpo y con el `Set-Cookie` que borra la cookie (`Max-Age=0`). Revoca la familia
completa del token. Es **idempotente**: repetirlo, enviar un token inexistente o no enviar cookie
sigue devolviendo 204 con la misma cabecera — no confirma ni desmiente que el token existiera.

Ignora la cabecera `Authorization`: un Bearer caducado o inválido no impide revocar.

**Errores**: ninguno propio. Antes de D36 daba `400` por cuerpo ausente o inválido.

### `GET /api/me` — usuario autenticado

Requiere `Authorization: Bearer <accessToken>`.

**200** → el mismo `UserResponse` del registro, con los roles tomados del contexto de seguridad
(authorities `ROLE_*` del access token, ya sin prefijo).

**Errores**: `401` sin token, con token mal firmado, expirado o de otro emisor · `404` si el
usuario del token ya no existe.

### `GET /api/catalogs/insurance-plans` — planes de EPS seleccionables (HU-009)

**Público, sin token.** Es la **única** lectura de catálogo pública: la consume el formulario de
registro, que por definición todavía no tiene sesión. El resto de `/api/catalogs/**` sigue
exigiendo access token.

**200** → lista ordenada por nombre de EPS y, dentro de cada EPS, por nombre de plan:

```json
[
  {
    "id": 3,
    "code": "CONTRIB_BASICO",
    "name": "Plan básico",
    "eps": { "id": 1, "code": "EPS_DEMO", "name": "EPS de prueba" },
    "regime": { "id": 1, "code": "CONTRIBUTIVO", "name": "Régimen contributivo" }
  }
]
```

- Solo aparecen los planes **activos** cuya **EPS también está activa** (RF-06). El mismo
  predicado valida el `insurancePlanId` del registro, así que el cliente no puede ofrecer algo
  que el servidor rechazaría.
- La EPS y el régimen se **derivan** del plan; la afiliación del usuario no los copia (3FN).
- La ruta es pública para **todos los métodos**, no solo `GET`: un `POST`, `PUT` o `DELETE` llega
  a MVC y responde `405`, como el resto de catálogos de solo lectura (HU-010 CA-06), en vez del
  `401` que daría la cadena de seguridad.

## S4 — perfil, afiliación y recuperación de contraseña (acordado el 2026-09-25, antes de implementar)

Decisiones en [[dec-006-decisiones-s4-ciclo-de-vida]] (D25–D27, D29).

### Política de contraseña (D29, INC-001)

Al **fijar** una contraseña (registro, restablecimiento, alta de profesional por el ADMIN):
mínimo 8 caracteres, al menos una letra y un dígito, máximo 72 bytes UTF-8. Si no cumple → `400`
con `fieldErrors.password` en español. El login **no** aplica la política: las cuentas creadas
antes siguen entrando.

**Implementado (2026-09-25)** en `domain/auth/PasswordPolicy` (única fuente de la regla), que usan
la anotación `@PasswordPolicyCompliant` de los DTO y, como defensa en profundidad, los casos de
uso de registro, alta de profesional y restablecimiento. Precisiones que el acuerdo no fijaba:

- **Mensajes**, uno por campo y en este orden: `debe tener al menos 8 caracteres` → `no debe
  superar 72 bytes en UTF-8 (la ñ y las vocales con tilde ocupan 2 bytes; los emojis, 4)` →
  `debe combinar al menos una letra y un número`. Campo vacío o ausente: `no debe estar vacío`.
- Los caracteres se cuentan como unidades UTF-16 (`String.length()`), igual que `value.length`
  en `citas-web`; el dígito es `0-9`, igual que el `/[0-9]/` del cliente.
- **Desviación:** este `400` sale de Bean Validation y, como todos los `400` de Bean Validation
  del slice de identidad, lleva `fieldErrors` pero **no** la extensión `code: "VALIDATION"`. Solo
  la lleva si la regla la rechaza en el caso de uso, cosa que no ocurre con la entrada REST
  actual. El cliente debe decidir por `fieldErrors`, no por `code`.
- `BootstrapAdminUseCase` (primer ADMIN desde variables de entorno, D5) no aplica la política: no
  es una contraseña que fije un usuario por la API.

### Perfil (HU-008) y afiliación (HU-009, segundo corte)

```ts
Affiliation  { id, plan: InsurancePlan /* mismo cuerpo que el catálogo público */, startedOn }
UserResponse + { affiliation: Affiliation | null }   // aditivo; null en ADMIN y PROFESSIONAL
```

| Método | Ruta | Rol | Cuerpo | Respuesta |
|---|---|---|---|---|
| GET | `/api/me` | autenticado | — | `UserResponse` con `affiliation` |
| PUT | `/api/me` | autenticado | `{ firstNames, lastNames, phone }` | 200 `UserResponse` · 400 `VALIDATION` · 400 `FIELD_NOT_EDITABLE` (`field`) si llega `email`, `documentType`, `documentNumber`, `password` o `roles` (D25) |
| PUT | `/api/me/affiliation` | USER | `{ insurancePlanId }` | 200 `Affiliation`. Cierra la vigente (`ended_on` = hoy) y abre una nueva; mismo plan = sin cambios · 422 `INSURANCE_PLAN_UNAVAILABLE` (D26) |
| DELETE | `/api/me/affiliation` | USER | — | 204. Cierra la vigente sin reemplazo; sin vigente → 204 igual |

El titular sale siempre del token: no hay forma de editar el perfil de otro (HU-008 CA-04/05).
`SecurityConfig` pasa de `/api/me` a `/api/me` + `/api/me/**`.

### Recuperación de contraseña (HU-006, HU-007)

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| POST | `/api/auth/password-recovery` | `{ email }` | **202** `{ message }` idéntico exista o no el email · 400 email inválido |
| POST | `/api/auth/password-reset` | `{ token, newPassword }` | **204** · 400 `VALIDATION` (política) · 400 `RESET_TOKEN_INVALID` (inexistente, caducado, usado o revocado: una sola respuesta) |

- Token aleatorio de un solo uso; en `password_reset_tokens` (V1) solo se guarda su SHA-256.
  Vigencia `PASSWORD_RESET_MINUTES` (por defecto 30). Pedir uno nuevo revoca los anteriores.
- **Exposición en laboratorio (D27):** si `PASSWORD_RESET_EXPOSE_TOKEN=true`, la respuesta de un
  email **existente** añade `devToken`. Por defecto `false`. Con la exposición activa, la
  respuesta deja de ser idéntica: es una excepción aceptada solo para el laboratorio. HU-006
  CA-02 se verifica con la exposición apagada. El token **nunca** se escribe en logs.
- Restablecer consume el token, cambia el hash y **revoca todos los refresh tokens** del usuario.
- Ambas rutas son públicas (`SecurityConfig` las añade a las de `/api/auth/**`).
- El frontend enlaza el paso 2 como `/restablecer-password?token=…`.

**Aclaraciones (2026-09-25):**
- El error de política al restablecer llega como `fieldErrors.newPassword`, que es el nombre del
  campo en el cuerpo. En registro y alta de profesional sigue siendo `fieldErrors.password`.
- "Letra" significa **cualquier letra Unicode** (`\p{L}`): `ñ` y las vocales con tilde cuentan. El
  cliente y el servidor usan la misma definición.
- `PUT /api/me/affiliation` con el plan que ya está vigente responde 200 con la afiliación vigente,
  sin cerrarla ni crear otra.

**Perfil y afiliación implementados (2026-09-25, F6)** — `MeController`, `ProfileUseCase`,
`ManageAffiliationUseCase`, `domain/user/ProfilePolicy`. Lo que el acuerdo no fijaba:

- `FIELD_NOT_EDITABLE`: `400`, `title: "Datos inválidos"`, `code: "FIELD_NOT_EDITABLE"`,
  `field: "<campo>"` y también `fieldErrors.<campo>`. Un campo fijo **presente cuenta aunque su
  valor sea `null`**; si llegan varios, se informa el primero en el orden `email`, `documentType`,
  `documentNumber`, `password`, `roles`. Los campos desconocidos (p. ej. `id`, `userId`) se ignoran
  y el titular sigue saliendo del token. La operación es todo o nada: no se aplican los editables
  que viajaban junto al campo fijo.
- Orden de validación: primero Bean Validation de los editables (`firstNames`/`lastNames` 1–100,
  `phone` 1–30, obligatorios, como el registro → 400 con `fieldErrors`), después el campo fijo.
- `PUT /api/me` responde el `UserResponse` con los roles del token y la `affiliation` vigente
  (omitida si no hay). La respuesta del **registro** no cambia de forma: nunca lleva `affiliation`.
- `PUT /api/me/affiliation` sin `insurancePlanId` → 400 `fieldErrors.insurancePlanId`. El plan de
  la afiliación mostrada **no** se filtra por activo: una afiliación a un plan o EPS ya desactivados
  se sigue mostrando (HU-012 CA-05). `ended_on` = hoy en America/Bogota. Dos cambios simultáneos
  del mismo usuario se serializan (bloqueo de la fila del usuario).
- **Divergencia con HU-009 CA-03:** el CA pide 409 al repetir el plan ya registrado; este contrato
  (acordado y ya implementado en el frontend) responde **200 sin cambios**. En ambos casos no se crea
  un segundo registro. Queda para que la verificación y el usuario decidan si el CA se reescribe.
- V9 sustituye `uq_affiliations_user_plan` por `uq_affiliations_user_plan_current
  (user_id, eps_plan_id, current_marker)` (D32): se puede volver a un plan ya usado.

**Recuperación implementada (2026-09-25)** — `PasswordRecoveryController`,
`RequestPasswordRecoveryUseCase`, `ResetPasswordUseCase`. Lo que el acuerdo no fijaba:

- `202` → `{ "message": "Si el correo corresponde a una cuenta, recibirás las instrucciones para
  restablecer la contraseña" }`; con D27 activa y email existente, además `"devToken": "<opaco>"`.
  `400` con `fieldErrors.email` si falta o no es un email.
- `RESET_TOKEN_INVALID`: `400`, `title: "Datos inválidos"`, `code: "RESET_TOKEN_INVALID"`,
  `detail: "El enlace para restablecer la contraseña no es válido o ya expiró; solicita uno
  nuevo"`, **sin** `fieldErrors`. `token` ausente o en blanco da `400` con `fieldErrors.token`.
- **Cuenta inactiva:** pedir la recuperación no crea token (misma respuesta `202`), y restablecer
  con un token de una cuenta que se desactivó después responde `RESET_TOKEN_INVALID`.
- Token: 32 bytes de `SecureRandom` en Base64 URL (43 caracteres), el mismo generador que el
  refresh token. En BD, solo su SHA-256. Pedir uno nuevo revoca los anteriores sin usar con
  `revoked_reason = SUPERSEDED`.
- Restablecer, en **una** transacción: bloquea el token, cambia el hash, lo consume y revoca todas
  las familias de refresh del usuario (`revoked_reason = PASSWORD_RESET`, D34). La política se
  comprueba antes: una contraseña inválida no consume el token.
- La entrega pasa por el puerto `PasswordResetNotifier`. El adaptador de laboratorio no envía
  correo y registra solo el id interno del usuario y la caducidad; nunca el token ni el email.
- **Riesgo aceptado:** la respuesta es idéntica, pero el tiempo no: con un email existente la API
  escribe en la base y con uno inexistente no. Igualarlo no lo pide el PRD; queda anotado.

## S4 — refresh token en cookie `HttpOnly` (D36, 2026-09-25)

**Motivo:** el usuario pidió que recargar la página (F5) no cierre la sesión. Hasta ahora el
refresh token vivía solo en la memoria de JavaScript ([[dec-002-rotacion-refresh-tokens]]), así
que se perdía al recargar. Moverlo a `localStorage` lo expondría a cualquier XSS; una cookie
`HttpOnly` sobrevive a la recarga y JavaScript no la puede leer. Esto cierra la pregunta abierta
S4 de [[sintesis-preguntas-abiertas]].

**Sustituye** a lo que dicen arriba sobre el refresh token en el cuerpo:

| Ruta | Cambio |
|---|---|
| `POST /api/auth/login` | 200 `{ accessToken, tokenType, expiresIn }` — **sin `refreshToken`** en el cuerpo. Añade `Set-Cookie: fcv_refresh=<opaco>; HttpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age=<JWT_REFRESH_DAYS en s>` |
| `POST /api/auth/refresh` | **Sin cuerpo**: lee la cookie `fcv_refresh`. 200 igual que el login y **rota** la cookie. Cookie ausente, inválida, caducada, revocada o reusada → 401 y la cookie se borra (`Max-Age=0`). La detección de reuso por familia no cambia |
| `POST /api/auth/logout` | **Sin cuerpo**: lee la cookie, revoca la familia y la borra. Sigue siendo idempotente: 204 siempre |

- `Secure` se controla con `REFRESH_COOKIE_SECURE` (por defecto `true`). Los navegadores aceptan
  cookies `Secure` en `http://localhost`, así que el laboratorio funciona sin HTTPS.
- **CORS:** `allowCredentials(true)`, con los orígenes explícitos de `FRONTEND_ORIGIN` (nunca `*`).
- **CSRF:** `SameSite=Strict`, más `Path=/api/auth`: la cookie solo viaja a las tres rutas de
  sesión y nunca desde otro sitio. `localhost:5174` y `localhost:8081` son el mismo *site* (el
  puerto no cuenta para `SameSite`). El access token sigue yendo en `Authorization: Bearer` y en
  memoria.
- **Frontend:** `credentials: 'include'` en las rutas de `/api/auth/**`. Al arrancar, antes de
  decidir si hay sesión, llama una vez a `refresh`: si da 200, restaura la sesión; si da 401,
  muestra el login. La renovación sigue siendo *single-flight*.

**Implementado en `citas-api` (2026-09-25)** — `RefreshTokenCookies` y `AuthController`. Lo que
el acuerdo no fijaba:

- La cookie no lleva `Domain` (queda ligada al host de la API). La que la borra repite
  `Path`, `Secure`, `HttpOnly` y `SameSite` con valor vacío y `Max-Age=0`; si no coincidiera el
  `Path`, el navegador no borraría la real.
- Una cookie de más de 256 caracteres se trata como inválida sin consultar la base (el mismo tope
  que tenía el campo `refreshToken`).
- `FRONTEND_ORIGIN` con `*` o un patrón **impide arrancar** la API: con credenciales, un comodín
  entregaría la sesión a cualquier sitio.
- **Compatibilidad:** el `citas-web` anterior a D36 sigue pudiendo iniciar sesión (el cuerpo trae
  `accessToken`), pero no recibe `refreshToken` en el cuerpo y no envía credenciales, así que no
  puede renovar ni revocar: la sesión termina cuando caduca el access token (15 min) y su logout
  no revoca nada en el servidor. Requiere el cambio de frontend descrito arriba.

## CORS

- Orígenes **exactos** desde `app.cors.allowed-origins`, alimentado por `FRONTEND_ORIGIN`.
  Nunca comodín (PRD §8): con `*` o un patrón la API no arranca.
- Desde D36, `Access-Control-Allow-Credentials: true` para esos orígenes (la cookie del refresh
  token lo necesita). `CorsConfigTest` fija el preflight desde `http://localhost:5174`.
- **En este workspace el origen permitido es `http://localhost:5174`**, no el 5173: desde el
  2026-09-23 el laboratorio tiene proyecto Docker propio y el servidor de desarrollo de Vite se
  fija al 5174 con `strictPort` (`.env.example` de la raíz: `FRONTEND_ORIGIN=http://localhost:5174`;
  ver [[riesgo-dos-copias-mismo-proyecto-docker]]). El *fallback* escrito en el código sigue
  siendo `${FRONTEND_ORIGIN:http://localhost:5173}` (`application.yml:69`), y las pruebas de
  integración usan 5173 porque `application-test.yml:28` lo fija así: el 5173 es un valor por
  defecto histórico, **no** el origen que sirve hoy la API.
- Un preflight desde un origen no configurado recibe `403` sin cabeceras de permiso, con cuerpo
  en texto plano `Invalid CORS request`: no es `ProblemDetail`, porque lo escribe
  `DefaultCorsProcessor` de Spring antes de llegar a los manejadores propios. El navegador no
  expone ese cuerpo al código del frontend.
- `FRONTEND_ORIGIN` se pasa al contenedor desde `docker-compose.yml`.

## Rutas públicas

Solo estas escapan a la autenticación: `POST /api/auth/register`, `/login`, `/refresh`,
`/logout`, `/password-recovery`, `/password-reset` (S4, HU-006/HU-007), la lectura de catálogo `/api/catalogs/insurance-plans`, más `/actuator/health`,
`/actuator/health/**` y `/error`. Todo lo demás exige access token válido; por eso un método o
una ruta inexistentes sin token responden `401`, no `405` ni `404`: la cadena de seguridad actúa
antes.

`/api/catalogs/insurance-plans` se declara **sin método**, a diferencia de las seis rutas de
autenticación: así una escritura sobre ella llega a MVC y responde `405` en vez de `401`, igual
que el resto de catálogos fijos (HU-010 CA-06). Es pública porque el formulario de registro la
necesita antes de existir la sesión, y no expone nada sensible: son planes comerciales de EPS.

Las seis rutas `POST` de autenticación, con coincidencia **exacta**, **ignoran** la cabecera
`Authorization` (`PublicEndpointsBearerTokenResolver`): se autentican con el cuerpo o con la
cookie `fcv_refresh`, y un Bearer caducado no puede impedir iniciar sesión, renovar, revocar ni
recuperar la contraseña. `GET` sobre esas rutas, subrutas
como `/api/auth/login/extra` y cualquier otra ruta siguen leyendo el token. Un solo matcher,
`SecurityConfig.PUBLIC_AUTH_ENDPOINTS`, alimenta a la vez el `permitAll` y el resolver, para que
no puedan divergir.

## Reglas e invariantes

- Toda HU que publique o modifique un endpoint actualiza este documento como parte de su DoD
  (HU-033 CA-08). Una discrepancia entre contrato e implementación bloquea el cierre de esa HU.
- La URL base del backend vive en `VITE_API_URL` de `citas-web`, nunca escrita en el código.
- Ningún ejemplo de este documento usa datos reales: todos los correos son de dominios de prueba.
- Los DTO de petición redefinen `toString()` para enmascarar contraseñas y tokens; un log
  accidental del objeto no los filtra.
- La API no arranca si `JWT_ACCESS_SECRET` falta, mide menos de 32 bytes o conserva el valor de
  ejemplo (contiene `CHANGE_ME`): así nadie despliega con la clave pública del `.env.example`.

## Relacionado

- [[dec-002-rotacion-refresh-tokens]] — por qué el refresh es opaco, rota y revoca por familia
- [[dec-001-libreria-jwt]] — por qué los beans `JwtEncoder`/`JwtDecoder` son propios
- [[arq-hexagonal-seguridad]] — dónde vive cada pieza y por qué el controlador no decide nada
- [[riesgo-spring-security-65-trampas]] — el prefijo `ROLE_` y la longitud del secreto HMAC
- [[contrato-rest-citas]] — el resto de `/api/catalogs/**`, que **sí** exige token
- [[datos-modelo-3fn]] — `affiliations`, `eps` y `eps_plans`, las tablas detrás de HU-009
- [[riesgo-dos-copias-mismo-proyecto-docker]] — el origen CORS y el puerto reales de este workspace

## Preguntas abiertas

- **INC-038 — formato de documentación**: este documento es Markdown mantenido a mano. No se
  añadió springdoc/OpenAPI: habría sido una dependencia nueva, y CA-01 de HU-033 exige el
  contenido, no la herramienta. Queda como candidato para S3 si se quiere el contrato generado.
- **INC-039 — versionado del contrato** entre repositorios: sin decidir. Mientras tanto rige
  CA-08: contrato e implementación se mueven juntos en cada HU.
- ~~**INC-001 — política de contraseña**~~: cerrada por D29 e implementada el 2026-09-25 (§S4).
- El access token no es revocable durante sus 15 minutos; el logout revoca el refresh, no el
  access. Pendiente de confirmación (ver [[sintesis-preguntas-abiertas]]).
- Errores que Spring genera por su cuenta fuera de los manejadores propios conservan título y
  detalle en inglés: `415 Unsupported Media Type` por un `Content-Type` no soportado; y, **con
  token válido**, `404 Not Found` en una ruta inexistente y `405 Method Not Allowed` en un método
  no soportado. Sin token, esos dos dan el `401` de la cadena de seguridad. No afectan a los
  flujos de identidad.
- **`WWW-Authenticate` con motivo en inglés.** El `error_description` del 401 distingue un token
  caducado de uno falsificado y va en inglés. Es el comportamiento estándar de RFC 6750 y el
  cuerpo sigue siendo uniforme. ¿Se deja así, o se emite solo `Bearer error="invalid_token"`? Es
  una decisión, no un defecto.

## Historial

- 2026-09-25 — implementados en `citas-api` D36 (refresh token en cookie `fcv_refresh`, CORS con
  credenciales), D29 (política de contraseña en el servidor) y la recuperación de contraseña
  (HU-006/HU-007, D27, D34). Cambian `login`, `refresh` y `logout`: el refresh token sale del
  cuerpo; `refresh` y `logout` ya no tienen cuerpo ni `400`. Las precisiones y la desviación del
  `code: "VALIDATION"` están en §S4.

- 2026-09-23 (LINT) — se rellenó el `fuentes` del frontmatter, que estaba vacío pese a que la
  página se había contrastado contra el código; y se corrigió el origen CORS: decía
  `http://localhost:5173` sin matizar, cuando el efectivo es el **5174**.
- 2026-09-17 — página creada al cerrar el hallazgo de la verificación independiente de S2, que
  marcó HU-033 como FAIL en sus cuatro puntos de DoD por no existir contrato documentado.
  **INC-040 decidido**: el formato de error uniforme es `ProblemDetail` (RFC 9457), que es lo que
  el código ya producía; queda así registrado en vez de quedar implícito.
- 2026-09-17 — la segunda verificación independiente encontró seis divergencias. Tres se
  corrigieron en el código: el 400 por cuerpo ilegible salía en inglés, `fieldErrors` dependía de
  `Accept-Language`, y un Bearer inválido bloqueaba las rutas públicas. Las otras tres se
  corrigieron aquí: el título del 403, el campo `instance` y la cabecera `WWW-Authenticate`, y el
  403 del preflight CORS. Además se documentaron el `jti` y la granularidad de `exp`.
- 2026-09-17 — la contraseña se limita en bytes UTF-8, no en caracteres. Antes el registro daba
  500 con contraseñas multibyte de más de 72 bytes, y el login aceptaba una contraseña distinta
  que compartiera los primeros 72 bytes. El login ya no valida la longitud: responde el 401
  genérico.
- 2026-09-17 — la tercera verificación dejó dos divergencias documentales, ya corregidas aquí:
  la cabecera `WWW-Authenticate` con `error_description` en inglés y el máximo de 256 caracteres
  de `refreshToken`. También se precisaron las rutas de `actuator` y el 401 ante rutas
  inexistentes.
- 2026-09-23 — HU-009 acotada a la ruta de registro: `POST /api/auth/register` acepta el campo
  opcional `insurancePlanId` y responde `422 INSURANCE_PLAN_UNAVAILABLE` si el plan no es
  seleccionable; aparece el primer `422` del slice de identidad y la primera ruta de catálogo
  pública, `GET /api/catalogs/insurance-plans`. La forma de la respuesta de registro no cambió.
