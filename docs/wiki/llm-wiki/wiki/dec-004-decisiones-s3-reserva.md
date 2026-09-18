---
titulo: "Decisión 004 — Decisiones de S3 para reservar citas (aprobación delegada)"
tipo: decision
estado: Provisional
actualizado: 2026-09-18
fuentes: ["PRD.md §4-§5", "PLAN_RETOMA_S3.md", "[[sintesis-preguntas-abiertas]]", "[[datos-modelo-3fn]]"]
tags: [decision, s3, reserva, aprobacion-delegada]
---

# Decisión 004 — Decisiones de S3 para reservar citas

`Provisional` porque el agente las tomó bajo **aprobación delegada** (el 2026-09-18 el usuario
pidió continuar S3 "a criterio del agente"). Cada una responde a una pregunta abierta de
[[sintesis-preguntas-abiertas]] o a una incógnita `INC-NNN` de las épicas. Pasan a `Vigente`
cuando el usuario las confirme; si rechaza alguna, se anota aquí y se abre la HU afectada.

| # | Pregunta | Decisión | Alternativa descartada | Motivo |
|---|---|---|---|---|
| D5 | A3 · ¿Cómo nace el primer ADMIN? | Al arrancar, si no hay ningún ADMIN y existen `ADMIN_BOOTSTRAP_EMAIL` y `ADMIN_BOOTSTRAP_PASSWORD`, se crea uno | Semilla Flyway con hash fijo | Un hash en una migración es una credencial versionada; el entorno es el único lugar de secretos (PRD §8) |
| D6 | INC-013 · Contraseña inicial del profesional | La fija el ADMIN en el alta | Enlace de activación por correo | RF-03 (recuperación) queda para S4; el correo real es opcional (PRD §9) |
| D7 | S3 / INC-009 · ¿Se precarga Medicina General? | Sí, por migración (`MEDICINA_GENERAL`, GENERAL, 30 min), protegida contra desactivación | Crearla a mano por el CRUD | RF-11 depende de ella; sin semilla la cita general no existe en un entorno limpio |
| D8 | E1/E2 · Defectos de auditoría | V5: historial sin `ON DELETE CASCADE` y origen `PROFESSIONAL` | Dejarlo para S4 | RN-12 exige que la auditoría no se borre; S3 ya crea historial |
| D9 | N2 · ¿60 min puede cruzar dos bloques? | No: dos slots consecutivos del **mismo** bloque | Unir bloques contiguos | Dos bloques pueden estar en sedes distintas; el bloque es la unidad de publicación |
| D10 | INC-024 · ¿Caduca la retención de una `REQUESTED`? | No caduca en S3 | Caducidad por tiempo | Requiere un proceso programado; se reevalúa en S5 con n8n |
| D11 | INC-014 / N5 · Profesional desactivado | Puede iniciar sesión; no se ofrece ni admite reservas nuevas; sus citas existentes se conservan | Bloquear el login | Desactivar no debe borrar agenda ni impedir consultarla |
| D12 | INC-032 · Aprobar una `REQUESTED` ya vencida | 409; el ADMIN debe rechazarla con motivo | Aprobarla igual | RN-06: no hay citas en el pasado |
| D14 | ¿Todo el acceso a datos por Spring Data JPA? (`RESTRICCIONES_TECNICAS.md`) | **Escrituras** de agregados por JPA; **lecturas** con varias tablas (listados de citas, bandeja, catálogos fijos, disponibilidad) por SQL con `JdbcTemplate` en adaptadores de infraestructura | Todo por JPA con proyecciones | Las vistas cruzan hasta 8 tablas; con JPA serían N+1 o JPQL igual de largo. **Desviación de la restricción, pendiente de que el usuario la acepte** |
| D13 | Hooks de S3 | Git hooks versionados en `.githooks/` de cada repo (`core.hooksPath`) | Hooks de Claude Code | La red debe decir "no" a cualquier commit, lo haga un agente o una persona |

## Consecuencias

- `.env.example` y `docker-compose.yml` ganan `ADMIN_BOOTSTRAP_EMAIL` y `ADMIN_BOOTSTRAP_PASSWORD` (vacíos).
- La búsqueda de disponibilidad ([[dec-003-libro-unico-slot-reservations]]) solo empareja slots del mismo bloque.
- El profesional desactivado desaparece de la búsqueda, pero HU-020 (S4) seguirá mostrándole su agenda.

## Relacionado

- [[sintesis-preguntas-abiertas]]
- [[dec-003-libro-unico-slot-reservations]]
- [[datos-modelo-3fn]]

## Historial

- 2026-09-18 — creada al planificar S3 (`PLAN_RETOMA_S3.md`).
