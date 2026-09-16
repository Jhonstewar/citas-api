---
titulo: "Decisión 002 — Refresh tokens rotativos con familia y detección de reuso"
tipo: decision
estado: Provisional
actualizado: 2026-09-16
fuentes: ["[[RES-001-spring-security-jwt]]", "PRD.md §RF-02", "PRD.md §8"]
tags: [decision, seguridad, jwt, backend, datos]
---

# Decisión 002 — Rotación de refresh tokens

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

## Relacionado

- [[dec-001-libreria-jwt]]
- [[datos-modelo-3fn]] — tablas `refresh_tokens` y `password_reset_tokens`
- [[arq-hexagonal-seguridad]]

## Pregunta abierta

¿Se acepta la ventana de 15 minutos de un access token no revocable, o se quiere una lista de
revocación? Añadirla implica estado en cada petición y contradice el diseño sin sesión. **Decisión
pendiente del usuario.**

## Estado de verificación

`Provisional`: procede de [[RES-001-spring-security-jwt]], aún no implementada.

## Historial

- 2026-09-16 — decisión registrada a partir de la investigación RES-001.
