---
titulo: "Contrato REST — Catálogos, profesionales, agenda y citas (S3)"
tipo: contrato
estado: Vigente
actualizado: 2026-10-04
fuentes: ["PRD.md §4", "HU-005, HU-010, HU-011, HU-013..HU-019, HU-022..HU-025, HU-029, HU-030, HU-032, HU-034 (S5, contrastado el 2026-10-04)", "[[dec-004-decisiones-s3-reserva]]", "[[dec-006-decisiones-s4-ciclo-de-vida]] D18, D22, D37, D38, D39", "código de citas contrastado el 2026-09-30 (LOOP_02 iter. 3)"]
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
  (`SecurityConfig.java:81` la declara `permitAll` **antes** del `authenticated()` de
  `/api/catalogs/**` de la línea 92). Cualquier otra ruta no declarada → denegada.
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
                  actorName: string | null, reason: string | null, changedAt,
                  event?: 'RESCHEDULED' }   // aditivo en S4 (D39); ver abajo cuándo viaja
AppointmentDetail Appointment & { history: HistoryEntry[] }
AdminAppointment  Appointment & { history, patient: PatientRef, lastReschedule? }  // sin cancellable/reschedulable (F9)
```

`status` ∈ `REQUESTED | APPROVED | REJECTED | CANCELLED | COMPLETED | NO_SHOW`.

**`HistoryEntry.event`** (aditivo, S4 · D39). Aparece **solo** en la fila cuyo `status` repite el de
la fila anterior, y esa fila solo puede ser la de una **reprogramación aprobada**: mueve fecha, hora
y sede sin mover el estado (`APPROVED` → `APPROVED`), así que sin el campo la línea de tiempo diría
"Aprobada" dos veces. En **cualquier otra fila no viaja en el JSON**, porque es nula y los nulos se
omiten (`default-property-inclusion: non_null`). Lo emiten `GET /api/patient/appointments/{id}`
(`AppointmentDetail.history[]`) y `GET /api/admin/appointments/{id}` (`AdminAppointment.history[]`),
ambos desde el mismo cuerpo (`infrastructure/rest/appointment/AppointmentResponses.java:62-63`). El
backend lo **deriva** al leer, sin columna ni migración nueva: la regla vive en el dominio
(`domain/appointment/HistoryEvent.java:48`, `between` = `previous != null && previous == current`) y
la aplica la capa de aplicación sobre el historial ya ordenado
(`application/appointment/HistoryEntry.java:28`). El orden es parte de la regla. Prueba que lo fija:
`RescheduleDecisionIntegrationTest:551` — con dos aprobaciones seguidas, `history[0].event` no existe
y `history[1].event` = `history[2].event` = `"RESCHEDULED"`, y el detalle del ADMIN deriva lo mismo
sobre la misma cita.

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
En el detalle del **paciente**, `actorName` de las entradas `ADMIN` es "Administración" (no el nombre del empleado); el ADMIN ve el nombre real. El historial no tiene rutas de escritura. Desde S4 la bandeja incluye también `type: 'RESCHEDULE_REQUEST'` (ver § S4).

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
HistoryEntry      + { event?: 'RESCHEDULED' }                 // aditivo (D39); declarado arriba
ProfessionalAppointment { id, status, statusName, date, startTime, endTime, durationMinutes,
                    site: SiteRef, specialty: SpecialtyRef,
                    patient: { fullName, documentType, documentNumber },  // mínimo de RF-16: sin email ni teléfono
                    closable: boolean,        // APPROVED y ya empezó (D19)
                    pendingReschedule: boolean }  // añadido el 2026-09-30: el paciente tiene una solicitud PENDING
Eps               { id, code, name, active, planCount }
EpsPlan           { id, epsId, code, name, active, regime: { code, name } }
```

`HistoryEntry.event` es el único tipo ampliado en S4 que se declara **fuera** de este bloque: vive
con los demás campos de `HistoryEntry` en § "Tipos compartidos", junto al párrafo que fija cuándo
viaja y en qué dos endpoints; aquí solo se lista para que los **cuatro** tipos que S4 amplía
(`Appointment`, `AppointmentDetail`, `AdminAppointment`, `HistoryEntry`) se vean juntos.

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
| POST | `/api/professional/appointments/{id}/complete` | — | 200 `ProfessionalAppointment` · 404 si no es suya · 409 `INVALID_TRANSITION` / `APPOINTMENT_NOT_STARTED` — **cancela la reprogramación `PENDING`** (D38) |
| POST | `/api/professional/appointments/{id}/no-show` | — | igual, con `NO_SHOW` |

El cierre escribe historial con origen `PROFESSIONAL` y actor en la misma transacción. La franja de
la **propia cita no se libera**: `COMPLETED` y `NO_SHOW` no liberan slots
(`releasesSlots()` solo es `true` para `REJECTED` y `CANCELLED`,
`domain/appointment/AppointmentStatus.java:44-46`; `appointment_statuses.releases_slots` de V4 debe
coincidir, y `S3DebtIntegrationTest:205` lo comprueba estado por estado).

**D38 — cerrar cancela la reprogramación pendiente.** Si la cita tiene una solicitud `PENDING`, los
dos endpoints de cierre la pasan a `CANCELLED` **en la misma transacción**, con el **profesional**
como decisor y `decisionReason = "Cita cerrada por el profesional"`, y **liberan su retención** por
el único camino de liberación (`ReservationHolder.ofRescheduleRequest`, que no toca la franja de la
cita). Así la franja propuesta vuelve a ofrecerse en `GET /api/patient/availability`. Sin esto la
retención quedaría sin salida, porque HU-031 CA-05 solo deja decidir sobre una cita `APPROVED`
(`application/appointment/ProfessionalAppointmentsUseCase.java:112-115`,
`domain/appointment/RescheduleRequest.java:181-183`).

