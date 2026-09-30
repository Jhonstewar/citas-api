---
titulo: "Síntesis — Preguntas abiertas del dominio"
tipo: sintesis
estado: Vigente
actualizado: 2026-09-30
fuentes: ["citas-api/docs/wiki/scrum/historias-de-usuario/", "PRD.md", "[[MODELO-DATOS-3FN]]", "PLAN_RETOMA_S4.md §4 F10"]
tags: [sintesis, preguntas-abiertas, dominio]
---

# Síntesis — Preguntas abiertas del dominio

Huecos del PRD que salieron al especificar las 33 HU. Ninguno bloquea S2 (autenticación), pero
todos afectan a S3–S4. Están agrupados aquí para que no se redescubran una y otra vez; el detalle
de cada uno vive en la HU citada. **Resolver cada uno exige una decisión del usuario, no una
inferencia del agente.**

## Esquema (verificadas contra las migraciones) — E1 y E2 CERRADAS

| # | Pregunta | Afecta a | Estado |
|---|---|---|---|
| E1 | El historial de estados se borra en cascada con la cita, en contra de RN-12 | [[datos-modelo-3fn]], HU-032 | **Cerrada**: `V5__audit_history_append_only.sql:14-15` recrea la FK con `ON DELETE RESTRICT` |
| E2 | Falta el origen `PROFESSIONAL` en la auditoría, aunque el profesional cierra atenciones | [[datos-modelo-3fn]], HU-021 | **Cerrada**: `V5__...sql:18` hace `MODIFY source ENUM('SYSTEM','USER','ADMIN','PROFESSIONAL')` |

Se dejan listadas, no se borran: ambas nacieron como defectos verificados de `V3` y la corrección
está en una migración que hay que poder rastrear hasta aquí. La **decisión** que las cerró (D8)
sigue `Provisional` hasta que el usuario la confirme.

## Reglas de negocio sin definir

| # | Pregunta | Afecta a | Supuesto actual |
|---|---|---|---|
| N1 | Si el paciente cancela una cita con reprogramación `PENDING`, ¿qué pasa con la franja retenida? | HU-026, HU-027 | Ninguno: V4 tiene el estado `CANCELLED` para reprogramación |
| N2 | Los 2 slots de una cita de 60 min, ¿pueden venir de dos bloques seguidos? | HU-022, HU-023, HU-024 | Deben estar en el mismo bloque |
| N3 | La decisión sobre una reprogramación no cambia el estado de la cita: ¿se registra en el historial? | HU-031, HU-032 | Sin definir |
| N4 | "Conservar la cita" tras rechazar una reprogramación, ¿se persiste como acción? | HU-028 | Sin definir |
| N5 | Un profesional desactivado, ¿puede seguir iniciando sesión? | HU-016, HU-002 | Sin definir |
| N6 | En la bandeja, los filtros de fecha y sede de una reprogramación, ¿usan la franja actual o la propuesta? | HU-029 | Sin definir |

## Autenticación (afectan directamente a S2)

| # | Pregunta | Dónde | Supuesto de GOAL_01 |
|---|---|---|---|
| A1 | Política de complejidad de contraseña | EP-001, HU-001 | Servidor: solo máx. 72 bytes. **Cliente**: mín. 8 con letra y número (`authValidation.ts`). La API acepta lo que el formulario rechaza; se alinean al decidir INC-001 |
| A2 | ¿ADMIN y PROFESSIONAL usan el mismo login que USER? | EP-001, HU-002 | Sí, un único `/api/auth/login` |
| A3 | ¿Cómo se crea el primer ADMIN? | EP-001 | Sin definir: no hay registro de ADMIN |
| A4 | Vigencia de access, refresh y reset token | EP-001 | 15 min / 7 días (`.env.example`) |

## Seguridad y datos

| # | Pregunta | Dónde |
|---|---|---|
| S1 | ¿Se acepta que el access token no sea revocable durante sus 15 minutos? | [[dec-002-rotacion-refresh-tokens]] |
| S2 | Documento único global o por tipo de documento | [[datos-modelo-3fn]] |
| S3 | ¿Se precarga "Medicina General" (RF-11) con una migración de datos? **Cerrada**: sí, `V6__seed_general_medicine.sql:9-10`, y la aplicación la protege contra desactivación y contra cambio de tipo (D7) | [[datos-modelo-3fn]] |
| S4 | El refresh token vive en memoria de JavaScript: un XSS en el mismo origen puede leerlo. ¿Se pasa a cookie `HttpOnly`? Cambiaría CSRF y CORS. **Cerrada el 2026-09-25 (D36):** sí, porque el usuario pidió que el F5 no cierre la sesión; ver [[dec-006-decisiones-s4-ciclo-de-vida]] | [[dec-002-rotacion-refresh-tokens]] |

