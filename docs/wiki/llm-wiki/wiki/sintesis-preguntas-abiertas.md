---
titulo: "Síntesis — Preguntas abiertas del dominio"
tipo: sintesis
estado: Vigente
actualizado: 2026-09-23
fuentes: ["citas-api/docs/wiki/scrum/historias-de-usuario/", "PRD.md", "[[MODELO-DATOS-3FN]]"]
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

## Relacionado

- [[dec-004-decisiones-s3-reserva]]
- [[datos-modelo-3fn]]
- [[dec-002-rotacion-refresh-tokens]]
- [[contrato-rest-identidad]]
- [[contrato-rest-citas]] — dónde se publican los endpoints que estas decisiones condicionan
- [[dec-005-sistema-visual-stitch]] — la pregunta abierta del gráfico "Citas por sede esta semana"

## Historial

- 2026-09-23 (LINT) — E1, E2 y S3 marcadas como cerradas con cita a `V5` y `V6`; las preguntas de
  afiliación renumeradas `A1–A3` → `AF1–AF3` para deshacer la colisión con las `A1–A4` de
  autenticación.
- 2026-09-18 — E1, E2, N2, N5 y A3 respondidas de forma provisional para S3 (D5–D13 en [[dec-004-decisiones-s3-reserva]]).
- 2026-09-17 — A1 precisada con la divergencia cliente/servidor; añadida S4 (refresh token en memoria JS) y la tabla D1–D4 de decisiones tomadas bajo aprobación delegada.
- 2026-09-16 — añadidas A1–A4 del informe de especificación (40 incógnitas `INC-NNN` registradas en las épicas).
- 2026-09-16 — creada al terminar la especificación Scrum (33 HU). E1 y E2 verificadas contra `V3__schedule_and_appointments.sql`.