> **Corregido el 2026-09-30.** Aquí se decía que el efecto «**no** se ve en la respuesta del cierre
> —`ProfessionalAppointment` no lleva la solicitud—». Eso describía una **asimetría que era un
> defecto**, no una decisión: el payload del paciente y el del ADMIN sí emitían `pendingReschedule`,
> así que al paciente se le avisaba antes de cancelar (D18) y al profesional no, aunque su cierre
> tuviera la misma consecuencia. `ProfessionalAppointment` **ya emite `pendingReschedule`**, y la
> pantalla del profesional advierte en la confirmación del cierre que cancelará la solicitud y
> liberará la franja propuesta. Lo destapó la verificación independiente del frontend.

El efecto se observa también en el detalle de la cita del paciente: `pendingReschedule` pasa a
`false` y `lastReschedule.status` a `CANCELLED`. Por D39 el cierre añade
**una sola** fila de historial, la del propio cierre. Pruebas:
`ProfessionalAgendaIntegrationTest:520` (`complete`), `:550` (`no-show`) y `:569` (si liberar la
retención falla, todo vuelve atrás: cita `APPROVED`, solicitud `PENDING`, retención intacta).

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
  `APPROVED`. Historial `ADMIN` con estado `APPROVED` y motivo que nombra las dos franjas (D22), que
  al leerse viaja con `event: 'RESCHEDULED'` (D39).
- **Rechazar**: libera la retención y la cita queda intacta. **No escribe ninguna fila de
  historial** (D39, que refina D22): el motivo vive solo en `lastReschedule.decisionReason`. Lo
  acordado el 2026-09-25 decía aquí "Historial igual, con el motivo"; **se corrigió el 2026-09-30**
  contra el código — evidencia en § "Implementado en F5".
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
  `closable: false`. **Añadido en el LOOP_02 (D38):** si la cita tiene una reprogramación `PENDING`,
  el cierre la cancela y libera su retención en la misma transacción (decisor = el profesional,
  motivo `"Cita cerrada por el profesional"`), sin liberar la franja de la propia cita; el detalle y
  la evidencia están en § "Profesional — PROFESSIONAL". La solicitud se bloquea **después** de la
  cita, el mismo orden único de bloqueo que usan cancelar y decidir.
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
- **Cuerpo obligatorio frente a cuerpo opcional — asimetría entre los cuatro endpoints con cuerpo de
  S4** (verificado el 2026-09-30, anotación del LOOP_02 iter. 3). `POST …/{id}/reschedule` es el
  **único** que **exige** cuerpo: su parámetro es `@RequestBody RescheduleBody`, sin
  `required = false` (`infrastructure/rest/appointment/PatientAppointmentController.java:91`). Los
  otros tres lo declaran `@RequestBody(required = false)`: `…/{id}/cancel`
  (`PatientAppointmentController.java:78`), `POST /api/admin/appointments/{id}/reject` y
  `POST /api/admin/reschedules/{id}/reject` (`AdminAppointmentController.java:82` y `:98`).
  Consecuencias observables, distintas en cada uno:
  - **reprogramar sin cuerpo** (o con JSON mal formado) → **400 antes de aplicar ninguna regla**, ni
    ownership ni estado: lo corta el conversor de mensajes. `title` = "Datos inválidos", `detail` =
    "El cuerpo de la petición falta o no es un JSON válido", y **sin `code` ni `fieldErrors`**, así
    que el frontend no puede distinguirlo por `code`
    (`infrastructure/rest/error/GlobalExceptionHandler.java:70-75`, que redefine
    `handleHttpMessageNotReadable` para no devolver el ProblemDetail genérico de Spring en inglés);
  - **cancelar sin cuerpo** → **200**: equivale a no dar motivo (aclaración 2);
  - **cualquiera de los dos `reject` sin cuerpo** → **400 con `fieldErrors.reason`**, el mismo error
    que `{ "reason": "" }`, porque el motivo es obligatorio y se valida en el dominio
    (`domain/appointment/Appointment.java:179-181` y `domain/appointment/RescheduleRequest.java:158-160`);
    en el rechazo de una reprogramación el estado se comprueba **antes**, así que una solicitud ya
    decidida da 409 y no 400.

  Es una asimetría real entre endpoints hermanos, no un detalle de forma: el cliente que envía `{}` o
  ningún cuerpo a `reschedule` recibe un 400 sin `code`. Si alguna vez conviene igualarlos, el cambio
  es de una línea, pero **hoy el contrato es este**.
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
- **Historial (D22, refinado por D39):** **solo aprobar** escribe fila, porque es la única decisión
  que cambia la cita: `APPROVED`/`ADMIN` con `reason` =
  `"Reprogramación aprobada: de 2026-10-01 08:00–09:00 (HIC) a 2026-10-02 09:00–09:30 (ICV)"`
  (`domain/appointment/RescheduleRequest.java:141-144`) y `event: 'RESCHEDULED'` al leerla.
  **Rechazar no añade ninguna fila a `history[]`** —ni una entrada `ADMIN` ni de ningún otro origen—:
  la cita no se toca, así que el motivo del rechazo viaja **solo** en
  `lastReschedule.decisionReason`, que es lo que pinta el aviso de HU-028
  (`application/appointment/RescheduleAppointmentUseCase.java:140-149`,
  `domain/appointment/RescheduleRequest.java:155-162`). Las cancelaciones automáticas de D18 y D38
  tampoco escriben fila propia. Prueba: `RescheduleDecisionIntegrationTest:508`
  (`onlyTheApprovalIsRecordedInTheHistory`), que además comprueba que el paciente lee el rechazo en
  `lastReschedule.decisionReason` y que `history[*].event` va vacío en esa cita.
  > **CORRECCIÓN del 2026-09-30 (LOOP_02, iteración 3).** Hasta hoy esta línea decía «rechazar
  > escribe `APPROVED`/`ADMIN` con `reason` = el motivo enviado, tal cual». Era cierto cuando se
  > escribió (F5, 2026-09-25) y dejó de serlo al aplicarse **D39** en la iteración 2 del LOOP_02, sin
  > que la página se actualizara: el contrato publicado afirmaba lo contrario del código. **D39
  > supersede a D22** en este punto. El `backend-verifier` lo marcó como gravedad **ALTA** porque el
  > frontend ya consume `event` y `decisionReason` y se quedaba sin contrato de referencia.
