---
titulo: "Log de la LLM Wiki"
tipo: sintesis
estado: Vigente
actualizado: 2026-09-16
tags: [log]
---

# Log — LLM Wiki de FCV Citas

Registro cronológico **append-only**. Nunca se editan ni se borran entradas pasadas.

Formato fijo del encabezado, para que sea parseable:

```
## [YYYY-MM-DD] <ingest|query|learn|lint> | <asunto>
```

Últimas entradas: `grep "^## \[" log.md | tail -5`

---

## [2026-09-16] learn | Sistema de agentes y LLM Wiki inicializados

- Creada la gobernanza raíz (`AGENTS.md`, `CLAUDE.md`) y diez agentes especializados en `.claude/agents/`.
- Creado el esquema de la wiki en `schema/SCHEMA.md` con las tres capas y los workflows INGEST / QUERY / LEARN / LINT.
- `index.md` y este log nacen vacíos: se llenan con fuentes reales, no con páginas especulativas.
- Abierto: falta el primer INGEST del PRD y de las restricciones técnicas.

## [2026-09-16] ingest | RES-001 — JWT access+refresh en Spring Boot 3.5.x

- Fuente: `raw/RES-001-spring-security-jwt.md`, investigación delegada a un subagente con 18 fuentes citadas.
- Páginas creadas: [[dec-001-libreria-jwt]], [[dec-002-rotacion-refresh-tokens]], [[riesgo-spring-security-65-trampas]], [[arq-hexagonal-seguridad]].
- Hallazgo con impacto inmediato: `NimbusJwtEncoder.withSecretKey(...)` es `@since 7.0` y no compila contra Spring Security 6.5.x, que es lo que trae Boot 3.5.x.
- Abierto: ¿se acepta que el access token no sea revocable durante sus 15 minutos? Decisión pendiente del usuario, registrada en [[dec-002-rotacion-refresh-tokens]].

## [2026-09-16] ingest | MODELO-DATOS-3FN — esquema propio aplicado con Flyway

- Fuente: `raw/MODELO-DATOS-3FN.md`, verificada contra las migraciones V1–V4 aplicadas en MySQL 8.4 (24 tablas).
- Páginas creadas: [[datos-modelo-3fn]], [[dec-003-libro-unico-slot-reservations]].
- Única desviación del análisis 3FN previo: fusión en `slot_reservations` para que el motor impida la doble reserva.
- Abierto: unicidad global del documento, dónde vive el régimen, seed de Medicina General (RF-11).

## [2026-09-16] learn | Repos remotos, aprobación delegada y reanudación tras límite de API

- DECISIÓN: repos remotos definitivos en `jhonnunez-svg` (citas-api, citas-web, FCV_Proyecto_Citas_v1); el repo del trainer queda como `upstream` en la raíz.
- DECISIÓN: HU-001 a HU-004 pasan a `Aprobada` por aprobación delegada (el usuario eligió S2 autónomo). Regla añadida en `AGENTS.md` §6.
- PREFERENCIA: el usuario quiere S2 ejecutado de forma autónoma, con la wiki actualizada en cada interacción.
- HECHO: `citas-web` compila (`npm run build`) y pasa typecheck; su contrato en `src/api/contracts.ts` es provisional y difiere en nombres de campo del backend.

## [2026-09-16] learn | Especificación Scrum completa: 9 épicas, 33 HU

- HECHO: 33 HU y 9 épicas en `scrum/`, sin wikilinks rotos (verificado). 4 `Aprobada` (HU-001..004), 1 `Pendiente de aprobación`, 28 `Borrador`.
- PREGUNTA ABIERTA: 8 incógnitas de dominio agrupadas en [[sintesis-preguntas-abiertas]].
- HECHO: dos defectos de esquema verificados en `V3__schedule_and_appointments.sql` — historial de auditoría con `ON DELETE CASCADE` (contradice RN-12) y `source` sin valor para el profesional. Registrados en [[datos-modelo-3fn]]; se corrigen en S3 con una V5.

## [2026-09-16] learn | Aviso de aprobación delegada aclarado y dudas de autenticación

- HECHO: el primer agente de especificación marcó como "proceso ajeno" el paso de HU-001..004 a `Aprobada`; el cambio fue del orquestador bajo aprobación delegada. README de `scrum/` corregido para decirlo explícitamente.
- PREGUNTA ABIERTA: 4 dudas de autenticación que afectan a S2 (política de contraseña, login único por rol, primer ADMIN, vigencias) añadidas a [[sintesis-preguntas-abiertas]]. Las épicas registran 40 incógnitas `INC-NNN` en total.

## [2026-09-16] learn | GOAL_01 verificado de forma independiente y commit S2 subido

