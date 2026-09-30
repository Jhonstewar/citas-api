---
id: HU-035
tipo: historia-de-usuario
titulo: "Publicar eventos de cambio de estado a n8n"
estado: Borrador
epica: "[[EP-010-automatizaciones-n8n]]"
requisitos: ["PRD §10", RF-19, RF-20]
esfuerzo: "Alto"
sprint_sugerido: "Sprint 10"
dependencias:
  - "[[HU-030-aprobar-o-rechazar-cita-especializada]]"
  - "[[HU-031-aprobar-o-rechazar-reprogramacion]]"
  - "[[HU-026-cancelar-una-cita-futura]]"
  - "[[HU-032-auditar-cambios-de-estado-de-cita]]"
  - "[[HU-033-publicar-contrato-rest-documentado]]"
  - "[[HU-005-autorizar-peticiones-por-rol-y-ownership]]"
relacionadas:
  - "[[HU-034-consultar-citas-para-automatizacion]]"
  - "[[HU-036-versionar-y-documentar-los-flujos-n8n]]"
  - "[[HU-027-solicitar-reprogramacion-de-cita-aprobada]]"
  - "[[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]]"
  - "[[HU-025-consultar-mis-citas-y-detalle]]"
---

# HU-035 — Publicar eventos de cambio de estado a n8n

## Historia de usuario

**COMO** USER (paciente)  
**QUIERO** que cada decisión o cancelación que cambia el estado de mi cita se publique como evento hacia n8n sin afectar la operación  
**PARA** recibir un aviso por correo de lo que ocurrió con mi cita, sin que una caída del servicio de avisos impida ni deshaga la operación

> Como USER (paciente), quiero que cada decisión o cancelación que cambia el estado de mi cita se publique como evento hacia n8n sin afectar la operación para recibir un aviso por correo de lo que ocurrió con mi cita, sin que una caída del servicio de avisos impida ni deshaga la operación.

## Contexto y descripción

[[HU-030-aprobar-o-rechazar-cita-especializada]], [[HU-031-aprobar-o-rechazar-reprogramacion]], [[HU-026-cancelar-una-cita-futura]] y [[HU-032-auditar-cambios-de-estado-de-cita]] dejaron la notificación fuera de alcance y la remitieron a «la automatización posterior de PRD §10». Esta HU es el punto de salida que permite a WF-002 reaccionar: Spring publica un evento HTTP hacia un webhook de n8n en cada transición relevante. Esta HU **no modifica esas HU**: las complementa. Sus criterios y su evidencia no cambian; el código de sus casos de uso recibe una llamada adicional al puerto.

Forma del diseño: un **puerto** `AppointmentEventPublisher` en `application` (sin Spring ni HTTP), un **adaptador** en `infrastructure` que hace el `POST`, y un adaptador nulo cuando no hay URL. Los casos de uso llaman al puerto **después** de que `tx.inTransaction` retorne, nunca dentro, con la vista ya leída. Un fallo del publicador se captura y se registra, **nunca** se propaga.

**Propuestas vigentes, pendientes de confirmar:** **D-E** (eventos: aprobación/rechazo de especializada, aprobación/rechazo de reprogramación y cancelación; la cita general auto-aprobada queda fuera) y **D-F** (entrega *best-effort* con reintentos tras el commit, sin *outbox*). Si el usuario cambia D-E o D-F, esta HU se reescribe antes de aprobarse.

**Contrato propuesto del evento** (`POST {N8N_WEBHOOK_WF002_URL}`, cabecera `X-Webhook-Secret`, cuerpo JSON):

