---
id: EP-010
tipo: epica
titulo: "Automatizaciones n8n"
estado: Aprobada
requisitos: ["PRD §10", RF-19, RF-20]
historias:
  - "[[HU-034-consultar-citas-para-automatizacion]]"
  - "[[HU-035-publicar-eventos-de-cambio-de-estado-a-n8n]]"
  - "[[HU-036-versionar-y-documentar-los-flujos-n8n]]"
dependencias:
  - "[[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]"
  - "[[EP-008-operacion-administrativa-de-solicitudes]]"
  - "[[EP-009-trazabilidad-y-contrato-rest]]"
  - "[[EP-001-identidad-y-acceso-seguro]]"
---

# EP-010 — Automatizaciones n8n

## Objetivo

Añadir, sin cambiar el núcleo funcional (PRD §10), tres automatizaciones sobre n8n que usan Gmail como canal de salida:

- **WF-001 — recordatorios de citas próximas** (S5, obligatorio): `Schedule → API de citas APPROVED próximas → Gmail → registro`.
- **WF-002 — notificación de cambio de estado** (S6, obligatorio): `Webhook desde Spring → n8n → Gmail → registro`.
- **WF-003 — resumen operativo diario por sede y estado** (S6, **bonus**): `Schedule → API del día → agrupar → Gmail`.

La épica cubre los tres puntos de contacto que hoy no existen en `citas-api`: una forma de lectura legítima para n8n (sin JWT de persona), un puerto de eventos de cambio de estado y el versionado documentado de los flujos exportados.

## Valor esperado

El paciente recibe aviso de lo que le ocurre a sus citas (recordatorio y cambios de estado) y operaciones recibe un resumen, sin que n8n gane poder de ADMIN ni que un fallo de n8n rompa una operación de negocio. Cierra la salida que ocho HU ya entregadas dejaron explícitamente fuera de alcance («pertenece a la automatización posterior de PRD §10»).

## Actores

- ADMIN — responsable de configurar, validar y operar las automatizaciones.
- USER — destinatario de los correos de recordatorio y de cambio de estado.
- Cliente de servicio `automation` (n8n) — no es una persona ni un rol de negocio: es el llamante de `/api/automation/**` mediante una clave dedicada de solo lectura (D-A). No se añade un cuarto rol al modelo de actores `USER`, `PROFESSIONAL`, `ADMIN`.

## Alcance

- Segunda cadena de seguridad propia para `/api/automation/**`, con la cabecera `X-Automation-Key`, solo `GET`, stateless, que no usa cuentas de persona (D-A).
- `GET /api/automation/appointments/upcoming?hours=` (WF-001) y, como criterio opcional/bonus, `GET /api/automation/appointments/daily?date=` (WF-003).
- Puerto de eventos `AppointmentEventPublisher` en `application`, adaptador HTTP en `infrastructure` y publicación **después** de confirmar la transacción, para aprobación/rechazo de cita especializada, aprobación/rechazo de reprogramación y cancelación (D-E).
- Variables de entorno `N8N_WEBHOOK_WF001_URL`, `N8N_WEBHOOK_WF002_URL`, `N8N_WEBHOOK_WF003_URL`, `N8N_WEBHOOK_SECRET` y `AUTOMATION_API_KEY`, sin valores reales en el repositorio.
- Exportación de `WF-001-appointment-reminders.json`, `WF-002-status-notifications.json` y, opcional, `WF-003-daily-operational-summary.json` a `citas-api/automations/n8n/` con su `README.md`.
- Evidencia de invocación MCP (listar, inspeccionar, ejecutar, leer ejecución), validación por ejecución controlada antes de publicar, registro/trazabilidad en una Data Table y riesgos residuales por escrito.
- Actualización del contrato REST ([[HU-033-publicar-contrato-rest-documentado]]) con los endpoints de automatización y el evento saliente.

## Fuera de alcance

- SMTP propio, SMS y WhatsApp (PRD §9): el único canal es Gmail vía n8n.
- Cualquier cambio en `citas-web`.
- Cambios al modelo de datos: sin migraciones Flyway, sin columna `reminder_sent_at`, sin tabla *outbox* (D-D, D-F).
- Entrega garantizada de eventos (*outbox* + planificador): queda como mejora documentada, no como requisito.
- Notificar la cita general auto-aprobada, la solicitud de reprogramación y la creación de solicitudes (D-E).
- Reutilizar, editar o archivar los tres borradores ajenos existentes en la instancia n8n (D-H).
- Despliegue permanente de la API: el túnel es temporal (D-B).
- Modificar las HU ya entregadas (HU-026, HU-027, HU-028, HU-030, HU-031, HU-032): esta épica las complementa sin reescribirlas.