## Decisiones del agente bajo aprobación delegada, pendientes de confirmar

Se tomaron para poder cerrar S2 y están implementadas, pero **no las ha confirmado el usuario**.

| # | Decisión | Dónde |
|---|---|---|
| D1 | Contrato REST en Markdown a mano, sin springdoc (INC-038) | [[contrato-rest-identidad]] |
| D2 | Formato de error `ProblemDetail` RFC 9457 con extensión `fieldErrors` (INC-040) | [[contrato-rest-identidad]] |
| D3 | Rotación de refresh con revocación por familia ante reuso y en el logout | [[dec-002-rotacion-refresh-tokens]] |
| D4 | Locale fijo `es_CO`: la API ignora `Accept-Language` | [[contrato-rest-identidad]] |

## Afiliación opcional en el registro — RESUELTA el 2026-09-23

Las tres preguntas las contestó el usuario el mismo día y el primer corte de HU-009 ya está
implementado. Se conservan aquí porque la decisión de contrato tiene alcance más allá de la HU.

> **Renumeradas en el LINT del 2026-09-23.** Nacieron como `A1`–`A3` y chocaban con las `A1`–`A4`
> de la sección de autenticación de arriba, que son otras preguntas y siguen abiertas. Aquí pasan
> a `AF1`–`AF3` (**AF** = afiliación). El log del 2026-09-23 las cita como A1–A3; es el mismo par
> de tablas, no dos conjuntos distintos.

| # | Pregunta | Respuesta |
|---|---|---|
| AF1 | ¿Se amplía el contrato de `POST /api/auth/register`, de HU-001 ya `Completada`? | Sí, con un campo **opcional**. HU-001 **sigue `Completada`**: al ser aditivo y opcional, todos sus criterios siguen siendo ciertos. El campo y sus reglas pertenecen a HU-009, que carga con la evidencia |
| AF2 | ¿Se aprueba HU-009 y se adelanta? | Sí, aprobación **directa del usuario**, no delegada. Acotada a un primer corte: solo la ruta de registro. Consultar y cambiar la afiliación desde el perfil queda para después, porque eso sí depende de HU-008 |
| AF3 | Sin planes en la base, ¿migración semilla o CRUD de HU-012? | **Ninguna de las dos: script.** `scripts/seed-eps-plans.ps1`, idempotente, con EPS ficticias. No contradice a `V4`, que siembra solo catálogos fijos, y deja HU-012 fuera. La tabla `affiliations` **ya existía desde `V2`**; lo que faltaba eran filas en `eps` y `eps_plans` (ver [[datos-modelo-3fn]]) |

**DECISIÓN de contrato, tomada por el agente bajo AF1:** `GET /api/catalogs/insurance-plans` es la
**única lectura de catálogo pública** del sistema. Quien se registra no tiene sesión todavía, así que
exigir token haría el campo inutilizable. Se declara en `SecurityConfig` **sin método** para que un
`POST` responda 405 y no 401, igual que el resto de catálogos (HU-010 CA-06). El resto de
`/api/catalogs/**` sigue exigiendo token, y hay una prueba que lo fija.

**DECISIÓN:** plan inexistente, inactivo o de EPS inactiva comparten respuesta —
`422 INSURANCE_PLAN_UNAVAILABLE` con el mismo cuerpo— para no revelar si el plan existe.

**HECHO verificado contra la API real** (no solo con pruebas): catálogo público con 7 planes
ofrecibles de los 9 sembrados (excluye el retirado y el de EPS inactiva), 405 en `POST`/`PUT`/
`DELETE`, 401 en los demás catálogos, afiliación creada por FK con `is_current = 1` y
`started_on` de hoy en `America/Bogota`, y **cero usuarios creados** en los tres casos de 422: la
transacción revierte el `INSERT` del usuario, no solo evita la afiliación.

**PREFERENCIA del usuario:** el selector de plan vive solo en el registro público de pacientes. El
alta de profesionales que hace el ADMIN no lo lleva: un profesional se da de alta por su rol, no
por su cobertura.

