---
id: MODELO-DATOS-3FN
tipo: modelo-datos
titulo: Modelo de datos de citas-api normalizado hasta 3FN
fecha: 2026-09-16
autor: jhonnunez@fcv.org
estado: vigente
---

# Modelo de datos de `citas-api` (3FN)

Diseño propio derivado de `PRD.md`, `RESTRICCIONES_TECNICAS.md` y
`database/ANALISIS_NORMALIZACION_3FN.md`. No se consultó la solución de referencia del
trainer. El esquema real lo produce Flyway (`src/main/resources/db/migration`); Hibernate
corre con `ddl-auto: validate` y nunca modifica el esquema.

Motor: MySQL 8.4, InnoDB, `utf8mb4` / `utf8mb4_0900_ai_ci`.

## 1. Migraciones

| Migración | Contenido |
|---|---|
| `V1__identity_and_fixed_catalogs.sql` | `document_types`, `roles`, `users`, `user_roles`, `appointment_types`, `appointment_statuses`, `reschedule_statuses`, `regimes`, `sites`, `refresh_tokens`, `password_reset_tokens` |
| `V2__configurable_catalogs_and_professionals.sql` | `eps`, `eps_plans`, `specialties`, `professionals`, `professional_specialties`, `professional_sites`, `affiliations` |
| `V3__schedule_and_appointments.sql` | `availability_blocks`, `availability_slots`, `appointments`, `appointment_status_history`, `reschedule_requests`, `slot_reservations` |
| `V4__seed_fixed_catalogs.sql` | Seeds de los catálogos fijos: tipos de documento, roles, tipos de cita, estados de cita, estados de reprogramación, regímenes y las dos sedes (HIC, ICV) |

Los catálogos configurables (`eps`, `eps_plans`, `specialties`) no se siembran: son CRUD de
ADMIN (RF-06).

## 2. Tablas, claves y cardinalidades

### 2.1 Identidad y autorización

| Tabla | PK | Claves alternas / únicas | FK |
|---|---|---|---|
| `document_types` | `id` | `code` | — |
| `roles` | `id` | `code` | — |
| `users` | `id` | `email`, `document_number` | `document_type_id → document_types` |
| `user_roles` | (`user_id`,`role_id`) | — | `users`, `roles` |

- `users (1) — (N) user_roles (N) — (1) roles`: relación N:M resuelta con tabla puente.
- Un profesional **no** es otra tabla de personas: es una fila de `users` con rol
  `PROFESSIONAL` más un perfil en `professionals`. Así los datos personales existen una sola
  vez y no hay anomalías de actualización.

### 2.2 Tokens de seguridad

| Tabla | PK | Únicos | Notas |
|---|---|---|---|
| `refresh_tokens` | `id` | `token_hash` | `family_id`, `used_at`, `revoked_at`, `revoked_reason`, `replaced_by_token_id` (auto-FK) |
| `password_reset_tokens` | `id` | `token_hash` | `used_at`, `revoked_at`, `revoked_reason` |

Solo se guarda el **hash SHA-256 hexadecimal** del token (columna `CHAR(64)` con índice
UNIQUE). El token en claro nunca toca la base. `refresh_tokens` está preparada para rotación
por familia: cada login abre una `family_id`, cada uso marca `used_at` y encadena
`replaced_by_token_id`; reusar un token ya consumido permite revocar la familia completa.
`password_reset_tokens` no rota (se consume o se revoca), por eso no lleva familia.

`users.password_hash` es `VARCHAR(255)`: admite BCrypt (60) y una migración futura a Argon2.

### 2.3 Catálogos fijos

`appointment_types` (GENERAL / SPECIALIZED con `requires_admin_approval`),
`appointment_statuses` (`is_terminal`, `releases_slots`), `reschedule_statuses`,
`regimes`, `sites` (HIC, ICV). Todos con `id` como PK e índice UNIQUE sobre `code`.

### 2.4 Catálogos configurables y profesionales

