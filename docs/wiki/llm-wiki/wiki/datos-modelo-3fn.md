---
titulo: "Datos — Modelo 3FN propio de citas-api"
tipo: datos
estado: Vigente
actualizado: 2026-09-30
fuentes: ["[[MODELO-DATOS-3FN]]", "database/reference/erd.mmd", "database/reference/README_DB.md", "citas-api/src/main/resources/db/migration/ (V1..V10)", "citas-api/src/test/java/com/fcv/citas/infrastructure/persistence/FlywayMigratesEmptySchemaTest.java:37,116,121", "database/ANALISIS_NORMALIZACION_3FN.md"]
tags: [datos, mysql, flyway, 3fn]
---

# Datos — Modelo 3FN de `citas-api`

## Qué es

Diseño **propio** del esquema, construido desde `database/ANALISIS_NORMALIZACION_3FN.md` sin
consultar el material de referencia del trainer.

> **Comparación ejecutada el 2026-09-30 (F10 de S4).** La comparación pendiente desde S2 está en
> § "Comparación contra la referencia del trainer" de esta página. `database/reference/` contiene
> `README_DB.md`, `erd.mmd` y el ERD en PNG/SVG; **`db.sql` no existe** aunque el README lo
> anuncie, así que se compara contra `erd.mmd` + `README_DB.md`. Con esto R4 de
> [[sintesis-preguntas-abiertas]] queda hecha; las preguntas que abre (C1–C6) están al final de
> esa sección.

## Lo que sabemos (verificado)

**Diez migraciones Flyway** aplicadas contra MySQL 8.4; el esquema deja **24 tablas** de negocio
sin contar `flyway_schema_history`. Ambas cifras están fijadas por
`FlywayMigratesEmptySchemaTest`, que afirma `containsExactly("1".."10")` sobre el historial leído
con SQL plano (línea 116) y `EXPECTED_TABLES = 24` (línea 37):

| Migración | Contenido |
|---|---|
| V1 | identidad (`users`, `roles`, `user_roles`, `document_types`), catálogos fijos, `refresh_tokens`, `password_reset_tokens` |
| V2 | `eps`, `eps_plans`, `specialties`, `professionals`, puentes N:M con sedes y especialidades, **`affiliations`** |
| V3 | `availability_blocks`, `availability_slots`, `appointments`, `appointment_status_history`, `reschedule_requests`, `slot_reservations` |
| V4 | seeds: 5 tipos de documento, 3 roles, 2 tipos de cita, 6 estados de cita, 4 de reprogramación, 3 regímenes, sedes HIC e ICV |
| V5 | auditoría append-only (D8): `fk_ash_appointment` pasa a `ON DELETE RESTRICT` y `source` gana `PROFESSIONAL` |
| V6 | semilla de la especialidad protegida `MEDICINA_GENERAL` (GENERAL, 30 min), decisión D7 |
| V7 | corrección de datos: la dirección de HIC con la raya del PRD §3, sin crear tablas |
| V8 | nombre único de especialidad (cierra el defecto S1 de S3, D-R1) |
| V9 | nombres únicos de EPS y de plan, y afiliación con historial: `uq_affiliations_user_plan` pasa a `(user_id, eps_plan_id, current_marker)` (D32, D33) |
| V10 | `reschedule_requests` guarda la franja **anterior** y la sede propuesta: `previous_date`, `previous_start_time`, `previous_end_time`, `previous_site_id`, `proposed_site_id` (D31) |

De V5 a V10 **ninguna crea tablas**: por eso el total sigue en 24 con diez migraciones
(`grep -c "CREATE TABLE"` da 0 en V5–V10).

> **Corregido el 2026-09-30.** Esta página decía "siete migraciones" y `containsExactly("1".."7")`:
> era el estado del 2026-09-23. S4 añadió V8, V9 y V10 (`ls db/migration` y
> `FlywayMigratesEmptySchemaTest.java:116`). El número de tabla no cambió, así que la cifra de 24
> sigue viva. V8 cierra el defecto S1 de [[sintesis-preguntas-abiertas]]; V9 y V10 vienen de D31–D33
> en [[dec-006-decisiones-s4-ciclo-de-vida]]. **Cuidado:** que las migraciones existan no significa
> que las HU que las motivaron estén verificadas — ninguna cerró en S4.

