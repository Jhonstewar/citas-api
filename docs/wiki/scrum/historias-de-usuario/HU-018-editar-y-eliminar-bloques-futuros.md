---
id: HU-018
tipo: historia-de-usuario
titulo: "Editar y eliminar bloques futuros"
estado: En desarrollo
epica: "[[EP-005-agenda-del-profesional]]"
requisitos: [RF-08]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 4"
dependencias:
  - "[[HU-017-crear-bloques-de-disponibilidad-con-slots]]"
relacionadas:
  - "[[HU-023-agendar-cita-de-medicina-general]]"
---

# HU-018 — Editar y eliminar bloques futuros

## Historia de usuario

**COMO** PROFESSIONAL  
**QUIERO** editar o eliminar mis bloques futuros que aún no tienen citas comprometidas  
**PARA** ajustar mi disponibilidad sin afectar a pacientes que ya reservaron

> Como PROFESSIONAL, quiero editar o eliminar mis bloques futuros que aún no tienen citas comprometidas para ajustar mi disponibilidad sin afectar a pacientes que ya reservaron.

## Contexto y descripción

RF-08 permite al profesional editar y eliminar bloques, pero acota la capacidad con dos condiciones: el bloque debe ser futuro y no debe tener citas comprometidas. La segunda condición protege al paciente que ya reservó, porque los slots de un bloque son la unidad sobre la que se crean las citas y su retención es la base de RN-01.

La edición de un bloque no es una simple actualización de campos: cambiar la franja obliga a recalcular la expansión en slots de 30 minutos establecida en [[HU-017-crear-bloques-de-disponibilidad-con-slots]], de modo que los slots que dejan de pertenecer al bloque desaparecen de la oferta y los que se añaden entran en ella. Al editar siguen vigentes las mismas reglas que al crear: franja no pasada, sin solapamiento con otros bloques del profesional y sede asignada.

## Alcance

- Edición de la franja horaria y de la sede de un bloque futuro propio sin citas comprometidas (RF-08).
- Eliminación de un bloque futuro propio sin citas comprometidas (RF-08).
- Recálculo de los slots del bloque tras la edición, con retirada de los slots que dejan de existir.
- Verificación de que el bloque no tiene ninguna cita comprometida antes de permitir la operación.
- Revalidación de franja no pasada, no solapamiento y sede asignada en cada edición.
- Endpoints REST de actualización y de eliminación de bloque, restringidos al profesional titular.
- Acciones de editar y eliminar en la pantalla de gestión de bloques de `citas-web`.

## Fuera de alcance

- Creación del bloque y su expansión inicial, que se cubre en [[HU-017-crear-bloques-de-disponibilidad-con-slots]].
- Cancelación o reubicación de las citas ya comprometidas en un bloque: el PRD no permite al profesional operar sobre citas de pacientes.
- Reprogramación de citas, que pertenece a [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]].
- Edición de bloques de otro profesional o por parte de ADMIN: no está en el PRD.
- Edición parcial de un bloque con slots ocupados: pendiente de la incógnita INC-019.

## Reglas de negocio

- Solo se editan o eliminan bloques futuros (RF-08, RN-06).
- Un bloque con al menos una cita comprometida no se edita ni se elimina (RF-08).
- Tras editar la franja, el bloque se vuelve a discretizar en slots de 30 minutos (RF-08).
- La edición no puede producir solapamiento con otro bloque del mismo profesional (RF-08).
- La sede resultante de la edición debe pertenecer a las sedes asignadas al profesional (RN-07).
- El profesional solo opera sobre sus propios bloques; la autorización es por rol y ownership (PRD §8).

## Dependencias y relaciones