- **`RescheduleRequest.decisionReason`:** `REJECTED` → el motivo enviado; `APPROVED` → se omite
  (nulo); `CANCELLED` → el motivo automático de quien la cerró, y el decisor es ese mismo actor:
  - `"Cita cancelada por el paciente"` cuando el paciente cancela la cita (D18/D37), decisor = el
    paciente;
  - `"Cita cerrada por el profesional"` cuando el profesional cierra la atención como `COMPLETED` o
    `NO_SHOW` (**D38**), decisor = el profesional.

  Las dos cadenas son constantes del dominio (`domain/appointment/RescheduleRequest.java:40` y `:43`)
  y ninguna otra las produce.
- **Bandeja:** `type` desconocido → 400 `VALIDATION` (`fieldErrors.type`). Orden estable por la franja
  de la entrada (la **propuesta** en una reprogramación), después tipo e id. En la entrada
  `RESCHEDULE_REQUEST`, `appointment` trae la franja **actual** y su `lastReschedule` es la propia
  solicitud; `history` va vacío en la bandeja, como en S3.
- **`summary.pendingReschedules`** = solicitudes sin decidir.
- **Búsqueda (HU-022 CA-03):** sin cambios de código: `JdbcAvailabilityQueries` ya excluía cualquier
  fila de `slot_reservations`; ahora hay productor y prueba.

## Verificación contra el código — corte S4 (F9, 2026-09-30)

Contraste de esta página con los controladores reales (`infrastructure/rest/appointment/*`,
`admin/AdminEpsController`). **Ninguna ruta, método, cuerpo ni código HTTP de § S4 diverge del
código.** Índice verificado de los endpoints de S4 de este repositorio (todos tienen prueba de
integración en `src/test/.../infrastructure/rest/`):

| Método y ruta | Rol | Cuerpo | Éxito | Controlador |
|---|---|---|---|---|
| `POST /api/patient/appointments/{id}/cancel` | USER | `{ reason? }`, opcional | 200 `AppointmentDetail` | `PatientAppointmentController` |
| `POST /api/patient/appointments/{id}/reschedule` | USER | `{ siteId, date, startTime, reason?, professionalId?, specialtyId? }`, **obligatorio** | 201 `RescheduleRequest` | ídem |
| `GET /api/professional/appointments?from&to&siteId?` | PROFESSIONAL | — | 200 `ProfessionalAppointment[]` | `ProfessionalAppointmentController` |
| `POST /api/professional/appointments/{id}/complete` · `/no-show` | PROFESSIONAL | — | 200 `ProfessionalAppointment` | ídem |
| `GET /api/admin/inbox?type&siteId&professionalId&specialtyId&date` | ADMIN | — | 200 `InboxEntry[]` | `AdminAppointmentController` |
| `POST /api/admin/reschedules/{id}/approve` | ADMIN | — | 200 `AdminAppointment` | ídem |
| `POST /api/admin/reschedules/{id}/reject` | ADMIN | `{ reason }`: opcional en HTTP, obligatorio en el dominio | 200 `AdminAppointment` | ídem |
| `GET /api/admin/summary` | ADMIN | — | 200 `{ pendingRequests, activeProfessionals, activeSpecialties, appointmentsToday, pendingReschedules }` | ídem |
| `GET/POST /api/admin/eps`, `GET/PUT/DELETE /api/admin/eps/{id}`, `PATCH /api/admin/eps/{id}/status` | ADMIN | `{ code, name }` / `{ name }` / `{ active }` | 200 · 201 · 204 | `AdminEpsController` |
| `GET/POST /api/admin/eps/{id}/plans`, `PUT/DELETE /api/admin/eps-plans/{id}`, `PATCH /api/admin/eps-plans/{id}/status` | ADMIN | `{ code, name, regimeCode }` / `{ name, regimeCode }` / `{ active }` | 200 · 201 · 204 | ídem |

Validación de los cuerpos de EPS (Bean Validation, `400` con `fieldErrors` y **sin** `code`):
`code` obligatorio ≤ 20 (EPS) o ≤ 30 (plan), `name` obligatorio ≤ 160, `regimeCode` obligatorio
("Seleccione el régimen"); `PATCH …/status` exige `active`.

### Ejemplos

`POST /api/patient/appointments/57/reschedule` con
`{ "siteId": 2, "date": "2026-10-05", "startTime": "09:00" }` → **201**:

