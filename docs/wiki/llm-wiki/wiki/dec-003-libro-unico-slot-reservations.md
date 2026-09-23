---
titulo: "Decisión 003 — Libro único de ocupación de slots"
tipo: decision
estado: Vigente
actualizado: 2026-09-23
fuentes: ["[[MODELO-DATOS-3FN]] §3", "citas-api/src/main/resources/db/migration/V3__schedule_and_appointments.sql"]
tags: [decision, datos, doble-reserva]
---

# Decisión 003 — `slot_reservations` como libro único de ocupación

## Decisión

Una sola tabla `slot_reservations` cuya **clave primaria es `slot_id`** registra toda ocupación
de un slot, sea de una cita o de la retención provisional de una reprogramación `PENDING`.
Un `CHECK` exige que cada fila tenga exactamente un titular.

## Alternativa descartada

Dos tablas, `appointment_slots` y `reschedule_request_slots`, como proponía
`database/ANALISIS_NORMALIZACION_3FN.md`. Con dos tablas **ninguna restricción UNIQUE** puede
impedir que la misma franja quede tomada en ambas a la vez, y la no-doble-reserva dependería del
código.

## Por qué no es desnormalización

Es la fusión de dos relaciones con la misma clave (`slot_id`); no introduce redundancia ni
dependencias transitivas.

## Consecuencias

- Dos transacciones concurrentes sobre el mismo slot → error de clave duplicada. **El motor impide
  la doble reserva**, no un `if`.
- Liberar slots (cancelar, rechazar, aprobar reprogramación) = borrar filas.
- `slot_order` (1 o 2) ordena los slots de una cita de 60 min; que sean **consecutivos** lo valida el dominio.

## Verificación

Probado con inserciones reales: segunda cita sobre el mismo slot y retención de reprogramación
sobre un slot ya ocupado fallan ambas con `Duplicate entry for key 'slot_reservations.PRIMARY'`.

### Trampa de JPA: la garantía depende de hacer INSERT

La PK solo protege si la reserva se **inserta**. `slot_id` es un id asignado; con eso Spring
Data `save()` hace `merge`, que busca la fila y, si existe, la **actualiza**. Resultado: la
segunda reserva sobrescribe la ajena y responde 201. Por eso `SlotReservationJpaEntity`
implementa `Persistable` con `isNew() = true`
(`citas-api/src/main/java/com/fcv/citas/infrastructure/persistence/appointment/SlotReservationJpaEntity.java`).

Verificado por mutación en S3 (`EVIDENCIAS_S3.md` §7): con `isNew() = false`,
`BookingIntegrationTest.doubleBookingIsRejectedWith409` falla (201 en vez de 409). La prueba
concurrente de 8 hilos **no** lo detecta, porque bajo carrera `merge` termina en un INSERT
duplicado. Hacen falta las dos pruebas.

## El código del 409 depende del bloqueo pesimista, no solo de la PK (2026-09-23)

**HECHO verificado con 24 perdedores en tres ejecuciones seguidas.** Hay **dos** caminos que
producen un 409 ante una doble reserva:

| Camino | De dónde sale | `code` |
|---|---|---|
| Esperado | `catch` de `JpaAppointmentRepositoryAdapter.reserveSlots` sobre el `saveAndFlush` | `SLOT_TAKEN` |
| Red de seguridad | `GlobalExceptionHandler`, para una violación de integridad que escape al commit | `CONCURRENT_CHANGE` |

Bajo concurrencia real sale **siempre `SLOT_TAKEN`**, y eso no es casualidad de la PK: el caso de
uso serializa a los contendientes con `SELECT … FOR UPDATE` sobre la fila del profesional
(`BookAppointmentUseCase`, antes de resolver bloques y slots), así que el `INSERT` duplicado falla
**dentro** de la transacción, donde el adaptador puede traducirlo. Sin ese bloqueo el esquema
seguiría impidiendo la doble reserva —la PK no se puede saltar— pero parte de los 409 pasarían a
salir como `CONCURRENT_CHANGE`, y el contrato publicado se volvería inestable.

**Por eso la prueba concurrente afirma ahora el `code` y no solo el status HTTP:** si alguien
quitara `lockProfessional` para "optimizar", las pruebas lo detectarían en vez de dejarlo pasar.
Antes solo comprobaba el 409 y habría aceptado el cambio en silencio.

**HECHO:** existe además la carrera **cruzada** general ↔ especializada que exige la DoD de
HU-024, con un profesional de dos especialidades de 30 min compitiendo por el mismo slot. La
prueba ramifica según el ganador real leído de la base, porque en tres ejecuciones seguidas ganó
el general dos veces y el especializado una: escrita con un ganador fijo habría sido intermitente.

Evidencia completa en `EVIDENCIAS_S3.md` §11, incluida la comprobación contra la API real con dos
clientes HTTP distintos.

## Pregunta abierta

`appointments` **no** tiene restricción que obligue a una cita a tener filas en
`slot_reservations`. Que toda cita ocupe su franja lo garantiza hoy el código, porque existe un
único camino de creación; la base no lo impediría si apareciera otro. Ver [[datos-modelo-3fn]].

## Relacionado

- [[datos-modelo-3fn]]
- [[contrato-rest-citas]]
- [[dec-004-decisiones-s3-reserva]] — D9: los dos slots de 60 min salen del **mismo** bloque

## Historial

- 2026-09-23 — el `code` del 409 depende también del `SELECT … FOR UPDATE` sobre el profesional,
  no solo de la PK; la prueba concurrente pasa a afirmar `code`. Suite del backend en **242**
  pruebas (`EVIDENCIAS_S3.md` §11).
- 2026-09-18 — S3: trampa de `merge` con id asignado verificada por mutación; la reserva se hace con `persist` (`Persistable.isNew`).

- 2026-09-16 — decisión registrada; es la única desviación del análisis 3FN previo.
