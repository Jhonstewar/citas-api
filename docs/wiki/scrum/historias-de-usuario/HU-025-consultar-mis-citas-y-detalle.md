---
id: HU-025
tipo: historia-de-usuario
titulo: "Consultar mis citas y su detalle"
estado: Aprobada
epica: "[[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]"
requisitos: [RF-13]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 5"
dependencias:
  - "[[HU-023-agendar-cita-de-medicina-general]]"
  - "[[HU-024-solicitar-cita-especializada]]"
  - "[[HU-005-autorizar-peticiones-por-rol-y-ownership]]"
relacionadas:
  - "[[HU-026-cancelar-una-cita-futura]]"
---

# HU-025 — Consultar mis citas y su detalle

## Historia de usuario

**COMO** USER autenticado del portal de agendamiento
**QUIERO** ver mis citas y su detalle filtrando por estado y por fecha
**PARA** saber en qué situación está cada una

> Como USER autenticado del portal de agendamiento, quiero ver mis citas y su detalle filtrando por estado y por fecha para saber en qué situación está cada una.

## Contexto y descripción

RF-13 establece que el usuario puede consultar sus citas, filtrarlas por estado y por fecha, y visualizar como mínimo siete datos: sede, profesional, especialidad, fecha y hora, duración, estado y motivo de rechazo cuando exista. Es la pantalla que cierra el ciclo abierto por [[HU-023-agendar-cita-de-medicina-general]] y [[HU-024-solicitar-cita-especializada]]: sin ella el paciente no tiene forma de saber si su solicitud especializada fue aprobada o rechazada, ni de localizar la cita sobre la que quiere actuar.

Es además el punto de entrada de las dos acciones posteriores del paciente: la cancelación de [[HU-026-cancelar-una-cita-futura]] y la solicitud de reprogramación de [[HU-027-solicitar-reprogramacion-de-cita-aprobada]]. Por eso el detalle debe ser suficiente para decidir, y la lectura debe estar acotada por ownership según PRD §8: el usuario autenticado accede a sus propias citas y solo a ellas.

## Alcance

- Endpoint de listado de las citas del usuario autenticado en `citas-api`.
- Filtro por estado de cita, aplicable sobre el listado.
- Filtro por fecha, aplicable sobre el listado.
- Endpoint de detalle de una cita concreta del usuario autenticado.
- Proyección de lectura con los siete datos mínimos de RF-13.
- Aplicación de la regla de ownership sobre listado y detalle.
- Pantalla "Mis citas" y pantalla de detalle en `citas-web` (PRD §6).

## Fuera de alcance

- Cancelación de la cita, que se cubre en [[HU-026-cancelar-una-cita-futura]].
- Solicitud de reprogramación, que se cubre en [[HU-027-solicitar-reprogramacion-de-cita-aprobada]].
- Consulta del historial completo de cambios de estado, que se cubre en [[HU-032-auditar-cambios-de-estado-de-cita]].
- Consulta de la agenda del profesional, que corresponde a [[HU-020-consultar-agenda-de-citas-aprobadas]].
- Exportación o impresión del listado: no está en el PRD.
- Notificación por correo de los cambios de estado: pertenece a la automatización posterior de PRD §10.

## Reglas de negocio

- El usuario solo ve sus propias citas; ninguna respuesta incluye citas de otro usuario (PRD §8, ownership).
- El listado admite filtro por estado y filtro por fecha (RF-13).
- El detalle muestra como mínimo sede, profesional, especialidad, fecha y hora, duración, estado y motivo de rechazo cuando exista (RF-13).
- El motivo de rechazo solo tiene valor cuando la cita fue rechazada por ADMIN (RF-12, RN-04); en cualquier otro estado el campo está vacío.
- La duración mostrada es la definida por la especialidad de la cita, 30 o 60 minutos (RF-09).
- La operación es de solo lectura: no cambia el estado de ninguna cita ni la ocupación de ningún slot.

