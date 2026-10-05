---
id: HU-034
tipo: historia-de-usuario
titulo: "Consultar citas para automatización"
estado: En validación
epica: "[[EP-010-automatizaciones-n8n]]"
requisitos: ["PRD §10", RF-20]
esfuerzo: "Alto"
sprint_sugerido: "Sprint 9"
dependencias:
  - "[[HU-005-autorizar-peticiones-por-rol-y-ownership]]"
  - "[[HU-033-publicar-contrato-rest-documentado]]"
  - "[[HU-026-cancelar-una-cita-futura]]"
  - "[[HU-030-aprobar-o-rechazar-cita-especializada]]"
  - "[[HU-031-aprobar-o-rechazar-reprogramacion]]"
relacionadas:
  - "[[HU-035-publicar-eventos-de-cambio-de-estado-a-n8n]]"
  - "[[HU-036-versionar-y-documentar-los-flujos-n8n]]"
  - "[[HU-032-auditar-cambios-de-estado-de-cita]]"
---

# HU-034 — Consultar citas para automatización

## Historia de usuario

**COMO** ADMIN responsable de las automatizaciones del laboratorio  
**QUIERO** que n8n pueda leer las citas `APPROVED` próximas (y, como opcional, el resumen del día) con una clave de API dedicada de solo lectura  
**PARA** que los recordatorios y el resumen operativo funcionen sin darle a n8n una cuenta con poder de ADMIN

> Como ADMIN responsable de las automatizaciones, quiero que n8n pueda leer las citas APPROVED próximas (y, como opcional, el resumen del día) con una clave de API dedicada de solo lectura para que los recordatorios y el resumen operativo funcionen sin darle a n8n una cuenta con poder de ADMIN.

## Contexto y descripción

Hoy todos los endpoints exigen un JWT de persona con rol y lo no declarado es `denyAll`: n8n no tiene ninguna forma legítima de leer citas. Usar una cuenta ADMIN le daría poder total, obligaría a manejar refresh por cookie y access de 15 minutos. La decisión **D-A** (tomada por el usuario el 2026-09-30) elige una **clave de API dedicada, de solo lectura**, en `/api/automation/**`, enviada en la cabecera `X-Automation-Key` y validada por una **cadena de seguridad propia**.

El llamante es un cliente de servicio, no una persona ni un rol de negocio. `GET /api/automation/appointments/upcoming?hours=` alimenta a WF-001 (recordatorios) y devuelve lo mínimo para componer un correo, incluido el correo del paciente, porque sin él el flujo no puede enviar. `GET /api/automation/appointments/daily?date=` alimenta a WF-003 (bonus, D-G) y no contiene datos personales.

**Propuesta vigente, pendiente de confirmar (D-C):** la ventana de WF-001 son las próximas 24 h, revisadas cada hora; el endpoint acepta `hours` para no fijar ese valor en el backend.

**Precondición:** esta HU depende de que S4 esté cerrada o de que el usuario autorice expresamente abrir S5.

## Alcance

- Cadena de seguridad secundaria con `securityMatcher("/api/automation/**")`, stateless, que solo admite `GET` y valida `X-Automation-Key` con comparación de tiempo constante.
- `GET /api/automation/appointments/upcoming?hours=` con filtro de estado en la consulta, ventana en `America/Bogota`, orden por inicio y vista mínima: `appointmentId`, nombre de pila y correo del paciente, `date`, `startTime`, `endTime`, `site` (`code`, `name`, `address`), profesional y especialidad.
- **Opcional / bonus (D-G):** `GET /api/automation/appointments/daily?date=` con filas `siteCode`, `status`, `specialty`, `startTime` y los contadores de pendientes, sin datos personales; por omisión, hoy en `America/Bogota`.
- Configuración de `AUTOMATION_API_KEY` por variable de entorno, con validación al arrancar.
- Respuestas de error `ProblemDetail` en español, con el mismo formato que el resto de la API.
- Actualización del contrato REST y de la documentación de seguridad.

## Fuera de alcance

