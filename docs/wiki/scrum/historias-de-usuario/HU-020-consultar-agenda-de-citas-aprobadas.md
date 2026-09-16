---
id: HU-020
tipo: historia-de-usuario
titulo: "Consultar la agenda de citas aprobadas"
estado: Borrador
epica: "[[EP-005-agenda-del-profesional]]"
requisitos: [RF-16]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 8"
dependencias:
  - "[[HU-023-agendar-cita-de-medicina-general]]"
  - "[[HU-030-aprobar-o-rechazar-cita-especializada]]"
relacionadas:
  - "[[HU-021-registrar-cierre-de-atencion]]"
  - "[[HU-005-autorizar-peticiones-por-rol-y-ownership]]"
---

# HU-020 — Consultar la agenda de citas aprobadas

## Historia de usuario

**COMO** PROFESSIONAL  
**QUIERO** consultar mis citas aprobadas por día o semana y por sede  
**PARA** prepararme para la atención del período

> Como PROFESSIONAL, quiero consultar mis citas aprobadas por día o semana y por sede para prepararme para la atención del período.

## Contexto y descripción

RF-16 define la agenda visible al profesional: sus citas en estado `APPROVED`, consultables por día o semana y por sede. Es una vista distinta del calendario de disponibilidad de [[HU-019-consultar-calendario-de-disponibilidad]]: aquella muestra la oferta publicada, esta muestra la demanda confirmada.

RF-16 añade una restricción explícita de privacidad: el profesional no puede ver datos de usuarios fuera de sus propias citas. Esto convierte la HU en un caso concreto de la autorización por rol y ownership exigida por PRD §8 y desarrollada en [[HU-005-autorizar-peticiones-por-rol-y-ownership]].

La agenda solo tiene contenido cuando existen citas aprobadas, que llegan por dos caminos: la aprobación automática de la cita general ([[HU-023-agendar-cita-de-medicina-general]]) y la decisión administrativa sobre la cita especializada ([[HU-030-aprobar-o-rechazar-cita-especializada]]). Por eso se sitúa después de ambas.

## Alcance

- Consulta de las citas en estado `APPROVED` del profesional autenticado (RF-16).
- Filtro por día y filtro por semana sobre la agenda (RF-16).
- Filtro por sede sobre la agenda (RF-16).
- Presentación de cada cita con su fecha y hora, duración, sede, especialidad y los datos del paciente estrictamente necesarios para la atención.
- Endpoint REST de agenda del profesional, restringido al rol `PROFESSIONAL` y a sus propias citas.
- Pantalla de agenda del profesional en `citas-web`.

## Fuera de alcance

- Cierre de la atención de la cita, que se cubre en [[HU-021-registrar-cierre-de-atencion]].
- Aprobación o rechazo de citas: el PRD lo asigna a ADMIN (PRD §2, RF-12).
- Consulta de citas en estados distintos de `APPROVED`, que no forma parte de RF-16.
- Creación, cancelación o reprogramación de citas por el profesional: no está en el PRD.
- Acceso del profesional a datos de usuarios con los que no tiene una cita (RF-16).

## Reglas de negocio

- La agenda lista únicamente citas en estado `APPROVED` (RF-16).
- La agenda lista únicamente citas del profesional autenticado (RF-16).
- El profesional no puede ver datos de usuarios fuera de sus propias citas (RF-16).
- Los filtros disponibles son día, semana y sede (RF-16).
- La autorización se aplica por rol y ownership, no solo por ocultación en la interfaz (PRD §8).

## Dependencias y relaciones