## Dependencias y relaciones

- Épica: [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]]
- Dependencias: [[HU-023-agendar-cita-de-medicina-general]], [[HU-024-solicitar-cita-especializada]], [[HU-005-autorizar-peticiones-por-rol-y-ownership]]
- Relacionadas: [[HU-026-cancelar-una-cita-futura]], [[HU-027-solicitar-reprogramacion-de-cita-aprobada]], [[HU-030-aprobar-o-rechazar-cita-especializada]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** No introduce ninguna transición de estado ni toca la ocupación de slots, lo que la mantiene lejos de las HU de escritura. La dificultad está en construir una proyección de lectura que cruce cita, sede, profesional, especialidad y duración sin arrastrar el modelo de dominio completo al adaptador, en aplicar los filtros combinables sin duplicar consultas, y en garantizar que el filtro de ownership se aplica en el servidor y no en el cliente.

## Tareas de desarrollo

- [ ] **T-01 — Definir la consulta de citas del paciente en la capa de aplicación**
  Dificultad: Medio
  Descripción: Caso de uso de consulta que recibe el identificador del usuario autenticado y los filtros opcionales de estado y fecha, y devuelve la proyección de lectura de sus citas, sin exponer entidades de persistencia.

- [ ] **T-02 — Definir la consulta de detalle de una cita**
  Dificultad: Medio
  Descripción: Caso de uso que recupera una cita por su identificador, verifica que pertenece al usuario autenticado y devuelve los siete datos mínimos de RF-13, incluido el motivo de rechazo cuando existe.

- [ ] **T-03 — Implementar el adaptador de persistencia de consulta**
  Dificultad: Medio
  Descripción: Repositorio de lectura con Spring Data JPA que resuelve el listado filtrado y el detalle en consultas acotadas, uniendo cita, sede, profesional y especialidad sin consultas en cascada por elemento.

- [ ] **T-04 — Exponer los adaptadores REST de listado y detalle**
  Dificultad: Medio
  Descripción: Controladores autenticados con los filtros de estado y fecha como parámetros opcionales, DTO de respuesta con los campos de RF-13, y respuesta de recurso no encontrado o no autorizado cuando la cita no pertenece al solicitante.

- [ ] **T-05 — Construir las pantallas de mis citas y detalle en citas-web**
  Dificultad: Medio
  Descripción: Vista de listado con controles de filtro por estado y por fecha, vista de detalle con los siete datos, y presentación del motivo de rechazo únicamente cuando la respuesta lo trae, consumiendo la URL del backend leída de la configuración de entorno.

- [ ] **T-06 — Pruebas de consulta y de ownership**
  Dificultad: Medio
  Descripción: Pruebas de aplicación para los filtros y de integración REST para el listado propio, el listado con filtros combinados, el detalle completo, el detalle sin motivo de rechazo y el intento de acceso a la cita de otro usuario.

## Criterios de aceptación

### CA-01 — El listado devuelve solo las citas del usuario autenticado

**Dado** dos usuarios `USER` distintos, cada uno con citas registradas en el sistema
**Cuando** el primero consulta su listado de citas con su access token
**Entonces** la respuesta contiene exclusivamente citas cuyo titular es ese usuario y ninguna cita del segundo usuario, con independencia de los filtros aplicados.

### CA-02 — Filtro por estado

**Dado** un usuario con citas en estados `APPROVED`, `REQUESTED`, `CANCELLED` y `REJECTED`
**Cuando** consulta su listado filtrando por el estado `APPROVED`
**Entonces** la respuesta contiene todas sus citas en `APPROVED` y ninguna cita en otro estado.

### CA-03 — Filtro por fecha

**Dado** un usuario con citas en fechas distintas
**Cuando** consulta su listado filtrando por una fecha concreta
**Entonces** la respuesta contiene únicamente las citas cuya fecha coincide con la solicitada, y al combinar ese filtro con el de estado la respuesta cumple ambas condiciones a la vez.

### CA-04 — El detalle muestra los siete datos mínimos de RF-13

**Dado** una cita perteneciente al usuario autenticado
**Cuando** consulta su detalle
**Entonces** la respuesta incluye sede, profesional, especialidad, fecha y hora, duración en minutos, estado y el campo de motivo de rechazo, y ninguno de los seis primeros campos viene vacío.

### CA-05 — Motivo de rechazo presente cuando la cita fue rechazada

**Dado** una cita del usuario en estado `REJECTED` cuyo rechazo administrativo se registró con un motivo
**Cuando** consulta su detalle
**Entonces** la respuesta incluye el motivo de rechazo con el mismo texto que registró el ADMIN.

### CA-06 — Ausencia de motivo de rechazo sin error

**Dado** una cita del usuario en estado `APPROVED`, que nunca fue rechazada
**Cuando** consulta su detalle
**Entonces** la API responde con éxito, el campo de motivo de rechazo viene vacío o ausente, y la pantalla de detalle de `citas-web` no muestra ese campo ni produce un error de renderizado.

### CA-07 — Lectura de la cita de otro usuario rechazada

**Dado** el identificador de una cita que pertenece a otro usuario
**Cuando** un `USER` autenticado solicita su detalle
**Entonces** la API responde con un error de autorización o de recurso no encontrado, no devuelve ningún dato de esa cita y no revela la existencia de datos del otro paciente.

### CA-08 — Consulta sin autenticación rechazada

**Dado** una petición al listado o al detalle de citas sin cabecera de autorización o con un access token expirado
**Cuando** la API procesa la petición
**Entonces** responde con un código de no autorizado y no devuelve ninguna cita.

## Definition of Done

- [ ] Los criterios CA-01 a CA-08 están validados con evidencia concreta.
- [ ] El filtro de ownership se aplica en la capa de aplicación o de persistencia del backend, y no depende de que el frontend envíe el identificador del usuario.
- [ ] El listado y el detalle son operaciones de solo lectura verificadas: ejecutarlas no produce ningún registro nuevo en el historial de estados de cita.
- [ ] La proyección de detalle expone exactamente los siete campos de RF-13 y ningún dato de otro paciente.
- [ ] El repositorio de lectura resuelve el listado sin emitir una consulta adicional por cada cita devuelta.
- [ ] Las pantallas de "mis citas" y "detalle" de PRD §6 existen en `citas-web` y consumen `citas-api` directamente, con la URL base leída de la configuración de entorno.
- [ ] Existen pruebas automatizadas del listado propio, de cada filtro, del detalle con y sin motivo de rechazo y del acceso denegado a la cita ajena, y pasan.
- [ ] El contrato de los endpoints de listado y detalle está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [ ] La trazabilidad de esta HU y de [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]] está actualizada en `docs/wiki/scrum/`.

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
| DoD | Pendiente | — | — |

## Historial de validación

- 2026-09-18 — Estado `Aprobada` por **aprobación delegada** de S3: el usuario pidió continuar S3 dejando las decisiones de diseño a criterio del agente (`AGENTS.md` §6). Alcance y decisiones D5–D13 en `PLAN_RETOMA_S3.md` y [[dec-004-decisiones-s3-reserva]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- El PRD no define el orden de presentación del listado ni si debe paginarse. Esta HU no lo exige; si el volumen de datos sintéticos lo hace necesario, debe confirmarse con el usuario del proyecto antes de implementar.
- RF-13 no distingue entre citas pasadas y futuras en el listado: esta HU asume que ambas se devuelven y que el filtro por fecha es el mecanismo para acotarlas.
- El motivo de rechazo mostrado aquí es el mismo que registra el ADMIN en [[HU-030-aprobar-o-rechazar-cita-especializada]] bajo RN-04; ambas HU deben referirse al mismo dato y no duplicarlo.