- Cualquier operación de escritura bajo `/api/automation/**`.
- Crear una cuenta ADMIN o un rol nuevo para n8n.
- Gestión de la clave desde la interfaz, o almacenamiento de la clave en base de datos.
- Envío de correos, el workflow n8n y el túnel (ver [[HU-036-versionar-y-documentar-los-flujos-n8n]]).
- Publicación de eventos hacia n8n ([[HU-035-publicar-eventos-de-cambio-de-estado-a-n8n]]).
- Cambios al modelo de datos o migraciones.
- Cambios en `citas-web`.
- Un endpoint `GET /api/automation/ping`: aparece en el plan de trabajo como criterio de salida, pero no está en los requisitos de esta HU (ver pregunta abierta en Notas).

## Reglas de negocio

- Solo las citas en estado `APPROVED`, futuras y dentro de la ventana solicitada entran en `upcoming`; "futura" se evalúa con la hora actual en `America/Bogota`.
- Minimización de datos: la respuesta no contiene documento ni teléfono del paciente (PRD §8, PRD §9).
- La clave llega solo por variable de entorno (PRD §8) y nunca se escribe en un log.
- Las reglas de selección viven en `application`/dominio y en la consulta, no en el controlador.
- Una clave de API no es un JWT de persona: ninguno sustituye al otro.

## Dependencias y relaciones

- Épica: [[EP-010-automatizaciones-n8n]]
- Dependencias: [[HU-005-autorizar-peticiones-por-rol-y-ownership]], [[HU-033-publicar-contrato-rest-documentado]], [[HU-026-cancelar-una-cita-futura]], [[HU-030-aprobar-o-rechazar-cita-especializada]], [[HU-031-aprobar-o-rechazar-reprogramacion]] (las citas `APPROVED` que se leen nacen o se mantienen en esas HU).
- Relacionadas: [[HU-035-publicar-eventos-de-cambio-de-estado-a-n8n]], [[HU-036-versionar-y-documentar-los-flujos-n8n]], [[HU-032-auditar-cambios-de-estado-de-cita]], [[HU-029-consultar-bandeja-administrativa]] (reutilizable para los contadores de pendientes del resumen diario)

## Esfuerzo

**Nivel:** Alto

**Justificación de dificultad:** Exige una segunda cadena de seguridad que convive con la existente sin debilitar su `denyAll`, una comparación de clave resistente a ataques de tiempo y un arranque que falla de forma segura ante una configuración débil. Añade además una consulta con ventana temporal sensible a la zona horaria y un conjunto de pruebas de minimización de datos y de no fuga de la clave.

## Tareas de desarrollo

- [x] **T-01 — Configuración y validación de la clave de automatización**  
  Dificultad: Medio  
  Descripción: Propiedad `AUTOMATION_API_KEY` leída por variable de entorno y declarada sin valor en `.env.example`, `docker-compose.yml` y `application.yml`. El arranque falla si vale `CHANGE_ME` o tiene menos de 32 bytes; con la variable vacía, la cadena de automatización rechaza toda petición. Mismo patrón que el secreto JWT.

- [x] **T-02 — Cadena de seguridad y filtro de clave**  
  Dificultad: Alto  
  Descripción: Segunda `SecurityFilterChain` con `securityMatcher("/api/automation/**")` y `@Order` anterior a la principal, stateless, solo `GET`. Filtro que compara `X-Automation-Key` con `MessageDigest.isEqual`, responde 401 `ProblemDetail` en español si falta o es incorrecta, y no registra la clave ni la cabecera en ningún log.

- [x] **T-03 — Consulta de citas `APPROVED` en una ventana**  
  Dificultad: Medio  
  Descripción: Método de consulta (`findApprovedStartingBetween`) en el puerto de consultas de citas y su implementación SQL sobre `appointments`, con el filtro de estado dentro de la consulta, ventana calculada en `America/Bogota` y orden por inicio. Devuelve una vista mínima sin documento ni teléfono.

- [x] **T-04 — Caso de uso y controlador de `upcoming`**  
  Dificultad: Medio  
  Descripción: `AutomationQueriesUseCase` en `application/automation` y `AutomationController` en `infrastructure/rest/automation`, con un DTO propio. `hours` se valida entre 1 y 72; fuera de rango o no numérico responde 400 `ProblemDetail` en español. Sin reglas de negocio en el controlador.

