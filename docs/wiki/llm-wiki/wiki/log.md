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

## [2026-09-18] learn | F6 de S3: bandeja y decisión del ADMIN (HU-029, HU-030) y defecto de zona horaria

- HECHO: `GET /api/admin/inbox` con filtros combinables; `POST /approve` conserva las reservas; `POST /reject` exige motivo (400 si falta), libera los slots en la misma transacción y el paciente ve el motivo. La decisión bloquea la fila (`PESSIMISTIC_WRITE`): aprobar y rechazar a la vez → un 200 y un 409, con un solo registro de decisión. Solicitud vencida → 409 `APPOINTMENT_EXPIRED` (D12).
- HECHO (defecto de S2 destapado en S3): la zona horaria del JVM se fijaba en un `@PostConstruct`, después de abrir conexiones; las horas `TIME` se desplazaban 5 h según el orden de las pruebas. Mitigado y documentado en [[riesgo-zona-horaria-columnas-time]].
- HECHO: 210 pruebas en verde, dos ejecuciones seguidas. [[contrato-rest-citas]] pasa a `Vigente`: todas sus rutas existen y tienen pruebas de integración.
- PREGUNTA ABIERTA: la bandeja devuelve `history: []` en cada entrada, porque el historial completo se pide en el detalle. Si el frontend lo necesita en la lista, habría que ampliarla.

## [2026-09-18] learn | Claude Code: AGENTS.md de cada repo cargado vía CLAUDE.md

- HECHO: Claude Code carga `CLAUDE.md`, no `AGENTS.md`. `citas-api/CLAUDE.md` y `citas-web/CLAUDE.md` (nuevos) importan su `AGENTS.md` con `@AGENTS.md`; el `CLAUDE.md` raíz ahora importa el `AGENTS.md` raíz en vez de solo enlazarlo.
- HECHO: los agentes `backend-*` y `frontend-*` leen el `AGENTS.md` de su repo antes de actuar (sección "Contexto obligatorio").
- HECHO: un subagente no puede lanzar subagentes; `s2-orchestrator` solo delega si corre como hilo principal (`claude --agent s2-orchestrator`).

## [2026-09-23] learn | Rediseño: el DESIGN.md de Stitch aplicado en código

- DECISIÓN del usuario: aplicar ya el diseño en código con los cuatro mockups disponibles, sin volver a Stitch por las pantallas que faltaban. Página nueva [[dec-005-sistema-visual-stitch]]; índice actualizado.
- HECHO verificado: las fuentes del diseño estaban declaradas en `tokens.css` pero nadie las cargaba, así que el navegador usaba la pila del sistema. Ahora se autoalojan con `@fontsource` (npm, sin CDN).
- DECISIÓN: donde el diseño de Stitch no alcanza el contraste de WCAG gana la accesibilidad; tres desviaciones (borde de campo, color de foco, franja elegida) quedan documentadas en la página y en `citas-web/docs/diseno/stitch/RETOMA_REDISENO.md`.
- HECHO: todo el contenido que Stitch inventó (EPS, historia clínica, SSL, JCI, SMS, sedes mal nombradas) se descartó. Commit `08cbe02` de `citas-web`; typecheck, lint, 83 pruebas y build en verde.
- PREGUNTA ABIERTA: el gráfico "Citas por sede esta semana" del panel admin no tiene endpoint; `GET /api/admin/summary` solo devuelve contadores.

## [2026-09-23] learn | Corrección: `DESIGN.md` lleva dos paletas y manda la del frontmatter