## Reglas de negocio

- PRD §10: las automatizaciones se agregan «sin cambiar el núcleo funcional». Una automatización nunca cambia el resultado de una operación de negocio.
- PRD §8: secretos únicamente por variables de entorno; evitar logging de passwords y tokens. La clave de automatización y el secreto del webhook se tratan como secretos.
- PRD §9: datos sintéticos; prohibidos los datos reales de FCV. Los pacientes del laboratorio usan correos `@ejemplo.test`.
- RF-19 / RN-12: la auditoría de estados no se sustituye ni se edita; los flujos la complementan.
- RN-11: los eventos parten de transiciones explícitas y verificables del dominio; no se emiten desde el controlador ni el frontend.

### Decisiones tomadas por el usuario (2026-09-30)

- **D-A** — n8n se autentica con una **clave de API dedicada, de solo lectura**, en `/api/automation/**`, por la cabecera `X-Automation-Key` y con **cadena de seguridad propia**. No se usa una cuenta ADMIN.
- **D-B** — La API llega a n8n por un **túnel temporal** para la demostración; se cierra al terminar.
- **D-H** — Los workflows se **crean de cero** en n8n con el prefijo `jhonNuñez-` (workflows, Data Table y credenciales). Los **paths de webhook no llevan ñ**. Los borradores existentes `6Ks5HWdXadUSBW7o`, `Cu7kjdPURjE8LnTp` y `OJqkZkMKhoJJTcXc` no se tocan.

### Propuestas vigentes, pendientes de confirmar por el usuario

> Actualización 2026-10-04: **D-C, D-D y D-I quedaron aprobadas** con «aprobdo lo del s5» (alcance S5, interpretado por el agente principal). D-E, D-F y D-G siguen pendientes hasta confirmación explícita antes de la Fase 5.

Se tratan como reglas de trabajo mientras no se confirmen; cualquiera puede cambiar y obligaría a revisar la HU afectada (registradas también como preguntas abiertas).

- **D-C** — Ventana de recordatorio: citas `APPROVED` que empiezan en las próximas **24 h**, revisadas **cada hora**. El PRD no la define.
- **D-D** — Anti-duplicado del recordatorio en una **Data Table de n8n** (sin migración). Riesgo residual: si se pierde la tabla, se reenvían recordatorios.
- **D-E** — Eventos de WF-002: aprobación y rechazo de cita especializada, aprobación y rechazo de reprogramación, y cancelación. La cita general auto-aprobada queda fuera.
- **D-F** — Entrega **best-effort**: publicación tras el commit, con timeouts y reintentos, sin *outbox*.
- **D-G** — WF-003 es **bonus**: entra al final y solo si WF-001 y WF-002 están cerrados.
- **D-I** — WF-001 y WF-003 llevan, además del Schedule, un **webhook de disparo a demanda**, para la ejecución controlada que exige S6.

## Dependencias

- [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]] — cancelación y reprogramación que generan eventos ([[HU-026-cancelar-una-cita-futura]]).
- [[EP-008-operacion-administrativa-de-solicitudes]] — decisiones administrativas que generan eventos ([[HU-030-aprobar-o-rechazar-cita-especializada]], [[HU-031-aprobar-o-rechazar-reprogramacion]]).
- [[EP-009-trazabilidad-y-contrato-rest]] — [[HU-032-auditar-cambios-de-estado-de-cita]] y [[HU-033-publicar-contrato-rest-documentado]].
- [[EP-001-identidad-y-acceso-seguro]] — [[HU-005-autorizar-peticiones-por-rol-y-ownership]]: la nueva cadena de seguridad convive con la de personas.
- **Precondición de apertura:** esta épica depende de que S4 esté cerrada o de que el usuario autorice expresamente abrir S5. Hoy varias de las HU de las que depende (HU-026, HU-027, HU-028, HU-031, HU-033) siguen en `En validación` o `En desarrollo` y la fase F10 de S4 no está cerrada.
- Acciones manuales del usuario fuera del repositorio: credencial Gmail OAuth2 propia (scope `gmail.send`), proyecto Google Cloud, túnel y credenciales Header Auth en n8n. Ningún agente las completa ni las recibe por chat.