```json
{ "id": 9, "appointmentId": 57, "status": "PENDING", "statusName": "Pendiente",
  "previous": { "date": "2026-10-01", "startTime": "08:00", "endTime": "08:30", "site": { "id": 1, "code": "HIC", "name": "…" } },
  "proposed": { "date": "2026-10-05", "startTime": "09:00", "endTime": "09:30", "site": { "id": 2, "code": "ICV", "name": "…" } },
  "createdAt": "2026-09-30T10:15:00" }
```

Error de transición (409, `code` estable; `title` y `detail` en español):

```json
{ "type": "about:blank", "title": "Conflicto", "status": 409,
  "detail": "Esta cita ya tiene una reprogramación pendiente de decisión",
  "instance": "/api/patient/appointments/57/reschedule", "code": "RESCHEDULE_PENDING" }
```

Cierre antes de hora (`POST /api/professional/appointments/57/complete`) → 409
`APPOINTMENT_NOT_STARTED`, `detail`: "La cita aún no ha empezado: se puede cerrar desde su hora de
inicio". `DELETE /api/admin/eps/3` con planes → 409 `EPS_REFERENCED`, `detail`: "La EPS tiene planes
registrados: desactívela en lugar de borrarla".

### Códigos `code` verificados en el código (S4)

`INVALID_TRANSITION`, `APPOINTMENT_EXPIRED`, `APPOINTMENT_NOT_STARTED`, `RESCHEDULE_PENDING`,
`SLOT_TAKEN` (409; lo lanza el adaptador de persistencia al chocar con la PK del libro de slots),
`CONCURRENT_CHANGE` (409; red de seguridad del `GlobalExceptionHandler`), `EPS_REFERENCED`,
`PLAN_REFERENCED`, `DUPLICATE`, `SAME_SLOT`, `WRONG_FLOW`, `SLOT_NOT_AVAILABLE`, `PAST_TIME`,
`SITE_NOT_ASSIGNED`, `PROFESSIONAL_INACTIVE`, `SPECIALTY_INACTIVE`, `SPECIALTY_NOT_ASSIGNED`,
`NOT_FOUND` (404), `VALIDATION` (400). Estado HTTP por tipo de excepción:
`InvalidRequestException` → 400, `NotFoundException` → 404, `ConflictException` → 409,
`BusinessRuleException` → 422.

### Errores de framework (F9): ahora en español

Antes de F9, los errores que genera Spring fuera de los manejadores propios salían con `title` y
`detail` en inglés. Ahora `GlobalExceptionHandler.handleExceptionInternal` los reescribe: mismo
código HTTP, mismas cabeceras (`Allow`, `Accept`), sin citar el valor recibido y **sin `code`**.

| Situación | Estado | `title` | `detail` |
|---|---|---|---|
| Ruta inexistente con token válido | 404 | No encontrado | El recurso solicitado no existe |
| Método no soportado con token válido | 405 | Método no permitido | El método HTTP no está permitido para este recurso |
| `Content-Type` no soportado | 415 | Tipo de contenido no soportado | El tipo de contenido de la petición no es compatible; use application/json |
| Cabecera o cookie obligatoria ausente | 400 | Datos inválidos | Falta la cabecera (o cookie) obligatoria «…» |
| Parámetro de consulta ausente o de tipo inválido | 400 | Datos inválidos | Falta el parámetro obligatorio «…» / El parámetro «…» tiene un formato inválido (con `code: VALIDATION`) |

Sin token, una ruta o método inexistente responde el 401 o 403 de la cadena de seguridad, no 404 ni
405 (`denyAll` para lo no declarado). La cabecera `WWW-Authenticate` del 401 también va ya en
español (ver [[contrato-rest-identidad]]). Prueba: `FrameworkErrorsSpanishIntegrationTest`.

### Divergencias y puntos abiertos hallados en F9

1. **HU-009 CA-03: cerrada, no abierta.** El criterio pedía 409 y el contrato responde 200
   idempotente (`PUT /api/me/affiliation` con el plan vigente). El usuario reescribió CA-03 el
   2026-09-30 para exigir el 200, así que hoy contrato y criterio coinciden. Ver
   [[contrato-rest-identidad]].
2. **Corregido en esta página:** `AdminAppointment` se declaraba como `AppointmentDetail & patient`,
   pero el código (`AdminAppointmentResponse`) no emite `cancellable` ni `reschedulable`; la bandeja
   decía que "incluirá" `RESCHEDULE_REQUEST` cuando ya lo incluye; las referencias a `SecurityConfig`
   estaban desplazadas (`:81` y `:92`).
3. **Abierta, menor:** `reason` de `reject` (cita y reprogramación) y de `cancel` no lleva Bean
   Validation: el 400 por motivo vacío o > 500 sale del dominio con `code: VALIDATION` y
   `fieldErrors.reason`, mientras que los DTO con Bean Validation (EPS, planes, `PUT /api/me`) dan
   `fieldErrors` **sin** `code`. El cliente debe decidir por `status` y `fieldErrors`, no por `code`,
   en los 400.
4. **Abierta, menor:** el 400 por cuerpo ausente o ilegible no lleva `code` (asimetría del cuerpo de
   § F5).

## S5 — automatización: citas próximas para n8n (HU-034, verificado el 2026-10-04)

Contrato de lectura para las automatizaciones de recordatorio (n8n). No es una ruta de persona: vive
en una **cadena de seguridad propia** y no usa JWT. Verificado contra el código, no contra el plan.

| Método | Ruta | Query | Respuesta |
|---|---|---|---|
| GET | `/api/automation/appointments/upcoming` | `hours` opcional, entero **1–72**, defecto **24** | 200 `UpcomingAppointment[]` · 400 `VALIDATION` · 401 · 405 (otro método con clave válida) |

