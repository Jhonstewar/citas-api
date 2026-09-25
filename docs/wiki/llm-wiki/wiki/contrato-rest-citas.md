---
titulo: "Contrato REST — Catálogos, profesionales, agenda y citas (S3)"
tipo: contrato
estado: Vigente
actualizado: 2026-09-25
fuentes: ["PRD.md §4", "HU-005, HU-010, HU-011, HU-013..HU-019, HU-022..HU-025, HU-029, HU-030, HU-032", "[[dec-004-decisiones-s3-reserva]]"]
tags: [contrato, rest, s3, citas]
---

# Contrato REST — Catálogos, profesionales, agenda y citas (S3)

Continúa [[contrato-rest-identidad]]: mismas convenciones (JSON, `Authorization: Bearer`,
errores `ProblemDetail` en español, locale fijo `es_CO`). Contrastado con el código al cerrar F6
(2026-09-18): cada ruta tiene prueba de integración en `citas-api/src/test/.../infrastructure/rest/`.
Los campos nulos se omiten del JSON (`default-property-inclusion: non_null`): `rejectionReason`,
`actorName` y `reason` pueden no venir.

## Convenciones

- **Fechas** `"2026-09-21"` (ISO, sin zona) y **horas** `"08:30"` (HH:mm). Zona de referencia
  del sistema: `America/Bogota`. "Pasado" se compara con la hora actual en esa zona.
- **Identificadores** numéricos (`id`). Los códigos de catálogo (`HIC`, `APPROVED`…) son estables.
- **Prefijo por rol** (HU-005): `/api/admin/**` → `ADMIN`, `/api/professional/**` →
  `PROFESSIONAL`, `/api/patient/**` → `USER`. `/api/catalogs/**` y `/api/me` → cualquier rol
  autenticado, **con una excepción**: `/api/catalogs/insurance-plans` es público desde HU-009
  (`SecurityConfig.java:69` la declara `permitAll` **antes** del `authenticated()` de
  `/api/catalogs/**` de la línea 76). Cualquier otra ruta no declarada → denegada.
- **Ownership:** un recurso ajeno responde **404** (no revela que existe).

### Códigos de error

| Código | Cuándo | `code` (extensión del ProblemDetail) |
|---|---|---|
| 400 | Campos inválidos (`fieldErrors`), bloque en el pasado, duración ≠ 30/60, rechazo sin motivo, conjuntos vacíos | `VALIDATION`, `PAST_TIME` |
| 401 | Sin token, token inválido o caducado | — |
| 403 | Rol insuficiente | — |
| 404 | No existe o no es del usuario | `NOT_FOUND` |
| 409 | Conflicto de estado o de unicidad | `DUPLICATE` (+ `field`), `SLOT_TAKEN`, `BLOCK_OVERLAP`, `BLOCK_HAS_APPOINTMENTS`, `INVALID_TRANSITION`, `APPOINTMENT_EXPIRED`, `SPECIALTY_REFERENCED`, `PROTECTED_SPECIALTY`, `CONCURRENT_CHANGE` (otra transacción cambió los datos a la vez; reintentar) |
| 422 | Regla de negocio | `PAST_TIME`, `SITE_NOT_ASSIGNED`, `SPECIALTY_INACTIVE`, `SPECIALTY_NOT_ASSIGNED`, `PROFESSIONAL_INACTIVE`, `WRONG_FLOW`, `SLOT_NOT_AVAILABLE` |

Todo error lleva `title` y `detail` en español listos para mostrarse. El frontend decide por
`status` y `code`, nunca parseando `detail`.

## Tipos compartidos

