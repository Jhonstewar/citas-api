---
titulo: "Índice de la LLM Wiki"
tipo: sintesis
estado: Vigente
actualizado: 2026-10-04
tags: [indice]
---

# Índice — LLM Wiki de FCV Citas

Catálogo por contenido de la wiki. **Empieza aquí**: elige las páginas relevantes desde este
índice y entra en ellas. Se actualiza cada vez que cambia la estructura.

Convenciones y workflows: [`../schema/SCHEMA.md`](../schema/SCHEMA.md).
Historia cronológica: [[log]].

## Estado del proyecto

- **F10 de S4 cerrada en lo verificable (2026-10-04):** verificación independiente hecha, 8 pruebas nuevas de
  backend y 8 de frontend sin cambios de producción, y decisiones directas del usuario sobre HU-009, HU-012,
  HU-008, HU-006 y HU-032 en [[dec-006-decisiones-s4-ciclo-de-vida]] § "Cierre de F10". Quedan **7 HU en
  `En validación`** que solo se confirman en navegador (HU-005, 007, 009, 011, 012, 022, 028). Las menciones
  de más abajo a "F10 no ha corrido" y a "ninguna a `Completada`" son historia del 2026-09-30
- **Sesión en curso:** S4 — **pausada el 2026-09-25, retomada el 2026-09-30** (el trabajo se hizo
  en otra máquina; hoy se bajaron los 16 + 28 + 14 commits que estaban en `origin/develop` y los
  tres repos quedaron limpios y al día). Plan en `PLAN_RETOMA_S4.md` (raíz), decisiones D15–D39 en
  [[dec-006-decisiones-s4-ciclo-de-vida]]. **F5 cerrada el 2026-09-30: LOOP_02 PASS en 3 iteraciones
  de 4.** HU-027, HU-028 y HU-031 pasan de `Aprobada` a `En validación` con matriz de evidencia;
  **ninguna a `Completada`**, porque les faltan criterios de frontend y la prueba manual de F10.
  Punto de retoma: **F8 (LOOP_03) → F9 → F10**
- ⚠️ **Al leer `PLAN_RETOMA_S4.md`:** sus casillas estaban mal en los dos sentidos y se **sanearon el
  2026-09-30** (F10 desmarcada salvo el push/merge; F4, F6 y F7 marcadas con su referencia en el
  código). Una casilla `[x]` significa "código escrito y suite verde", **no** "HU verificada": eso es
  F10 y no ha corrido. Si el plan se contradice, manda la sección "▶ Dónde retomar". Detalle y
  pruebas en [[dec-006-decisiones-s4-ciclo-de-vida]] § "Estado real de S4"
- **S5–S6 (n8n) planificada el 2026-09-30, sin implementar.** Diagnóstico y contratos en `PLAN_S5_S6_N8N.md` y pasos ejecutables en `PLAN_EJECUCION_S5_S6.md` (raíz). Épica y HU en `Borrador`: [[EP-010-automatizaciones-n8n]], [[HU-034-consultar-citas-para-automatizacion]], [[HU-035-publicar-eventos-de-cambio-de-estado-a-n8n]], [[HU-036-versionar-y-documentar-los-flujos-n8n]]; esperan la frase de aprobación del usuario. Decisiones tomadas y preguntas abiertas en [[sintesis-preguntas-abiertas]] § "S5–S6 (n8n)". Los tres flujos de n8n ya existen (inactivos, con prefijo `jhonNuñez-`), pero el backend de automatización no existe todavía.
- **Ramas:** `origin/main` y `origin/develop` están **idénticos** en los tres repos desde el merge
  del 2026-09-25, así que `main` contiene S4 a medio verificar. Contradecía `AGENTS.md:102`; el
  usuario decidió el 2026-09-30 **dejarlo así** y hacer el próximo merge a `main` solo al cerrar F10