| Tabla | PK | Únicos | FK |
|---|---|---|---|
| `eps` | `id` | `code` | — |
| `eps_plans` | `id` | (`eps_id`,`code`) | `eps`, `regimes` |
| `specialties` | `id` | `code` | `appointment_types` |
| `professionals` | `id` | `user_id`, `professional_code`, `license_number` | `users` |
| `professional_specialties` | (`professional_id`,`specialty_id`) | (`professional_id`,`primary_marker`) | `professionals`, `specialties` |
| `professional_sites` | (`professional_id`,`site_id`) | — | `professionals`, `sites` |
| `affiliations` | `id` | (`user_id`,`eps_plan_id`), (`user_id`,`current_marker`) | `users`, `eps_plans` |

- `users (1) — (0..1) professionals`.
- `professionals (N) — (N) specialties` y `professionals (N) — (N) sites`.
- `eps (1) — (N) eps_plans (1) — (N) affiliations (N) — (1) users`.
- `specialties.duration_minutes` con `CHECK IN (30, 60)`: RF-09, el profesional no la
  sobrescribe.

### 2.5 Agenda, citas y auditoría

| Tabla | PK | Únicos | FK |
|---|---|---|---|
| `availability_blocks` | `id` | (`professional_id`,`block_date`,`start_time`) | `professionals`, `sites` |
| `availability_slots` | `id` | (`availability_block_id`,`start_time`) | `availability_blocks` |
| `appointments` | `id` | — | `users`(paciente), `professionals`, `sites`, `specialties`, `affiliations`, `appointment_statuses` |
| `appointment_status_history` | `id` | — | `appointments`, `appointment_statuses`, `users`(actor, nullable) |
| `reschedule_requests` | `id` | (`appointment_id`,`active_marker`) | `appointments`, `reschedule_statuses`, `users`×2 |
| `slot_reservations` | `slot_id` | (`appointment_id`,`slot_order`), (`reschedule_request_id`,`slot_order`) | `availability_slots`, `appointments`, `reschedule_requests` |

Cardinalidades: `professionals (1) — (N) availability_blocks (1) — (N) availability_slots`;
`appointments (1) — (N) appointment_status_history`;
`appointments (1) — (N) reschedule_requests` (a lo sumo una sin decidir);
`availability_slots (1) — (0..1) slot_reservations`.

## 3. No-doble-reserva garantizada por el motor (RN-01)

`slot_reservations` es un **libro único de ocupación**: su PK es `slot_id`, es decir un índice
UNIQUE sobre el slot reservado. Un slot no puede aparecer dos veces, ni siquiera cuando un
titular es una cita y el otro una solicitud de reprogramación que retiene la nueva franja
mientras está `PENDING` (RF-15). Dos transacciones concurrentes que intenten tomar el mismo
slot producen un error de clave duplicada: el motor impide la doble reserva, no el código.

Por eso **no** se modelaron dos tablas separadas (`appointment_slots` y
`reschedule_request_slots`): con dos tablas ninguna restricción UNIQUE podría impedir que la
misma franja quedase tomada en ambas a la vez. Es la única desviación respecto de
`ANALISIS_NORMALIZACION_3FN.md`, y es una fusión de dos relaciones con la misma clave
(`slot_id`), no una denormalización: no introduce redundancia.

Liberar slots (RN-09: cancelación, rechazo, reprogramación aprobada) es borrar filas de esta
tabla. `slot_order` (1 o 2) ordena los dos slots de una atención de 60 minutos; que sean
consecutivos (RN-05) lo valida el dominio al elegirlos.

## 4. Dependencias funcionales