- [ ] **T-05 — (Opcional/bonus) Consulta y endpoint `daily`** *(fuera de entrega de S5: WF-003 opcional, D-G sin confirmar)*  
  Dificultad: Medio  
  Descripción: Consulta por fecha, `date` con valor por omisión «hoy» en `America/Bogota`, filas sin datos personales y contadores de pendientes reutilizando el cálculo existente del resumen administrativo. Un día sin citas devuelve `rows: []`.

- [x] **T-06 — Pruebas de integración y de arquitectura**  
  Dificultad: Alto  
  Descripción: Pruebas de 401, de JWT de persona que no sustituye la clave, de filtro de estado y ventana, de minimización de datos, de límites de `hours`, de método no permitido, de fallo de arranque por configuración débil y de ausencia de la clave en logs. `HexagonalArchitectureTest` sigue en verde.

- [x] **T-07 — Contrato REST y documentación de seguridad**  
  Dificultad: Bajo  
  Descripción: Sección nueva en `contrato-rest-citas.md` con cabecera, parámetros, respuesta, códigos y ejemplos, y nota de la cadena secundaria en la documentación de seguridad, según [[HU-033-publicar-contrato-rest-documentado]].

## Criterios de aceptación

### CA-01 — Sin clave, 401

**Dado** la API en marcha con `AUTOMATION_API_KEY` configurada  
**Cuando** se invoca `GET /api/automation/appointments/upcoming` sin la cabecera `X-Automation-Key`  
**Entonces** la API responde 401 con un `ProblemDetail` en español y el cuerpo no contiene ninguna cita.

### CA-02 — Clave errónea, 401

**Dado** la API en marcha con una clave configurada  
**Cuando** se invoca el endpoint con una `X-Automation-Key` distinta, vacía o de otra longitud  
**Entonces** la API responde 401 con un `ProblemDetail` en español, con el mismo cuerpo que la respuesta de CA-01 (no se diferencia «clave ausente» de «clave incorrecta» en el mensaje).

### CA-03 — Un JWT de persona no sustituye la clave

**Dado** sesiones válidas de un `ADMIN`, un `PROFESSIONAL` y un `USER`  
**Cuando** cada una invoca `/api/automation/appointments/upcoming` con su `Authorization: Bearer` y sin `X-Automation-Key`  
**Entonces** las tres reciben 401, y a la inversa una petición con la clave correcta a `/api/admin/**`, `/api/professional/**` o `/api/patient/**` recibe 401 o 403 y no accede a nada.

### CA-04 — Solo APPROVED futuras dentro de la ventana

**Dado** citas en `REQUESTED`, `APPROVED`, `REJECTED`, `CANCELLED`, `COMPLETED` y `NO_SHOW`, una `APPROVED` ya pasada y una `APPROVED` futura fuera de la ventana  
**Cuando** se invoca `upcoming?hours=24` con la clave correcta  
**Entonces** la respuesta contiene únicamente las citas `APPROVED` cuyo inicio es posterior a la hora actual en `America/Bogota` y no posterior a 24 horas, ordenadas por inicio, y excluye las demás.

### CA-05 — Sin documento ni teléfono

**Dado** una cita `APPROVED` dentro de la ventana de un paciente con documento y teléfono registrados  
**Cuando** se consulta `upcoming` con la clave correcta  
**Entonces** la respuesta incluye `appointmentId`, nombre de pila, correo, `date`, `startTime`, `endTime`, `site`, profesional y especialidad, y no contiene en ningún campo el documento ni el teléfono del paciente.

### CA-06 — `hours` acotado

**Dado** la clave correcta  
**Cuando** se invoca `upcoming` con `hours=0`, `hours=73`, `hours=-1`, `hours=abc` o con `hours` vacío  
**Entonces** la API responde 400 con un `ProblemDetail` en español que indica el rango permitido (1 a 72), y con `hours=1` y `hours=72` responde 200.

### CA-07 — Solo método GET

**Dado** la clave correcta  
**Cuando** se invoca cualquier ruta de `/api/automation/**` con `POST`, `PUT`, `PATCH` o `DELETE`  
**Entonces** la API responde 405 o 403 y no se ejecuta ninguna acción ni cambia ningún dato.

### CA-08 — La clave nunca aparece en logs

