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

- **Sesión en curso:** S3 — código completo (F1–F10); en F11 (cierre): LINT de wiki hecho el
  2026-09-23, quedan las HU a `Completada` y el commit de cierre
- **En paralelo:** rediseño del frontend con los mockups de Stitch, ya aplicado en código — ver [[dec-005-sistema-visual-stitch]]
- **Sesión anterior:** S2 cerrada salvo la prueba manual en navegador
- **Repos:** `Jhonstewar/citas-api`, `Jhonstewar/citas-web`, `Jhonstewar/FCV_Proyecto_Citas_v1` (origen histórico: `jhonnunez-svg`)
- **Stack fijado:** Java 21 LTS · Spring Boot 3.5.x · hexagonal · MySQL 8.4 · Flyway · JWT ·
  React + TypeScript + Vite · Node 24 LTS

### Cifras vigentes (verificadas el 2026-09-23)

Cualquier número distinto en una página de la wiki es historia fechada, no el estado de hoy.

| Qué | Valor | Dónde se comprobó |
|---|---|---|
| Suite del backend | **242** pruebas | `EVIDENCIAS_S3.md` §11 |
| Suite del frontend | **88** pruebas (11 archivos) | `npm test` ejecutado en `citas-web` |
| Migraciones Flyway | **V1..V7**, 24 tablas de negocio | `FlywayMigratesEmptySchemaTest.java:37,118` |
| Proyecto Docker | `fcv-citas-v1`, contenedores `fcv-citas-v1-*` | `docker-compose.yml:5,11,41,85` |
| Puertos del **host** | MySQL **3308** · API **8081** · Vite **5174** (web en contenedor 5175, Angular 4201) | `.env.example` de la raíz; `docker-compose.yml:22,71` |
| Puertos **dentro** del contenedor | MySQL 3306 · API 8080 | `docker-compose.yml:22,71` |

## Dominio

_(sin páginas todavía)_ — candidatas repetidamente mencionadas y sin página propia: **slot /
disponibilidad**, **estados de la cita y sus transiciones**, **afiliación**.

## Arquitectura

- [[arq-hexagonal-seguridad]] — la regla de no-dependencia del framework vigilada con ArchUnit, los puertos de seguridad del dominio y por qué `SecurityConfig` no lleva puerto

## Contratos REST

- [[contrato-rest-identidad]] — registro (con `insurancePlanId` opcional), login, refresh rotativo, logout, `/api/me` y el catálogo público de planes de EPS: rutas, cuerpos, `ProblemDetail` como formato de error uniforme, tabla de códigos y CORS al 5174
- [[contrato-rest-citas]] — S3: catálogos, especialidades, profesionales, bloques, disponibilidad, reserva general/especializada, bandeja y decisión; tabla de códigos 400/409/422 con `code`

## Decisiones

- [[dec-001-libreria-jwt]] — se usa el resource-server de Spring, no jjwt; con HMAC los beans `JwtDecoder`/`JwtEncoder` son obligatorios
- [[dec-002-rotacion-refresh-tokens]] — refresh opaco rotativo con familia y detección de reuso, persistido como SHA-256; qué obliga a hacer en el cliente (renovación única, épocas de sesión)
- [[dec-003-libro-unico-slot-reservations]] — una sola tabla con PK `slot_id` hace imposible la doble reserva; el **código** `SLOT_TAKEN` del 409 depende además del `SELECT … FOR UPDATE`
- [[dec-004-decisiones-s3-reserva]] — D5–D14, provisionales: primer ADMIN por variables de entorno, Medicina General precargada, V5 de auditoría, 60 min en un mismo bloque, lecturas por `JdbcTemplate`, hooks de git
- [[dec-005-sistema-visual-stitch]] — el `DESIGN.md` de Stitch es la fuente de verdad visual; **manda el `colors:` del frontmatter, no la prosa**, y con esa paleta no hace falta ninguna desviación por contraste; fuentes autoalojadas sin CDN y lo que Stitch inventó se descarta

## Datos y modelo

- [[datos-modelo-3fn]] — **7 migraciones Flyway** y 24 tablas, `affiliations` desde V2, qué garantiza el motor y qué el dominio, cómo se prueba el esquema (desde vacío, base de pruebas aislada), preguntas abiertas y las ya cerradas por V5/V6

## Riesgos

- [[riesgo-spring-security-65-trampas]] — cinco trampas de Spring Security 6.5.x: API del encoder, secreto HMAC (y placeholders), prefijo `ROLE_`, Bearer inválido frente a `permitAll`, y el truncado a 72 bytes de BCrypt en `checkpw`
- [[riesgo-prueba-intermitente-flyway]] — `FlywayMigratesEmptySchemaTest` falló una vez sin relación con el cambio; hipótesis: `target/` en el montaje de Windows
- [[riesgo-dos-copias-mismo-proyecto-docker]] — otra copia del laboratorio poseía los contenedores por compartir `COMPOSE_PROJECT_NAME`; mitigado con proyecto, nombres y puertos propios, y `strictPort` en Vite
- [[riesgo-zona-horaria-columnas-time]] — mitigado: la zona del JVM se fijaba tarde (`@PostConstruct`) y las horas `TIME` se desplazaban 5 h según el orden de las pruebas

## Síntesis

- [[sintesis-preguntas-abiertas]] — huecos del PRD detectados al especificar las 33 HU, los dos defectos de esquema ya corregidos por `V5`, la afiliación opcional resuelta (`AF1–AF3`) y las decisiones del agente bajo aprobación delegada pendientes de confirmar (D1–D4)

## Fuentes en `raw/`

- `MODELO-DATOS-3FN.md` — diseño 3FN propio: tablas, claves, dependencias funcionales, justificación de formas normales
- `RES-001-spring-security-jwt.md` — investigación con 18 fuentes sobre JWT access+refresh en Spring Boot 3.5.x

## Los dos archivos que no son páginas

- `index.md` — este catálogo. Toda página de `wiki/` aparece arriba, agrupada por tipo.
- [[log]] — registro cronológico append-only de `ingest` / `query` / `learn` / `lint`.

**Cobertura del catálogo, comprobada en el LINT del 2026-09-23:** 14 páginas en `wiki/` y 14
entradas aquí. Ninguna página queda fuera y ninguna entrada apunta a un archivo inexistente.

**Enlaces que salen de `llm-wiki/`:** [[contrato-rest-identidad]] usa cuatro wikilinks a HU
(`[[HU-001-registrar-cuenta-de-usuario]]`, `HU-004`, `HU-005`, `HU-033`). No son enlaces rotos:
los archivos existen en `citas-api/docs/wiki/scrum/historias-de-usuario/`. Resuelven solo si la
bóveda de Obsidian se abre en `citas-api/docs/wiki/` (o por encima), no si se abre en
`llm-wiki/`. **Pregunta para el usuario:** ¿dónde tiene la raíz de la bóveda? De la respuesta
depende si conviene cambiarlos a enlaces relativos.

---

**Nota para el agente:** este índice se llena con el workflow `INGEST` de fuentes reales y con el
`LEARN` de cada interacción. No lo rellenes por adelantado con páginas especulativas.