```text
users:                     id -> document_type_id, document_number, first_names, last_names,
                                 email, phone, password_hash, active, created_at, updated_at
                           email -> id            (clave candidata)
                           document_number -> id  (clave candidata)
user_roles:                (user_id, role_id) -> assigned_at
appointment_types:         id -> code, name, requires_admin_approval
appointment_statuses:      id -> code, name, is_terminal, releases_slots
reschedule_statuses:       id -> code, name, is_terminal
regimes:                   id -> code, name
sites:                     id -> code, name, address, city, department, active
eps:                       id -> code, name, active
eps_plans:                 id -> eps_id, regime_id, code, name, active
                           (eps_id, code) -> id   (clave candidata)
specialties:               id -> appointment_type_id, code, name, duration_minutes, active
professionals:             id -> user_id, professional_code, license_number, active
professional_specialties:  (professional_id, specialty_id) -> is_primary, assigned_at
professional_sites:        (professional_id, site_id) -> assigned_at
affiliations:              id -> user_id, eps_plan_id, membership_number, is_current,
                                 started_on, ended_on
availability_blocks:       id -> professional_id, site_id, block_date, start_time, end_time
availability_slots:        id -> availability_block_id, start_time
                           start_time -> end_time  (columna GENERADA por el motor)
appointments:              id -> patient_user_id, professional_id, site_id, specialty_id,
                                 affiliation_id, status_id, scheduled_date, start_time, end_time
appointment_status_history:id -> appointment_id, status_id, actor_user_id, source, reason, changed_at
reschedule_requests:       id -> appointment_id, status_id, requested_by_user_id,
                                 proposed_date, proposed_start_time, proposed_end_time,
                                 request_reason, decided_by_user_id, decided_at, decision_reason
slot_reservations:         slot_id -> reservation_type, appointment_id,
                                      reschedule_request_id, slot_order, created_at
refresh_tokens:            id -> user_id, token_hash, family_id, issued_at, expires_at,
                                 used_at, revoked_at, revoked_reason, replaced_by_token_id
                           token_hash -> id       (clave candidata)
password_reset_tokens:     id -> user_id, token_hash, issued_at, expires_at, used_at,
                                 revoked_at, revoked_reason
```

## 5. Justificación de las formas normales

### 1FN — atomicidad y clave

Toda relación tiene clave primaria y todos sus atributos son escalares. No hay listas
embebidas: los roles de un usuario, las especialidades y sedes de un profesional y los slots
de una cita son filas de tablas puente, no cadenas separadas por comas. Los horarios se
guardan como `DATE` y `TIME` separados y atómicos, no como texto libre.

### 2FN — dependencia de la clave completa

Solo cuatro relaciones tienen PK compuesta: `user_roles`, `professional_specialties`,
`professional_sites` y `slot_reservations` (esta última tiene PK simple, `slot_id`). En las
tres primeras, los únicos atributos no clave son propios de la relación:

- `professional_specialties.is_primary` depende de la pareja completa
  (professional, specialty). No es un atributo del profesional ni de la especialidad.
- `professional_sites.assigned_at` y `user_roles.assigned_at` ídem.

No existe atributo no clave que dependa solo de una parte de la clave. En el resto de tablas
la PK es `id`, un atributo único, por lo que 2FN se cumple trivialmente.

### 3FN — sin dependencias transitivas

Los puntos donde un diseño ingenuo rompería 3FN y cómo se evitaron:

1. **Datos denormalizados en `appointments`.** La cita no guarda el nombre de la EPS, del
   plan, del régimen, del profesional, de la especialidad ni del estado: solo claves
   foráneas. Copiar cualquiera de esos nombres crearía
   `appointment_id → specialty_id → specialty_name`.
2. **Tipo de cita en `appointments`.** No se almacena `appointment_type_id` en la cita porque
   se alcanza por `specialty_id → appointment_type_id`. Guardarlo sería transitivo.
3. **Política de aprobación.** `requires_admin_approval` vive en `appointment_types`, no en
   `specialties`: la cadena `specialty_id → appointment_type_id → requires_admin_approval` es
   transitiva y se eliminó extrayendo el catálogo de tipos (corrección heredada del análisis
   de normalización previo).