## Respondidas de forma provisional en S3 (aprobación delegada)

Las preguntas **E1, E2, N2, N5 y A3** (la A3 de *autenticación*: cómo nace el primer ADMIN), y las
incógnitas INC-009, INC-013, INC-014, INC-024 e INC-032, tienen una respuesta provisional (D5–D13)
en [[dec-004-decisiones-s3-reserva]]. Siguen listadas arriba hasta que el usuario las confirme.
E1, E2 y S3 ya tienen además la corrección **aplicada** en `V5` y `V6`.

## Respondidas para S4 (2026-09-25)

**N1, N3 y N6** de las reglas de negocio, **A1 y A4** de autenticación (política de contraseña y
vigencia del token de recuperación), y las incógnitas INC-001, 002, 005, 006, 007, 011, 018, 027,
028, 029, 030, 031 y 036 tienen respuesta en [[dec-006-decisiones-s4-ciclo-de-vida]] (D15–D30).
El usuario respondió directamente **N1** (D18) e **INC-018** (D19); las demás son provisionales
bajo aprobación delegada. **N4** no necesitaba decisión: HU-028 T-03 ya dice que "conservar" no
hace ninguna llamada de escritura.

## Defectos de especificación que salieron al cerrar S3 (2026-09-23)

Los tres aparecieron al exigir evidencia HU por HU, no al escribir el código: son casos donde una
Definition of Done pide algo que la implementación no cumple, y por eso su HU **no** cerró.
Verificados contra el esquema y el código, no deducidos.

| # | Defecto | Impide cerrar | Decisión pendiente |
|---|---|---|---|
| S1 | `specialties` **no tiene restricción única sobre `name`**. Solo existe `uq_specialties_code`; la unicidad del nombre vive únicamente en `ManageSpecialtiesUseCase`, así que dos procesos concurrentes podrían crear especialidades homónimas | HU-011 | ¿Migración posterior a V7 que añada la única, o se acepta que la regla viva solo en el caso de uso? |
| S2 | Activar y desactivar un profesional **no son operaciones del dominio**. `Professional` no tiene `activate()` ni `deactivate()`: se pasa por el puerto genérico `ProfessionalRepository#setActive(long, boolean)`, que es justo lo que la DoD de la HU descarta. Tampoco hay prueba de que desactivar conserve las citas existentes | HU-016 | ¿Se sube la transición al dominio, o se relaja la DoD? |
| S3 | La regla de **consecutividad de 60 minutos está escrita dos veces**: en `AvailabilityBlock#canHost` (la usa la reserva) y en el SQL de `JdbcAvailabilityQueries` (la usa la búsqueda). Hoy coinciden, pero nada impide que divierjan y entonces la búsqueda ofrecería franjas que la reserva rechaza | HU-022 | ¿Se unifica en un solo sitio, o se añade una prueba que compare las dos implementaciones? |

Ninguno es un fallo de comportamiento observable: los tres son riesgos de que el comportamiento
correcto de hoy deje de serlo sin que nada avise. Ver [[datos-modelo-3fn]] y
[[dec-004-decisiones-s3-reserva]].

**HECHO relacionado:** no existe productor de retenciones por reprogramación
(`reservation_type = 'RESCHEDULE_REQUEST'`). El esquema las soporta desde `V3`, pero hasta que
HU-027 y HU-031 existan, cualquier criterio que dependa de ellas es literalmente no verificable.
Es la causa de que HU-022 y HU-029 queden abiertas.

## Defecto de documento y preguntas abiertas al retomar S4 (2026-09-30)

Salieron al bajar a esta máquina los commits de S4 y comprobar el estado real contra los ficheros.
No son huecos del PRD como los de arriba: son huecos entre lo que los documentos del proyecto dicen
y lo que pasó. El detalle y las pruebas están en
[[dec-006-decisiones-s4-ciclo-de-vida]] § "Estado real de S4".