```json
{ "eventId": "uuid-v4",
  "eventType": "APPOINTMENT_APPROVED | APPOINTMENT_REJECTED | APPOINTMENT_CANCELLED | RESCHEDULE_APPROVED | RESCHEDULE_REJECTED",
  "occurredAt": "2026-10-01T10:15:00-05:00",
  "appointmentId": 57, "status": "APPROVED",
  "patient": { "firstName": "Ana", "email": "ana.perez@ejemplo.test" },
  "appointment": { "date": "2026-10-05", "startTime": "09:00", "endTime": "09:30",
                   "site": { "code": "ICV", "name": "…" }, "professional": "…", "specialty": "…" },
  "reason": "texto libre opcional (rechazo o cancelación)" }
```

`reason` es texto libre escrito por una persona: contenido no confiable para n8n.

**Precondición:** esta HU depende de que S4 esté cerrada o de que el usuario autorice expresamente abrir S5/S6.

## Alcance

- Puerto `AppointmentEventPublisher` en `application/appointment`, con el evento como tipo del dominio o de la aplicación, sin dependencias de framework.
- Llamada al puerto desde aprobar y rechazar cita especializada, aprobar y rechazar reprogramación, y cancelar, después de confirmada la transacción.
- Adaptador `N8nWebhookPublisher` en `infrastructure/automation`: `POST` con cabecera `X-Webhook-Secret`, timeouts de conexión y lectura, reintentos con espera creciente, ejecución que no retiene la petición del usuario y log sin cuerpo ni correo.
- Adaptador nulo (`NoOp`) cuando `N8N_WEBHOOK_WF002_URL` está vacía.
- Propiedades de configuración `app.n8n` con `N8N_WEBHOOK_WF001_URL`, `N8N_WEBHOOK_WF002_URL`, `N8N_WEBHOOK_WF003_URL` y `N8N_WEBHOOK_SECRET`, validadas al arrancar. Spring solo **publica** en operación normal a la URL de WF-002; las URL de WF-001 y WF-003 quedan disponibles para el disparo a demanda (D-I).
- Documentación del contrato del evento en `contrato-rest-citas.md` y de la decisión de entrega en la wiki.

## Fuera de alcance

- El workflow WF-002 en n8n y el envío por Gmail ([[HU-036-versionar-y-documentar-los-flujos-n8n]]).
- Tabla *outbox*, planificador de reenvío o garantía de entrega (D-F).
- Notificar la cita general auto-aprobada, la solicitud de reprogramación ([[HU-027-solicitar-reprogramacion-de-cita-aprobada]]) y la creación de solicitudes (D-E).
- Nuevas columnas, tablas o migraciones.
- Cambios en `citas-web`.
- Cambiar el historial de estados de [[HU-032-auditar-cambios-de-estado-de-cita]]: el evento no sustituye la auditoría (RF-19, RN-12).
- Modificar el texto o la matriz de HU-030, HU-031, HU-026, HU-032.

## Reglas de negocio

- El evento nace de una transición de estado del dominio confirmada (RN-11), no del controlador ni del frontend.
- La publicación ocurre después del commit: un evento de una operación que falla nunca sale.
- Un fallo, un timeout o una respuesta no exitosa del destino no cambian la respuesta HTTP al usuario ni revierten la operación de negocio (PRD §10: sin cambiar el núcleo funcional).
- Secretos solo por variable de entorno (PRD §8); el log no incluye el secreto, el cuerpo ni el correo del paciente (PRD §8).
- Los datos enviados se limitan a lo necesario para el correo; sin documento ni teléfono.
- El rechazo de una reprogramación no cambia el estado de la cita y no escribe fila de historial (D39), pero sí emite `RESCHEDULE_REJECTED` porque es una decisión que el paciente debe conocer (D-E).

## Dependencias y relaciones

- Épica: [[EP-010-automatizaciones-n8n]]
- Dependencias: [[HU-030-aprobar-o-rechazar-cita-especializada]], [[HU-031-aprobar-o-rechazar-reprogramacion]], [[HU-026-cancelar-una-cita-futura]], [[HU-032-auditar-cambios-de-estado-de-cita]], [[HU-033-publicar-contrato-rest-documentado]], [[HU-005-autorizar-peticiones-por-rol-y-ownership]]
- Relacionadas: [[HU-034-consultar-citas-para-automatizacion]], [[HU-036-versionar-y-documentar-los-flujos-n8n]], [[HU-027-solicitar-reprogramacion-de-cita-aprobada]], [[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]], [[HU-025-consultar-mis-citas-y-detalle]]