Hibernate corre con `ddl-auto: validate`: **Flyway manda sobre el esquema**.

### `affiliations` existe desde V2

La tabla **está creada desde V2** (`V2__configurable_catalogs_and_professionals.sql:145`), con
`uq_affiliations_user_plan`, `uq_affiliations_user_current` sobre la columna generada
`current_marker`, `ck_affiliations_dates` y FK a `users` y `eps_plans`. `V3` ya la referencia
(`V3__schedule_and_appointments.sql:97`). Lo que estaba **vacío** hasta el 2026-09-23 no era la
tabla sino los catálogos `eps` y `eps_plans`, que `V4` no siembra a propósito (solo siembra
catálogos *fijos*) y que ahora llena `scripts/seed-eps-plans.ps1`. Desde HU-009 el registro
inserta la afiliación en la misma transacción que la cuenta — ver
[[contrato-rest-identidad]] y [[sintesis-preguntas-abiertas]].

### Cómo se prueba el esquema

- **Desde cero.** `FlywayMigratesEmptySchemaTest` crea un esquema desechable, aplica V1–V10 sobre
  él (`containsExactly("1".."10")`, línea 116), valida y cuenta 24 tablas; después lo borra. Así una migración que solo funcione sobre una
  base ya migrada no pasa desapercibida.
- **Base propia de pruebas.** Las pruebas de integración usan `citas_fcv_training_test`, no la
  base de desarrollo `citas_fcv_training`. La crea `scripts/init-test-db.ps1` en la raíz, y el
  compose le pasa el nombre como `DB_NAME_TEST`. Esa base no se recrea en cada ejecución: Flyway
  la encuentra al día.
- El script concede al usuario de la aplicación todos los privilegios sobre el patrón
  `citas\_fcv\_%`, para que la prueba pueda crear y borrar su esquema desechable. Es aceptable en
  laboratorio y **no** debe replicarse en un despliegue real.

## Comparación contra la referencia del trainer (F10, 2026-09-30)

**Límite de la fuente.** La referencia es un ERD Mermaid (`database/reference/erd.mmd`) que lista
**solo columnas clave** de cada entidad, más la prosa de `README_DB.md`. Sin `db.sql` no se puede
afirmar que a la referencia le *falte* una columna no clave (p. ej. `first_names`, `created_at`):
las diferencias de columnas se limitan a lo que el ERD muestra. Las diferencias de **tablas** y
de **relaciones** sí son firmes.

### Equivalencia entidad ↔ tabla

| Referencia (`erd.mmd`) | `citas-api` | Migración | Equivalencia |
|---|---|---|---|
| `USERS` | `users` | V1 | Igual; nosotros añadimos `document_type_id` |
| `ROLES` | `roles` | V1 | Igual |
| `USER_ROLES` | `user_roles` | V1 | Igual (PK compuesta) |
| `REFRESH_TOKENS` | `refresh_tokens` | V1 | Igual + rotación por familia |
| `PASSWORD_RESET_TOKENS` | `password_reset_tokens` | V1 | Igual (+ `revoked_at`) |
| `INSURANCE_REGIMES` | `regimes` | V1 | Solo cambia el nombre |
| `EPS` | `eps` | V2, V9 | Igual (+ `UNIQUE(name)` en V9) |
| `EPS_PLANS` | `eps_plans` | V2, V9 | Igual: `regime_id` en el plan en ambos modelos |
| `USER_INSURANCE_AFFILIATIONS` | `affiliations` | V2, V9 | Igual + `started_on`/`ended_on` e historial |
| `PROFESSIONALS` | `professionals` | V2 | Igual (1:1 con `users`, código y licencia únicos) |
| `APPOINTMENT_TYPES` | `appointment_types` | V1 | Igual, `requires_admin_approval` en el tipo |
| `SPECIALTIES` | `specialties` | V2, V6, V8 | Igual (`duration_minutes` ↔ `appointment_duration_minutes`) |
| `LOCATIONS` | `sites` | V1, V7 | Solo cambia el nombre; nosotros añadimos `city`, `department`, `active` |
| `PROFESSIONAL_SPECIALTIES` | `professional_specialties` | V2 | Igual, con `is_primary` |
| `PROFESSIONAL_LOCATIONS` | `professional_sites` | V2 | Igual |
| `APPOINTMENT_STATUSES` | `appointment_statuses` | V1, V4 | Igual + `releases_slots` |
| `APPOINTMENTS` | `appointments` | V3 | Mismas FKs; fecha/hora partida en `DATE` + `TIME` |
| `AVAILABILITY_BLOCKS` | `availability_blocks` | V3 | Igual salvo `active` (la referencia lo tiene) |
| `PROFESSIONAL_SLOTS` | `availability_slots` **+** `slot_reservations` | V3 | **Diferencia estructural** (ver abajo) |
| `APPOINTMENT_STATUS_HISTORY` | `appointment_status_history` | V3, V5 | Igual; `change_source` ↔ `source` |
| `RESCHEDULE_REQUEST_STATUSES` | `reschedule_statuses` | V1 | Solo cambia el nombre |
| `RESCHEDULE_REQUESTS` | `reschedule_requests` | V3, V10 | Igual en lo esencial; difieren `patient_action_after_rejection` y la franja anterior |
| — | `document_types` | V1 | Solo nuestra |

