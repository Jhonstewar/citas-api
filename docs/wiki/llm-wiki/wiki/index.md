---
titulo: "Índice de la LLM Wiki"
tipo: sintesis
estado: Vigente
actualizado: 2026-09-17
tags: [indice]
---

# Índice — LLM Wiki de FCV Citas

Catálogo por contenido de la wiki. **Empieza aquí**: elige las páginas relevantes desde este
índice y entra en ellas. Se actualiza cada vez que cambia la estructura.

Convenciones y workflows: [`../schema/SCHEMA.md`](../schema/SCHEMA.md).
Historia cronológica: [[log]].

## Estado del proyecto

- **Sesión en curso:** S2 — especificar, inicializar y construir el primer incremento
- **Repos:** `jhonnunez-svg/citas-api`, `jhonnunez-svg/citas-web`, `jhonnunez-svg/FCV_Proyecto_Citas_v1`
- **Stack fijado:** Java 21 LTS · Spring Boot 3.5.x · hexagonal · MySQL 8.4 · Flyway · JWT ·
  React + TypeScript + Vite · Node 24 LTS

## Dominio

_(sin páginas todavía)_

## Arquitectura

- [[arq-hexagonal-seguridad]] — la regla de no-dependencia del framework, los puertos de seguridad del dominio y por qué `SecurityConfig` no lleva puerto

## Contratos REST

- [[contrato-rest-identidad]] — registro, login, refresh rotativo, logout y `/api/me`: rutas, cuerpos, `ProblemDetail` como formato de error uniforme, tabla de códigos y CORS

## Decisiones

- [[dec-001-libreria-jwt]] — se usa el resource-server de Spring, no jjwt; con HMAC los beans `JwtDecoder`/`JwtEncoder` son obligatorios
- [[dec-002-rotacion-refresh-tokens]] — refresh opaco rotativo con familia y detección de reuso, persistido como SHA-256; qué obliga a hacer en el cliente (renovación única, épocas de sesión)
- [[dec-003-libro-unico-slot-reservations]] — una sola tabla con PK `slot_id` hace imposible la doble reserva a nivel de motor

## Datos y modelo

- [[datos-modelo-3fn]] — 4 migraciones Flyway, 24 tablas, qué garantiza el motor y qué el dominio, cómo se prueba el esquema (desde vacío, base de pruebas aislada), preguntas abiertas

## Riesgos

- [[riesgo-spring-security-65-trampas]] — cinco trampas de Spring Security 6.5.x: API del encoder, secreto HMAC (y placeholders), prefijo `ROLE_`, Bearer inválido frente a `permitAll`, y el truncado a 72 bytes de BCrypt en `checkpw`

## Síntesis

- [[sintesis-preguntas-abiertas]] — huecos del PRD detectados al especificar las 33 HU, dos defectos de esquema verificados y las decisiones del agente bajo aprobación delegada pendientes de confirmar (D1–D4)

## Fuentes en `raw/`

- `MODELO-DATOS-3FN.md` — diseño 3FN propio: tablas, claves, dependencias funcionales, justificación de formas normales
- `RES-001-spring-security-jwt.md` — investigación con 18 fuentes sobre JWT access+refresh en Spring Boot 3.5.x

---

**Nota para el agente:** este índice se llena con el workflow `INGEST` de fuentes reales y con el
`LEARN` de cada interacción. No lo rellenes por adelantado con páginas especulativas.