## Esfuerzo

**Nivel:** Alto

**Justificación de dificultad:** Toca cinco casos de uso ya entregados sin alterar su comportamiento, debe respetar el orden «después del commit» en un entorno transaccional y garantizar que ningún fallo saliente contamine la operación. Requiere un adaptador con timeouts, reintentos y ejecución no bloqueante, probado contra un servidor HTTP local, y pruebas que demuestren la ausencia de eventos cuando la transacción falla.

## Tareas de desarrollo

- [ ] **T-01 — Definir el evento y el puerto de publicación**  
  Dificultad: Medio  
  Descripción: Tipo de evento con `eventId`, `eventType`, `occurredAt`, cita, paciente y motivo, y la interfaz `AppointmentEventPublisher` en `application`. Sin Spring, sin HTTP, sin JSON.

- [ ] **T-02 — Publicar desde los casos de uso, después del commit**  
  Dificultad: Alto  
  Descripción: En aprobar/rechazar cita especializada, aprobar/rechazar reprogramación y cancelar, construir el evento con la vista ya leída y llamar al puerto fuera del bloque `tx.inTransaction`. Cualquier excepción del publicador se captura y se registra sin propagarse.

- [ ] **T-03 — Adaptador HTTP hacia n8n**  
  Dificultad: Alto  
  Descripción: `N8nWebhookPublisher` con cliente HTTP, timeouts de conexión y lectura, hasta tres reintentos con espera creciente, cabecera `X-Webhook-Secret`, ejecución asíncrona y log sin cuerpo ni correo.

- [ ] **T-04 — Adaptador nulo y configuración**  
  Dificultad: Medio  
  Descripción: `NoOpAppointmentEventPublisher` activo si `N8N_WEBHOOK_WF002_URL` está vacía. Clase `@ConfigurationProperties("app.n8n")` con las tres URL y el secreto; validación al arrancar (URL `https`, rechazo de `CHANGE_ME`). Variables declaradas sin valores en `.env.example`, `docker-compose.yml` y `application.yml`.

- [ ] **T-05 — Pruebas de casos de uso con publicador falso**  
  Dificultad: Alto  
  Descripción: Evento correcto por transición, ausencia de evento si la transacción falla, y publicador que lanza excepción sin cambiar el resultado ni revertir.

- [ ] **T-06 — Pruebas del adaptador contra un servidor HTTP local**  
  Dificultad: Medio  
  Descripción: Verifican cabecera, cuerpo, reintentos ante error y timeout, y contenido del log.

- [ ] **T-07 — Contrato del evento y decisión de entrega**  
  Dificultad: Bajo  
  Descripción: Documentar el evento en `contrato-rest-citas.md` y registrar D-F y sus límites en la wiki, según [[HU-033-publicar-contrato-rest-documentado]].

## Criterios de aceptación

### CA-01 — Un evento por transición, con tipo e identificador

**Dado** una cita especializada `REQUESTED`, una reprogramación `PENDING` y una cita `APPROVED` futura del paciente  
**Cuando** ADMIN aprueba la especializada, ADMIN rechaza otra, ADMIN aprueba una reprogramación, ADMIN rechaza otra y el paciente cancela una cita  
**Entonces** se publica exactamente un evento por cada operación, con `eventType` `APPOINTMENT_APPROVED`, `APPOINTMENT_REJECTED`, `RESCHEDULE_APPROVED`, `RESCHEDULE_REJECTED` y `APPOINTMENT_CANCELLED` respectivamente, y los cinco `eventId` son distintos.

### CA-02 — Ningún evento si la transacción falla