Resultado: **22 entidades de la referencia ↔ 24 tablas nuestras**. Las dos tablas de más son
`document_types` y el desdoble `PROFESSIONAL_SLOTS` → `availability_slots` + `slot_reservations`.
Los dos modelos coinciden en todas las decisiones de normalización grandes: roles N:M, profesional
como extensión de `users`, cadena régimen → plan ← EPS con la afiliación apuntando al plan, tipo
de cita como catálogo con la política de aprobación, y citas con solo FKs.

### Qué tiene la referencia y nosotros no

| Diferencia | Justificación en la wiki |
|---|---|
| **Una sola tabla `PROFESSIONAL_SLOTS` con `appointment_id` nulable** como marca de ocupación | Descartada a propósito: [[dec-003-libro-unico-slot-reservations]]. Con la ocupación dentro del slot no cabe la **retención** de la franja propuesta de una reprogramación `PENDING` sin una segunda columna o tabla, y ahí la unicidad ya no la da una sola restricción. Ver la sección de doble reserva |
| `RESCHEDULE_REQUESTS.patient_action_after_rejection` | "Conservar la cita" tras un rechazo **no se persiste**: HU-028 T-03 y N4 en [[sintesis-preguntas-abiertas]]; contrato en [[contrato-rest-citas]]. Cancelar se registra como transición normal de la cita |
| `AVAILABILITY_BLOCKS.active` | **Sin decisión** que lo respalde ni lo descarte — ver C1 |
| Fecha y hora en `DATETIME` (`scheduled_start_at`, `start_at`, `requested_start_at`) | Nosotros usamos `DATE` + `TIME` atómicos ([[MODELO-DATOS-3FN]] §5 1FN). No hay DEC que compare contra `DATETIME`; la elección tuvo coste real en [[riesgo-zona-horaria-columnas-time]] — ver C2 |

### Qué tenemos nosotros y la referencia no

