---
titulo: "Datos — Modelo 3FN propio de citas-api"
tipo: datos
estado: Vigente
actualizado: 2026-09-30
fuentes: ["[[MODELO-DATOS-3FN]]", "citas-api/src/main/resources/db/migration/ (V1..V10)", "citas-api/src/test/java/com/fcv/citas/infrastructure/persistence/FlywayMigratesEmptySchemaTest.java:37,116,121", "database/ANALISIS_NORMALIZACION_3FN.md"]
tags: [datos, mysql, flyway, 3fn]
---

# Datos — Modelo 3FN de `citas-api`

## Qué es

Diseño **propio** del esquema, construido desde `database/ANALISIS_NORMALIZACION_3FN.md` sin
consultar el material de referencia del trainer.

> **Corregido en el LINT del 2026-09-23:** esta página citaba `database/reference/db.sql` como la
> solución de referencia. **Ese archivo no existe.** Lo que hay en `database/reference/` es
> `README_DB.md`, `erd.mmd` y el ERD en PNG/SVG (verificado con `ls database/reference`). La
> comparación contra esa referencia **sigue pendiente**: S2 cerró sin hacerla.
>
> **Confirmado el 2026-09-30:** sigue pendiente. La casilla de F10 que la reclama estaba marcada
> `[x]` (ya desmarcada ese mismo día), pero F10 nunca se ejecutó — el plan se escribió
> entero en un solo commit. Esta página es una de las pruebas de ello; ver
> [[dec-006-decisiones-s4-ciclo-de-vida]] § "Estado real de S4" y R4 de
> [[sintesis-preguntas-abiertas]].

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

- **Desde cero.** `FlywayMigratesEmptySchemaTest` crea un esquema desechable, aplica V1–V7 sobre
  él, valida y cuenta 24 tablas; después lo borra. Así una migración que solo funcione sobre una
  base ya migrada no pasa desapercibida.
- **Base propia de pruebas.** Las pruebas de integración usan `citas_fcv_training_test`, no la
  base de desarrollo `citas_fcv_training`. La crea `scripts/init-test-db.ps1` en la raíz, y el
  compose le pasa el nombre como `DB_NAME_TEST`. Esa base no se recrea en cada ejecución: Flyway
  la encuentra al día.
- El script concede al usuario de la aplicación todos los privilegios sobre el patrón
  `citas\_fcv\_%`, para que la prueba pueda crear y borrar su esquema desechable. Es aceptable en
  laboratorio y **no** debe replicarse en un despliegue real.

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
