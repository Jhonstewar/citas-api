---
titulo: "Síntesis — Preguntas abiertas del dominio"
tipo: sintesis
estado: Vigente
actualizado: 2026-09-17
fuentes: ["citas-api/docs/wiki/scrum/historias-de-usuario/", "PRD.md", "[[MODELO-DATOS-3FN]]"]
tags: [sintesis, preguntas-abiertas, dominio]
---

# Síntesis — Preguntas abiertas del dominio

Huecos del PRD que salieron al especificar las 33 HU. Ninguno bloquea S2 (autenticación), pero
todos afectan a S3–S4. Están agrupados aquí para que no se redescubran una y otra vez; el detalle
de cada uno vive en la HU citada. **Resolver cada uno exige una decisión del usuario, no una
inferencia del agente.**

## Esquema (verificadas contra las migraciones)

| # | Pregunta | Afecta a |
|---|---|---|
| E1 | El historial de estados se borra en cascada con la cita, en contra de RN-12 | [[datos-modelo-3fn]], HU-032 |
| E2 | Falta el origen `PROFESSIONAL` en la auditoría, aunque el profesional cierra atenciones | [[datos-modelo-3fn]], HU-021 |

## Reglas de negocio sin definir

| # | Pregunta | Afecta a | Supuesto actual |
|---|---|---|---|
| N1 | Si el paciente cancela una cita con reprogramación `PENDING`, ¿qué pasa con la franja retenida? | HU-026, HU-027 | Ninguno: V4 tiene el estado `CANCELLED` para reprogramación |
| N2 | Los 2 slots de una cita de 60 min, ¿pueden venir de dos bloques seguidos? | HU-022, HU-023, HU-024 | Deben estar en el mismo bloque |
| N3 | La decisión sobre una reprogramación no cambia el estado de la cita: ¿se registra en el historial? | HU-031, HU-032 | Sin definir |
| N4 | "Conservar la cita" tras rechazar una reprogramación, ¿se persiste como acción? | HU-028 | Sin definir |
| N5 | Un profesional desactivado, ¿puede seguir iniciando sesión? | HU-016, HU-002 | Sin definir |
| N6 | En la bandeja, los filtros de fecha y sede de una reprogramación, ¿usan la franja actual o la propuesta? | HU-029 | Sin definir |

## Autenticación (afectan directamente a S2)

| # | Pregunta | Dónde | Supuesto de GOAL_01 |
|---|---|---|---|
| A1 | Política de complejidad de contraseña | EP-001, HU-001 | Servidor: solo máx. 72 bytes. **Cliente**: mín. 8 con letra y número (`authValidation.ts`). La API acepta lo que el formulario rechaza; se alinean al decidir INC-001 |
| A2 | ¿ADMIN y PROFESSIONAL usan el mismo login que USER? | EP-001, HU-002 | Sí, un único `/api/auth/login` |
| A3 | ¿Cómo se crea el primer ADMIN? | EP-001 | Sin definir: no hay registro de ADMIN |
| A4 | Vigencia de access, refresh y reset token | EP-001 | 15 min / 7 días (`.env.example`) |

## Seguridad y datos

| # | Pregunta | Dónde |
|---|---|---|
| S1 | ¿Se acepta que el access token no sea revocable durante sus 15 minutos? | [[dec-002-rotacion-refresh-tokens]] |
| S2 | Documento único global o por tipo de documento | [[datos-modelo-3fn]] |
| S3 | ¿Se precarga "Medicina General" (RF-11) con una migración de datos? | [[datos-modelo-3fn]] |
| S4 | El refresh token vive en memoria de JavaScript: un XSS en el mismo origen puede leerlo. ¿Se pasa a cookie `HttpOnly`? Cambiaría CSRF y CORS | [[dec-002-rotacion-refresh-tokens]] |

## Decisiones del agente bajo aprobación delegada, pendientes de confirmar

Se tomaron para poder cerrar S2 y están implementadas, pero **no las ha confirmado el usuario**.

| # | Decisión | Dónde |
|---|---|---|
| D1 | Contrato REST en Markdown a mano, sin springdoc (INC-038) | [[contrato-rest-identidad]] |
| D2 | Formato de error `ProblemDetail` RFC 9457 con extensión `fieldErrors` (INC-040) | [[contrato-rest-identidad]] |
| D3 | Rotación de refresh con revocación por familia ante reuso y en el logout | [[dec-002-rotacion-refresh-tokens]] |
| D4 | Locale fijo `es_CO`: la API ignora `Accept-Language` | [[contrato-rest-identidad]] |

## Relacionado

- [[datos-modelo-3fn]]
- [[dec-002-rotacion-refresh-tokens]]
- [[contrato-rest-identidad]]

## Historial

- 2026-09-17 — A1 precisada con la divergencia cliente/servidor; añadida S4 (refresh token en memoria JS) y la tabla D1–D4 de decisiones tomadas bajo aprobación delegada.
- 2026-09-16 — añadidas A1–A4 del informe de especificación (40 incógnitas `INC-NNN` registradas en las épicas).
- 2026-09-16 — creada al terminar la especificación Scrum (33 HU). E1 y E2 verificadas contra `V3__schedule_and_appointments.sql`.