- HECHO: verificador independiente → `mvn test` 33/33 en verde; hash SHA-256 del refresh, revocación de familia por reuso, mismo 401 para email o clave incorrectos, dominio sin imports de framework: confirmados.
- HECHO: ninguna de HU-001..004 puede pasar a `Completada`: HU-003 falla en el frontend (sin refresh automático ante 401), falta el contrato REST documentado (HU-033) y la trazabilidad, y no se probó la migración sobre una BD vacía.
- DECISIÓN: commit `feat(s2)` en `develop` de citas-api, citas-web y la raíz, subido a `jhonnunez-svg`. `main` de los subrepos es un commit inicial vacío.
- Pendientes ordenados en `PLAN_RETOMA_S2.md` (raíz).

## [2026-09-17] learn | HU-001..004 Completadas tras tres rondas de verificación independiente

- HECHO: backend 104 pruebas y frontend 42, en verde. Verificación independiente con prueba de mutación: mueren los 11 mutantes del backend y 28 de 29 del frontend; el que sobrevive es cosmético (`maxLength`). HU-001..004 → `Completada`.
- HECHO: defectos reales encontrados y corregidos: el login aceptaba una contraseña distinta con los mismos 72 primeros bytes (truncado de `BCrypt.checkpw`), y un Bearer caducado bloqueaba refresh y logout. En el cliente había cuatro carreras de sesión y un reintento con la identidad de otra sesión. Registrados en [[riesgo-spring-security-65-trampas]] (trampas 4 y 5) y [[dec-002-rotacion-refresh-tokens]].
- DECISIÓN (aprobación delegada, pendiente de confirmar): locale fijo `es_CO`; las rutas públicas de auth ignoran `Authorization`; límite de contraseña en bytes UTF-8 en infraestructura. [[contrato-rest-identidad]] alineado con el código; tabla D1–D4 en [[sintesis-preguntas-abiertas]].
- PREGUNTA ABIERTA: política de contraseña (INC-001, el cliente aplica una que el servidor no), refresh token en cookie `HttpOnly` (S4), `error_description` en inglés en `WWW-Authenticate`. Queda la prueba manual en navegador.

## [2026-09-18] learn | AGENTS.md por repo (S2 paso 2) y repos en `Jhonstewar`

- HECHO: `citas-api/AGENTS.md` y `citas-web/AGENTS.md` generados con `PROMPT_AGENT_CITAS_API.md` y `PROMPT_AGENT_CITAS_WEB.md` a partir del código real (stack, capas hexagonales, reglas ArchUnit, comandos Docker/npm). Se borran los `AGENTS.md.template`.
- DECISIÓN: `citas-web/AGENTS.md` se revisa de nuevo tras importar el diseño de AI Studio (S2 paso 4); si el stack importado difiere, gana lo importado y aprobado.
- DECISIÓN: los remotos de trabajo pasan a `Jhonstewar` (FCV_Proyecto_Citas_v1, citas-api, citas-web); `jhonnunez-svg` queda como origen histórico.

## [2026-09-18] learn | Plan de S3, alcance aprobado por delegación y decisiones D5–D13

- HECHO: base verificada antes de S3 sobre `develop`: backend 104/104 pruebas, frontend 42/42 más build y lint en verde.
- DECISIÓN: 18 HU pasan a `Aprobada` por aprobación delegada de S3 (HU-005, 010, 011, 013–019, 022–025, 029, 030, 032, 033). Cancelación, reprogramación, perfil, EPS y cierre de atención quedan para S4.
- DECISIÓN (provisional): D5–D13 en [[dec-004-decisiones-s3-reserva]]: primer ADMIN por variables de entorno, contraseña inicial del profesional fijada por el ADMIN, Medicina General precargada, V5 de auditoría (E1/E2), 60 min en un mismo bloque, retención sin caducidad, profesional desactivado sin reservas nuevas, 409 al aprobar una cita vencida, hooks de git versionados.
- PREFERENCIA: el usuario no usó Stitch; el diseño del frontend queda a criterio del agente ("bonito e intuitivo").
- PREFERENCIA: trabajo por fases con puntos de control (commit por repo y casilla en `PLAN_RETOMA_S3.md`) para poder interrumpir, subir y retomar.

## [2026-09-18] learn | F1 de S3: la red que dice "no" (hooks locales)

- HECHO: hooks `pre-commit` versionados en `.githooks/` de los tres repos, activados con `scripts/install-hooks.ps1` (`core.hooksPath`). Raíz: secretos. `citas-api`: secretos + `mvn test` en Docker. `citas-web`: secretos + typecheck + lint + vitest. Evidencia en `EVIDENCIAS_S3.md`.
- HECHO: el escáner bloqueó una contraseña ficticia en `application.yml` y el hook de `citas-web` bloqueó una prueba roja. La corrección (credenciales del primer ADMIN por variables de entorno) pasó: `citas-api@062725b`.
- HECHO: la auditoría `--all` de los tres repos no encontró secretos; la regla de código daba 4 falsos positivos en mensajes de UI y rutas y se ajustó.
- PREGUNTA ABIERTA: `FlywayMigratesEmptySchemaTest` falló una vez de forma intermitente. Ver [[riesgo-prueba-intermitente-flyway]].