```ts
SiteRef         { id, code, name }
Site            { id, code, name, address, city, department }
SpecialtyRef    { id, code, name, appointmentType: 'GENERAL' | 'SPECIALIZED', durationMinutes: 30 | 60 }
Specialty       SpecialtyRef & { requiresAdminApproval: boolean, active: boolean, protected: boolean }
ProfessionalRef { id, fullName }
PatientRef      { id, fullName, documentType, documentNumber, email, phone }
Professional    { id, userId, firstNames, lastNames, fullName, documentType, documentNumber, email, phone,
                  professionalCode, licenseNumber, active,
                  specialties: (SpecialtyRef & { primary: boolean })[], sites: SiteRef[] }
Slot            { id, startTime, endTime, available: boolean }
Block           { id, date, startTime, endTime, site: SiteRef, editable: boolean, slots: Slot[] }
Offer           { professional: ProfessionalRef, site: SiteRef, specialty: SpecialtyRef,
                  date, startTime, endTime, durationMinutes }
Appointment     { id, status, statusName, date, startTime, endTime, durationMinutes,
                  site: SiteRef, professional: ProfessionalRef, specialty: SpecialtyRef,
                  rejectionReason: string | null, createdAt }
HistoryEntry    { status, statusName, source: 'SYSTEM'|'USER'|'ADMIN'|'PROFESSIONAL',
                  actorName: string | null, reason: string | null, changedAt }
AppointmentDetail Appointment & { history: HistoryEntry[] }
AdminAppointment  AppointmentDetail & { patient: PatientRef }
```

`status` ∈ `REQUESTED | APPROVED | REJECTED | CANCELLED | COMPLETED | NO_SHOW`.

## Catálogos — cualquier rol autenticado (HU-010)

| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/api/catalogs/sites` | `Site[]` (HIC, ICV) |
| GET | `/api/catalogs/specialties` | `Specialty[]` **solo activas** |
| GET | `/api/catalogs/appointment-types` | `{ code, name, requiresAdminApproval }[]` |
| GET | `/api/catalogs/appointment-statuses` | `{ code, name, terminal }[]` |
| GET | `/api/catalogs/reschedule-statuses` | `{ code, name, terminal }[]` (incluye `PENDING`, HU-010 CA-04) |
| GET | `/api/catalogs/document-types` | `{ code, name }[]` |
| GET | `/api/catalogs/roles` | `{ code, name }[]` |
| GET | `/api/catalogs/regimes` | `{ code, name }[]` |
| GET | `/api/catalogs/insurance-plans` | **público, sin token** (HU-009) — planes de EPS ofrecibles; el detalle del cuerpo vive en [[contrato-rest-identidad]] |

Las nueve rutas salen del mismo `CatalogController` (`@RequestMapping("/api/catalogs")`,
`CatalogController.java:22,33-78`). No hay escritura sobre catálogos fijos: `POST/PUT/DELETE` → 405.
`insurance-plans` se declara **sin método** en `SecurityConfig` justamente para conservar ese 405
en vez del 401 que daría la cadena de seguridad.

## Especialidades — ADMIN (HU-011)

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| GET | `/api/admin/specialties` | — | `Specialty[]` (activas e inactivas) |
| POST | `/api/admin/specialties` | `{ code, name, appointmentType, durationMinutes }` | 201 `Specialty` · 400 · 409 `DUPLICATE` (`field` = `code` o `name`; nombre único sin distinguir mayúsculas) |
| PUT | `/api/admin/specialties/{id}` | `{ name, appointmentType, durationMinutes }` | 200 `Specialty` · 409 `PROTECTED_SPECIALTY` si se cambia el tipo de Medicina General · 409 `SPECIALTY_REFERENCED` si se cambia el tipo de una especialidad con profesionales o citas · 409 `DUPLICATE` |
| PATCH | `/api/admin/specialties/{id}/status` | `{ active }` | 200 `Specialty` · 409 `PROTECTED_SPECIALTY` al desactivar Medicina General |
| DELETE | `/api/admin/specialties/{id}` | — | 204 · 409 `SPECIALTY_REFERENCED` si la usa un profesional o una cita |

`code` es inmutable; se normaliza a mayúsculas. `durationMinutes` ∉ {30, 60} → 400 con
`fieldErrors.durationMinutes = "Solo se admiten 30 o 60 minutos"`.

## Profesionales — ADMIN (HU-013 a HU-016)

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| GET | `/api/admin/professionals` | query opcional `active`, `specialtyId`, `siteId` | `Professional[]` |
| GET | `/api/admin/professionals/{id}` | — | `Professional` |
| POST | `/api/admin/professionals` | `{ firstNames, lastNames, documentType, documentNumber, email, phone, password, professionalCode, licenseNumber, specialtyIds, primarySpecialtyId, siteIds }` | 201 · 400 · 409 `DUPLICATE` con `field` ∈ `email`, `documentNumber`, `professionalCode`, `licenseNumber` · 422 `SPECIALTY_INACTIVE` |
| PUT | `/api/admin/professionals/{id}` | `{ firstNames, lastNames, phone }` | 200 (código y matrícula no se editan: HU-013, INC-015) |
| PUT | `/api/admin/professionals/{id}/specialties` | `{ specialtyIds, primarySpecialtyId }` | 200 · 400 (vacío o primaria fuera del conjunto) · 422 `SPECIALTY_INACTIVE` |
| PUT | `/api/admin/professionals/{id}/sites` | `{ siteIds }` | 200 · 400 (vacío o sede inexistente) |
| PATCH | `/api/admin/professionals/{id}/status` | `{ active }` | 200 |

El alta es atómica: usuario con rol `PROFESSIONAL` + perfil + especialidades + sedes, o nada.
La contraseña inicial la fija el ADMIN (D6) y nunca vuelve en la respuesta. No existe borrado.

## Agenda — PROFESSIONAL (HU-017 a HU-019)

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| GET | `/api/professional/me` | — | `Professional` (el propio) |
| GET | `/api/professional/blocks?from=&to=` | — | `Block[]` ordenados por fecha y hora; rango máximo 62 días |
| POST | `/api/professional/blocks` | `{ siteId, date, startTime, endTime }` | 201 `Block` · 400 `PAST_TIME`/rejilla · 409 `BLOCK_OVERLAP` · 422 `SITE_NOT_ASSIGNED` |
| PUT | `/api/professional/blocks/{id}` | igual | 200 `Block` · 400 `PAST_TIME` · 409 `BLOCK_OVERLAP`/`BLOCK_HAS_APPOINTMENTS` · 422 |
| DELETE | `/api/professional/blocks/{id}` | — | 204 · 400 `PAST_TIME` · 409 `BLOCK_HAS_APPOINTMENTS` |

Horas en la rejilla de 30 min (`:00` o `:30`), `endTime > startTime`. El bloque se expande en
slots de 30 min. El titular sale siempre del token; un bloque ajeno → 404.
`editable = futuro && sin reservas`.

## Reserva — USER (HU-022 a HU-025)

| Método | Ruta | Cuerpo / query | Respuesta |
|---|---|---|---|
| GET | `/api/patient/availability` | `date` obligatorio; `specialtyId` **o** `appointmentType` (`GENERAL`/`SPECIALIZED`) obligatorio, pueden ir los dos; `siteId`, `professionalId` opcionales (RF-10) | `Offer[]` ordenadas por hora |
| GET | `/api/patient/availability/days` | `from`, `to` (máx. 62 días); mismos filtros que la anterior | `{ date, offers }[]` solo días con oferta |
| POST | `/api/patient/appointments/general` | `{ professionalId, siteId, specialtyId, date, startTime }` | 201 `Appointment` `APPROVED` |
| POST | `/api/patient/appointments/specialized` | igual | 201 `Appointment` `REQUESTED` |
| GET | `/api/patient/appointments` | `status`, `date` opcionales | `Appointment[]` (próximas primero) |
| GET | `/api/patient/appointments/{id}` | — | `AppointmentDetail` · 404 si no es suya |

Errores de reserva: 409 `SLOT_TAKEN` (otra reserva ganó la franja), 422 `PAST_TIME`,
`WRONG_FLOW` (especializada por la ruta general o al revés; `detail` indica la ruta correcta),
`SPECIALTY_INACTIVE`, `SPECIALTY_NOT_ASSIGNED`, `PROFESSIONAL_INACTIVE`, `SITE_NOT_ASSIGNED` y
`SLOT_NOT_AVAILABLE` (no existe bloque/slot, o 60 min sin su consecutivo en el mismo bloque, D9).

La duración la fija la especialidad (RF-09): el cuerpo no la lleva. La oferta solo empareja
slots libres del **mismo bloque**, excluye el pasado, profesionales inactivos y especialidades
inactivas o no asignadas. Buscar no retiene nada.

## Operación — ADMIN (HU-029, HU-030, HU-032)

| Método | Ruta | Cuerpo / query | Respuesta |
|---|---|---|---|
| GET | `/api/admin/inbox` | `siteId`, `professionalId`, `specialtyId`, `date` opcionales | `{ type: 'APPOINTMENT_REQUEST', appointment: AdminAppointment }[]` |
| GET | `/api/admin/appointments/{id}` | — | `AdminAppointment` |
| POST | `/api/admin/appointments/{id}/approve` | — | 200 `AdminAppointment` · 409 `INVALID_TRANSITION`/`APPOINTMENT_EXPIRED` |
| POST | `/api/admin/appointments/{id}/reject` | `{ reason }` (1–500) | 200 · 400 sin motivo · 409 `INVALID_TRANSITION` |
| GET | `/api/admin/summary` | — | `{ pendingRequests, activeProfessionals, activeSpecialties, appointmentsToday }` |

Aprobar conserva las reservas de slots; rechazar las borra en la misma transacción (RN-09).
Cada transición escribe una fila de historial en la misma transacción (HU-032): creación general
→ `SYSTEM` sin actor, solicitud especializada → `USER`, decisión → `ADMIN` con motivo si rechaza.
En el detalle del **paciente**, `actorName` de las entradas `ADMIN` es "Administración" (no el nombre del empleado); el ADMIN ve el nombre real. El historial no tiene rutas de escritura. En S4 la bandeja incluirá `type: 'RESCHEDULE_REQUEST'`.

## S4 — ciclo de vida, agenda del profesional y EPS (acordado el 2026-09-25, antes de implementar)

Contrato común de backend y frontend para S4, escrito antes del código igual que el de S3. Las
decisiones que cita están en [[dec-006-decisiones-s4-ciclo-de-vida]]. Cuenta y contraseña están en
[[contrato-rest-identidad]] §S4.

### Tipos nuevos o ampliados

```ts
TimeSlot          { date, startTime, endTime, site: SiteRef }
RescheduleRequest { id, appointmentId, status: 'PENDING'|'APPROVED'|'REJECTED'|'CANCELLED', statusName,
                    previous: TimeSlot,      // franja de la cita al pedirla (V10)
                    proposed: TimeSlot,      // franja pedida; la sede puede cambiar (D21)
                    requestReason: string | null, decisionReason: string | null,
                    createdAt, decidedAt: string | null }
