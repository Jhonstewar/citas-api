---
titulo: "Decisión 003 — Libro único de ocupación de slots"
tipo: decision
estado: Vigente
actualizado: 2026-09-16
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

## Relacionado

- [[datos-modelo-3fn]]

## Historial

- 2026-09-16 — decisión registrada; es la única desviación del análisis 3FN previo.