**Dado** peticiones con clave correcta, errónea y ausente  
**Cuando** se inspecciona la salida de log de la API durante esas peticiones y durante el arranque  
**Entonces** ningún registro contiene el valor de `AUTOMATION_API_KEY` ni el de la cabecera recibida.

### CA-09 — El arranque falla con una clave débil

**Dado** la API configurada con `AUTOMATION_API_KEY=CHANGE_ME`, o con una clave de menos de 32 bytes  
**Cuando** se intenta arrancar  
**Entonces** el proceso falla con un mensaje de configuración que no imprime el valor de la clave; con la variable vacía el arranque continúa pero `/api/automation/**` rechaza toda petición con 401.

### CA-10 — Contrato REST actualizado

**Dado** los endpoints implementados  
**Cuando** se compara `contrato-rest-citas.md` con el código y las pruebas  
**Entonces** el contrato documenta ruta, cabecera `X-Automation-Key`, parámetros, cuerpo de respuesta, códigos 200, 400, 401 y 405 y un ejemplo, sin discrepancias con la implementación ([[HU-033-publicar-contrato-rest-documentado]]).

### CA-11 — (Opcional/bonus) Resumen diario sin datos personales

**Dado** un día con citas en distintas sedes y estados, y otro día sin citas  
**Cuando** se invoca `GET /api/automation/appointments/daily?date=YYYY-MM-DD` con la clave correcta  
**Entonces** el primer día devuelve una fila por cita con `siteCode`, `status`, `specialty` y `startTime` más los contadores de pendientes, sin nombre, correo, documento ni teléfono; el segundo devuelve `rows: []`; sin `date` se usa el día actual en `America/Bogota`; una fecha inválida devuelve 400 `ProblemDetail`. Este criterio es opcional y su ausencia no impide completar la HU si WF-003 se declara fuera de entrega.

## Definition of Done

