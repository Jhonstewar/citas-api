---
titulo: "Decisión 003 — Libro único de ocupación de slots"
tipo: decision
estado: Vigente
actualizado: 2026-09-18
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

## Relacionado

- [[datos-modelo-3fn]]
- [[contrato-rest-citas]]

## Historial

- 2026-09-18 — S3: trampa de `merge` con id asignado verificada por mutación; la reserva se hace con `persist` (`Persistable.isNew`).

- 2026-09-16 — decisión registrada; es la única desviación del análisis 3FN previo.
