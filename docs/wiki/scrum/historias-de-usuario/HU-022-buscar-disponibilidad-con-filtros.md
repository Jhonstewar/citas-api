---
id: HU-022
tipo: historia-de-usuario
titulo: "Buscar disponibilidad con filtros"
estado: Aprobada
epica: "[[EP-006-busqueda-de-disponibilidad-y-reserva]]"
requisitos: [RF-10, RF-09]
esfuerzo: "Alto"
sprint_sugerido: "Sprint 4"
dependencias:
  - "[[HU-017-crear-bloques-de-disponibilidad-con-slots]]"
  - "[[HU-014-asignar-especialidades-y-especialidad-primaria]]"
  - "[[HU-011-gestionar-especialidades-y-su-duracion]]"
relacionadas:
  - "[[HU-023-agendar-cita-de-medicina-general]]"
  - "[[HU-024-solicitar-cita-especializada]]"
  - "[[HU-016-activar-o-desactivar-profesional]]"
  - "[[HU-026-cancelar-una-cita-futura]]"
  - "[[HU-027-solicitar-reprogramacion-de-cita-aprobada]]"
---

# HU-022 — Buscar disponibilidad con filtros

## Historia de usuario

**COMO** USER autenticado  
**QUIERO** buscar horarios filtrando por sede, tipo de cita, especialidad, profesional y fecha  
**PARA** elegir solo franjas en las que realmente puedo ser atendido durante toda la duración que exige la especialidad

> Como USER autenticado, quiero buscar horarios filtrando por sede, tipo de cita, especialidad, profesional y fecha para elegir solo franjas en las que realmente puedo ser atendido durante toda la duración que exige la especialidad.

## Contexto y descripción

RF-10 define cinco filtros de búsqueda (sede, tipo de cita general o especializada, especialidad, profesional y fecha) y una regla decisiva: solo se muestran horarios que puedan completar toda la duración requerida. Como la duración la fija la especialidad (RF-09), una especialidad de 30 minutos ofrece cualquier slot libre, mientras que una de 60 minutos solo ofrece un inicio cuando ese slot y el inmediatamente siguiente están libres y son consecutivos (RN-05).

La ocupación se lee de `slot_reservations` (V3), libro único cuya PK sobre `slot_id` cubre tanto citas `REQUESTED`/`APPROVED` como retenciones de reprogramación `PENDING`. Un slot presente en esa tabla no está disponible (RN-01). La búsqueda es una lectura: no retiene nada. La garantía contra la doble reserva se aplica al confirmar en [[HU-023-agendar-cita-de-medicina-general]] y [[HU-024-solicitar-cita-especializada]], de modo que un resultado de búsqueda puede quedar obsoleto y eso es un comportamiento esperado.

Aunque pertenece a [[EP-006-busqueda-de-disponibilidad-y-reserva]], se planifica en el Sprint 4 porque cierra el incremento de disponibilidad iniciado por [[HU-017-crear-bloques-de-disponibilidad-con-slots]].

## Alcance

- Endpoint REST de búsqueda de disponibilidad en `citas-api` para usuarios autenticados con rol `USER`.
- Filtros por sede, tipo de cita (`GENERAL` / `SPECIALIZED`), especialidad, profesional y fecha (RF-10).
- Cálculo de franjas ofrecidas según la duración de la especialidad: un slot para 30 minutos, dos slots consecutivos libres para 60 minutos.
- Exclusión de slots reservados o retenidos, slots en el pasado, profesionales inactivos, especialidades inactivas o no asociadas al profesional y sedes no asignadas.
- Resultado con sede, profesional, especialidad, fecha, hora de inicio, hora de fin y duración de cada franja, más los identificadores necesarios para reservar.
- Pantalla "buscar disponibilidad" en `citas-web` (PRD §6).

## Fuera de alcance

- Creación de la cita, que se cubre en [[HU-023-agendar-cita-de-medicina-general]] y [[HU-024-solicitar-cita-especializada]].
- Retención de slots durante la navegación: la búsqueda no bloquea franjas.
- Búsqueda por parte de ADMIN o de PROFESSIONAL: RF-10 la asigna a USER.
- Horizonte máximo de fechas consultables: pendiente de INC-023.
- Recomendación automática de profesional: pendiente de INC-026.

## Reglas de negocio

- Solo se muestran horarios que puedan completar toda la duración requerida (RF-10).
- 30 minutos = 1 slot; 60 minutos = 2 slots consecutivos disponibles (RF-09, RN-05).
- Un slot presente en `slot_reservations`, por cita o por retención de reprogramación, no está disponible (RN-01).
- No se ofrecen franjas en el pasado (RN-06).
- Solo se ofrecen especialidades activas y asociadas al profesional (RN-08).
- Solo se ofrecen slots de bloques en sedes asignadas al profesional (RN-07).
- No se ofrecen profesionales inactivos ([[HU-016-activar-o-desactivar-profesional]]).
- El tipo de cita se deriva de la especialidad y no se elige de forma independiente a ella.