Fuentes: `infrastructure/rest/automation/AutomationController.java:31-34` (ruta, `DEFAULT_HOURS = 24`),
`application/automation/AutomationQueriesUseCase.java:18-36` (rango y ventana).

### Autenticación y cadena de seguridad

- **Cabecera `X-Automation-Key`** (`AutomationApiKeyFilter.java:36`). Es la única credencial aceptada.
- **Solo `GET`, y el orden importa** (`AutomationApiKeyFilter.java:52-62`; corregido tras la iteración 1 de
  HU-034, CA-07):
  1. Clave ausente o inválida → **401 con cualquier método** (también POST/PUT/PATCH/DELETE), sin 405.
  2. Clave válida con POST/PUT/PATCH/DELETE → **405** con cabecera `Allow: GET` y `ProblemDetail` en español
     ("Método no permitido" / "El método HTTP no está permitido para este recurso",
     `ProblemJsonSecurityHandlers.methodNotAllowed`, líneas 58-62). No llega al controlador: no ejecuta nada.
  - Pruebas: `AutomationUpcomingIntegrationTest.writeMethodsWithValidKeyReturn405AndNeverExecute` (exige 405,
    `Allow: GET`, `status` 405 y `detail`; la cita sigue `APPROVED` y no cambia el número de citas) y
    `wrongOrMissingKeyWithAnyMethodReturns401NotMethodNotAllowed` (clave errónea o ausente, 401 con los cuatro métodos).
  - **Supersede** al texto previo de esta sección, que decía que los métodos no GET recibían 401 antes de mirar
    la clave; ese comportamiento era el de la versión inicial del filtro, ya no vigente.
- **Cadena aparte** (`AutomationSecurityConfig.java:25-47`): `@Order(1)`, `securityMatcher("/api/automation/**")`,
  stateless, sin CORS (cliente servidor a servidor), sin `oauth2ResourceServer`. La cadena principal
  ([[arq-hexagonal-seguridad]], `SecurityConfig`) conserva su `denyAll` para todo lo demás.
- **Un JWT de persona no sustituye la clave:** con `Authorization: Bearer` de ADMIN, PROFESSIONAL o
  USER y sin clave → 401; con JWT y clave errónea → 401 (`personJwtWithoutKeyReturns401ForEveryRole`,
  `personJwtWithWrongKeyReturns401`). A la inversa, la clave válida **no abre** rutas de persona
  (`/api/admin/...`, `/api/professional/...`, `/api/patient/...`, `/api/me` → 401/403,
  `validKeyDoesNotOpenPersonRoutes`). La única autoridad aceptada es `ROLE_AUTOMATION`, que concede solo el filtro.
- **Clave de configuración vacía = cadena cerrada: todo 401**, también con cabecera vacía, con
  cualquier valor y con un JWT de persona (`AutomationApiKeyFilter.java:28,67-69`;
  `AutomationClosedChainIntegrationTest`, contexto con `app.automation.api-key=`). La API arranca igual.
  La clave se lee de la propiedad `app.automation.api-key` (`application.yml:91-92`, variable de entorno
  con valor por defecto vacío). **El valor no se documenta ni se versiona.**

### DECISIÓN: comparación de la clave

Se compara el **SHA-256 de ambos valores** con `MessageDigest.isEqual` (tiempo constante)
(`AutomationApiKeyFilter.java:71,74-79`). El digest esperado se calcula una vez al construir el filtro.
- **Motivo:** `isEqual` sobre los bytes crudos devolvería antes si las longitudes difieren y filtraría
  la longitud de la clave; con digests de 32 bytes fijos, ni el contenido ni la longitud se filtran por tiempo.
- **Descartado:** `String.equals` (corta en el primer byte distinto) y `isEqual` directo sobre la clave.
- Cabecera ausente o vacía → rechazo inmediato sin comparar. El filtro **nunca registra** la clave ni la cabecera.

### Ventana y selección

- Ventana **(ahora, ahora + `hours`]**: inicio **exclusivo**, fin **inclusivo**, con "ahora" en hora de
  `America/Bogota` (`SystemZone.now(clock)`). Se compara el instante de **inicio** de la cita:
  `TIMESTAMP(scheduled_date, start_time) > :from AND <= :to`
  (`infrastructure/persistence/appointment/JdbcAppointmentQueries.java:155-156`).
  Una cita que empieza exactamente ahora queda fuera; una que empieza en ahora+`hours`, dentro
  (`exactlyNowPlusHoursIsIncludedAndExactlyNowIsExcluded`, con reloj fijo).
- **Solo `APPROVED`** (`st.code = 'APPROVED'`, línea 154). `REQUESTED`, `REJECTED`, `CANCELLED`,
  `COMPLETED` y `NO_SHOW` no salen (`onlyApprovedStatusIsReturned`).
- **Orden:** fecha, hora de inicio e id ascendentes (línea 157; `resultsAreOrderedByDateAndStartTimeAscending`).
- `hours` ausente → 24. Presente pero vacío o no numérico → mismo 400 que fuera de rango
  (`AutomationController.java:37-46`); el espacio alrededor se recorta.

### Respuesta 200

`UpcomingAppointment[]` (`UpcomingAppointmentResponse.java:10-14`). Claves **exactas**, sin ninguna más
(`responseHasExactlyTheContractKeysAndNoDocumentPhoneOrHistory`):

```ts
UpcomingAppointment { appointmentId: number, patientFirstName, patientEmail, date, startTime, endTime,
                      site: { code, name, address }, professional: string, specialty: string }
```

