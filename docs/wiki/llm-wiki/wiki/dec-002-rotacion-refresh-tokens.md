---
titulo: "Decisión 002 — Refresh tokens rotativos con familia y detección de reuso"
tipo: decision
estado: Provisional
actualizado: 2026-09-25
fuentes: ["[[RES-001-spring-security-jwt]]", "PRD.md §RF-02", "PRD.md §8", "citas-web/src/auth/sessionManager.ts"]
tags: [decision, seguridad, jwt, backend, datos]
---

# Decisión 002 — Rotación de refresh tokens

> **Actualizada el 2026-09-25 (D36):** el **transporte** del refresh token cambió. Ya no viaja en
> el cuerpo ni vive en la memoria de JavaScript: va en la cookie `fcv_refresh` (`HttpOnly; Secure;
> SameSite=Strict; Path=/api/auth`), para que recargar la página no cierre la sesión. La rotación,
> las familias y la detección de reuso descritas abajo **no cambian**. Donde esta página hable de
> "memoria de JS" o de `{ refreshToken }` en el cuerpo, manda
> [[dec-006-decisiones-s4-ciclo-de-vida]] D36 y [[contrato-rest-identidad]] §S4.

## Decisión

Refresh tokens **rotativos, agrupados en familias, con detección de reuso** (patrón de RFC 9700 /
BCP 240).

El refresh token **no es un JWT**: es un valor **opaco** de 32 bytes de `SecureRandom`.

## Cómo se persiste

En MySQL se guarda **solo el hash SHA-256 en hexadecimal**, con índice `UNIQUE`.

**No se usa BCrypt para el refresh token.** Un valor de 32 bytes aleatorios ya tiene entropía
alta: no necesita una función deliberadamente lenta, y el salt de BCrypt impediría buscarlo por
índice. Esto **no** contradice el uso de BCrypt para passwords: ahí la entropía la pone un humano
y la lentitud es justamente la defensa.

Columnas necesarias: `family_id`, `used_at`, `revoked_at`, `revoked_reason`, `replaced_by_id`.

## Comportamiento

- Cada refresh consumido se marca `used_at` y emite uno nuevo en la **misma familia**.
- Si se presenta un token que ya tiene `used_at != null`, es señal de robo: **se revoca la familia
  entera**, no solo ese token.
- Logout = revocar la familia.
- El **access token dura 15 minutos y no es revocable**. Es una consecuencia aceptada de usar JWT
  sin estado: durante esa ventana, un access token robado sigue sirviendo. Queda documentado aquí
  como decisión consciente, no como descuido.

## Consecuencias en el cliente (`citas-web`)

La rotación obliga al frontend a ser cuidadoso. Todo vive en `citas-web/src/auth/sessionManager.ts`:

- **Una sola renovación en vuelo.** Dos renovaciones simultáneas presentarían el mismo refresh;
  la segunda cuenta como reuso y revoca la familia, así que el usuario acabaría expulsado. Todas
  las peticiones que reciben 401 a la vez esperan la misma promesa (`singleFlight.ts`).
- **Épocas de sesión.** Cada login y cada logout abren una época nueva. Una renovación que
  responde después del logout no aplica sus tokens, porque resucitaría la sesión cerrada, y un
  rechazo tardío tampoco cierra la sesión nueva. Descartarlos en el cliente basta: el logout
  revoca la familia en el servidor aunque su refresh ya estuviera rotado
  (`citas-api` `LogoutUseCase`), así que esos tokens no quedan vivos.
- **Un 401 se trata según de quién era el token** (`tokenStatus`). Si es el vigente, se renueva.
  Si es uno anterior de la misma sesión, otra petición ya renovó: basta con reintentar, sin rotar
  otra vez. Si es de una sesión ya cerrada, ni se reintenta ni se renueva, porque la petición se
  ejecutaría con la identidad de otra sesión, por ejemplo la de otro usuario que inició sesión en
  la misma pestaña.
- **Sin respuesta no es rechazo.** Si la red o el servidor fallan durante la renovación, la
  sesión sigue abierta y el error se propaga. Solo un 400/401 del servidor la termina.
- **Tokens solo en memoria**, fuera del contexto y del estado de React: React solo observa si hay
  sesión. Los datos y roles del usuario salen de `GET /api/me`, no de decodificar el JWT.

Cada una de estas reglas tiene una prueba que falla si se revierte el código (comprobado por
mutación el 2026-09-17).

## Relacionado

- [[dec-001-libreria-jwt]]
- [[datos-modelo-3fn]] — tablas `refresh_tokens` y `password_reset_tokens`
- [[arq-hexagonal-seguridad]]
- [[contrato-rest-identidad]] — `refresh` y `logout` tal como los expone la API

## Pregunta abierta

¿Se acepta la ventana de 15 minutos de un access token no revocable, o se quiere una lista de
revocación? Añadirla implica estado en cada petición y contradice el diseño sin sesión. **Decisión
pendiente del usuario.**

## Estado de verificación

**Implementada y verificada** en `RefreshSessionUseCase` (rotación, y revocación de la familia
ante reuso) y `LogoutUseCase` (revocación de la familia), con pruebas de integración. Sigue
`Provisional` porque la eligió el agente bajo aprobación delegada y el usuario aún no la ha
confirmado, ni tampoco la ventana de 15 minutos de arriba.

## Historial

- 2026-09-17 — marcada como implementada; añadidas las consecuencias en el cliente tras
  corregir cuatro carreras que encontró la verificación independiente de `citas-web`.
- 2026-09-16 — decisión registrada a partir de la investigación RES-001.
