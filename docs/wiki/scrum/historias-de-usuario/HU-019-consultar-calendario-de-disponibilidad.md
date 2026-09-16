---
id: HU-019
tipo: historia-de-usuario
titulo: "Consultar el calendario de disponibilidad propio"
estado: Borrador
epica: "[[EP-005-agenda-del-profesional]]"
requisitos: [RF-08]
esfuerzo: "Bajo"
sprint_sugerido: "Sprint 4"
dependencias:
  - "[[HU-017-crear-bloques-de-disponibilidad-con-slots]]"
relacionadas:
  - "[[HU-020-consultar-agenda-de-citas-aprobadas]]"
---

# HU-019 — Consultar el calendario de disponibilidad propio

## Historia de usuario

**COMO** PROFESSIONAL  
**QUIERO** ver mi calendario de bloques publicados  
**PARA** comprobar mi disponibilidad antes de modificarla

> Como PROFESSIONAL, quiero ver mi calendario de bloques publicados para comprobar mi disponibilidad antes de modificarla.

## Contexto y descripción

RF-08 incluye entre las capacidades del profesional la consulta de su calendario. Es la vista que cierra el ciclo de gestión de la agenda: el profesional publica bloques en [[HU-017-crear-bloques-de-disponibilidad-con-slots]], los ajusta en [[HU-018-editar-y-eliminar-bloques-futuros]] y necesita ver el estado resultante antes de volver a intervenir.

El calendario no muestra solo las franjas publicadas: debe distinguir qué slots siguen libres y cuáles ya están ocupados o retenidos, porque esa diferencia es precisamente la que determina si un bloque puede editarse o eliminarse (RF-08) y la que el profesional necesita para decidir dónde añadir disponibilidad. Es una capacidad de solo lectura sobre datos propios y no altera el estado del sistema.

## Alcance

- Consulta del calendario de bloques del profesional autenticado (RF-08).
- Presentación de cada bloque con su fecha, su franja horaria y su sede.
- Distinción visible entre slots libres y slots ocupados o retenidos dentro de cada bloque.
- Filtro por rango de fechas sobre la consulta.
- Endpoint REST de consulta de calendario propio, restringido al rol `PROFESSIONAL` y a sus propios bloques.
- Pantalla de calendario en `citas-web` para el rol `PROFESSIONAL`.

## Fuera de alcance

- Creación, edición y eliminación de bloques, cubiertas en [[HU-017-crear-bloques-de-disponibilidad-con-slots]] y [[HU-018-editar-y-eliminar-bloques-futuros]].
- Consulta de citas aprobadas y de los datos del paciente, que se cubre en [[HU-020-consultar-agenda-de-citas-aprobadas]].
- Búsqueda de disponibilidad por parte del paciente, que se cubre en [[HU-022-buscar-disponibilidad-con-filtros]].
- Vista del calendario de un profesional por parte de ADMIN: no está en el PRD.
- Exportación del calendario a formatos externos: no está en el PRD.

## Reglas de negocio

- El profesional consulta únicamente su propio calendario; la autorización es por rol y ownership (PRD §8).
- El calendario refleja la sede de cada bloque, ya que la sede se selecciona por bloque (RF-08).
- El estado del slot distingue disponible de ocupado o retenido, en coherencia con RN-01.
- El calendario no expone datos de usuarios ajenos a las propias citas del profesional (RF-16).

## Dependencias y relaciones

- Épica: [[EP-005-agenda-del-profesional]]
- Dependencias: [[HU-017-crear-bloques-de-disponibilidad-con-slots]]
- Relacionadas: [[HU-018-editar-y-eliminar-bloques-futuros]], [[HU-020-consultar-agenda-de-citas-aprobadas]], [[HU-005-autorizar-peticiones-por-rol-y-ownership]]

## Esfuerzo

**Nivel:** Bajo

**Justificación de dificultad:** Es una consulta de solo lectura sobre un esquema ya creado, sin reglas de escritura ni transacciones. El trabajo se concentra en la proyección de bloques y slots hacia el cliente, el filtro por rango de fechas y la vista de calendario del frontend.

## Tareas de desarrollo

- [ ] **T-01 — Definir la consulta de calendario en la capa de aplicación**  
  Dificultad: Bajo  
  Descripción: Caso de uso de consulta que resuelve el profesional desde el contexto de autenticación, acepta un rango de fechas opcional y devuelve los bloques con su sede, su franja y el estado de cada slot.

- [ ] **T-02 — Implementar el adaptador de persistencia de la consulta**  
  Dificultad: Bajo  
  Descripción: Consulta sobre las tablas de bloques y slots filtrada por profesional y rango de fechas, que evita la carga perezosa por slot y aprovecha los índices creados en la migración de agenda.