- HECHO verificado contra el `code.html` de los mockups: la prosa de `DESIGN.md` (primario `#0B5C8C`, aqua `#14B8A6`, fondo `#F4F7FA`) **no** es la paleta que los mockups pintan. La buena es el bloque `colors:` del frontmatter, que el `code.html` carga como configuración de Tailwind: primario `#00446A`, acento `#006B5F`, fondo `#F6F9FF`, texto `#101D27` / `#41474F`, bordes `#C1C7D0` / `#717880`.
- HECHO: el primer intento usó la paleta de la prosa; **el usuario detectó que los colores no coincidían**. Corregido en `tokens.css` y en [[dec-005-sistema-visual-stitch]] el mismo día.
- DECISIÓN: `#0B5C8C` se conserva como `primary-container` y como primera parada del degradado del panel de marca, no como acción principal.
- HECHO: con la paleta correcta desaparecen las tres desviaciones por contraste que se habían documentado; el esquema Material ya viene con los pares calculados.
- PREFERENCIA del usuario: revisa la fidelidad visual contra los mockups y la reclama. Ante un export de diseño con varias fuentes de color, verificar primero cuál consume el código exportado.

## [2026-09-23] learn | El backend que respondía era de otra copia del laboratorio

- HECHO verificado con `docker inspect`: los contenedores en marcha montaban `Documents\FCV_DES_AND\citas`, no este workspace. Las dos copias compartían `COMPOSE_PROJECT_NAME` (`fcv-citas-training`) y los mismos `container_name`, así que Docker las trataba como el mismo proyecto y mandaba la última que hizo `up`. Página nueva [[riesgo-dos-copias-mismo-proyecto-docker]].
- HECHO: `docker compose run --rm` crea un contenedor nuevo con el montaje de este repo; `docker compose exec` se engancha al existente. Por eso el hook pre-commit (que usa `run`) nunca delató el problema y el diagnóstico manual (con `exec`) sí se equivocó de carpeta.
- DECISIÓN: este workspace pasa a proyecto `fcv-citas-v1`, contenedores `fcv-citas-v1-*` y puertos 3308 / 8081 / 5174 (host) / 5175 (contenedor web) / 4201. Las dos copias pueden convivir.
- DECISIÓN: `vite.config.ts` fija el 5174 con `strictPort: true`. Si Vite salta de puerto, el origen deja de coincidir con `FRONTEND_ORIGIN` y el fallo de CORS se ve en la interfaz como "no pudimos contactar al servidor", que manda a depurar donde no es.
- HECHO: con el stack propio, Flyway aplicó las 7 migraciones sobre base vacía, Spring encontró 14 repositorios, el humo E2E dio 29/29 y `POST /api/auth/register` devolvió 201 con `Access-Control-Allow-Origin: http://localhost:5174`.

## [2026-09-23] learn | El plan de S3 que trajo el usuario apunta a la otra copia del repo

- HECHO verificado: el plan da por existente la migración `V1__identity.sql`, propone rutas `/api/v1/...` y pide "cerrar S2 marcando HU-001 y HU-002 como completadas". Nada de eso encaja aquí: el V1 real es `V1__identity_and_fixed_catalogs.sql` (vamos por V7), el prefijo es `/api/...` sin `v1`, y HU-001 a HU-004 están `Completada` desde S2. Encaja en cambio con `Documents\FCV_DES_AND\citas` — ver [[riesgo-dos-copias-mismo-proyecto-docker]].
- HECHO: su numeración de HU tampoco coincide con la de `docs/wiki/scrum/`. Su "HU-011 = afiliación opcional" es nuestra HU-009; nuestra HU-011 es gestionar especialidades. Su "HU-025 aún no existe" es nuestra HU-025, ya implementada.
- HECHO verificado por cobertura: de los cinco bloques del plan, lo único que no existe es la afiliación opcional. Backend con 185 pruebas y frontend con 83 cubren catálogos, especialidades, profesionales, bloques y slots, búsqueda, reserva general/especializada, bandeja, decisión e historial; el frontend no tiene ningún dato simulado ni `localStorage`.
- PREGUNTA ABIERTA: la afiliación opcional exige tres decisiones del usuario (A1–A3 en [[sintesis-preguntas-abiertas]]), incluida la de que `eps` y `eps_plans` están vacías y `V4` no las siembra. No se implementó nada.