Appointment       + { pendingReschedule: boolean }            // aditivo
AppointmentDetail + { lastReschedule: RescheduleRequest | null, // la más reciente, en cualquier estado (HU-028)
                      cancellable: boolean,                     // futura y no terminal (D16, D17)
                      reschedulable: boolean }                  // APPROVED, futura y sin PENDING (D20)
AdminAppointment  + { lastReschedule: RescheduleRequest | null }
ProfessionalAppointment { id, status, statusName, date, startTime, endTime, durationMinutes,
                    site: SiteRef, specialty: SpecialtyRef,
                    patient: { fullName, documentType, documentNumber },  // mínimo de RF-16: sin email ni teléfono
                    closable: boolean }       // APPROVED y ya empezó (D19)
Eps               { id, code, name, active, planCount }
EpsPlan           { id, epsId, code, name, active, regime: { code, name } }
```

### Nuevos códigos de error

| Código | `code` | Cuándo |
|---|---|---|
| 409 | `APPOINTMENT_EXPIRED` | (existente) cancelar o reprogramar una cita que ya empezó; aprobar una reprogramación cuya franja propuesta ya pasó (D23) |
| 409 | `APPOINTMENT_NOT_STARTED` | cerrar como `COMPLETED`/`NO_SHOW` antes de la hora de inicio (D19) |
| 409 | `RESCHEDULE_PENDING` | pedir una reprogramación con otra `PENDING` sobre la misma cita (D20) |
| 409 | `EPS_REFERENCED`, `PLAN_REFERENCED` | borrar una EPS con planes, o un plan con afiliaciones (D28) |
| 422 | `SAME_SLOT` | la franja propuesta es la misma que la actual |

`INVALID_TRANSITION` (409) cubre cancelar una cita terminal, reprogramar una no `APPROVED`,
cerrar una no `APPROVED`, y decidir una solicitud que ya no está `PENDING`.

### Paciente — USER (HU-026, HU-027, HU-028)

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| POST | `/api/patient/appointments/{id}/cancel` | `{ reason? }` (≤ 500) | 200 `AppointmentDetail` `CANCELLED` · 404 · 409 `INVALID_TRANSITION` / `APPOINTMENT_EXPIRED` |
| POST | `/api/patient/appointments/{id}/reschedule` | `{ siteId, date, startTime, reason? }` | 201 `RescheduleRequest` `PENDING` · 404 · 409 `INVALID_TRANSITION` / `RESCHEDULE_PENDING` / `APPOINTMENT_EXPIRED` / `SLOT_TAKEN` · 422 `PAST_TIME` / `SAME_SLOT` / `SLOT_NOT_AVAILABLE` / `SITE_NOT_ASSIGNED` / `PROFESSIONAL_INACTIVE` / `SPECIALTY_INACTIVE` |

- **Cancelar** libera las reservas de la cita y escribe historial `USER` en la misma transacción.
  Si hay una reprogramación `PENDING`, pasa a `CANCELLED` y libera también su retención (D18).
- **Reprogramar** conserva profesional y especialidad: el cuerpo no los lleva. La duración sale de
  la especialidad y la regla de 60 min es la misma que la de la reserva (D9). La franja nueva queda
  retenida con `reservation_type = 'RESCHEDULE_REQUEST'`; la cita no cambia.
- Para elegir la franja nueva, el frontend usa la búsqueda existente
  `GET /api/patient/availability[/days]` con `specialtyId` y `professionalId` de la cita.
- "Conservar la cita" tras un rechazo **no** llama a la API (HU-028 T-03).

### Profesional — PROFESSIONAL (HU-020, HU-021)

| Método | Ruta | Cuerpo / query | Respuesta |
|---|---|---|---|
| GET | `/api/professional/appointments` | `from`, `to` obligatorios (máx. 62 días; día = `from`=`to`), `siteId` opcional | `ProfessionalAppointment[]` solo `APPROVED` y propias, por fecha y hora |
| POST | `/api/professional/appointments/{id}/complete` | — | 200 `ProfessionalAppointment` · 404 si no es suya · 409 `INVALID_TRANSITION` / `APPOINTMENT_NOT_STARTED` |
| POST | `/api/professional/appointments/{id}/no-show` | — | igual |

El cierre escribe historial con origen `PROFESSIONAL` y actor en la misma transacción. Las
reservas **no** se liberan (`COMPLETED` y `NO_SHOW` tienen `releases_slots = false`).

### Administración — ADMIN (HU-029, HU-031, HU-012)

| Método | Ruta | Cuerpo / query | Respuesta |
|---|---|---|---|
| GET | `/api/admin/inbox` | mismos filtros + `type` opcional (`APPOINTMENT_REQUEST`/`RESCHEDULE_REQUEST`) | `InboxEntry[]` |
| POST | `/api/admin/reschedules/{id}/approve` | — | 200 `AdminAppointment` con la franja nueva · 404 · 409 `INVALID_TRANSITION` / `APPOINTMENT_EXPIRED` / `CONCURRENT_CHANGE` |
| POST | `/api/admin/reschedules/{id}/reject` | `{ reason }` (1–500) | 200 `AdminAppointment` · 400 sin motivo · 409 `INVALID_TRANSITION` |
| GET | `/api/admin/summary` | — | + `pendingReschedules` |
| GET | `/api/admin/eps` | — | `Eps[]` activas e inactivas |
| POST | `/api/admin/eps` | `{ code, name }` | 201 `Eps` · 409 `DUPLICATE` (`field` = `code`/`name`) |
| PUT | `/api/admin/eps/{id}` | `{ name }` | 200 `Eps` |
| PATCH | `/api/admin/eps/{id}/status` | `{ active }` | 200 `Eps` |
| DELETE | `/api/admin/eps/{id}` | — | 204 · 409 `EPS_REFERENCED` |
| GET | `/api/admin/eps/{id}/plans` | — | `EpsPlan[]` |
| POST | `/api/admin/eps/{id}/plans` | `{ code, name, regimeCode }` | 201 `EpsPlan` · 409 `DUPLICATE` (código o nombre dentro de la EPS) · 400 régimen inexistente |
| PUT | `/api/admin/eps-plans/{id}` | `{ name, regimeCode }` | 200 `EpsPlan` |
| PATCH | `/api/admin/eps-plans/{id}/status` | `{ active }` | 200 `EpsPlan` |
| DELETE | `/api/admin/eps-plans/{id}` | — | 204 · 409 `PLAN_REFERENCED` |

```ts
InboxEntry = { type: 'APPOINTMENT_REQUEST', appointment: AdminAppointment }
           | { type: 'RESCHEDULE_REQUEST',  appointment: AdminAppointment, reschedule: RescheduleRequest }