- [ ] **T-03 — Exponer el adaptador REST de calendario propio**  
  Dificultad: Bajo  
  Descripción: Endpoint de consulta restringido al rol `PROFESSIONAL`, con parámetros de rango de fechas validados, que responde siempre con los bloques del usuario autenticado y nunca con los de un identificador recibido por parámetro.

- [ ] **T-04 — Construir la pantalla de calendario en citas-web**  
  Dificultad: Medio  
  Descripción: Vista React + TypeScript que presenta los bloques del profesional agrupados por fecha, muestra la sede y la franja de cada uno, diferencia visualmente los slots libres de los ocupados o retenidos y permite acotar el rango de fechas mostrado.

- [ ] **T-05 — Pruebas de la consulta de calendario**  
  Dificultad: Bajo  
  Descripción: Pruebas de integración REST para la consulta con y sin filtro de fechas, para la ausencia de bloques ajenos en la respuesta y para la correcta diferenciación entre slots libres y ocupados.

## Criterios de aceptación

### CA-01 — El calendario muestra los bloques propios con fecha, franja y sede

**Dado** un profesional autenticado con dos bloques publicados en una misma fecha futura, 08:00–12:00 en HIC y 14:00–17:00 en HIC  
**Cuando** consulta su calendario  
**Entonces** la respuesta incluye los dos bloques y, para cada uno, su fecha, su hora de inicio, su hora de fin y la sede a la que pertenece.

### CA-02 — El calendario distingue slots libres de ocupados o retenidos

**Dado** un bloque propio de 8 slots en el que uno está ocupado por una cita aprobada y otro está retenido por una solicitud pendiente  
**Cuando** el profesional consulta su calendario  
**Entonces** la respuesta marca esos dos slots como no disponibles y los seis restantes como disponibles, y la vista de `citas-web` los diferencia visualmente.

### CA-03 — Un profesional no ve el calendario de otro

**Dado** el profesional A autenticado y el profesional B con bloques publicados  
**Cuando** A consulta el calendario, incluso indicando explícitamente el identificador de B en la petición  
**Entonces** la respuesta contiene únicamente bloques de A y ningún bloque de B, o la API responde con un error de autorización.

### CA-04 — El filtro por rango de fechas acota el resultado

**Dado** un profesional con bloques publicados en tres fechas distintas  
**Cuando** consulta su calendario indicando un rango que cubre solo la segunda de esas fechas  
**Entonces** la respuesta contiene exclusivamente los bloques de esa fecha y ninguno de las otras dos.

### CA-05 — Calendario sin bloques publicados

**Dado** un profesional autenticado que no ha publicado ningún bloque en el rango consultado  
**Cuando** consulta su calendario  
**Entonces** la API responde con éxito y una colección vacía, y la pantalla de `citas-web` muestra el estado sin bloques en lugar de un error.

## Definition of Done

- [ ] Los criterios CA-01 a CA-05 están validados con evidencia concreta.
- [ ] El endpoint de calendario exige rol `PROFESSIONAL` y resuelve el titular desde el contexto de autenticación, ignorando cualquier identificador recibido por parámetro.
- [ ] La respuesta incluye el estado de cada slot y no expone datos de pacientes ni de citas ajenas.
- [ ] El filtro por rango de fechas se valida en el servidor y se aplica en la consulta a la base de datos, no en memoria sobre el conjunto completo.
- [ ] La consulta no genera una carga por slot: se resuelve con un número de consultas acotado e independiente del número de slots.
- [ ] La pantalla de calendario de `citas-web` consume la API mediante la URL del backend leída de la configuración de entorno.
- [ ] Existen pruebas automatizadas de la consulta con filtro de fechas y del aislamiento entre profesionales, y pasan.
- [ ] El contrato del endpoint de calendario está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [ ] La trazabilidad de esta HU y de [[EP-005-agenda-del-profesional]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Pendiente | — | — |
| CA-02 | Pendiente | — | — |
| CA-03 | Pendiente | — | — |
| CA-04 | Pendiente | — | — |
| CA-05 | Pendiente | — | — |
| DoD | Pendiente | — | — |

## Historial de validación

- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- Incógnita abierta **INC-017** (ver [[EP-005-agenda-del-profesional]]): sin zona horaria de referencia definida, el filtro por rango de fechas de CA-04 queda expresado sobre la fecha del bloque, sin fijar el desplazamiento aplicado.
- El PRD no define si el calendario debe mostrar también los bloques pasados o solo los futuros. Esta HU no restringe la consulta al futuro y deja el recorte al filtro de fechas; debe confirmarse con el usuario del proyecto.
- El PRD no define la granularidad de la vista (día, semana o mes) para el calendario de disponibilidad, a diferencia de RF-16 que sí la fija para la agenda de citas. La elección queda a decisión del desarrollador.
