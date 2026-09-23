---
titulo: "Contrato REST — identidad y acceso"
tipo: contrato
estado: Vigente
actualizado: 2026-09-23
fuentes: []
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
| Autenticación | `Authorization: Bearer <accessToken>`. Sin cookies: la API es *stateless* (`SessionCreationPolicy.STATELESS`). |
| CSRF | Deshabilitado, coherente con una API sin cookies de sesión. |
| Refresh token | Viaja SIEMPRE en el cuerpo, NUNCA en la ruta ni en la cadena de consulta (HU-003 CA-08). |
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
| `password` | obligatorio, máx. **72 bytes UTF-8** (límite de BCrypt), no 72 caracteres: la ñ y las vocales con tilde ocupan 2 bytes y los emojis 4. Sin política de complejidad — INC-001 |
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

**200** → `TokenResponse`:

```json
{
  "accessToken": "<JWT HS256>",
  "refreshToken": "<valor opaco>",
  "tokenType": "Bearer",
  "expiresIn": 900
}
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

Público: se autentica con el refresh token del cuerpo e **ignora** la cabecera `Authorization`,
así que un access token caducado no impide renovar.

```json
{ "refreshToken": "<valor opaco>" }
```

`refreshToken`: obligatorio, máx. 256 caracteres. Mismo DTO (`RefreshTokenRequest`) en `logout`.

**200** → `TokenResponse`, con access token nuevo **y refresh token nuevo**: la renovación
**rota** el token. El presentado queda consumido y enlazado a su reemplazo, dentro de la misma
familia.

El access token renovado siempre es distinto del anterior (`jti`). Su `exp` es posterior al del
sustituido siempre que haya pasado al menos un segundo desde la emisión anterior: `exp` tiene
granularidad de segundos.

**Errores**: `400` validación · `401` para token inexistente, expirado, revocado, ya consumido o
de usuario desactivado; todos con el **mismo cuerpo**, sin distinguir el motivo.

> **Reuso**: presentar un refresh token ya consumido revoca la **familia completa** —también el
> token legítimo más reciente— y obliga a iniciar sesión otra vez. Por eso `citas-web` mantiene
> una sola renovación en vuelo: dos renovaciones simultáneas con el mismo token se leerían como
> un robo.

### `POST /api/auth/logout` — cerrar sesión (HU-004)

```json
{ "refreshToken": "<valor opaco>" }
```

**204** sin cuerpo. Revoca la familia completa del token. Es **idempotente**: repetirlo, o
enviar un token inexistente, sigue devolviendo 204 — no confirma ni desmiente que el token
existiera.

Ignora la cabecera `Authorization`: un Bearer caducado o inválido no impide revocar.

**Errores**: `400` si el campo viene vacío o supera 256 caracteres (con `fieldErrors`), o si el
cuerpo falta o no es JSON (sin `fieldErrors`).

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

## CORS

- Orígenes **exactos** desde `app.cors.allowed-origins`, alimentado por `FRONTEND_ORIGIN`
  (por defecto `http://localhost:5173`). Nunca comodín (PRD §8).
- Un preflight desde un origen no configurado recibe `403` sin cabeceras de permiso, con cuerpo
  en texto plano `Invalid CORS request`: no es `ProblemDetail`, porque lo escribe
  `DefaultCorsProcessor` de Spring antes de llegar a los manejadores propios. El navegador no
  expone ese cuerpo al código del frontend.
- `FRONTEND_ORIGIN` se pasa al contenedor desde `docker-compose.yml`.

## Rutas públicas

Solo estas escapan a la autenticación: `POST /api/auth/register`, `/login`, `/refresh`,
`/logout`, la lectura de catálogo `/api/catalogs/insurance-plans`, más `/actuator/health`,
`/actuator/health/**` y `/error`. Todo lo demás exige access token válido; por eso un método o
una ruta inexistentes sin token responden `401`, no `405` ni `404`: la cadena de seguridad actúa
antes.

`/api/catalogs/insurance-plans` se declara **sin método**, a diferencia de las cuatro rutas de
autenticación: así una escritura sobre ella llega a MVC y responde `405` en vez de `401`, igual
que el resto de catálogos fijos (HU-010 CA-06). Es pública porque el formulario de registro la
necesita antes de existir la sesión, y no expone nada sensible: son planes comerciales de EPS.

Las cuatro rutas `POST` de autenticación, con coincidencia **exacta**, **ignoran** la cabecera
`Authorization` (`PublicEndpointsBearerTokenResolver`): se autentican con el cuerpo, y un Bearer
caducado no puede impedir iniciar sesión, renovar ni revocar. `GET` sobre esas rutas, subrutas
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

## Preguntas abiertas

- **INC-038 — formato de documentación**: este documento es Markdown mantenido a mano. No se
  añadió springdoc/OpenAPI: habría sido una dependencia nueva, y CA-01 de HU-033 exige el
  contenido, no la herramienta. Queda como candidato para S3 si se quiere el contrato generado.
- **INC-039 — versionado del contrato** entre repositorios: sin decidir. Mientras tanto rige
  CA-08: contrato e implementación se mueven juntos en cada HU.
- **INC-001 — política de contraseña**: hoy solo hay un máximo de 72 bytes, sin mínimo ni
  complejidad. Afecta al `400` de `register`.
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
