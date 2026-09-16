---
titulo: "Datos — Modelo 3FN propio de citas-api"
tipo: datos
estado: Vigente
actualizado: 2026-09-16
fuentes: ["[[MODELO-DATOS-3FN]]", "citas-api/src/main/resources/db/migration/V1__identity_and_fixed_catalogs.sql", "database/ANALISIS_NORMALIZACION_3FN.md"]
tags: [datos, mysql, flyway, 3fn]
---

# Datos — Modelo 3FN de `citas-api`

## Qué es

Diseño **propio** del esquema, construido desde `database/ANALISIS_NORMALIZACION_3FN.md` sin
consultar la solución de referencia del trainer (`database/reference/db.sql`). La comparación
contra la referencia queda pendiente como paso explícito de S2.

## Lo que sabemos (verificado)

Cuatro migraciones Flyway aplicadas contra MySQL 8.4 (`flyway_schema_history` en v4, 24 tablas):

| Migración | Contenido |
|---|---|
| V1 | identidad (`users`, `roles`, `user_roles`, `document_types`), catálogos fijos, `refresh_tokens`, `password_reset_tokens` |
| V2 | `eps`, `eps_plans`, `specialties`, `professionals`, puentes N:M con sedes y especialidades, `affiliations` |
| V3 | `availability_blocks`, `availability_slots`, `appointments`, `appointment_status_history`, `reschedule_requests`, `slot_reservations` |
| V4 | seeds: 5 tipos de documento, 3 roles, 2 tipos de cita, 6 estados de cita, 4 de reprogramación, 3 regímenes, sedes HIC e ICV |

Hibernate corre con `ddl-auto: validate`: **Flyway manda sobre el esquema**.

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
- `specialties` no se siembra (es catálogo configurable), pero RF-11 presupone que **Medicina General** existe. Probablemente haga falta una V5 de datos sintéticos.
- Flyway advierte que MySQL 8.4 es más nuevo que su soporte probado. Advertencia, no error.
- **Conflicto con RN-12 (verificado en `V3__schedule_and_appointments.sql:127`):** la FK de `appointment_status_history` hacia `appointments` es `ON DELETE CASCADE`. Borrar una cita borraría su auditoría. Probable corrección: `RESTRICT` en una V5, dado que las citas no se borran sino que cambian de estado.
- **Origen de auditoría incompleto (verificado en `V3__...sql:120`):** `source ENUM('SYSTEM','USER','ADMIN')` no admite acciones del profesional, pero RF-17 (cerrar atención como `COMPLETED`/`NO_SHOW`) las produce. Afecta a HU-021. Ver [[sintesis-preguntas-abiertas]].

## Relacionado

- [[dec-003-libro-unico-slot-reservations]]
- [[dec-002-rotacion-refresh-tokens]]
- [[arq-hexagonal-seguridad]]

## Historial

- 2026-09-16 — página creada por INGEST de `raw/MODELO-DATOS-3FN.md`, verificada contra las migraciones aplicadas.