`date` es `YYYY-MM-DD`; `startTime`/`endTime` son `HH:mm`. `professional` es nombres y apellidos
concatenados; `specialty`, el nombre de la especialidad. Minimización de datos: el correo viaja porque sin
él no hay recordatorio; **no hay documento, teléfono ni historial**, ni identificadores de paciente o
profesional. Sin citas en la ventana → `[]`.

```json
[
  {
    "appointmentId": 57,
    "patientFirstName": "Ana",
    "patientEmail": "ana@ejemplo.test",
    "date": "2026-10-05",
    "startTime": "09:00",
    "endTime": "09:30",
    "site": { "code": "HIC", "name": "…", "address": "…" },
    "professional": "Nombre Apellido",
    "specialty": "Medicina General"
  }
]
```

(Datos sintéticos: las claves son las del código, los valores son ilustrativos.)

### Errores

Los dos errores de datos son `application/problem+json` en español, con `type: "about:blank"`; el 405 (clave
válida, método no GET) usa el mismo formato y se describe en § Autenticación.

**401 — `commenceApiKey`** (`ProblemJsonSecurityHandlers.java:52-55`). Cuerpo fijo, idéntico para cabecera
ausente, clave errónea (con cualquier método), de otra longitud, vacía o cadena cerrada (la prueba
`wrongKeyAndDifferentLengthKeyAndEmptyKeyReturnTheSame401BodyAsNoKey` exige igualdad con el caso sin clave
y que no repita lo enviado). Solo lleva `type`, `title`, `status`, `detail`, `instance` (`write`, líneas
77-89). **No lleva `WWW-Authenticate`**: no hay token que presentar, a diferencia del 401 de personas
([[contrato-rest-identidad]]).

```json
{ "type": "about:blank", "title": "No autenticado", "status": 401,
  "detail": "Se requiere una credencial de automatización válida",
  "instance": "/api/automation/appointments/upcoming" }
```

**400 — `hours` inválido** (`0`, `73`, `-1`, `abc`, vacío): `InvalidRequestException.field("hours", …)` →
`code: VALIDATION` y `fieldErrors.hours` = "Las horas deben ser un entero entre 1 y 72"
(`AutomationController.java:44`, `AutomationQueriesUseCase.java:31-32`; `hoursOutOfRangeOrNotNumericReturns400Validation`).
Los límites 1 y 72 dan 200 (`hoursAtTheLimitsReturns200`). El 400 solo se ve **con clave válida**: sin clave
manda el 401, porque la autenticación va antes que el controlador.

### Pruebas

- `AutomationUpcomingIntegrationTest` (CA-01 a CA-07; reloj fijo 2032-06-15 10:00 Bogotá para probar los bordes).
- `AutomationClosedChainIntegrationTest` (clave vacía = todo 401).
- `AutomationKeyNotLoggedIntegrationTest.neitherConfiguredNorSentKeyAppearsInLogs`: ni la clave configurada ni la
  enviada aparecen en los logs.
- `AutomationKeyStartupIntegrationTest`: arranque real que **falla** con clave débil (menos de 32 bytes,
  `AutomationProperties.MIN_KEY_BYTES`) o con `CHANGE_ME` aunque supere los 32 bytes, sin imprimir el valor;
  clave vacía arranca (cadena cerrada).
- Suite completa **565/565 el 2026-10-04**.

## S5–S6 — evento de cambio de estado (HU-035, verificado el 2026-10-04)

**No es un endpoint de la API.** Es una **publicación saliente**: Spring hace `POST` a la URL de
`N8N_WEBHOOK_WF002_URL` (WF-002 de n8n) cuando una cita cambia de estado. Aquí se documenta como contrato
porque n8n depende de su forma exacta. La decisión de entrega está en [[dec-007-entrega-best-effort-eventos-n8n]].

### Petición saliente

- Origen: `N8nWebhookPublisher` (`citas-api/src/main/java/com/fcv/citas/infrastructure/automation/N8nWebhookPublisher.java:154`).
- Cabeceras: `Content-Type: application/json` y `X-Webhook-Secret: <N8N_WEBHOOK_SECRET>` (`:53`, `:154`). El valor del secreto nunca
  se documenta ni se registra en log.
- Destino: la URL de `app.n8n.wf002-url`; si no está vacía debe empezar por `https://` o el arranque falla
  (`N8nProperties.java:27-40`).
- Tiempos: conexión 2 s, lectura 5 s (`N8nWebhookPublisher.java:46-47`).

### Cuerpo JSON exacto (`toPayload`, `N8nWebhookPublisher.java:171-198`)

```json
{
  "eventId": "<UUID v4>",
  "eventType": "APPOINTMENT_REJECTED",
  "occurredAt": "2026-10-01T10:15:00-05:00",
  "appointmentId": 57,
  "patient": { "fullName": "Ana Perez", "email": "<correo del paciente>" },
  "appointment": {
    "date": "2026-10-05",
    "startTime": "09:00",
    "endTime": "09:30",
    "site": { "code": "ICV", "name": "Clinica Ficticia Valle" },
    "professional": "Dr. Carlos Ruiz",
    "specialty": "Cardiologia"
  },
  "reason": "Sin cupo"
}
```

(Ejemplo con datos ficticios del test.) Reglas, todas verificadas:

- Claves de primer nivel: `eventId`, `eventType`, `occurredAt`, `appointmentId`, `patient`, `appointment`, `reason`; `patient` lleva solo
  `fullName` y `email`; `appointment` lleva `date`, `startTime`, `endTime`, `site{code,name}`, `professional` (nombre completo, texto) y
  `specialty` (nombre, texto). `N8nWebhookPublisherTest.theBodyHasExactlyTheKeysOfTheContract` (`:239`) comprueba el conjunto exacto de claves.