## [2026-09-18] learn | F2 de S3: autorización por rol, catálogos, especialidades, V5/V6

- HECHO: Red → Green demostrado con `AuthorizationIntegrationTest` (9 de 10 en rojo antes del código; verde después). Suite: 126 pruebas en verde, ArchUnit incluido. Evidencia en `EVIDENCIAS_S3.md` §6.
- HECHO: V5 quita el `ON DELETE CASCADE` del historial y añade el origen `PROFESSIONAL` (E1/E2 resueltas). V6 siembra `MEDICINA_GENERAL`. `FlywayMigratesEmptySchemaTest` comprueba ambas desde un esquema vacío.
- HECHO: prefijos por rol en `SecurityConfig` (`/api/admin`, `/api/professional`, `/api/patient`) y `denyAll` por defecto; `/api/catalogs/**` para cualquier rol autenticado, sin escritura (405).
- DECISIÓN (provisional, desviación): D14 en [[dec-004-decisiones-s3-reserva]]: lecturas con varias tablas por SQL (`JdbcTemplate`); escrituras por JPA. Choca con la letra de `RESTRICCIONES_TECNICAS.md` y queda pendiente de que el usuario la acepte.
- PREFERENCIA: el usuario pidió seguir la skill `scrum-spec-orchestrator` también en la ejecución: las HU pasan a `En desarrollo` al empezar su fase y se cierran con matriz de evidencia.

## [2026-09-18] learn | F3 de S3: gestión de profesionales (HU-013 a HU-016)

- HECHO: alta atómica usuario PROFESSIONAL + perfil + especialidades + sedes; duplicados → 409 con `field`; primaria validada en el dominio (`SpecialtyAssignment`); sedes obligatorias; activar/desactivar sin borrado. 12 pruebas de integración + 7 de dominio.
- HECHO: el código profesional y la matrícula no se editan tras el alta (HU-013 los deja fuera de alcance, INC-015). El contrato se corrigió antes de implementar y se avisó al frontend.
- HECHO (trampa): `Set.copyOf(...).contains(null)` lanza `NullPointerException` en vez de devolver `false`; validar nulos con `stream().anyMatch(Objects::isNull)`. Salió como un 500 en el alta y tiene prueba de regresión (`ProfessionalTest`).
- HECHO: Spring Data no detecta repositorios anidados en otra clase (`considerNestedRepositories=false` por defecto); cada repositorio va en su propio archivo.

## [2026-09-18] learn | F4 de S3: agenda del profesional (HU-017 a HU-019)

- HECHO: `AvailabilityBlock` (dominio) discretiza en slots de 30 min, valida la rejilla :00/:30, el rango, el solape (contiguos no solapan), el pasado (RN-06) y si una cita de N slots cabe en el mismo bloque (RN-05, D9). 12 pruebas de dominio.
- HECHO: las escrituras de agenda de un profesional se serializan con `SELECT … FOR UPDATE` sobre su fila, porque la base solo impide dos bloques con el mismo inicio, no solapes.
- HECHO: Hibernate 6 con `hibernate.jdbc.time_zone=America/Bogota` NO desplaza las columnas `TIME` al guardar `LocalTime` (comprobado con lectura SQL cruda en `ScheduleIntegrationTest`). Esa prueba queda como vigilancia.
- HECHO: 170 pruebas en verde (10 de integración de agenda).

## [2026-09-18] learn | F5 de S3: reserva general y especializada (HU-022 a HU-025, HU-032), GOAL_02 backend

- HECHO: dos flujos separados, `POST /api/patient/appointments/general` (APPROVED, historial SYSTEM sin actor) y `/specialized` (REQUESTED, historial USER con el paciente como actor). El equivocado → 422 `WRONG_FLOW`.
- HECHO: la doble reserva la impide solo la PK de `slot_reservations`; el caso de uso no comprueba antes. 8 reservas concurrentes del mismo slot → 1 × 201 y 7 × 409 (repetido 4 veces). Evidencia en `EVIDENCIAS_S3.md` §7.
- HECHO (trampa): `SlotReservationJpaEntity` implementa `Persistable.isNew() = true`. Con id asignado, Spring Data haría `merge`, que ante un slot ya reservado ACTUALIZARÍA la reserva ajena en vez de fallar: se perdería la garantía de RN-01 sin que ninguna prueba de un solo hilo lo notara.
- HECHO: el historial se escribe con un `Repository` de solo `save` (sin métodos de borrado ni actualización, RN-12). La disponibilidad empareja en SQL el slot siguiente del mismo bloque para 60 min (RN-05, D9).
- HECHO: 199 pruebas en verde. GOAL_02: falta la parte de navegador (E2E) contra el backend real.
- HECHO (verificado por mutación): con `isNew() = false`, la segunda reserva del mismo slot devuelve 201 y sobrescribe la reserva ajena; `doubleBookingIsRejectedWith409` lo detecta, la prueba concurrente no. Registrado en [[dec-003-libro-unico-slot-reservations]].