- Épica: [[EP-005-agenda-del-profesional]]
- Dependencias: [[HU-023-agendar-cita-de-medicina-general]], [[HU-030-aprobar-o-rechazar-cita-especializada]]
- Relacionadas: [[HU-019-consultar-calendario-de-disponibilidad]], [[HU-021-registrar-cierre-de-atencion]], [[HU-005-autorizar-peticiones-por-rol-y-ownership]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** Es una consulta de solo lectura, pero cruza cita, paciente, especialidad y sede, y la restricción de privacidad de RF-16 obliga a decidir y justificar qué campos del paciente se exponen. Los tres filtros de RF-16 deben resolverse en la consulta a la base de datos y probarse de forma combinada.

## Tareas de desarrollo

- [ ] **T-01 — Definir la proyección de agenda en la capa de aplicación**  
  Dificultad: Medio  
  Descripción: Caso de uso de consulta que resuelve el profesional desde el contexto de autenticación, acepta los filtros de día, semana y sede, y devuelve una proyección de cita con únicamente los campos que RF-16 permite exponer al profesional.

- [ ] **T-02 — Implementar el adaptador de persistencia de la agenda**  
  Dificultad: Medio  
  Descripción: Consulta sobre citas filtrada por profesional, estado `APPROVED`, rango temporal y sede, resolviendo especialidad y datos mínimos del paciente sin cargar el agregado completo de usuario.

- [ ] **T-03 — Exponer el adaptador REST de agenda del profesional**  
  Dificultad: Bajo  
  Descripción: Endpoint restringido al rol `PROFESSIONAL`, con parámetros de día, semana y sede validados, que devuelve siempre las citas del usuario autenticado y rechaza cualquier intento de consultar por otro profesional.

- [ ] **T-04 — Construir la pantalla de agenda del profesional en citas-web**  
  Dificultad: Medio  
  Descripción: Vista React + TypeScript con conmutador entre día y semana, selector de sede, listado ordenado por hora de inicio y detalle de cada cita limitado a los campos devueltos por la API.

- [ ] **T-05 — Pruebas de la agenda y de su aislamiento**  
  Dificultad: Medio  
  Descripción: Pruebas de integración REST para el filtrado por día, por semana y por sede, para la exclusión de citas en estados distintos de `APPROVED`, para la ausencia de citas de otros profesionales y para la ausencia de campos de paciente no autorizados en la respuesta.

## Criterios de aceptación

### CA-01 — La agenda lista solo citas APPROVED del profesional autenticado

**Dado** un profesional con citas propias en estados `APPROVED`, `REQUESTED`, `REJECTED` y `CANCELLED` en la misma fecha  
**Cuando** consulta su agenda para esa fecha  
**Entonces** la respuesta contiene únicamente las citas en estado `APPROVED` y ninguna de los otros tres estados.

### CA-02 — Filtro por día

**Dado** un profesional con citas aprobadas en tres fechas distintas de la misma semana  
**Cuando** consulta su agenda filtrando por una de esas fechas  
**Entonces** la respuesta contiene exclusivamente las citas aprobadas de esa fecha, ordenadas por hora de inicio.

### CA-03 — Filtro por semana

**Dado** un profesional con citas aprobadas dentro de una semana y otras citas aprobadas en la semana siguiente  
**Cuando** consulta su agenda filtrando por la primera semana  
**Entonces** la respuesta contiene todas las citas aprobadas de esa semana y ninguna de la semana siguiente.

### CA-04 — Filtro por sede

**Dado** un profesional habilitado en HIC y en ICV con citas aprobadas en ambas sedes el mismo día  
**Cuando** consulta su agenda de ese día filtrando por la sede HIC  
**Entonces** la respuesta contiene solo las citas cuya sede es HIC y ninguna de ICV.

### CA-05 — Un profesional no accede a citas de otro

**Dado** el profesional A autenticado y el profesional B con citas aprobadas  
**Cuando** A consulta la agenda indicando el identificador de B en la petición  
**Entonces** la API responde con un error de autorización o devuelve exclusivamente las citas de A, y en ningún caso aparece una cita de B.

### CA-06 — No se exponen datos de usuarios fuera de las propias citas

**Dado** un profesional autenticado consultando su agenda  
**Cuando** se inspecciona el cuerpo completo de la respuesta  
**Entonces** solo aparecen datos de los pacientes que tienen una cita aprobada con ese profesional, y para cada uno solo los campos necesarios para la atención, sin contraseñas, hashes, ni datos de afiliación o de contacto no requeridos por RF-16.

### CA-07 — Agenda vacía en un período sin citas

**Dado** un profesional autenticado y una fecha en la que no tiene ninguna cita aprobada  
**Cuando** consulta su agenda para esa fecha  
**Entonces** la API responde con éxito y una colección vacía, y la pantalla muestra el estado sin citas en lugar de un error.

## Definition of Done

- [ ] Los criterios CA-01 a CA-07 están validados con evidencia concreta.
- [ ] El endpoint de agenda exige rol `PROFESSIONAL` y resuelve el titular desde el contexto de autenticación, no desde un parámetro de la petición.
- [ ] El filtro de estado `APPROVED` se aplica en la consulta a la base de datos y no como filtrado posterior en el cliente.
- [ ] Los filtros de día, semana y sede están implementados en el servidor y son combinables entre sí.
- [ ] La lista de campos de paciente expuestos está decidida explícitamente y documentada, y no se serializa la entidad de usuario completa.
- [ ] La pantalla de agenda de `citas-web` consume la API mediante la URL del backend leída de la configuración de entorno.
- [ ] Existen pruebas automatizadas del filtrado por estado, de los tres filtros de RF-16 y del aislamiento entre profesionales, y pasan.
- [ ] El contrato del endpoint de agenda está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
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
| CA-07 | Pendiente | — | — |
| DoD | Pendiente | — | — |

## Historial de validación

- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- Incógnita abierta **INC-017** (ver [[EP-005-agenda-del-profesional]]): sin zona horaria de referencia definida, los filtros por día y por semana de CA-02 y CA-03 quedan expresados sobre la fecha de la cita, sin fijar el desplazamiento aplicado.
- RF-16 prohíbe exponer datos de usuarios fuera de las propias citas, pero no enumera qué campos del paciente sí puede ver el profesional dentro de sus citas. El conjunto exacto es una decisión humana pendiente; CA-06 se redacta sobre el principio de mínimo necesario y no sobre una lista inventada.
- El PRD no define el día de inicio de la semana para el filtro semanal de RF-16. Debe confirmarse con el usuario del proyecto antes de implementar.