- **Sin documento ni teléfono** del paciente: el test afirma que el cuerpo no contiene ninguno de los dos (`:266`).
- Formatos: `date` ISO `yyyy-MM-dd` (`LocalDate.toString()`); `startTime`/`endTime` `HH:mm`; `occurredAt` ISO con desfase, truncado a
  segundos (`ISO_OFFSET_DATE_TIME`, `:57`, `:200-202`), tomado del reloj inyectado (`AppointmentEvent.java:35`).
- `eventId` es un UUID v4 nuevo por evento (`AppointmentEvent.java:35`), pensado como clave de idempotencia para el destino. No hay
  garantía de entrega única: ver reintentos.
- `reason`: **siempre presente**; `null` si no hay motivo (`aMissingReasonIsSentAsNull`, `:270`). Un motivo en blanco se normaliza a `null`
  (`AppointmentEvent.java:34`). Se **recorta a 300 caracteres** (`MAX_REASON_LENGTH`, `:52`, `:204-213`) sin partir un par sustituto.
  Es texto libre del usuario: **contenido no confiable** (ver [[dec-007-entrega-best-effort-eventos-n8n]]).

### Tipos de evento (`AppointmentEventType`) y cuándo se emite

| `eventType` | Emitido por | `reason` |
|---|---|---|
| `APPOINTMENT_APPROVED` | `AdminAppointmentsUseCase.approve` (`:114`) | `null` |
| `APPOINTMENT_REJECTED` | `AdminAppointmentsUseCase.reject` (`:129`) | motivo del rechazo |
| `APPOINTMENT_CANCELLED` | `CancelAppointmentUseCase` (`:67`) | motivo de cancelación, si lo hay |
| `RESCHEDULE_APPROVED` | `RescheduleAppointmentUseCase` (`:136`) | `null` |
| `RESCHEDULE_REJECTED` | `RescheduleAppointmentUseCase` (`:155`) | motivo del rechazo |

**No se emite** (cubierto por `AppointmentEventPublishingIntegrationTest`): la reserva general auto-aprobada
(`autoApprovedGeneralBookingEmitsNothing`), completar y no-show (`completingAndNoShowEmitNothing`), solicitar una reprogramación
(`requestingAReschedulingEmitsNothing`), y cualquier operación que falle: transición inválida 409 o rechazo sin motivo 400
(`invalidTransitionEmitsNothing`, `rejectionWithoutReasonEmitsNothing`).

### Cuándo y cómo se publica

- **Después del commit y fuera de la transacción:** el caso de uso cierra `tx.inTransaction(...)`, relee el detalle y solo entonces llama a
  `events.emit(...)` (p. ej. `AdminAppointmentsUseCase.java:107-115`). El publicador observa el estado ya confirmado
  (`thePublisherObservesTheAlreadyCommittedState`).
- **Un fallo nunca cambia el resultado:** `AppointmentEventEmitter` captura cualquier `RuntimeException` y registra solo su clase
  (`AppointmentEventEmitter.java:26-33`); `aFailingPublisherDoesNotChangeTheOutcome` comprueba que aprobar, rechazar y cancelar
  siguen respondiendo igual.
- **Asíncrono, executor propio y acotado:** 2 hilos daemon `n8n-webhook-N`, cola de 100 y `AbortPolicy` (`:55-56`, `:217-225`); si la
  cola está llena el evento se descarta con un WARN y no se lanza nada (`:109-112`). No se declara como bean `Executor` para no
  desactivar el `applicationTaskExecutor` de Spring Boot (`AppointmentEventPublisherConfig.java:17-19`).
- **Reintentos:** hasta **3 intentos**, esperas de **1 s y 2 s** entre ellos (`MAX_ATTEMPTS`, `:51`; la espera de 4 s de
  `PRODUCTION_WAITS` solo valdría para un cuarto intento que no existe). Se reintenta solo ante error de red/timeout o **5xx**;
  **2xx y 4xx son definitivos** (`:117-151`).
- **Configuración** (`AppointmentEventPublisherConfig.java:25-32`, `N8nProperties.java`):
  - `N8N_WEBHOOK_WF002_URL` vacía: bean `NoOpAppointmentEventPublisher`, no se envía nada.
  - `N8N_WEBHOOK_SECRET` vacío con URL activa: no se envía y se registra un WARN (`:95-98`).
  - `N8N_WEBHOOK_SECRET` que contiene `CHANGE_ME` (sin distinguir mayúsculas): **falla el arranque** sin imprimir el valor
    (`N8nProperties.java:23-26`).
- **Logs:** solo tipo de evento, `eventId`, código HTTP o clase de la excepción. **Nunca** cuerpo, correo, `reason`, secreto ni URL
  (puede llevar un token en la ruta) (`:39-40`; `theLogNeverContainsEmailReasonSecretBodyOrUrl`). `N8nProperties.toString()` enmascara
  el secreto y las URL.

### Pruebas que respaldan esta sección

- `N8nWebhookPublisherTest` — cuerpo exacto, `reason` nulo, cabeceras, reintentos (502,502,200 = 3 intentos; 400 sin reintento; 2xx sin
  reintento; 5xx, red y timeout abandonan tras 3), asincronía, cola saturada, valores de producción, sin secreto no envía, y el log
  sin datos sensibles.
