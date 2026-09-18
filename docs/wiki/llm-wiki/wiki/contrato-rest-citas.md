---
titulo: "Contrato REST — Catálogos, profesionales, agenda y citas (S3)"
tipo: contrato
estado: Vigente
actualizado: 2026-09-18
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
  autenticado. Cualquier otra ruta no declarada → denegada.
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

No hay escritura sobre catálogos fijos: `POST/PUT/DELETE` → 405.

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

## Relacionado

- [[contrato-rest-identidad]]
- [[dec-003-libro-unico-slot-reservations]]
- [[dec-004-decisiones-s3-reserva]]

## Historial

- 2026-09-18 — tras la verificación independiente: filtro `appointmentType` en disponibilidad, catálogo de estados de reprogramación, `CONCURRENT_CHANGE`, nombre de especialidad único, tipo no editable si está en uso, `actorName` enmascarado para el paciente. La bandeja ya no filtra por el tipo actual de la especialidad.
- 2026-09-18 — creado antes de implementar S3, como contrato común de backend y frontend.