- [ ] Los criterios CA-01 a CA-10 están validados con evidencia concreta; CA-11 lo está o queda declarado fuera de entrega.
- [ ] La cadena de seguridad de automatización es independiente de la principal, que conserva su `denyAll` para todo lo no declarado.
- [ ] La comparación de la clave usa comparación de tiempo constante.
- [ ] El filtro por estado `APPROVED` está en la consulta SQL y no se hace en memoria en el controlador.
- [ ] La regla de selección no vive en el controlador y el controlador no accede a repositorios.
- [ ] No se crean migraciones ni cambios de esquema.
- [ ] `.env.example`, `docker-compose.yml` y `application.yml` declaran `AUTOMATION_API_KEY` sin ningún valor real; `.env` no se versiona.
- [ ] Existen pruebas automatizadas de los criterios y pasan junto con la suite completa, ejecutada en Docker.
- [ ] `HexagonalArchitectureTest` sigue en verde.
- [ ] El contrato REST documenta los endpoints según [[HU-033-publicar-contrato-rest-documentado]].
- [ ] Verificación independiente por un agente distinto del que implementó.
- [ ] La trazabilidad de esta HU y de [[EP-010-automatizaciones-n8n]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
Verificación independiente (agente distinto del implementador), iteración 1 y 2, ambas del 2026-10-04. La iteración 1 dejó FAIL en CA-07, CA-08 y CA-09 (ver historial); la tabla refleja el resultado de la **iteración 2: PASS en CA-01 a CA-10**, suite completa **565/565** y `HexagonalArchitectureTest` 4/4. Abreviaturas: **AUIT** = `AutomationUpcomingIntegrationTest`, **ACCIT** = `AutomationClosedChainIntegrationTest`, **AKNLIT** = `AutomationKeyNotLoggedIntegrationTest`, **AKSIT** = `AutomationKeyStartupIntegrationTest`, **AN8PT** = `AutomationAndN8nPropertiesTest`.

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | `AUIT.withoutKeyHeaderReturns401ProblemInSpanish` | 401 con `ProblemDetail` en español y sin ninguna cita en el cuerpo |
| CA-02 | Cumple | `AUIT.wrongKeyAndDifferentLengthKeyAndEmptyKeyReturnTheSame401BodyAsNoKey` | Clave errónea, de otra longitud y vacía: mismo cuerpo que la ausente |
| CA-03 | Cumple | `AUIT.personJwtWithoutKeyReturns401ForEveryRole`, `AUIT.personJwtWithWrongKeyReturns401`, `AUIT.validKeyDoesNotOpenPersonRoutes`; `ACCIT` | ADMIN, PROFESSIONAL y USER reciben 401 sin la clave; la clave correcta no abre `/api/admin/**`, `/api/professional/**` ni `/api/patient/**` |
| CA-04 | Cumple | `JdbcAppointmentQueries.findApprovedStartingBetween` (filtro `APPROVED` en el SQL); `AUIT.onlyApprovedStatusIsReturned`, `AUIT.pastAndBeyondWindowAreExcluded`, `AUIT.exactlyNowPlusHoursIsIncludedAndExactlyNowIsExcluded` | Borde fijado por los dos lados: ahora + `hours` incluido, ahora excluido |
| CA-05 | Cumple | `AUIT.responseHasExactlyTheContractKeysAndNoDocumentPhoneOrHistory` | Lista cerrada de claves del contrato; sin documento, teléfono ni historial |
| CA-06 | Cumple | `AUIT.hoursOutOfRangeOrNotNumericReturns400Validation`, `AUIT.hoursAtTheLimitsReturns200` | 400 fuera de 1–72 y no numérico; 200 en 1 y 72. `hours` ausente = 24 (D-C aprobada) |
| CA-07 | Cumple | `AutomationApiKeyFilter:52-62`; `AUIT.writeMethodsWithValidKeyReturn405AndNeverExecute`, `AUIT.wrongOrMissingKeyWithAnyMethodReturns401NotMethodNotAllowed` | **Iteración 1: FAIL** (401 en lugar de 405 con clave válida y método de escritura). Corregido y reverificado en la iteración 2 |
| CA-08 | Cumple | `AKNLIT` | **Iteración 1: FAIL** (sin prueba de logs). Límite anotado: se probó con los loggers de seguridad y web en TRACE, no con todos los loggers |
| CA-09 | Cumple | `AKSIT`; `AN8PT` | **Iteración 1: FAIL** (solo `ApplicationContextRunner`, no un arranque real). Corregido con arranque de contexto real en `AKSIT` |
| CA-10 | Cumple | `citas-api/docs/wiki/llm-wiki/wiki/contrato-rest-citas.md` §«S5 — automatización» | Ruta, cabecera, parámetros, respuesta, 200/400/401/405 y ejemplo; `llm-wiki/` queda fuera del límite de escritura de esta skill: la evidencia se leyó |
| CA-11 (opcional) | Fuera de entrega de S5 | — | WF-003 es opcional y D-G sigue sin confirmar; la HU puede completarse sin este criterio (texto de CA-11) |
| DoD — CA-01 a CA-10 validados | Cumple | Filas CA-01 a CA-10; verificación independiente iteración 2 | CA-11 declarado fuera de entrega |
| DoD — Cadena independiente con `denyAll` intacto | Cumple | `AutomationSecurityConfig`; `ACCIT`; `AUIT.validKeyDoesNotOpenPersonRoutes` | Verificado por la iteración 2 |
| DoD — Comparación de tiempo constante | Cumple | `AutomationApiKeyFilter` compara SHA-256 de ambas claves con `MessageDigest.isEqual` | **Decisión:** SHA-256 + `isEqual` se acepta como equivalente o mejor que `isEqual` directo, porque no filtra la longitud de la clave |
| DoD — Filtro de estado en SQL | Cumple | `JdbcAppointmentQueries.findApprovedStartingBetween`; `AUIT.onlyApprovedStatusIsReturned` | — |
| DoD — Sin reglas en el controlador | Cumple | `AutomationController` delega en `AutomationQueriesUseCase` (`application/automation`); `AutomationQueriesUseCaseTest`; `HexagonalArchitectureTest` | — |
| DoD — Sin migraciones | Cumple | Ninguna migración nueva asociada a esta HU | Verificado por la iteración 2 |
| DoD — Variable declarada sin valores reales | Cumple | `AutomationProperties`; `AKSIT`, `AN8PT` | `.env` no se versiona |
| DoD — Pruebas y suite completa en Docker | Cumple | Suite 565/565 en Docker | Iteración 2 |
| DoD — `HexagonalArchitectureTest` | Cumple | `HexagonalArchitectureTest` 4/4 | Iteración 2 |
| DoD — Contrato REST | Cumple | Fila CA-10 | — |
| DoD — Verificación independiente | Cumple | Iteración 1 (FAIL en CA-07, CA-08, CA-09) e iteración 2 (PASS) | Agente distinto del implementador |
| DoD — Trazabilidad actualizada | Cumple | Esta matriz, el historial y `docs/wiki/scrum/README.md` | — |

## Historial de validación

- 2026-10-04 — **Verificación independiente, iteraciones 1 y 2; estado `Aprobada` → `En validación`. NO pasa a `Completada`: espera la F9.** **Iteración 1: FAIL** en CA-07 (con clave válida y método de escritura respondía 401 en lugar de 405), CA-08 (sin prueba de logs) y CA-09 (solo con `ApplicationContextRunner`, sin arranque real). Se corrigió. **Iteración 2: PASS** en CA-01 a CA-10 y en las DoD «CA-01 a CA-10 validados» y «pruebas y suite en Docker» (suite 565/565, `HexagonalArchitectureTest` 4/4). **CA-11 y T-05 se declaran fuera de entrega de S5:** WF-003 es opcional y D-G sigue sin confirmar. T-01 a T-04, T-06 y T-07 marcadas. **Decisiones aceptadas:** comparación SHA-256 + `MessageDigest.isEqual` (equivalente o mejor, no filtra la longitud); `commenceApiKey` responde 401 sin `WWW-Authenticate`; `hours` ausente = 24 (D-C aprobada).
- 2026-10-04 — Observaciones sin cambio de estado: CA-08 se probó solo con los loggers de seguridad y web en TRACE; `N8nProperties.secret` no valida su longitud, pregunta abierta para [[HU-035-publicar-eventos-de-cambio-de-estado-a-n8n]] que no se resuelve aquí.
- 2026-09-30 — HU creada en estado `Borrador` por el especificador Scrum a partir de `PLAN_S5_S6_N8N.md` (§2, §3, §5). Recoge D-A y D-B, decididas por el usuario; D-C y D-G figuran como propuesta vigente pendiente de confirmar. No está aprobada.
- 2026-10-04 — Aprobada por el usuario el 2026-10-04 con su mensaje «aprobdo lo del s5»; alcance S5 (EP-010 y HU-034, D-C, D-D, D-I); HU-035/036 pendientes de confirmación. Alcance interpretado por el agente principal; D-C (ventana 24 h cada hora) queda vigente por esa aprobación. CA-11 y T-05 siguen opcionales (D-G, de S6, sin confirmar).

## Notas y decisiones

- **D-A (decidida):** clave dedicada de solo lectura, cabecera `X-Automation-Key`, cadena propia. **D-B (decidida):** el túnel temporal que permite a n8n llegar a la API no forma parte de esta HU (ver [[HU-036-versionar-y-documentar-los-flujos-n8n]]); HTTPS y clave son obligatorios mientras esté abierto.
- **D-C (aprobada el 2026-10-04):** ventana 24 h revisada cada hora; `hours` ausente = 24, y el endpoint acota `hours` entre 1 y 72.
- **D-G (propuesta, pendiente):** CA-11 y T-05 son opcionales y quedan **fuera de entrega de S5**; solo se abordarían si WF-001 y WF-002 están cerrados y el usuario confirma D-G.
- **Aceptadas en la verificación (2026-10-04):** comparación SHA-256 + `MessageDigest.isEqual` (no filtra longitud); 401 sin `WWW-Authenticate` (`commenceApiKey`).
- El correo del paciente sí viaja en `upcoming`: es el mínimo para enviar el recordatorio. Se deja anotado como dato personal sintético (`@ejemplo.test`) y como riesgo residual en [[HU-036-versionar-y-documentar-los-flujos-n8n]].
- Pregunta abierta: ¿se exige `GET /api/automation/ping` como comprobación de la clave? El plan lo menciona en su criterio de salida de la Fase 0 pero no lo incluye en el contrato; no se inventó un criterio.
- Pregunta abierta: formato de error de `hours` no numérico; se aplica el `ProblemDetail` estándar de la API (INC-040).
- Precondición: S4 cerrada o autorización expresa del usuario para abrir S5.