## Historias de usuario

- [[HU-034-consultar-citas-para-automatizacion]]
- [[HU-035-publicar-eventos-de-cambio-de-estado-a-n8n]]
- [[HU-036-versionar-y-documentar-los-flujos-n8n]]

## Criterio de completitud de la épica

- [ ] Todas las HU obligatorias de esta épica están `Completada`.
- [ ] `/api/automation/**` rechaza con 401 toda petición sin la clave correcta y ninguna cuenta de persona la sustituye.
- [ ] Cada transición de D-E emite exactamente un evento, y ninguno si la transacción falla.
- [ ] Un fallo de n8n no cambia el resultado de ninguna operación de negocio.
- [ ] WF-001 y WF-002 están exportados en `citas-api/automations/n8n/` sin credenciales ni correos reales, y validados por ejecución controlada antes de publicarse.
- [ ] Hay evidencia de invocación MCP y riesgos residuales por escrito.
- [ ] El contrato REST documenta los endpoints de automatización y el evento saliente.
- [ ] WF-003 es opcional: su ausencia no impide completar la épica, pero debe declararse explícitamente como fuera de entrega si no se hace.
- [ ] No quedan dependencias bloqueantes dentro del alcance de la épica.

## Riesgos e incógnitas

- **INC-041** — El PRD §10 no define la ventana ni la frecuencia del recordatorio (D-C, pendiente de confirmar).
- **INC-042** — Política ante un evento no entregado (D-F): el PRD no la define; la propuesta es *best-effort* sin *outbox*.
- **INC-043** — Si la cita general auto-aprobada, la solicitud de reprogramación o la creación de solicitudes deben notificar (D-E).
- **INC-044** — Qué hace WF-003 con un día sin citas ni pendientes: enviar «sin actividad» o no enviar.
- **INC-045** — Conservación del anti-duplicado (D-D): dónde vive y qué ocurre si la Data Table se pierde.
- **INC-046** — Dueño y ciclo de rotación de `AUTOMATION_API_KEY` y `N8N_WEBHOOK_SECRET`; la rotación se limita a cambiar la variable.
- **Riesgos residuales** (a documentar en [[HU-036-versionar-y-documentar-los-flujos-n8n]]): texto libre (`reason`, nombres) que llega al correo del paciente; clave de larga vida en una instancia compartida; eventos perdidos si n8n cae más allá de los reintentos; duplicados si se borra la Data Table; superficie pública del túnel; colisión de nombres y visibilidad de ejecuciones en la instancia compartida; rebotes por correos ficticios; OAuth personal de Gmail.
- **Bloqueadores transversales** (plan de trabajo S5–S6): n8n es remoto y la API corre en `localhost:8081`, de modo que WF-001 y WF-003 no funcionan sin el túnel; falta la credencial Gmail.

## Historial

- 2026-09-30 — Épica creada en estado `Borrador` por el especificador Scrum a partir de `PLAN_S5_S6_N8N.md`, `PRD.md` §8–§10 y RF-19, `GUIA_SESIONES_S2_S6.md` (S5, S6) y `RESTRICCIONES_TECNICAS.md`. Incorpora como reglas D-A, D-B y D-H (decididas por el usuario) y como propuestas pendientes D-C, D-D, D-E, D-F, D-G y D-I. No está aprobada.
- 2026-10-04 — Aprobada por el usuario el 2026-10-04 con su mensaje «aprobdo lo del s5»; alcance S5 (EP-010 y HU-034, D-C, D-D, D-I); HU-035/036 pendientes de confirmación. Alcance interpretado por el agente principal a partir de esa frase (respuesta a la pregunta de seguir con S5 con la frase de aprobación de `PLAN_EJECUCION_S5_S6.md` §0). D-C, D-D y D-I pasan a vigentes por esa aprobación (afectan a WF-001); D-E, D-F y D-G (S6) siguen como propuestas pendientes de confirmación explícita antes de la Fase 5. HU-035 y HU-036 siguen en `Borrador`. Se autoriza abrir S5, lo que resuelve la precondición de apertura para el alcance de S5.