- `AppointmentEventPublisherSelectionTest` — URL vacía o propiedad ausente = NoOp y no se envía nada; URL configurada = publicador HTTP.
- `AppointmentEventPublishingIntegrationTest` — los cinco tipos, un `eventId` distinto por evento, los casos que no emiten, estado ya
  confirmado y fallo del publicador sin efecto.
- `AutomationAndN8nPropertiesTest` — secreto con `CHANGE_ME` falla el arranque sin imprimirse, vacío/ordinario arranca, `toString`
  enmascarado y URL no vacía sin `https://` falla el arranque.

**PREGUNTA ABIERTA:** no se ha ejecutado aún el flujo real WF-002 contra este publicador (los flujos de n8n están inactivos; ver
[[sintesis-preguntas-abiertas]] § "S5–S6 (n8n)"). Lo anterior es contrato verificado en el lado de Spring, no extremo a extremo.

## Relacionado

- [[dec-007-entrega-best-effort-eventos-n8n]] — por qué la entrega del evento es best-effort y qué se pierde
- [[arq-hexagonal-seguridad]] — la cadena principal frente a la de automatización
- [[contrato-rest-identidad]]
- [[dec-003-libro-unico-slot-reservations]]
- [[dec-004-decisiones-s3-reserva]]
- [[dec-005-sistema-visual-stitch]] — el frontend que consume estas rutas; las sedes que pinta salen de `/api/catalogs/sites`, no de los mockups
- [[riesgo-zona-horaria-columnas-time]] — por qué las horas de `Block`, `Slot` y `Offer` son fiables

## Historial

- 2026-10-04 — S6 F5: añadida § «S5–S6 — evento de cambio de estado» (publicación saliente hacia n8n, no un endpoint): cuerpo JSON exacto, cinco tipos, cuándo no se emite, post-commit, reintentos, configuración y logs; contrastada con `N8nWebhookPublisher` y sus cuatro clases de prueba. Decisión en [[dec-007-entrega-best-effort-eventos-n8n]].
- 2026-10-04 — S5 F3: CA-07 corregido (clave inválida/ausente → 401 con cualquier método; clave válida con POST/PUT/PATCH/DELETE → 405 con `Allow: GET`; supersede al "no GET = 401"). Documentadas las pruebas de clave fuera de logs y de arranque con clave débil/`CHANGE_ME`; suite 565/565.
- 2026-10-04 — S5 F2: añadida § «S5 — automatización» con `GET /api/automation/appointments/upcoming` (clave `X-Automation-Key`, ventana, respuesta exacta, 401 sin `WWW-Authenticate`, 400 `hours`, decisión SHA-256 + `isEqual`), contrastada con controlador, filtro, config y pruebas.
- 2026-09-30 — F9 (S4): verificado el corte S4 contra los controladores reales (índice, ejemplos, códigos), documentados los errores de framework ya en español y las divergencias halladas; corregidos `AdminAppointment`, la bandeja y las referencias a `SecurityConfig`.
- 2026-09-30 — LOOP_02 iteración 3: **alineado con el código lo que el `backend-verifier` marcó como
  gravedad ALTA** (el contrato afirmaba lo contrario del código y el frontend consumía un campo sin
  contrato). Cuatro correcciones, todas verificadas contra los ficheros y las pruebas que se citan:
  (1) rechazar una reprogramación **no** escribe historial —se corrigen las **dos** frases que decían
  lo contrario, la del acuerdo y la de «Implementado en F5»— y el motivo viaja solo en
  `lastReschedule.decisionReason` (D39 supersede a D22); (2) `HistoryEntry` declara
  `event?: 'RESCHEDULED'` con la regla exacta de cuándo viaja y en qué dos endpoints; (3)
  `decisionReason` incorpora `"Cita cerrada por el profesional"` (D38) junto al de D18/D37; (4)
  `/complete` y `/no-show` documentan que cancelan la reprogramación `PENDING` y liberan su retención
  en la misma transacción, sin liberar la franja de la propia cita. Las pruebas que se citan no están
  solo leídas: la suite del backend corrió **entera y en verde** ese mismo día, una vez por el Builder
  y otra por el `backend-verifier`. Dos remates del mismo día: `HistoryEntry + { event?: … }` se lista
  también entre los tipos ampliados de S4 (la regla sigue en § "Tipos compartidos") y se documenta la
  **asimetría del cuerpo** de los cuatro endpoints con cuerpo — solo `reschedule` lo exige, y sin él
  responde 400 sin `code`.
- 2026-09-25 — F5 (LOOP_02): reprogramación implementada. Añadida la subsección «Implementado en F5»
  con V10, el cuerpo opcional `professionalId`/`specialtyId` (422 `WRONG_FLOW`), el cruce con la
  franja actual (422 `SLOT_NOT_AVAILABLE`), el orden de errores, el texto del historial (D22), el
  `decisionReason` por estado (D37) y el `type` inválido de la bandeja (400).
- 2026-09-23 (LINT) — resuelta la contradicción con [[contrato-rest-identidad]]: esta página
  afirmaba que **todo** `/api/catalogs/**` exigía rol autenticado, cuando desde HU-009
  `insurance-plans` es público. Añadida la ruta al catálogo y la excepción a la regla de prefijo.
- 2026-09-18 — tras la verificación independiente: filtro `appointmentType` en disponibilidad, catálogo de estados de reprogramación, `CONCURRENT_CHANGE`, nombre de especialidad único, tipo no editable si está en uso, `actorName` enmascarado para el paciente. La bandeja ya no filtra por el tipo actual de la especialidad.
- 2026-09-18 — creado antes de implementar S3, como contrato común de backend y frontend.