```

- En una reprogramación, los filtros de fecha y sede se aplican a la **franja propuesta** (D24).
- **Aprobar** reprogramación: bajo bloqueo, borra las reservas `APPOINTMENT` antiguas y convierte
  las `RESCHEDULE_REQUEST` en `APPOINTMENT` de la cita **actualizando la fila** (no se borra y se
  reinserta, para no abrir hueco). Mueve fecha, hora y sede de la cita; la solicitud pasa a
  `APPROVED`. Historial `ADMIN` con estado `APPROVED` y motivo que nombra las dos franjas (D22).
- **Rechazar**: libera la retención y la cita queda intacta. Historial igual, con el motivo.
- Desactivar una EPS o un plan los retira de `GET /api/catalogs/insurance-plans` sin tocar las
  afiliaciones existentes (HU-012 CA-05).

### Aclaraciones del contrato S4 (2026-09-25, tras implementar la capa REST del frontend)

1. **Nulos:** como en S3, los campos nulos se **omiten** del JSON (`lastReschedule`,
   `requestReason`, `decisionReason`, `decidedAt`, `affiliation`). El cliente los tipa como
   opcionales.
2. **Cancelar:** el cuerpo es opcional. Sin cuerpo, `{}` o `{ "reason": "" }` son válidos y
   equivalen a no dar motivo.
3. **Motivo de reprogramación** (`reason` al pedirla): opcional, ≤ 500, igual que al cancelar.
4. **`EpsPlan.regime`** es `{ id, code, name }`, igual que en `InsurancePlan`. El alta y la edición
   siguen recibiendo `regimeCode`.
5. **`AdminAppointment`** no emite `cancellable` ni `reschedulable`, que son acciones del paciente.
   Sí emite `lastReschedule`.
6. **Agenda del profesional:** solo `APPROVED` (HU-020 CA-01). Una cita recién cerrada sale de la
   lista, y la UI lo confirma con un aviso.
7. **Tras pedir una reprogramación** la respuesta es la `RescheduleRequest`. La UI vuelve a pedir
   el detalle de la cita para refrescar `pendingReschedule` y `reschedulable`.
8. **`Appointment` (listado del paciente) también lleva `cancellable`**, para que el inicio ofrezca
   "Cancelar" solo cuando el servidor lo admite. Es aditivo.
9. **`GET /api/admin/eps/{id}`** → 200 `Eps` · 404. Lo usa el detalle `/admin/eps/:id`.
10. El paciente no tiene una ruta que liste las sedes de un profesional. La pantalla de
    reprogramación ofrece el catálogo de sedes, y la búsqueda filtrada por profesional solo devuelve
    franjas donde atiende. Es suficiente para S4.

### Implementado en F4 y F6 (2026-09-25) — precisiones y desviaciones

Backend de HU-020, HU-021 y HU-012 implementado contra este contrato, sin cambiar ninguna forma
acordada. Lo que el acuerdo no fijaba:

- **Numeración de migraciones (desviación de D31–D33):** la migración de EPS y afiliaciones es
  **`V9__eps_names_and_affiliation_history.sql`**, no V10. Flyway 11.7.2 corre con
  `outOfOrder = false` y `validate-on-migrate: true` sobre bases persistentes: una V9 aplicada
  después de una V10 quedaría "ignored" y el arranque fallaría. Por eso **la reprogramación (D31)
  debe usar `V10`**; el comentario `// (V9)` de `RescheduleRequest.previous` pasa a ser V10.