- **Entorno de esta máquina (2026-09-30; reconfirmado el 2026-10-04: el proyecto Docker sigue como `fcv-citas-training` con MySQL en 3307):** Docker Desktop **arrancado**, MySQL 8.4 en
  `fcv-citas-v1-mysql` (healthy) y la base de pruebas `citas_fcv_training_test` creada con
  `.\scripts\init-test-db.ps1`. **Pero el `.env` de la raíz quedó atrás de `dafb0fa`:** no viaja en
  git, así que `COMPOSE_PROJECT_NAME` sigue valiendo `fcv-citas-training` y MySQL se publica en
  **3307**, no 3308. Comprobable sin abrirlo, con `docker compose ls` y `docker port`. Los volúmenes
  se crean como `fcv-citas-training_*` y se comparten con cualquier otra copia del laboratorio: es el
  riesgo de [[riesgo-dos-copias-mismo-proyecto-docker]], latente porque hoy solo hay una copia.
  Pendiente del usuario alinear `COMPOSE_PROJECT_NAME`, `MYSQL_PORT` y `API_PORT` con `.env.example`.
  Node del host es **22.19** y `react-router@8.4.0` pide ≥22.22.0 (`AGENTS.md` pide Node 24)
- **En paralelo:** rediseño del frontend con los mockups de Stitch, ya aplicado en código — ver [[dec-005-sistema-visual-stitch]]
- **Sesión anterior:** S3 cerrada el 2026-09-23 con 12 HU `Completada` y 6 abiertas con su causa
- **Antes:** S2 cerrada salvo la prueba manual en navegador
- **Repos:** `Jhonstewar/citas-api`, `Jhonstewar/citas-web`, `Jhonstewar/FCV_Proyecto_Citas_v1` (origen histórico: `jhonnunez-svg`)
- **Stack fijado:** Java 21 LTS · Spring Boot 3.5.x · hexagonal · MySQL 8.4 · Flyway · JWT ·
  React + TypeScript + Vite · Node 24 LTS

### Cifras vigentes (cada fila lleva la fecha en que se comprobó; sin fecha = 2026-09-23)

Cualquier número distinto en una página de la wiki es historia fechada, no el estado de hoy.

| Qué | Valor | Dónde se comprobó |
|---|---|---|
| Suite del backend (**2026-10-04**) | **521/521** tras F10 (513 + 8 en `SpecialtyAdminIntegrationTest`, `BookingIntegrationTest` y `FlywayV10BackfillTest`) | cifra del cierre de F10; las 8 pruebas existen en `citas-api/src/test` (sin commitear al escribir esto) |
| (historia) backend 2026-09-30 | **484** pruebas, `BUILD SUCCESS` · `HexagonalArchitectureTest` 4/4 · clases concurrentes 89/89 en dos pasadas | `docker compose run --rm citas-api-dev mvn -B test`, ejecutado por el `backend-verifier` y de nuevo tras el cierre del LOOP_02 |
| Suite del frontend (**2026-10-04**) | **256/256** (248 + 8 de `citas-web/src/recoveryAndReschedule.test.tsx`, 8 casos, archivo nuevo sin commitear al escribir esto) | cifra del cierre de F10 |
| (historia) frontend 2026-09-30 | **218** pruebas, 20 archivos · typecheck 0 · `oxlint` 0 · build OK | los cuatro comandos ejecutados por el `frontend-verifier` en `citas-web` |
| Migraciones Flyway (**2026-09-30**) | **V1..V10**, 24 tablas de negocio (V8, V9, V10 son de S4 y no crean tablas) | `FlywayMigratesEmptySchemaTest.java:37,116,121` |
| Proyecto Docker | `fcv-citas-v1`, contenedores `fcv-citas-v1-*` | `docker-compose.yml:5,11,41,85` |
| Puertos del **host** | MySQL **3308** · API **8081** · Vite **5174** (web en contenedor 5175, Angular 4201) | `.env.example` de la raíz; `docker-compose.yml:22,76` |
| Puertos **dentro** del contenedor | MySQL 3306 · API 8080 | `docker-compose.yml:22,76` |
| HU por estado (**2026-10-04**, tras F10) | **25** `Completada` · **7** `En validación` (HU-005, 007, 009, 011, 012, 022, 028) · 1 `En desarrollo` (HU-033) · 3 `Borrador` (HU-034..036) | frontmatter de los 36 `HU-*.md` de `citas-api/docs/wiki/scrum/historias-de-usuario/`, contado el 2026-10-04 |
| (historia) HU por estado 2026-09-30 | 16 `Completada` · 8 `En validación` · 8 `Aprobada` · 1 `En desarrollo` | tras cerrar F5 |

