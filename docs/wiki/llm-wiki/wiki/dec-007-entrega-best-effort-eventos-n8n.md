---
titulo: "Decisión 007 — Entrega best-effort de los eventos de cita hacia n8n"
tipo: decision
estado: Vigente
actualizado: 2026-10-04
fuentes: ["PLAN_S5_S6_N8N.md D-F (línea 115)", "PLAN_EJECUCION_S5_S6.md §10 riesgo 3", "citas-api/src/main/java/com/fcv/citas/infrastructure/automation/N8nWebhookPublisher.java", "citas-api/src/main/java/com/fcv/citas/application/appointment/AppointmentEventEmitter.java", "[[HU-035-publicar-eventos-de-cambio-de-estado-a-n8n]]", "[[contrato-rest-citas]]"]
tags: [decision, s5, s6, n8n, eventos, best-effort, d-f]
---

# Decisión 007 — Entrega best-effort de los eventos hacia n8n (D-F)

## Decisión

Cuando una cita cambia de estado, Spring publica el evento al webhook de WF-002 con una política **best-effort**:

- **Sin outbox.** No hay tabla ni planificador que garantice la entrega.
- **3 intentos** con esperas de 1 s y 2 s; solo se reintenta ante red/timeout/5xx (2xx y 4xx son definitivos).
  Verificado en `N8nWebhookPublisher.java:51,117-151`.
- **El evento se pierde** si n8n está caído más allá de esos reintentos, o si la cola acotada (100) se llena. Queda solo un WARN sin
  cuerpo. Es el **riesgo residual 3** de `PLAN_EJECUCION_S5_S6.md` §10.
- **El fallo del publicador nunca revierte ni cambia la respuesta** de la operación de negocio: se publica después del commit, fuera
  de la transacción, y `AppointmentEventEmitter` captura cualquier `RuntimeException`
  (`AppointmentEventEmitter.java:26-33`; prueba `aFailingPublisherDoesNotChangeTheOutcome`).
- **El contenido libre es no confiable.** `reason` (y nombres) viajan como texto plano, recortados a 300 caracteres; n8n debe tratarlos como
  dato, nunca como instrucción. La mitigación **no elimina** el riesgo (residual 1 del runbook).

Detalle del contrato en [[contrato-rest-citas]] § «S5–S6 — evento de cambio de estado».

## Aprobación

D-F fue **aprobada por el usuario el 2026-10-04** (respuesta directa en sesión). Estaba como propuesta en `PLAN_S5_S6_N8N.md`
(opción (a)); la (b) quedó como mejora documentada.

## Alternativas descartadas

| Alternativa | Por qué se descartó |
|---|---|
| **Outbox transaccional** (tabla + planificador) | Garantizaría entrega al menos una vez, pero exige esquema nuevo, migración, un planificador y manejo de duplicados; desproporcionado para un laboratorio con correos ficticios. Queda como mejora si el evento pasara a ser crítico. |
| **Publicar dentro de la transacción** | Un n8n lento o caído retendría conexiones y bloqueos de la reserva (`SELECT … FOR UPDATE`, ver [[dec-003-libro-unico-slot-reservations]]) y podría revertir una operación válida; además notificaría hechos que un rollback deshace. |
| **Cola externa** (broker de mensajes) | Añade infraestructura que el workspace no tiene y que la restricción de stack no pide; mismo coste que el outbox con más piezas móviles. |

## Consecuencias

- `eventId` (UUID v4) viaja como clave de idempotencia para que n8n pueda deduplicar si algún día se añade reenvío.
- Cualquier demo o evidencia debe declarar este riesgo residual (`EVIDENCIAS_S5.md`, `EVIDENCIAS_S6.md`).
- Si el evento deja de ser informativo y pasa a ser obligatorio, esta decisión se revisa y se pasa a outbox.

## Relacionado

- [[contrato-rest-citas]] — cuerpo exacto, tipos, reintentos y configuración
- [[sintesis-preguntas-abiertas]] — § S5–S6 (n8n): D-A…D-I y el entorno n8n verificado
- [[dec-006-decisiones-s4-ciclo-de-vida]] — las transiciones de estado que originan los eventos
- [[HU-035-publicar-eventos-de-cambio-de-estado-a-n8n]]
- [[EP-010-automatizaciones-n8n]]