4. **Duración de la cita.** No hay columna `duration_minutes` en `appointments`: la duración
   es `end_time - start_time`. La duración *contractual* de la especialidad está una sola vez
   en `specialties.duration_minutes`.
5. **Motivo de rechazo.** No se duplica en `appointments`: vive en
   `appointment_status_history.reason`, que es la fuente de verdad de RF-19. RF-13 lo obtiene
   con un join a la última transición.
6. **Slots.** `availability_slots` no repite `professional_id`, `site_id` ni la fecha: todos
   dependen del bloque (`slot_id → availability_block_id → professional_id/site_id/block_date`).
   Su `end_time` es una columna generada por el motor a partir de `start_time`, por lo que no
   puede desincronizarse.
7. **Reprogramación.** `reschedule_requests` no repite profesional ni especialidad: los
   conserva de la cita original (`appointment_id → professional_id/specialty_id`). RF-15
   establece que cambiar de profesional es una cita nueva.
8. **Régimen.** `regime_id` está en `eps_plans`, no en `eps`, porque una misma EPS puede
   ofrecer planes en varios regímenes: no existe la dependencia `eps_id → regime_id`. Si el
   negocio confirmara esa dependencia, la columna debería subir a `eps` para no violar 3FN.
9. **Afiliación en la cita.** `appointments.affiliation_id` apunta a la afiliación, no a la
   EPS ni al plan ni al régimen; esos tres se alcanzan por navegación.

**Excepción consciente y documentada:** `appointments.scheduled_date`, `start_time`,
`end_time` y `reschedule_requests.proposed_*` son *snapshots operativos*, no copias de
catálogo. El acuerdo con el paciente debe sobrevivir a que el profesional edite o elimine sus
bloques futuros (RF-08). No generan anomalías de actualización porque ningún otro registro es
su fuente de verdad una vez agendada la cita.

## 6. Restricciones de integridad declaradas en el esquema

| Regla | Mecanismo |
|---|---|
| RN-01 no doble reserva | PK `slot_reservations.slot_id` |
| RF-01 email y documento únicos | `uq_users_email`, `uq_users_document_number` |
| RF-07 una sola especialidad primaria | columna generada `primary_marker` + UNIQUE (`professional_id`,`primary_marker`) |
| RF-04 sin plan/EPS/régimen duplicado por usuario | UNIQUE (`user_id`,`eps_plan_id`) |
| RF-04 una afiliación vigente | columna generada `current_marker` + UNIQUE (`user_id`,`current_marker`) |
| RF-15 una reprogramación sin decidir por cita | columna generada `active_marker` + UNIQUE (`appointment_id`,`active_marker`) |
| RF-09 duración 30 o 60 | `CHECK (duration_minutes IN (30,60))` |
| RF-08 rejilla de 30 min | `CHECK` sobre `MINUTE()`/`SECOND()` en bloques y slots |
| RF-19 actor obligatorio salvo SYSTEM | `CHECK (source = 'SYSTEM' OR actor_user_id IS NOT NULL)` |
| Titular único de una reserva | `CHECK` de exclusividad entre `appointment_id` y `reschedule_request_id` |

Las reglas que SQL no puede expresar con una restricción se validan en el dominio y dentro de
una transacción: no solape de bloques (RF-08), slots consecutivos para 60 min (RN-05), nada en
el pasado (RN-06), sede habilitada para el profesional (RN-07), especialidad activa y asociada
(RN-08), transiciones de estado válidas (RN-11) y pertenencia de la afiliación al paciente.

## 7. Pendientes para sesiones posteriores

- Entidades JPA que mapeen este esquema (Hibernate valida contra él, no lo genera).
- Slice de autenticación: registro, login, rotación de refresh tokens y recuperación de clave
  sobre `refresh_tokens` / `password_reset_tokens`.
- Seeds sintéticos de `specialties` (incluida `Medicina General`, RF-11), `eps`, `eps_plans`,
  profesionales y citas de ejemplo: son datos configurables, no catálogo fijo.