| # | Pregunta | Estado |
|---|---|---|
| R1 | **Defecto verificado, no pregunta:** las 7 casillas de F10 de `PLAN_RETOMA_S4.md` estaban `[x]` y F10 no se ejecutó; el archivo entero se escribió en el commit `bc13adc`. Resultó estar mal **en los dos sentidos**: F4, F6 y F7 estaban sin marcar con su código escrito | **Cerrada el 2026-09-30.** Casillas saneadas contra el código real: F10 desmarcada salvo el push/merge, F4/F6/F7 marcadas con la referencia que las prueba, y nota de precedencia añadida al §4 |
| R2 | `main` == `develop` en los tres repos desde el 2026-09-25 con S4 a medio verificar (F5, F8, F9 y F10 sin terminar), contra `AGENTS.md:102`. ¿Se acepta el estado, se revierte el merge, o se redefine qué significa "estable" para `main`? | **Resuelta el 2026-09-30:** el usuario decide **dejar `main` como está** —es un laboratorio y revertir un merge publicado añade más riesgo que valor— y que el próximo merge a `main` se haga solo al cerrar F10 |
| R3 | ¿Cuáles de las **11 HU `Aprobada` con código escrito** resisten la verificación independiente? | **Parcialmente respondida el 2026-09-30** al cerrar F5: **3 de las 11** (HU-027, HU-028, HU-031) pasaron la verificación de backend con matriz completa y están en `En validación`; ninguna llegó a `Completada` porque les faltan los criterios de frontend uno a uno y la prueba manual en navegador. Las **8 restantes** siguen sin verificar. Docker ya no es el impedimento: las suites corren (484/484 y 218/218) |
| R4 | La comparación del modelo 3FN propio contra `database/reference/` sigue pendiente desde S2, aunque su casilla de F10 esté marcada | Abierta — ver [[datos-modelo-3fn]] |

## Relacionado

- [[dec-004-decisiones-s3-reserva]]
- [[datos-modelo-3fn]]
- [[dec-002-rotacion-refresh-tokens]]
- [[contrato-rest-identidad]]
- [[contrato-rest-citas]] — dónde se publican los endpoints que estas decisiones condicionan
- [[dec-005-sistema-visual-stitch]] — la pregunta abierta del gráfico "Citas por sede esta semana"

## Historial

- 2026-09-30 — añadidas R1–R4 al retomar S4: el defecto de las casillas de F10, `main` con trabajo a
  medio verificar, las 11 HU `Aprobada` sin verificación independiente y la comparación 3FN que
  sigue pendiente. El mismo día se cerraron R1 (casillas saneadas) y R2 (el usuario deja `main` como
  está); quedan abiertas R3 y R4, que dependen de F10.
- 2026-09-23 (LINT) — E1, E2 y S3 marcadas como cerradas con cita a `V5` y `V6`; las preguntas de
  afiliación renumeradas `A1–A3` → `AF1–AF3` para deshacer la colisión con las `A1–A4` de
  autenticación.
- 2026-09-18 — E1, E2, N2, N5 y A3 respondidas de forma provisional para S3 (D5–D13 en [[dec-004-decisiones-s3-reserva]]).
- 2026-09-17 — A1 precisada con la divergencia cliente/servidor; añadida S4 (refresh token en memoria JS) y la tabla D1–D4 de decisiones tomadas bajo aprobación delegada.
- 2026-09-16 — añadidas A1–A4 del informe de especificación (40 incógnitas `INC-NNN` registradas en las épicas).
- 2026-09-16 — creada al terminar la especificación Scrum (33 HU). E1 y E2 verificadas contra `V3__schedule_and_appointments.sql`.

## Abiertas al cerrar el LOOP_02 de S4 (2026-09-30)

Las destapó la verificación independiente de la reprogramación. Ninguna bloquea el cierre de F5; todas
van a F10. Detalle y evidencia en `evidencias/s4/loops/LOOP-02/iter-2-verifier.json`.