| Diferencia | Justificación en la wiki |
|---|---|
| `slot_reservations` (PK `slot_id`, titular cita **o** reprogramación, `slot_order`) | [[dec-003-libro-unico-slot-reservations]]; retención de la franja propuesta por D18/D20 de [[dec-006-decisiones-s4-ciclo-de-vida]] |
| `document_types` + `users.document_type_id` | Solo el diseño [[MODELO-DATOS-3FN]] §2; D35 de [[dec-006-decisiones-s4-ciclo-de-vida]] lo usa (el profesional ve tipo y número). La unicidad global de `document_number` sigue abierta (ver "Preguntas abiertas") |
| `refresh_tokens.family_id`, `used_at`, `replaced_by_token_id`, `revoked_reason` | [[dec-002-rotacion-refresh-tokens]] (detección de reutilización); D34 revoca todas las familias al restablecer |
| `appointment_statuses.releases_slots` | [[MODELO-DATOS-3FN]] §3 y [[contrato-rest-citas]] (debe coincidir con `AppointmentStatus`) |
| `affiliations.started_on` / `ended_on` e historial por `current_marker` | D26 y D32 de [[dec-006-decisiones-s4-ciclo-de-vida]] |
| `reschedule_requests.previous_*` y `proposed_site_id` (V10) | D31 (HU-027 CA-03/CA-09) y D21 (otra sede). El README de la referencia dice que la solicitud "conserva la fecha anterior", pero su ERD solo muestra `requested_*`: ahí coincidimos con la prosa, no con el diagrama |
| `reschedule_requests.decided_by_user_id`, `decided_at`, `decision_reason` | RF-15 y [[MODELO-DATOS-3FN]] §3; no aparecen en el ERD de la referencia (puede ser solo omisión del diagrama) |
| `source` con `PROFESSIONAL` además de `SYSTEM`/`USER`/`ADMIN` | D8 de [[dec-004-decisiones-s3-reserva]] (V5) |
| `sites.city`, `department`, `active` | Solo el diseño [[MODELO-DATOS-3FN]]; V7 corrige la dirección según PRD §3. No hay DEC — trivial, no se abre pregunta |
| `UNIQUE` sobre nombres de especialidad, EPS y plan | D-R1 (V8) y D33 (V9) |
| Columnas generadas + `UNIQUE` (`primary_marker`, `current_marker`, `active_marker`) y `CHECK` de rejilla y duración | [[MODELO-DATOS-3FN]] §6. La referencia no muestra constraints; no se puede decir si las tiene |

### Evaluación 3FN

**Referencia (sobre lo que muestra el ERD):** no se ve ninguna dependencia transitiva. Guarda
`requires_admin_approval` en `APPOINTMENT_TYPES` y `regime_id` en `EPS_PLANS`, igual que nosotros.
`PROFESSIONAL_SLOTS.start_at`/`end_at` como `DATETIME` repite la fecha del bloque
(`slot → availability_block_id → available_date`): es una dependencia transitiva **salvo** que se
lea como instante absoluto; nuestro `availability_slots` no repite la fecha y deriva `end_time`
con columna generada ([[MODELO-DATOS-3FN]] §5 punto 6).

**Nuestro modelo:** sin dependencias parciales (las PK compuestas solo tienen atributos propios
de la relación). Revisión de posibles transitividades o redundancias:

| Punto | Veredicto |
|---|---|
| `appointments.scheduled_date/start_time/end_time` | Snapshot intencional, no transitividad: el acuerdo sobrevive a la edición de bloques (RF-08, [[MODELO-DATOS-3FN]] §5 "Excepción consciente"). La referencia hace lo mismo con `scheduled_*_at` |
| `reschedule_requests.previous_*` (V10) | Snapshot intencional: tras aprobar, la cita se mueve y la franja anterior deja de existir en otra parte (D31) |
| `reschedule_requests.proposed_site_id` | Derivable de la reserva retenida (`slot_reservations → slot → bloque → site_id`) mientras la retención existe, pero la retención se borra al decidir; como registro histórico es snapshot (D31). Aceptable |
| `slot_reservations.reservation_type` | Redundante con cuál de las dos FK es no nula; `ck_slot_reservations_owner` impide que diverjan. Redundancia controlada por el motor, no anomalía |
| `affiliations.is_current` frente a `ended_on` | **Posible redundancia sin decisión**: si "vigente" equivale siempre a `ended_on IS NULL`, `is_current` depende de otro atributo no clave. Ningún `CHECK` las ata — ver C3 |
| `reschedule_requests.status_id` frente a `decided_at` | **Posible redundancia sin decisión**: `PENDING` equivale a `decided_at IS NULL`, y `active_marker` se genera desde `decided_at`, no desde el estado. Ningún `CHECK` las ata — ver C4 |

Conclusión: **ambos modelos cumplen 3FN** en lo verificable; los dos puntos de riesgo propios son
redundancias entre columnas de la misma fila, no transitividades a través de otra entidad.

### Índices y constraints anti doble reserva

