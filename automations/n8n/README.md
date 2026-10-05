# Automatizaciones n8n (EP-010)

Flujos exportados de la instancia `https://impulso-n8n.aiacademy.com.co` (proyecto personal de la cuenta de la
instancia). Laboratorio FCV, **datos ficticios**. Prefijo de nombre: `jhonNuñez-`.

> **Estado: BORRADOR del 2026-10-04.** Los JSON reflejan lo que hay en n8n hoy. Ningún flujo ha corrido todavía de
> extremo a extremo con credenciales reales: no se ha enviado ningún correo ni se ha activado ningún flujo. Se
> reexportan cuando cada flujo pase su prueba (Fases 4, 7 y 8 de `PLAN_EJECUCION_S5_S6.md`).

| Archivo | Flujo en n8n | Qué hace | Estado |
|---|---|---|---|
| `WF-001-appointment-reminders.json` | `9vNgFpXjjAkwse7o` | Cada hora (y a demanda por webhook) consulta `GET /api/automation/appointments/upcoming`, descarta recordatorios ya enviados y manda un correo por cita | **Defecto conocido**, ver abajo |
| `WF-002-status-notifications.json` | `7XF4DTWA11YViP42` | Webhook al que Spring publica los cambios de estado; valida, evita duplicados y notifica al paciente | Sin probar con la API real. Lee `patient.fullName` desde el 2026-10-04 |
| `WF-003-daily-operational-summary.json` | `BRksx12kTEtI5r2l` | Cada día a las 07:00 consulta `GET /api/automation/appointments/daily` y manda el resumen a operaciones | Sin probar |

Los `.md` con el mismo nombre son las descripciones originales de la sesión S2.

## Defecto conocido de WF-001 (sin corregir)

El nodo **«Solo APPROVED»** compara `$json.status` con `"APPROVED"`, pero `upcoming` **no devuelve `status`** (solo
entrega citas aprobadas; ver el contrato en la wiki). Con la comparación estricta descartaría todas las citas y no
saldría ningún recordatorio. Corrección: borrar ese nodo y conectar «Consultar citas próximas» directamente con
«Descartar ya enviados». Pendiente de aplicar en n8n y de reexportar.

## Qué NO está en estos archivos

- Credenciales y secretos: se quitaron los ids de credencial. Hay que conectarlas a mano al importar.
- Valores de configuración reales: la URL del túnel y los correos son marcadores (`CONFIGURAR-…`).

## Cómo importar

1. En n8n: *Workflows → Import from file* y elegir el JSON.
2. Conectar las credenciales de cada nodo:
   - `Gmail OAuth2` (solo envío) en los nodos de Gmail.
   - `Templated Custom Auth` con la plantilla `{"headers":{"X-Automation-Key":"{{api_key}}"}}` en los nodos HTTP que
     llaman a `/api/automation/**` (WF-001 y WF-003). El valor es tu `AUTOMATION_API_KEY`.
   - `Header Auth` con nombre `X-Webhook-Secret` en los webhooks (WF-002 y los «a demanda»). El valor es tu
     `N8N_WEBHOOK_SECRET`.
3. Crear la Data Table de registro con las columnas `eventId`, `workflow`, `appointmentId`, `recipientMasked`,
   `result`, `detail`, `executionId`, `loggedAt` y **sustituir su id** (`iqNu0V8hyF7mXAau` es el de la instancia
   original) en los nodos de Data Table.
4. En el nodo «Configuración» (o «Normalizar evento» en WF-002): URL del túnel y un correo de prueba. Dejar
   `testMode = true` hasta la demo final.

## Variables de la API relacionadas

`AUTOMATION_API_KEY`, `N8N_WEBHOOK_SECRET`, `N8N_WEBHOOK_WF001_URL`, `N8N_WEBHOOK_WF002_URL`,
`N8N_WEBHOOK_WF003_URL` (ver `.env.example` de la raíz del workspace; todas sin valor).

## Riesgos

Ver la sección de riesgos residuales de `PLAN_EJECUCION_S5_S6.md` §10: texto libre llegando a correos, clave de API
de larga vida en una instancia compartida, entrega best-effort del evento (`dec-007`), túnel público durante la demo.