**Las cifras del 2026-09-30 son de ejecuciones reales de ese día, no de un registro heredado.** Cada una la
corrió el Verifier independiente correspondiente, además del Builder. Progresión de la sesión:
backend 471 → 480 (D38 y D39) → **484** (las dos pruebas que protegen el invariante de D39);
frontend 212 → **218**.

> **Aviso para el próximo agente.** Hasta el 2026-09-30 esta sección decía que Docker estaba apagado
> y que las cifras no se habían vuelto a ejecutar. Era cierto a primera hora y dejó de serlo al
> arrancar Docker Desktop, pero la página no se actualizó hasta el cierre del turno, y en ese
> intervalo **un agente leyó el dato viejo aquí y lo propagó de buena fe** a otro documento como
> reserva de «leído, no ejecutado». Cuando cambies el entorno, actualiza esta fila en el momento: un
> dato viejo en el índice no se queda quieto, se copia.

## Dominio

_(sin páginas todavía)_ — candidatas repetidamente mencionadas y sin página propia: **slot /
disponibilidad**, **estados de la cita y sus transiciones**, **afiliación**.

## Arquitectura

- [[arq-hexagonal-seguridad]] — la regla de no-dependencia del framework vigilada con ArchUnit, los puertos de seguridad del dominio y por qué `SecurityConfig` no lleva puerto

## Contratos REST

- [[contrato-rest-identidad]] — registro (con `insurancePlanId` opcional), login, refresh rotativo, logout, `/api/me` y el catálogo público de planes de EPS: rutas, cuerpos, `ProblemDetail` como formato de error uniforme, tabla de códigos y CORS al 5174
- [[contrato-rest-citas]] — S3: catálogos, especialidades, profesionales, bloques, disponibilidad, reserva general/especializada, bandeja y decisión; tabla de códigos 400/409/422 con `code`; S5: `GET /api/automation/appointments/upcoming`; S6: contrato del evento saliente a n8n (WF-002), no un endpoint

## Decisiones

- [[dec-001-libreria-jwt]] — se usa el resource-server de Spring, no jjwt; con HMAC los beans `JwtDecoder`/`JwtEncoder` son obligatorios
- [[dec-002-rotacion-refresh-tokens]] — refresh opaco rotativo con familia y detección de reuso, persistido como SHA-256; qué obliga a hacer en el cliente (renovación única, épocas de sesión)
- [[dec-003-libro-unico-slot-reservations]] — una sola tabla con PK `slot_id` hace imposible la doble reserva; el **código** `SLOT_TAKEN` del 409 depende además del `SELECT … FOR UPDATE`
- [[dec-004-decisiones-s3-reserva]] — D5–D14, provisionales: primer ADMIN por variables de entorno, Medicina General precargada, V5 de auditoría, 60 min en un mismo bloque, lecturas por `JdbcTemplate`, hooks de git
- [[dec-006-decisiones-s4-ciclo-de-vida]] — D15–D39 de S4: aprobación delegada, cancelar con reprogramación pendiente libera las dos franjas, cierre de atención desde la hora de inicio, una afiliación vigente, token de recuperación solo en laboratorio, y pantallas nuevas sin mockup de Stitch. Además, el **estado real de S4 al retomarla el 2026-09-30**: cero HU cerradas, LOOP_02 a medias, las casillas de F10 que no son de fiar y `main` con trabajo a medio verificar
- [[dec-007-entrega-best-effort-eventos-n8n]] — D-F aprobada el 2026-10-04: los eventos de cita hacia n8n se entregan best-effort (sin outbox, 3 intentos, se pierden si n8n cae), el fallo nunca revierte la operación y `reason` es contenido no confiable; alternativas descartadas
- [[dec-005-sistema-visual-stitch]] — el `DESIGN.md` de Stitch es la fuente de verdad visual; **manda el `colors:` del frontmatter, no la prosa**, y con esa paleta no hace falta ninguna desviación por contraste; fuentes autoalojadas sin CDN y lo que Stitch inventó se descarta