| Aspecto | Referencia | `citas-api` |
|---|---|---|
| Dónde vive la ocupación | `PROFESSIONAL_SLOTS.appointment_id` (nulable) | Tabla aparte `slot_reservations`, **PK = `slot_id`** (V3) |
| Qué impide dos citas en un slot | La propia columna: un slot solo tiene un `appointment_id`. Reservar es un `UPDATE` que debe condicionarse a `appointment_id IS NULL` (o bloquear la fila) — si el código hace `UPDATE` incondicional, el segundo sobrescribe al primero | Un `INSERT` duplicado falla por PK. Trampa conocida: exige `INSERT`, no `merge` (`Persistable.isNew() = true`, [[dec-003-libro-unico-slot-reservations]]) |
| Retención de reprogramación `PENDING` | No representable en el ERD sin otra columna | Misma PK: una franja no puede estar en una cita y en una retención a la vez |
| Unicidad de slots dentro del bloque | No visible en el ERD | `uq_availability_slots_block_start (availability_block_id, start_time)` + `CHECK` de rejilla |
| Bloques duplicados | No visible | `uq_availability_blocks_start (professional_id, block_date, start_time)`; el **no solape** lo valida el dominio |
| 60 min = 2 slots | README: dos slots consecutivos | `slot_order IN (1,2)`, `uq_slot_reservations_appointment_order`, `uq_slot_reservations_request_order`; consecutivos y del mismo bloque (D9) en el dominio |
| Concurrencia | No documentada | `SELECT … FOR UPDATE` sobre el profesional + PK como red; el 409 sale `SLOT_TAKEN` de forma estable ([[dec-003-libro-unico-slot-reservations]]) |
| Índices de consulta | No visibles | `ix_appointments_*` (paciente, profesional, bandeja por estado, sede, especialidad, afiliación), `ix_availability_blocks_lookup (site_id, block_date)`, `ix_ash_appointment (appointment_id, changed_at)` |

El diseño de la referencia **también** hace estructuralmente imposible que un slot pertenezca a dos
citas, pero traslada al código que la reserva sea un `UPDATE` condicionado; el nuestro lo hace
fallar en el motor con un `INSERT`, y además cubre la retención de reprogramaciones.

### Preguntas abiertas de la comparación

- **C1.** La referencia tiene `availability_blocks.active`; nosotros no. ¿Cómo se retira un bloque
  futuro con citas (RF-08)? No hay DEC que lo resuelva.
- **C2.** `DATE` + `TIME` frente a `DATETIME`: nunca se decidió contra la alternativa, y causó
  [[riesgo-zona-horaria-columnas-time]]. ¿Se mantiene como decisión explícita?
- **C3.** `affiliations.is_current` y `ended_on` pueden divergir; ¿se añade un `CHECK`
  (`is_current = (ended_on IS NULL)`) en una migración nueva, o hay caso de negocio para la
  divergencia?
- **C4.** `reschedule_requests.status_id` y `decided_at` pueden divergir; ¿se ata con un `CHECK`
  o se acepta como invariante de dominio?
- **C5.** `database/reference/README_DB.md` anuncia un `db.sql` que no existe. Sin él no se pueden
  comparar columnas no clave ni constraints. ¿Lo aporta el trainer?
- **C6.** La referencia modela `patient_action_after_rejection`; nosotros no (HU-028 T-03). Si el
  trainer lo considera requisito, falta HU; hoy no hay RF que lo exija.

## Reglas garantizadas por el motor, no por el código

- **No doble reserva (RN-01):** ver [[dec-003-libro-unico-slot-reservations]].
- Email y documento únicos (RF-01).
- Una sola especialidad primaria por profesional, una afiliación vigente por usuario, una
  reprogramación sin decidir por cita — con columnas generadas + UNIQUE.
- Duración `IN (30,60)`, rejilla de 30 minutos, actor obligatorio salvo origen `SYSTEM` — con `CHECK`.

Lo que SQL no puede expresar queda para el **dominio, dentro de una transacción**: no solape de
bloques, slots consecutivos para 60 min, nada en el pasado, sede habilitada, especialidad activa
y asociada, transiciones de estado válidas. Ver [[arq-hexagonal-seguridad]] para la regla de capas.

Tokens: refresh y reset se guardan solo como hash; ver [[dec-002-rotacion-refresh-tokens]].

## Preguntas abiertas

