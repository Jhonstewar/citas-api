---
titulo: "Índice de la LLM Wiki"
tipo: sintesis
estado: Vigente
actualizado: 2026-09-23
tags: [indice]
---

# Índice — LLM Wiki de FCV Citas

Catálogo por contenido de la wiki. **Empieza aquí**: elige las páginas relevantes desde este
índice y entra en ellas. Se actualiza cada vez que cambia la estructura.

Convenciones y workflows: [`../schema/SCHEMA.md`](../schema/SCHEMA.md).
Historia cronológica: [[log]].

## Estado del proyecto

- **Sesión en curso:** S3 — código completo (F1–F10); falta el cierre de F11: HU a `Completada`, LINT de wiki y commit de cierre (plan: `PLAN_RETOMA_S3.md` en la raíz)
- **En paralelo:** rediseño del frontend con los mockups de Stitch, ya aplicado en código — ver [[dec-005-sistema-visual-stitch]]
- **Sesión anterior:** S2 cerrada salvo la prueba manual en navegador
- **Repos:** `Jhonstewar/citas-api`, `Jhonstewar/citas-web`, `Jhonstewar/FCV_Proyecto_Citas_v1` (origen histórico: `jhonnunez-svg`)
- **Stack fijado:** Java 21 LTS · Spring Boot 3.5.x · hexagonal · MySQL 8.4 · Flyway · JWT ·
  React + TypeScript + Vite · Node 24 LTS

## Dominio

_(sin páginas todavía)_

## Arquitectura

- [[arq-hexagonal-seguridad]] — la regla de no-dependencia del framework, los puertos de seguridad del dominio y por qué `SecurityConfig` no lleva puerto

## Contratos REST

- [[contrato-rest-identidad]] — registro, login, refresh rotativo, logout y `/api/me`: rutas, cuerpos, `ProblemDetail` como formato de error uniforme, tabla de códigos y CORS
- [[contrato-rest-citas]] — S3: catálogos, especialidades, profesionales, bloques, disponibilidad, reserva general/especializada, bandeja y decisión; tabla de códigos 400/409/422 con `code`

## Decisiones

- [[dec-001-libreria-jwt]] — se usa el resource-server de Spring, no jjwt; con HMAC los beans `JwtDecoder`/`JwtEncoder` son obligatorios
- [[dec-002-rotacion-refresh-tokens]] — refresh opaco rotativo con familia y detección de reuso, persistido como SHA-256; qué obliga a hacer en el cliente (renovación única, épocas de sesión)
- [[dec-003-libro-unico-slot-reservations]] — una sola tabla con PK `slot_id` hace imposible la doble reserva a nivel de motor
- [[dec-004-decisiones-s3-reserva]] — D5–D13, provisionales: primer ADMIN por variables de entorno, Medicina General precargada, V5 de auditoría, 60 min en un mismo bloque, hooks de git
- [[dec-005-sistema-visual-stitch]] — el `DESIGN.md` de Stitch es la fuente de verdad visual; fuentes autoalojadas sin CDN, tres desviaciones por contraste y todo lo que Stitch inventó se descarta

## Datos y modelo

- [[datos-modelo-3fn]] — 4 migraciones Flyway, 24 tablas, qué garantiza el motor y qué el dominio, cómo se prueba el esquema (desde vacío, base de pruebas aislada), preguntas abiertas

## Riesgos

- [[riesgo-spring-security-65-trampas]] — cinco trampas de Spring Security 6.5.x: API del encoder, secreto HMAC (y placeholders), prefijo `ROLE_`, Bearer inválido frente a `permitAll`, y el truncado a 72 bytes de BCrypt en `checkpw`
- [[riesgo-prueba-intermitente-flyway]] — `FlywayMigratesEmptySchemaTest` falló una vez sin relación con el cambio; hipótesis: `target/` en el montaje de Windows
- [[riesgo-dos-copias-mismo-proyecto-docker]] — otra copia del laboratorio poseía los contenedores por compartir `COMPOSE_PROJECT_NAME`; mitigado con proyecto, nombres y puertos propios, y `strictPort` en Vite
- [[riesgo-zona-horaria-columnas-time]] — mitigado: la zona del JVM se fijaba tarde (`@PostConstruct`) y las horas `TIME` se desplazaban 5 h según el orden de las pruebas

## Síntesis

- [[sintesis-preguntas-abiertas]] — huecos del PRD detectados al especificar las 33 HU, dos defectos de esquema verificados y las decisiones del agente bajo aprobación delegada pendientes de confirmar (D1–D4)

## Fuentes en `raw/`

- `MODELO-DATOS-3FN.md` — diseño 3FN propio: tablas, claves, dependencias funcionales, justificación de formas normales
- `RES-001-spring-security-jwt.md` — investigación con 18 fuentes sobre JWT access+refresh en Spring Boot 3.5.x

---

**Nota para el agente:** este índice se llena con el workflow `INGEST` de fuentes reales y con el
`LEARN` de cada interacción. No lo rellenes por adelantado con páginas especulativas.