## Datos y modelo

- [[datos-modelo-3fn]] — **10 migraciones Flyway** y 24 tablas, `affiliations` desde V2, qué garantiza el motor y qué el dominio, cómo se prueba el esquema (desde vacío, base de pruebas aislada), preguntas abiertas y las ya cerradas por V5/V6

## Riesgos

- [[riesgo-spring-security-65-trampas]] — cinco trampas de Spring Security 6.5.x: API del encoder, secreto HMAC (y placeholders), prefijo `ROLE_`, Bearer inválido frente a `permitAll`, y el truncado a 72 bytes de BCrypt en `checkpw`
- [[riesgo-prueba-intermitente-flyway]] — `FlywayMigratesEmptySchemaTest` falló una vez sin relación con el cambio; hipótesis: `target/` en el montaje de Windows
- [[riesgo-dos-copias-mismo-proyecto-docker]] — otra copia del laboratorio poseía los contenedores por compartir `COMPOSE_PROJECT_NAME`; mitigado con proyecto, nombres y puertos propios, y `strictPort` en Vite
- [[riesgo-zona-horaria-columnas-time]] — mitigado: la zona del JVM se fijaba tarde (`@PostConstruct`) y las horas `TIME` se desplazaban 5 h según el orden de las pruebas

## Síntesis

- [[sintesis-preguntas-abiertas]] — huecos del PRD detectados al especificar las 33 HU, los dos defectos de esquema ya corregidos por `V5`, la afiliación opcional resuelta (`AF1–AF3`) y las decisiones del agente bajo aprobación delegada pendientes de confirmar (D1–D4); **R1–R4** salieron al retomar S4 el 2026-09-30, **R5–R12** al cerrar LOOP_02, y la sección "Tras F10 (2026-10-04)" lista lo que sigue abierto

## Fuentes en `raw/`

- `MODELO-DATOS-3FN.md` — diseño 3FN propio: tablas, claves, dependencias funcionales, justificación de formas normales
- `RES-001-spring-security-jwt.md` — investigación con 18 fuentes sobre JWT access+refresh en Spring Boot 3.5.x

## Los dos archivos que no son páginas

- `index.md` — este catálogo. Toda página de `wiki/` aparece arriba, agrupada por tipo.
- [[log]] — registro cronológico append-only de `ingest` / `query` / `learn` / `lint`.

**Cobertura del catálogo, comprobada en el LINT del 2026-10-04 (y ampliada con dec-007 el mismo día):** 16 páginas en `wiki/` y 16
entradas aquí (eran 14 el 2026-09-23, antes de dec-005 y dec-006). Ninguna página queda fuera y ninguna entrada apunta a un archivo inexistente.

**Enlaces que salen de `llm-wiki/`:** [[contrato-rest-identidad]] usa cuatro wikilinks a HU
(`[[HU-001-registrar-cuenta-de-usuario]]`, `HU-004`, `HU-005`, `HU-033`). No son enlaces rotos:
los archivos existen en `citas-api/docs/wiki/scrum/historias-de-usuario/`. Resuelven solo si la
bóveda de Obsidian se abre en `citas-api/docs/wiki/` (o por encima), no si se abre en
`llm-wiki/`. **Pregunta para el usuario:** ¿dónde tiene la raíz de la bóveda? De la respuesta
depende si conviene cambiarlos a enlaces relativos.

---

**Nota para el agente:** este índice se llena con el workflow `INGEST` de fuentes reales y con el
`LEARN` de cada interacción. No lo rellenes por adelantado con páginas especulativas.
