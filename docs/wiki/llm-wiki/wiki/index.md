---
titulo: "Índice de la LLM Wiki"
tipo: sintesis
estado: Vigente
actualizado: 2026-09-16
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

_(sin páginas todavía — se escriben cuando exista el primer endpoint real)_

## Decisiones

- [[dec-001-libreria-jwt]] — se usa el resource-server de Spring, no jjwt; con HMAC los beans `JwtDecoder`/`JwtEncoder` son obligatorios
- [[dec-002-rotacion-refresh-tokens]] — refresh opaco rotativo con familia y detección de reuso, persistido como SHA-256
- [[dec-003-libro-unico-slot-reservations]] — una sola tabla con PK `slot_id` hace imposible la doble reserva a nivel de motor

## Datos y modelo

- [[datos-modelo-3fn]] — 4 migraciones Flyway, 24 tablas, qué garantiza el motor y qué el dominio, preguntas abiertas del esquema

## Riesgos

- [[riesgo-spring-security-65-trampas]] — tres trampas de Spring Security 6.5.x detectadas antes de escribir código: API del encoder, longitud del secreto HMAC y prefijo `ROLE_`

## Síntesis

- [[sintesis-preguntas-abiertas]] — huecos del PRD detectados al especificar las 33 HU, más dos defectos de esquema verificados (auditoría en cascada, origen PROFESSIONAL)

## Fuentes en `raw/`

- `MODELO-DATOS-3FN.md` — diseño 3FN propio: tablas, claves, dependencias funcionales, justificación de formas normales
- `RES-001-spring-security-jwt.md` — investigación con 18 fuentes sobre JWT access+refresh en Spring Boot 3.5.x

---

**Nota para el agente:** este índice se llena con el workflow `INGEST` de fuentes reales y con el
`LEARN` de cada interacción. No lo rellenes por adelantado con páginas especulativas.