- Épica: [[EP-005-agenda-del-profesional]]
- Dependencias: [[HU-017-crear-bloques-de-disponibilidad-con-slots]]
- Relacionadas: [[HU-019-consultar-calendario-de-disponibilidad]], [[HU-022-buscar-disponibilidad-con-filtros]], [[HU-023-agendar-cita-de-medicina-general]], [[HU-005-autorizar-peticiones-por-rol-y-ownership]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** Reutiliza el modelo, las validaciones y la expansión en slots ya construidos en HU-017, por lo que no introduce esquema nuevo. La dificultad está en el recálculo de slots sin dejar huérfanos y en la comprobación de citas comprometidas, que cruza la agenda con el dominio de citas y debe resolverse de forma transaccional.

## Tareas de desarrollo

- [ ] **T-01 — Definir la regla de bloque modificable en el dominio**  
  Dificultad: Medio  
  Descripción: Regla de dominio que determina si un bloque admite edición o eliminación, combinando la condición de bloque futuro con la ausencia de citas comprometidas sobre cualquiera de sus slots.

- [ ] **T-02 — Implementar el recálculo de slots tras la edición**  
  Dificultad: Medio  
  Descripción: Lógica de dominio que, al cambiar la franja del bloque, produce el nuevo conjunto de slots de 30 minutos, determina cuáles se conservan y cuáles se retiran, y garantiza que ningún slot quede asociado a una franja que el bloque ya no cubre.

- [ ] **T-03 — Implementar los casos de uso de edición y eliminación de bloque**  
  Dificultad: Medio  
  Descripción: Casos de uso que resuelven el bloque, verifican la titularidad contra el profesional autenticado, aplican las reglas de bloque modificable, revalidan franja, solapamiento y sede, y persisten el resultado en una sola transacción.

- [ ] **T-04 — Exponer los adaptadores REST de actualización y eliminación**  
  Dificultad: Bajo  
  Descripción: Endpoints de actualización y de eliminación de bloque restringidos al rol `PROFESSIONAL`, con DTO validado y errores diferenciados para bloque con citas, bloque pasado, solapamiento, sede no asignada y bloque ajeno.

- [ ] **T-05 — Añadir edición y eliminación a la pantalla de bloques en citas-web**  
  Dificultad: Medio  
  Descripción: Acciones de editar y eliminar en la vista de gestión de bloques, con confirmación previa a la eliminación, presentación de los errores devueltos por la API y refresco de los slots mostrados tras la edición.

- [ ] **T-06 — Pruebas de edición y eliminación de bloques**  
  Dificultad: Medio  
  Descripción: Pruebas de dominio y de integración para la edición correcta con recálculo de slots, el rechazo por cita comprometida, el rechazo por bloque pasado, el rechazo por solapamiento y sede no asignada, y el intento sobre un bloque ajeno.

## Criterios de aceptación

### CA-01 — Edición y eliminación de un bloque futuro sin citas

**Dado** un bloque propio de fecha futura cuyos slots están todos libres  
**Cuando** el profesional modifica su franja horaria y, en otra operación, elimina un segundo bloque en las mismas condiciones  
**Entonces** la API acepta ambas operaciones, el primer bloque queda persistido con la nueva franja y el segundo deja de aparecer en el calendario del profesional.

### CA-02 — Bloque con cita comprometida no se edita ni se elimina

**Dado** un bloque futuro propio con al menos un slot ocupado por una cita comprometida  
**Cuando** el profesional intenta editar su franja y, en otra operación, intenta eliminarlo  
**Entonces** la API rechaza las dos peticiones con un error que indica la existencia de citas comprometidas, el bloque conserva su franja y sus slots, y la cita existente no se altera.

### CA-03 — Bloque pasado no se modifica

**Dado** un bloque propio cuya franja ya transcurrió  
**Cuando** el profesional intenta editarlo o eliminarlo  
**Entonces** la API responde con un error que identifica el bloque como pasado y no se produce ningún cambio en el bloque ni en sus slots (RN-06).

### CA-04 — Los slots se recalculan tras la edición

**Dado** un bloque futuro 08:00–12:00 con sus 8 slots libres  
**Cuando** el profesional lo edita a la franja 08:00–10:00  
**Entonces** el bloque queda con exactamente 4 slots con inicios en 08:00, 08:30, 09:00 y 09:30, los slots de 10:00 en adelante dejan de existir y una búsqueda de disponibilidad sobre esa fecha ya no los ofrece.

### CA-05 — La edición mantiene las reglas de solapamiento y de sede

**Dado** un profesional asignado solo a HIC con los bloques 08:00–12:00 y 14:00–17:00 en una misma fecha futura  
**Cuando** intenta editar el primero para que termine a las 15:00 y, en otra operación, intenta cambiar su sede a ICV  
**Entonces** la API rechaza la primera petición por solapamiento con el segundo bloque y la segunda por sede no asignada al profesional, y en ningún caso se persiste el cambio.

### CA-06 — Un profesional no opera bloques de otro

**Dado** un bloque futuro sin citas perteneciente al profesional B  
**Cuando** el profesional A autenticado intenta editarlo y después eliminarlo  
**Entonces** la API responde en ambos casos con un error de autorización y el bloque de B permanece sin cambios.

## Definition of Done

- [ ] Los criterios CA-01 a CA-06 están validados con evidencia concreta.
- [ ] La regla de bloque modificable está implementada en el dominio y considera tanto la condición de bloque futuro como la ausencia de citas comprometidas.
- [ ] La edición y el recálculo de slots ocurren en una única transacción: no queda ningún slot asociado a una franja que el bloque ya no cubre.
- [ ] La eliminación no deja slots huérfanos en la base de datos.
- [ ] Los endpoints de actualización y eliminación exigen rol `PROFESSIONAL` y verifican la titularidad del bloque contra el usuario autenticado.
- [ ] Los errores de bloque con citas, bloque pasado, solapamiento y sede no asignada son distinguibles entre sí en la respuesta de la API.
- [ ] La pantalla de gestión de bloques de `citas-web` refleja los slots recalculados tras la edición sin necesidad de recargar manualmente la aplicación.
- [ ] Existen pruebas automatizadas del recálculo de slots, del rechazo por cita comprometida y del intento sobre un bloque ajeno, y pasan.
- [ ] El contrato de los endpoints de actualización y eliminación está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [ ] La trazabilidad de esta HU y de [[EP-005-agenda-del-profesional]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Pendiente | — | — |
| CA-02 | Pendiente | — | — |
| CA-03 | Pendiente | — | — |
| CA-04 | Pendiente | — | — |
| CA-05 | Pendiente | — | — |
| CA-06 | Pendiente | — | — |
| DoD | Pendiente | — | — |

## Historial de validación

- 2026-09-18 — Estado `En desarrollo` (skill `scrum-spec-orchestrator`, paso 9): se inicia la implementación en la fase F4 de `PLAN_RETOMA_S3.md`.
- 2026-09-18 — Estado `Aprobada` por **aprobación delegada** de S3: el usuario pidió continuar S3 dejando las decisiones de diseño a criterio del agente (`AGENTS.md` §6). Alcance y decisiones D5–D13 en `PLAN_RETOMA_S3.md` y [[dec-004-decisiones-s3-reserva]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- Incógnita abierta **INC-019** (ver [[EP-005-agenda-del-profesional]]): el PRD no define si un bloque parcialmente ocupado puede editarse en su parte libre, o si la restricción de RF-08 aplica al bloque completo. CA-02 se redacta sobre la interpretación restrictiva (el bloque completo queda bloqueado); si la decisión humana cambia, este CA y la regla de T-01 deben revisarse.
- Incógnita abierta **INC-017** (ver [[EP-005-agenda-del-profesional]]): la zona horaria de referencia no está definida, lo que afecta a la determinación de "bloque pasado" en CA-03.
- El PRD no precisa qué estados de cita cuentan como "cita comprometida". La definición operativa debe confirmarse con el usuario del proyecto, ya que determina si un slot retenido por una solicitud `REQUESTED` bloquea la edición.