**Dado** una operación que falla y hace rollback (rechazo sin motivo, transición inválida 409, decisión concurrente perdedora, cancelación de cita ajena)  
**Cuando** se ejecuta  
**Entonces** el publicador no recibe ningún evento y el adaptador no emite ninguna petición saliente.

### CA-03 — La publicación ocurre después del commit

**Dado** un publicador de prueba que, al ser invocado, consulta la base de datos  
**Cuando** se ejecuta una aprobación  
**Entonces** el publicador observa ya persistido el nuevo estado de la cita, y el código de los casos de uso invoca el puerto fuera del bloque `tx.inTransaction`.

### CA-04 — El fallo del publicador no afecta la operación

**Dado** un publicador que lanza una excepción, y un destino que no responde o responde 500  
**Cuando** ADMIN aprueba una cita  
**Entonces** la API responde el mismo 200 con el nuevo estado que sin publicador, la cita queda en el nuevo estado con su historial, y el fallo queda registrado en el log.

### CA-05 — Sin URL, publicador nulo

**Dado** `N8N_WEBHOOK_WF002_URL` vacía  
**Cuando** se ejecuta cualquier transición  
**Entonces** se usa el adaptador nulo, no se abre ninguna conexión saliente y todo el comportamiento anterior de la API permanece igual.

### CA-06 — Cabecera de secreto

**Dado** `N8N_WEBHOOK_SECRET` y `N8N_WEBHOOK_WF002_URL` configurados apuntando a un servidor HTTP local de prueba  
**Cuando** se publica un evento  
**Entonces** el servidor recibe un `POST` con la cabecera `X-Webhook-Secret` igual al secreto y un cuerpo JSON con los campos del contrato; con el secreto vacío el backend no envía.

### CA-07 — El log no contiene cuerpo ni correo

**Dado** eventos publicados con éxito y con error  
**Cuando** se inspecciona el log  
**Entonces** ningún registro contiene el correo del paciente, el texto de `reason`, el cuerpo JSON ni el valor del secreto; sí puede contener `eventId`, `eventType` y el código de resultado.

### CA-08 — Reintentos y timeouts

**Dado** un servidor local que falla las dos primeras peticiones y acierta la tercera, y otro que nunca responde  
**Cuando** se publica un evento  
**Entonces** en el primer caso se hacen tres intentos con espera creciente y el evento llega; en el segundo la publicación se abandona tras los timeouts y los reintentos configurados, sin retener la respuesta HTTP al usuario más allá de esos límites, y queda un registro del fallo.

### CA-09 — Arquitectura hexagonal respetada

**Dado** el código con el puerto y el adaptador  
**Cuando** se ejecuta `HexagonalArchitectureTest` y la suite completa  
**Entonces** ambos pasan y el puerto en `application` no importa clases de Spring ni de HTTP.

### CA-10 — Validación de configuración

**Dado** `N8N_WEBHOOK_WF002_URL` con esquema distinto de `https`, o `N8N_WEBHOOK_SECRET=CHANGE_ME`  
**Cuando** se intenta arrancar  
**Entonces** el arranque falla con un mensaje de configuración que no imprime el secreto; con ambas variables vacías arranca con el adaptador nulo.

### CA-11 — Contrato del evento documentado

**Dado** el evento implementado  
**Cuando** se compara `contrato-rest-citas.md` con el código y las pruebas  
**Entonces** el contrato documenta URL de destino por variable, cabecera, los cinco `eventType`, el cuerpo con sus campos y su carácter de texto libre no confiable para `reason`, la semántica *best-effort* y un ejemplo, sin discrepancias.

### CA-12 — Las HU previas no cambian de comportamiento

**Dado** las pruebas existentes de HU-026, HU-030, HU-031 y HU-032  
**Cuando** se ejecuta la suite completa tras esta HU  
**Entonces** todas siguen pasando sin haber modificado sus expectativas.

## Definition of Done