- `users.document_number` es UNIQUE **global**, no por tipo de documento. Sigue la letra de RF-01, pero un pasaporte y una cédula con el mismo número chocarían.
- `appointments` no guarda duración, tipo ni motivo de rechazo (derivables o en el historial): mostrar el motivo (RF-13) exige un join.
- `eps_plans.regime_id` vive en el plan, asumiendo que una EPS tiene planes de varios regímenes. Si el negocio dice `EPS → régimen`, hay que subir la columna.
- Flyway advierte que MySQL 8.4 es más nuevo que su soporte probado. Advertencia, no error.
- `appointments` **no** tiene restricción que obligue a una cita a tener filas en
  `slot_reservations`. Que toda cita ocupe su franja lo garantiza hoy el código, porque existe un
  único camino de creación. Ver [[dec-003-libro-unico-slot-reservations]].

## Cerradas (antes figuraban aquí como abiertas)

`V5` y `V6` ya existen, así que tres preguntas de esta página quedaron resueltas y se movieron
aquí en el LINT del 2026-09-23 en vez de borrarse:

| Antes decía | Estado real verificado |
|---|---|
| "Conflicto con RN-12: la FK de `appointment_status_history` es `ON DELETE CASCADE`" (`V3__...sql:127`) — era E1 | **Resuelto en V5**: `ADD CONSTRAINT fk_ash_appointment … ON DELETE RESTRICT` (`V5__audit_history_append_only.sql:14-15`) |
| "Origen de auditoría incompleto: `source ENUM('SYSTEM','USER','ADMIN')`" (`V3__...sql:120`) — era E2 | **Resuelto en V5**: `MODIFY source ENUM('SYSTEM','USER','ADMIN','PROFESSIONAL')` (`V5__...sql:18`) |
| "`specialties` no se siembra… probablemente haga falta una V5 de datos sintéticos" | **Resuelto en V6**: `INSERT INTO specialties … 'MEDICINA_GENERAL'` (`V6__seed_general_medicine.sql:9-10`). La V5 acabó siendo la de auditoría, no la de datos |

Siguen `Provisional` como *decisiones* (D7, D8 de [[dec-004-decisiones-s3-reserva]]) porque el
usuario no las ha confirmado, pero ya no son huecos del esquema.

## Relacionado

- [[dec-003-libro-unico-slot-reservations]]
- [[dec-002-rotacion-refresh-tokens]]
- [[arq-hexagonal-seguridad]]
- [[riesgo-zona-horaria-columnas-time]] — las columnas `TIME` de `availability_blocks` y `availability_slots` se desplazaban 5 h según la zona del JVM
- [[contrato-rest-identidad]] — `affiliations` y el catálogo público de planes de EPS
- [[sintesis-preguntas-abiertas]]

## Historial

- 2026-10-04 — C5 reconfirmada: `database/reference/` tiene `README_DB.md`, `erd.mmd` y el ERD, y **no** `db.sql`. Nota de entorno: `FlywayV10BackfillTest` usa el esquema desechable `citas_fcv_migrations_v10_check` (`FlywayV10BackfillTest.java:26`); no es una base de trabajo.
- 2026-09-30 — **F10: comparación real contra `database/reference/`** (`erd.mmd` y `README_DB.md`;
  `db.sql` no existe). Equivalencias, diferencias justificadas con decisiones existentes,
  evaluación 3FN y anti doble reserva. Preguntas nuevas C1–C6.

- 2026-09-30 — siete migraciones → **diez** (V8, V9, V10 de S4), verificado con `ls db/migration` y
  `FlywayMigratesEmptySchemaTest.java:116`; 24 tablas sigue en pie. Confirmado que la comparación
  contra `database/reference/` sigue pendiente; su casilla de F10 estaba marcada por error y se
  desmarcó ese mismo día.
- 2026-09-23 (LINT) — corregidas dos cifras desfasadas (cuatro migraciones → **siete**;
  `flyway_schema_history` en v4 → **v7**) y una ruta inexistente (`database/reference/db.sql`).
  E1, E2 y la semilla de Medicina General pasan de abiertas a cerradas con cita a V5 y V6.
  Añadida la sección sobre `affiliations`, que existe desde V2.
- 2026-09-17 — añadido cómo se prueba el esquema: migración desde un esquema vacío y base de pruebas aislada.
- 2026-09-16 — página creada por INGEST de `raw/MODELO-DATOS-3FN.md`, verificada contra las migraciones aplicadas.