## [2026-09-23] learn | HU-009 primer corte: afiliación opcional al registrarse

- DECISIÓN del usuario (A1–A3, resueltas en [[sintesis-preguntas-abiertas]]): se amplía el registro con un campo opcional; HU-001 **sigue `Completada`** porque el cambio es aditivo; HU-009 aprobada por él directamente y acotada a la ruta de registro; y el catálogo de EPS se crea **por script**, no por migración ni por CRUD de HU-012.
- DECISIÓN de contrato: `GET /api/catalogs/insurance-plans` es la **única lectura de catálogo pública**. Quien se registra no tiene sesión, así que exigir token haría el campo inutilizable. Declarada sin método en `SecurityConfig` para que un POST responda 405 y no 401. Documentada en [[contrato-rest-identidad]].
- DECISIÓN: plan inexistente, inactivo o de EPS inactiva comparten el mismo `422 INSURANCE_PLAN_UNAVAILABLE` para no revelar si el plan existe. El predicado "plan ofrecible" se escribe una sola vez y lo comparten el listado y la validación, así que no pueden divergir.
- HECHO verificado contra la API real, no solo con pruebas: 7 planes ofrecibles de 9 sembrados, 405 en métodos no-GET, 401 en los demás catálogos, afiliación por FK con `is_current = 1` y `started_on` de hoy en Bogotá, y **cero usuarios creados** en los tres 422 — la transacción revierte el INSERT del usuario.
- HECHO: la suite del backend pasa de 227 a **241** y la del frontend de 83 a **88**. Corrige de paso una cifra que circulaba mal: el baseline eran 227 pruebas, no 185.
- HECHO: matar el `docker compose exec` del host **no** mata el JVM del contenedor; hay que `pkill` dentro. Registrado en [[riesgo-dos-copias-mismo-proyecto-docker]] porque durante esta verificación hizo parecer roto un endpoint que funcionaba.

## [2026-09-23] learn | El código del 409 de doble reserva depende del bloqueo, no solo de la PK

- HECHO verificado con 24 perdedores en tres ejecuciones: ante una doble reserva salen siempre `409 SLOT_TAKEN`, nunca `CONCURRENT_CHANGE`. No es mérito solo de la PK de `slot_reservations`: el `SELECT … FOR UPDATE` sobre la fila del profesional serializa a los contendientes, así que el INSERT duplicado falla **dentro** de la transacción y el adaptador puede traducirlo. Sin ese bloqueo la doble reserva seguiría siendo imposible, pero parte de los 409 cambiarían de código. Registrado en [[dec-003-libro-unico-slot-reservations]].
- DECISIÓN: la prueba concurrente pasa a afirmar el `code` y no solo el status, precisamente para que quitar el bloqueo pesimista rompa una prueba en vez de degradar el contrato en silencio.
- HECHO: existe ya la carrera cruzada general ↔ especializada que exigía la DoD de HU-024 y que no tenía evidencia. La prueba ramifica según el ganador real; en tres ejecuciones ganó el general dos veces y el especializado una, así que asumir ganador la habría hecho intermitente.
- HECHO: la garantía última sigue siendo del motor. En el camino de escritura **no hay ningún pre-chequeo de "slot libre"** en la aplicación, y `SlotReservationJpaEntity` implementa `Persistable` con `isNew() = true` para que Spring Data haga `persist` y no `merge`: con `merge` actualizaría la fila ajena en vez de fallar y la doble reserva pasaría inadvertida.
- PREGUNTA ABIERTA: `appointments` no tiene restricción que obligue a una cita a tener filas en `slot_reservations`. Que toda cita ocupe su franja lo garantiza hoy el código, porque existe un único camino de creación; la base no lo impediría si apareciera otro.
- HECHO: suite del backend en **242** pruebas. Evidencia en `EVIDENCIAS_S3.md` §11.