- [ ] Los criterios CA-01 a CA-12 están validados con evidencia concreta.
- [ ] El puerto vive en `application` y el adaptador en `infrastructure`; ninguna regla de publicación está en el controlador.
- [ ] Ninguna llamada al puerto ocurre dentro de `tx.inTransaction`.
- [ ] No se crean migraciones, tablas ni columnas.
- [ ] `.env.example`, `docker-compose.yml` y `application.yml` declaran las cinco variables del grupo de n8n sin valores reales; `.env` no se versiona.
- [ ] Existen pruebas automatizadas de los criterios y pasan junto con la suite completa, ejecutada en Docker.
- [ ] `HexagonalArchitectureTest` sigue en verde.
- [ ] El contrato del evento queda documentado según [[HU-033-publicar-contrato-rest-documentado]] y la decisión D-F queda registrada en la wiki.
- [ ] Se comprobó con una prueba real extremo a extremo (aprobar una solicitud en la aplicación contra el webhook de WF-002 publicado) que la ejecución, el correo y el registro existen, o se declara qué parte no fue verificable.
- [ ] Verificación independiente por un agente distinto del que implementó.
- [ ] La trazabilidad de esta HU y de [[EP-010-automatizaciones-n8n]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Pendiente | — | — |
| CA-02 | Pendiente | — | — |
| CA-03 | Pendiente | — | — |
| CA-04 | Pendiente | — | — |
| CA-05 | Pendiente | — | — |
| CA-06 | Pendiente | — | — |
| CA-07 | Pendiente | — | — |
| CA-08 | Pendiente | — | — |
| CA-09 | Pendiente | — | — |
| CA-10 | Pendiente | — | — |
| CA-11 | Pendiente | — | — |
| CA-12 | Pendiente | — | — |
| DoD — CA-01 a CA-12 validados | Pendiente | — | — |
| DoD — Puerto en application y adaptador en infrastructure | Pendiente | — | — |
| DoD — Ninguna llamada dentro de `tx.inTransaction` | Pendiente | — | — |
| DoD — Sin migraciones | Pendiente | — | — |
| DoD — Variables declaradas sin valores reales | Pendiente | — | — |
| DoD — Pruebas y suite completa en Docker | Pendiente | — | — |
| DoD — `HexagonalArchitectureTest` | Pendiente | — | — |
| DoD — Contrato del evento y decisión D-F | Pendiente | — | — |
| DoD — Prueba real de extremo a extremo | Pendiente | — | — |
| DoD — Verificación independiente | Pendiente | — | — |
| DoD — Trazabilidad actualizada | Pendiente | — | — |

## Historial de validación

- 2026-09-30 — HU creada en estado `Borrador` por el especificador Scrum a partir de `PLAN_S5_S6_N8N.md` (§0.2, §2, §4). D-E, D-F e D-I figuran como propuesta vigente pendiente de confirmar. No está aprobada.

## Notas y decisiones

- **Propuestas pendientes (D-E, D-F, D-I):** véanse la épica y la lista de preguntas abiertas.
- Esta HU introduce un cambio de código en casos de uso de HU `Completada` o `En validación` (HU-026, HU-030, HU-031). El comportamiento observable no cambia (CA-12); se deja aquí la trazabilidad en lugar de tocar esas HU, por indicación de no modificarlas. Reabrir alguna sería decisión del usuario.
- El paciente recibe el `reason` escrito por ADMIN o por el paciente: debe revisarse que no contenga datos de otras personas (riesgo residual documentado en [[HU-036-versionar-y-documentar-los-flujos-n8n]]).
- Pregunta abierta: la cancelación puede incluir `reason`? El contrato REST de HU-026 no lo garantiza; el campo es opcional en el evento y debe verificarse contra el código al implementar.
- Pregunta abierta: ¿se reenvía algo ante eventos perdidos? D-F responde que no.
- Precondición: S4 cerrada o autorización expresa del usuario para abrir S5/S6.