| # | Pregunta | Estado |
|---|---|---|
| R5 | La carrera **«cerrar la atención» contra «decidir la reprogramación»**, que D38 abre, no tiene prueba concurrente. Existe el caso secuencial y el argumento estructural es sólido —los tres caminos bloquean primero la cita y después la solicitud, un orden único que serializa sin interbloquear— pero la concurrencia concreta no está probada | Abierta. `NO VERIFICABLE` por falta de prueba, no fallo |
| R6 | El aviso de una solicitud `CANCELLED` en el detalle del paciente **descarta `decisionReason`**, así que quien vea su reprogramación cancelada porque el profesional cerró la atención (D38) no recibe ninguna explicación. El texto visible no miente, calla. D37 creó ese dato justo «para que la solicitud no quede sin explicación en la bandeja **ni en el detalle**» | Abierta — defecto de completitud, gravedad media |
| R7 | **HU-021 no menciona D38 en ninguna parte** —ni alcance, ni reglas, ni ningún CA— aunque cerrar la atención cancele la solicitud `PENDING` y libere su retención, que es un efecto observable de dos endpoints REST ya implementado y documentado en [[contrato-rest-citas]] | Abierta |
| R8 | Las matrices de evidencia de **HU-021, HU-026 y HU-029** siguen sin rellenar; la de HU-029 es de S3 y afirma que la mitad de reprogramaciones «no existe», cuando ya existe con evidencia | Abierta hasta F10 |
| R9 | El texto **«Su hora ya llegó»** que el paciente ve cuando no puede cancelar depende de que `cancellable === false` implique «la cita ya empezó». Hoy **está garantizado** (verificado contra `Appointment.isCancellableAt` y la tabla de transiciones), pero **ninguna prueba de frontend protege esa garantía**: si alguien añade una causa a `isCancellableAt`, el texto miente y las 218 pruebas siguen en verde | Abierta — riesgo de acoplamiento, no defecto |
| R10 | La derivación de `RESCHEDULED` se apoya en un invariante de **aplicación** que el esquema no protege: `appointment_status_history` admite dos filas consecutivas con el mismo `status_id` por vías ajenas a la aplicación (SQL manual, semillas). Mientras solo escriba la aplicación se cumple, y `HistoryWritersArchitectureTest` lo fija sobre bytecode; un `INSERT` nativo escrito a mano no lo vería | Abierta — límite conocido, gravedad baja |
| R11 | **HU-029 CA-06** se apoya en la regla de prefijo de `SecurityConfig` y en una prueba sobre otro endpoint: ninguna asevera 403/401 sobre `/api/admin/inbox` | Abierta — informativa |
| R12 | **`HU-031:67`** («Cada decisión queda registrada con actor y origen `ADMIN`») no es falsa —el rechazo sí guarda decisor y origen, en `reschedule_requests`— pero invita a la lectura vieja de D22. Quedó fuera de los cinco textos que D40 autorizó alinear | Abierta — cosmética |

- 2026-09-30 — añadidas R5–R12 al cerrar el LOOP_02. R1 y R2 quedaron cerradas ese mismo día; R3 pasó
  a parcialmente respondida (3 de 11 HU verificadas); R4 sigue abierta.

## S5–S6 (n8n): decisiones y preguntas abiertas al planificar (2026-09-30)

Plan completo en `PLAN_S5_S6_N8N.md` (raíz). Nada está implementado; ninguna HU de EP-010 existe todavía.

**Decididas por el usuario (directas, no delegadas):**

- **DECISIÓN D-A:** n8n se autentica contra la API con una **clave dedicada de solo lectura** en `/api/automation/**`
  (cabecera `X-Automation-Key`, cadena de seguridad propia), no con una cuenta ADMIN.
- **DECISIÓN D-B:** n8n es remoto y la API local, así que para la demo se usa un **túnel temporal**.
- **DECISIÓN D-H:** los workflows se crean **de cero** con prefijo `jhonNuñez-` porque la instancia n8n es compartida; los
  tres borradores existentes (`6Ks5HWdXadUSBW7o`, `Cu7kjdPURjE8LnTp`, `OJqkZkMKhoJJTcXc`) no se tocan.
- **PREFERENCIA:** el `.env` lleva tres variables de webhook, una por flujo (`N8N_WEBHOOK_WF001_URL`, `…WF002_URL`,
  `…WF003_URL`), más `N8N_WEBHOOK_SECRET` y `AUTOMATION_API_KEY`.

**HECHO verificado:** ocho HU (023, 025, 026, 027, 028, 030, 031, 032) sacan el correo de su alcance y lo remiten a PRD §10;
el backend no tiene puerto de eventos, cliente HTTP saliente ni endpoint legible sin JWT de persona. Por la regla
«sin HU aprobada no se implementa», hace falta la épica EP-010.

**Abiertas:** ventana del recordatorio (D-C, propuesta 24 h) · anti-duplicado en Data Table o columna (D-D) · si la cita
general auto-aprobada notifica (D-E) · entrega *best-effort* u *outbox* (D-F) · si WF-003 entra (D-G) · webhooks a demanda
en WF-001/003 (D-I) · qué hacer con un día sin citas en WF-003 · S5 no debería abrirse con S4 en F10 sin autorización expresa.