- **Agenda del profesional:** `from` o `to` ausentes → 400 `VALIDATION` ("Falta el parámetro
  obligatorio"); `to < from` o más de 62 días → 400 `VALIDATION` con `fieldErrors.to`. Un
  `professionalId` en la consulta se ignora: el titular sale del token (HU-020 CA-05). El
  `ProfessionalAppointment` lo proyecta el caso de uso (`ProfessionalPatientRef`, D35), no el
  controlador. Una cuenta PROFESSIONAL sin perfil profesional → 404.
- **Cierre:** la cita ajena y la inexistente responden igual (404 `NOT_FOUND`), antes de mirar el
  estado. Primero el estado (409 `INVALID_TRANSITION`) y después la hora (409
  `APPOINTMENT_NOT_STARTED`). La respuesta 200 es el `ProfessionalAppointment` ya cerrado, con
  `closable: false`.
- **EPS y planes:** el `code` se normaliza a mayúsculas y los espacios a `_` (igual que
  especialidades). Los nombres son únicos sin distinguir mayúsculas ni tildes (collation
  `utf8mb4_0900_ai_ci`, V9). `POST /api/admin/eps/{id}/plans` y `GET …/plans` sobre una EPS
  inexistente → **404 `NOT_FOUND`** (la EPS va en la ruta; HU-012 CA-02 habla de "error de
  validación": se resuelve como 404 y queda anotado para la verificación). Régimen ausente →
  400 `fieldErrors.regimeCode` (Bean Validation, sin `code`); régimen fuera del catálogo fijo →
  400 `VALIDATION` con `fieldErrors.regimeCode`. `PUT`/`PATCH`/`DELETE` sobre ids inexistentes →
  404. `EPS_REFERENCED` = la EPS tiene planes (con o sin afiliaciones); `PLAN_REFERENCED` = alguna
  afiliación, vigente o cerrada, apunta al plan. Desactivar una EPS **no** desactiva sus planes: los
  retira de la oferta porque el predicado "plan ofrecible" exige plan activo y EPS activa.
- Listados: `GET /api/admin/eps` por nombre; `GET …/plans` por nombre.

### Implementado en F5 (2026-09-25) — reprogramación: precisiones y desviaciones

Backend de HU-027, HU-031, la lectura de HU-028, la mitad de reprogramaciones de HU-029 y HU-022
CA-03, contra este contrato. Ninguna forma acordada cambia; lo que el acuerdo no fijaba:

- **Migración `V10__reschedule_previous_slot.sql`** (D31): `previous_date`, `previous_start_time`,
  `previous_end_time`, `previous_site_id` y `proposed_site_id` en `reschedule_requests`, obligatorias,
  con FK a `sites` y `CHECK previous_end_time > previous_start_time`. Las filas previas se rellenan
  con la franja actual de la cita y la sede del bloque retenido (o la de la cita).
- **Cuerpo de `POST …/{id}/reschedule` (aditivo):** además de `{ siteId, date, startTime, reason? }`
  acepta `professionalId` y `specialtyId` **opcionales** que el frontend no envía. Si llegan y difieren
  de los de la cita → **422 `WRONG_FLOW`** ("cambiar de profesional o especialidad es una cita nueva"),
  que es lo que exige HU-027 CA-02. Iguales o ausentes → se ignoran.
- **Orden de errores al pedir:** 400 `VALIDATION` (`fieldErrors.siteId/date/startTime`, `reason` > 500)
  → 404 → 409 `INVALID_TRANSITION` / `APPOINTMENT_EXPIRED` / `RESCHEDULE_PENDING` → 422 `WRONG_FLOW` →
  las reglas de franja de la reserva, por el **mismo** código (`SlotAllocator`: `SPECIALTY_INACTIVE`,
  `PROFESSIONAL_INACTIVE`, `SPECIALTY_NOT_ASSIGNED`, `SITE_NOT_ASSIGNED`, `PAST_TIME`,
  `SLOT_NOT_AVAILABLE`) → 422 `SAME_SLOT` → 409 `SLOT_TAKEN`.
- **Franja que se cruza con la actual** (p. ej. 60 min 08:00 → 08:30): **422 `SLOT_NOT_AVAILABLE`**
  con `detail` propio. En el libro único esa media hora ya es de la propia cita, así que no se puede
  retener; se rechaza antes de intentarlo en vez de devolver un `SLOT_TAKEN` engañoso.
- `SAME_SLOT` = mismo día, misma hora de inicio y misma sede.
- **60 min con el slot siguiente ocupado** → 409 `SLOT_TAKEN` (la PK), sin retener el primero; sin
  slot siguiente en el bloque → 422 `SLOT_NOT_AVAILABLE` (igual que la reserva, D9).
- **Pedir no escribe historial** de la cita: su estado no cambia. La respuesta 201 es la
  `RescheduleRequest` (aclaración 7).
- **Aprobar / rechazar:** el id de la ruta es el de la **solicitud**. Solicitud inexistente → 404
  `NOT_FOUND`. Orden: 404 → 409 `INVALID_TRANSITION` (solicitud no `PENDING` o cita no `APPROVED`,
  también al **rechazar**, HU-031 CA-05) → al aprobar, 409 `APPOINTMENT_EXPIRED` (D23) → al rechazar,
  400 `fieldErrors.reason`. `CONCURRENT_CHANGE` solo aparece como red de seguridad (interbloqueo).
- **Historial (D22):** aprobar escribe `APPROVED`/`ADMIN` con `reason` =
  `"Reprogramación aprobada: de 2026-10-01 08:00–09:00 (HIC) a 2026-10-02 09:00–09:30 (ICV)"`;
  rechazar escribe `APPROVED`/`ADMIN` con `reason` = el motivo enviado, tal cual.
- **`RescheduleRequest.decisionReason`:** `REJECTED` → el motivo; `APPROVED` → se omite (nulo);
  `CANCELLED` por D18 → `"Cita cancelada por el paciente"` (D37), con el paciente como decisor.
- **Bandeja:** `type` desconocido → 400 `VALIDATION` (`fieldErrors.type`). Orden estable por la franja
  de la entrada (la **propuesta** en una reprogramación), después tipo e id. En la entrada
  `RESCHEDULE_REQUEST`, `appointment` trae la franja **actual** y su `lastReschedule` es la propia
  solicitud; `history` va vacío en la bandeja, como en S3.
- **`summary.pendingReschedules`** = solicitudes sin decidir.
- **Búsqueda (HU-022 CA-03):** sin cambios de código: `JdbcAvailabilityQueries` ya excluía cualquier
  fila de `slot_reservations`; ahora hay productor y prueba.

## Relacionado

- [[contrato-rest-identidad]]
- [[dec-003-libro-unico-slot-reservations]]
- [[dec-004-decisiones-s3-reserva]]
- [[dec-005-sistema-visual-stitch]] — el frontend que consume estas rutas; las sedes que pinta salen de `/api/catalogs/sites`, no de los mockups
- [[riesgo-zona-horaria-columnas-time]] — por qué las horas de `Block`, `Slot` y `Offer` son fiables

## Historial

- 2026-09-25 — F5 (LOOP_02): reprogramación implementada. Añadida la subsección «Implementado en F5»
  con V10, el cuerpo opcional `professionalId`/`specialtyId` (422 `WRONG_FLOW`), el cruce con la
  franja actual (422 `SLOT_NOT_AVAILABLE`), el orden de errores, el texto del historial (D22), el
  `decisionReason` por estado (D37) y el `type` inválido de la bandeja (400).
- 2026-09-23 (LINT) — resuelta la contradicción con [[contrato-rest-identidad]]: esta página
  afirmaba que **todo** `/api/catalogs/**` exigía rol autenticado, cuando desde HU-009
  `insurance-plans` es público. Añadida la ruta al catálogo y la excepción a la regla de prefijo.
- 2026-09-18 — tras la verificación independiente: filtro `appointmentType` en disponibilidad, catálogo de estados de reprogramación, `CONCURRENT_CHANGE`, nombre de especialidad único, tipo no editable si está en uso, `actorName` enmascarado para el paciente. La bandeja ya no filtra por el tipo actual de la especialidad.
- 2026-09-18 — creado antes de implementar S3, como contrato común de backend y frontend.