## Dependencias y relaciones

- Épica: [[EP-006-busqueda-de-disponibilidad-y-reserva]]
- Dependencias: [[HU-017-crear-bloques-de-disponibilidad-con-slots]], [[HU-014-asignar-especialidades-y-especialidad-primaria]], [[HU-011-gestionar-especialidades-y-su-duracion]]
- Relacionadas: [[HU-010-consultar-catalogos-fijos-precargados]], [[HU-015-asignar-sedes-al-profesional]], [[HU-016-activar-o-desactivar-profesional]], [[HU-018-editar-y-eliminar-bloques-futuros]], [[HU-019-consultar-calendario-de-disponibilidad]], [[HU-023-agendar-cita-de-medicina-general]], [[HU-024-solicitar-cita-especializada]], [[HU-026-cancelar-una-cita-futura]], [[HU-027-solicitar-reprogramacion-de-cita-aprobada]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Alto

**Justificación de dificultad:** Es la consulta más compleja del producto: cruza bloques, slots, reservas, profesionales, especialidades, sedes y estado activo, y además debe resolver la consecutividad de dos slots libres para las especialidades de 60 minutos, con casos límite reales en el último slot de un bloque y en slots parcialmente ocupados. Su resultado es la base que reutilizan la reserva, la reprogramación y la verificación de liberación de slots.

## Tareas de desarrollo

- [ ] **T-01 — Modelar la regla de franja ofrecible en el dominio**  
  Dificultad: Alto  
  Descripción: Servicio de dominio que, dada una secuencia ordenada de slots de un bloque con su ocupación y la duración de la especialidad, devuelve los inicios ofrecibles: todo slot libre para 30 minutos y todo slot libre seguido de su consecutivo libre para 60 minutos, excluyendo franjas pasadas. Sin dependencias de framework.

- [ ] **T-02 — Implementar la consulta de candidatos en persistencia**  
  Dificultad: Alto  
  Descripción: Adaptador de lectura sobre `availability_blocks`, `availability_slots` y `slot_reservations` que aplica en base de datos los filtros de sede, fecha, profesional activo, especialidad activa y asociada, y sede asignada, con uso de los índices existentes de V3.

- [ ] **T-03 — Implementar el caso de uso de búsqueda**  
  Dificultad: Medio  
  Descripción: Caso de uso que valida los filtros, resuelve la duración desde la especialidad, obtiene candidatos, aplica la regla de franja ofrecible y compone el resultado con los datos de presentación.

- [ ] **T-04 — Exponer el adaptador REST de búsqueda**  
  Dificultad: Medio  
  Descripción: Endpoint autenticado con parámetros de filtro validados, errores diferenciados para filtros incoherentes (por ejemplo especialidad que no corresponde al tipo indicado) y respuesta con las franjas ofrecidas.

- [ ] **T-05 — Construir la pantalla de buscar disponibilidad en citas-web**  
  Dificultad: Medio  
  Descripción: Vista con los cinco filtros alimentados por catálogos y profesionales de la API, listado de franjas con su duración y acción para continuar hacia la reserva.

- [ ] **T-06 — Pruebas de búsqueda**  
  Dificultad: Alto  
  Descripción: Pruebas de dominio de la regla de franja ofrecible (último slot del bloque, slot siguiente ocupado, slot pasado) e integración con datos de agenda y reservas para cada filtro y cada exclusión.

## Criterios de aceptación

### CA-01 — Especialidad de 30 minutos ofrece cada slot libre

**Dado** un bloque futuro 08:00–10:00 en HIC de un profesional activo asociado a una especialidad de 30 minutos, con el slot 08:30 reservado  
**Cuando** un USER busca disponibilidad para esa especialidad, sede y fecha  
**Entonces** el resultado ofrece los inicios 08:00, 09:00 y 09:30 con duración de 30 minutos, y no ofrece 08:30.

### CA-02 — Especialidad de 60 minutos exige dos slots consecutivos libres

**Dado** un bloque futuro 08:00–10:00 en HIC de un profesional activo asociado a una especialidad de 60 minutos, con el slot 09:00 reservado  
**Cuando** un USER busca disponibilidad para esa especialidad, sede y fecha  
**Entonces** el resultado ofrece únicamente el inicio 08:00 con fin 09:00; no ofrece 08:30 (su consecutivo está ocupado), ni 09:30 (no tiene slot siguiente dentro del bloque), ni 09:00 (reservado) (RF-10, RN-05).

### CA-03 — Los slots retenidos no se ofrecen

**Dado** un slot retenido por una cita `REQUESTED` y otro retenido por una solicitud de reprogramación `PENDING`  
**Cuando** un USER busca disponibilidad que incluiría ambos  
**Entonces** ninguno de los dos aparece en el resultado (RN-01).

### CA-04 — No se ofrecen franjas en el pasado

**Dado** un bloque del día actual cuyos primeros slots ya han comenzado respecto del instante de la petición  
**Cuando** un USER busca disponibilidad para ese día  
**Entonces** el resultado solo contiene franjas cuyo inicio es posterior al instante de la petición (RN-06).

### CA-05 — Filtros aplicados

**Dado** oferta publicada en HIC e ICV, por varios profesionales y especialidades de tipo `GENERAL` y `SPECIALIZED`, en varias fechas  
**Cuando** un USER combina los filtros de sede, tipo de cita, especialidad, profesional y fecha  
**Entonces** cada franja del resultado cumple simultáneamente todos los filtros enviados, y omitir un filtro amplía el resultado sin incluir franjas que incumplan los filtros restantes.

### CA-06 — Solo especialidades activas y asociadas, y profesionales activos

**Dado** un profesional inactivo con slots libres, un profesional activo no asociado a la especialidad buscada y una especialidad desactivada  
**Cuando** un USER busca disponibilidad que los incluiría  
**Entonces** no aparecen franjas del profesional inactivo, ni del no asociado para esa especialidad, ni de la especialidad desactivada (RN-08).

### CA-07 — Cada franja informa su duración completa

**Dado** un resultado de búsqueda no vacío  
**Cuando** se inspecciona cada franja  
**Entonces** incluye sede, profesional, especialidad, fecha, hora de inicio, hora de fin y duración, y la duración coincide con la de la especialidad (RF-09).

### CA-08 — La búsqueda no retiene franjas

**Dado** dos usuarios que obtienen la misma franja en sus resultados  
**Cuando** ninguno de ellos confirma una reserva  
**Entonces** no se crea ninguna fila en `slot_reservations` y la franja sigue disponible para ambos.

### CA-09 — Acceso restringido

**Dado** una petición sin autenticar  
**Cuando** invoca la búsqueda de disponibilidad  
**Entonces** la API responde con el error de autenticación definido en [[HU-005-autorizar-peticiones-por-rol-y-ownership]] y no devuelve franjas.

## Definition of Done

- [ ] Los criterios CA-01 a CA-09 están validados con evidencia concreta.
- [ ] La regla de franja ofrecible, incluida la consecutividad de 60 minutos, está implementada en el dominio sin dependencias de Spring ni de JPA y es reutilizable por la reserva y la reprogramación.
- [ ] La ocupación se determina exclusivamente a partir de `slot_reservations`, sin un estado de slot paralelo que pueda desincronizarse.
- [ ] La búsqueda es de solo lectura y no escribe en ninguna tabla.
- [ ] No se crean migraciones salvo índices adicionales justificados, que irían en una migración Flyway posterior a V4.
- [ ] La pantalla de `citas-web` consume la API mediante la URL leída de la configuración de entorno y no calcula disponibilidad en el cliente.
- [ ] Existen pruebas automatizadas de dominio para la regla de 30 y 60 minutos con sus casos límite, y de integración para cada filtro y cada exclusión, y pasan.
- [ ] El contrato del endpoint de búsqueda está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [ ] La trazabilidad de esta HU y de [[EP-006-busqueda-de-disponibilidad-y-reserva]] está actualizada en `docs/wiki/scrum/`.

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
| DoD | Pendiente | — | — |

## Historial de validación

- 2026-09-18 — Estado `Aprobada` por **aprobación delegada** de S3: el usuario pidió continuar S3 dejando las decisiones de diseño a criterio del agente (`AGENTS.md` §6). Alcance y decisiones D5–D13 en `PLAN_RETOMA_S3.md` y [[dec-004-decisiones-s3-reserva]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- Incógnita abierta **INC-017** (ver [[EP-005-agenda-del-profesional]]): la zona horaria de referencia condiciona CA-04; se expresa como comparación con el instante de la petición.
- Incógnita abierta **INC-020** (ver [[EP-005-agenda-del-profesional]]): los bloques no están asociados a especialidad, de modo que un slot libre se ofrece para cualquier especialidad activa asociada al profesional. Si se decide lo contrario, cambian CA-05 y CA-06.
- Incógnita abierta **INC-022** y **INC-023** (ver [[EP-006-busqueda-de-disponibilidad-y-reserva]]): no hay antelación mínima ni horizonte máximo definidos; la búsqueda solo excluye el pasado.
- Incógnita abierta **INC-026** (ver [[EP-006-busqueda-de-disponibilidad-y-reserva]]): no hay asignación automática de profesional; el filtro de profesional es opcional y el usuario elige la franja de un profesional concreto.
- El PRD no define si dos slots consecutivos pueden pertenecer a dos bloques contiguos del mismo profesional y sede (por ejemplo 08:00–10:00 y 10:00–12:00). CA-02 asume que ambos slots deben pertenecer al mismo bloque; debe confirmarse antes de implementar porque altera T-01.
- Incógnita abierta **INC-014** (ver [[EP-004-gestion-de-profesionales]]): si a un profesional se le retira una sede con bloques futuros, esta HU no ofrece esos slots, por coherencia con RN-07.
