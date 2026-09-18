---
id: HU-011
tipo: historia-de-usuario
titulo: "Gestionar especialidades y su duración"
estado: Aprobada
epica: "[[EP-003-catalogos-del-sistema]]"
requisitos: [RF-06, RF-09]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 3"
dependencias:
  - "[[HU-010-consultar-catalogos-fijos-precargados]]"
  - "[[HU-005-autorizar-peticiones-por-rol-y-ownership]]"
relacionadas:
  - "[[HU-014-asignar-especialidades-y-especialidad-primaria]]"
  - "[[HU-022-buscar-disponibilidad-con-filtros]]"
---

# HU-011 — Gestionar especialidades y su duración

## Historia de usuario

**COMO** ADMIN
**QUIERO** crear, editar, activar y desactivar especialidades indicando su duración y su tipo de cita
**PARA** que la oferta clínica y la duración de cada cita tengan una única fuente de verdad.

> Como ADMIN, quiero crear, editar, activar y desactivar especialidades indicando su duración y su tipo de cita para que la oferta clínica y la duración de cada cita tengan una única fuente de verdad.

## Contexto y descripción

RF-06 declara la especialidad como catálogo configurable gestionado por ADMIN, y RF-09 le asigna una responsabilidad adicional decisiva: cada especialidad define una duración de 30 o 60 minutos y el profesional no puede sobrescribirla. La duración determina cuántos slots consecutivos necesita una cita (30 min = 1 slot, 60 min = 2 slots consecutivos), por lo que la especialidad es la fuente de verdad del cálculo de disponibilidad.

La especialidad también determina la política de aprobación. RN-02 y RN-03 distinguen citas generales, que se aprueban automáticamente, de citas especializadas, que requieren la intervención de ADMIN. Esa distinción no depende de la cita ni del profesional: depende del tipo de cita asociado a la especialidad, que es su única fuente de verdad.

Por último, RF-06 prohíbe el borrado físico de un catálogo referenciado por transacciones. Una especialidad ya usada por citas o asignada a profesionales se desactiva, nunca se elimina, para no romper el histórico exigido por RN-12.

## Alcance

- Migración Flyway de la tabla de especialidades con nombre, duración, tipo de cita y marca de activación.
- Creación de una especialidad por ADMIN indicando nombre, duración y tipo de cita.
- Edición de los datos de una especialidad existente.
- Activación y desactivación de una especialidad.
- Listado y consulta de especialidades, con filtro por estado de activación y por tipo de cita.
- Validación server-side de la duración permitida (30 o 60 minutos).
- Derivación de la política de aprobación a partir del tipo de cita de la especialidad.
- Exclusión de las especialidades desactivadas de la oferta para nuevas reservas.
- Pantalla de CRUD de especialidades en `citas-web`, restringida a ADMIN.

## Fuera de alcance

- Asignación de especialidades a un profesional y marcado de la primaria, que se cubre en [[HU-014-asignar-especialidades-y-especialidad-primaria]].
- Cálculo de slots y de disponibilidad a partir de la duración, que pertenece a la agenda y a la búsqueda de disponibilidad.
- Aprobación o rechazo de citas especializadas, que pertenece a la operación administrativa de solicitudes.
- Creación de tipos de cita nuevos: general y especializada son los dos valores que el PRD reconoce (RF-10).
- Tarifas, cupos o restricciones por especialidad: no están en el PRD.

## Reglas de negocio

- Solo ADMIN gestiona los catálogos configurables (RF-06, PRD §8 autorización por rol).
- Cada especialidad define una duración de 30 o 60 minutos, y solo esos dos valores son admisibles (RF-09).
- El profesional no sobrescribe la duración definida por la especialidad (RF-09).
- Cada especialidad se asocia a un tipo de cita general o especializada (RF-10).
- La política de aprobación deriva del tipo de cita de la especialidad: general se aprueba automáticamente (RN-02) y especializada requiere ADMIN (RN-03). La especialidad es la única fuente de verdad de esa política.
- No se permite borrar físicamente una especialidad referenciada por transacciones; se desactiva (RF-06).
- Una especialidad debe estar activa para poder reservarse (RN-08).
- Los nombres de especialidad del laboratorio son sintéticos y no reproducen la oferta real de FCV (PRD §9).

## Dependencias y relaciones

- Épica: [[EP-003-catalogos-del-sistema]]
- Dependencias: [[HU-010-consultar-catalogos-fijos-precargados]], [[HU-005-autorizar-peticiones-por-rol-y-ownership]]
- Relacionadas: [[HU-012-gestionar-eps-y-planes]], [[HU-014-asignar-especialidades-y-especialidad-primaria]], [[HU-022-buscar-disponibilidad-con-filtros]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** El CRUD en sí es rutinario, pero esta HU concentra tres reglas que el resto del producto da por ciertas: la duración cerrada a 30/60, la derivación de la política de aprobación desde el tipo de cita, y la desactivación en lugar del borrado cuando hay referencias. Detectar que una especialidad está referenciada obliga a consultar citas y asignaciones de profesional, lo que añade acoplamiento a agregados que otras HU construyen.

## Tareas de desarrollo

- [ ] **T-01 — Modelar la especialidad en el dominio**
  Dificultad: Medio
  Descripción: Entidad de dominio con nombre, duración como objeto de valor restringido a 30 o 60 minutos, tipo de cita general o especializada y estado de activación, con la invariante de duración validada en el constructor y sin dependencias de framework.

- [ ] **T-02 — Crear la migración Flyway de especialidades**
  Dificultad: Bajo
  Descripción: Migración versionada que crea la tabla de especialidades con nombre único, duración en minutos, referencia al tipo de cita y columna de activación con valor por defecto activo, y una restricción que solo admita las duraciones válidas.

- [ ] **T-03 — Implementar los casos de uso de creación y edición**
  Dificultad: Medio
  Descripción: Casos de uso de aplicación que validan nombre, duración y tipo de cita, comprueban la unicidad del nombre y persisten la especialidad a través de puertos de repositorio.

- [ ] **T-04 — Implementar los casos de uso de activación y desactivación**
  Dificultad: Medio
  Descripción: Casos de uso que cambian el estado de activación sin eliminar el registro, y comprobación de referencias en citas y en asignaciones de profesional para impedir el borrado físico de una especialidad referenciada.

- [ ] **T-05 — Exponer el adaptador REST del CRUD de especialidades**
  Dificultad: Medio
  Descripción: Controlador con DTO validados, listado con filtro por activación y por tipo de cita, respuestas de error diferenciadas para duración inválida, nombre duplicado y borrado no permitido por referencias existentes.

- [ ] **T-06 — Restringir el CRUD a ADMIN en la configuración de seguridad**
  Dificultad: Bajo
  Descripción: Declarar las rutas de escritura de especialidades como accesibles solo con rol `ADMIN`, dejando la consulta disponible para los roles que la necesitan en sus formularios.

- [ ] **T-07 — Construir la pantalla de CRUD de especialidades en citas-web**
  Dificultad: Medio
  Descripción: Vista React + TypeScript visible solo para ADMIN con listado, alta, edición y conmutación de activación, selector de duración limitado a 30 y 60 minutos, selector de tipo de cita y presentación de los errores devueltos por la API.

- [ ] **T-08 — Pruebas de dominio, aplicación e integración de especialidades**
  Dificultad: Medio
  Descripción: Pruebas de la invariante de duración, del rechazo de valores distintos de 30 y 60, de la desactivación frente al borrado con referencias, de la exclusión de inactivas en la oferta y de la restricción de rol en el adaptador REST.

## Criterios de aceptación

### CA-01 — Alta de especialidad con duración válida

**Dado** una sesión autenticada con rol `ADMIN`
**Cuando** crea una especialidad con nombre no existente, duración de 30 minutos y tipo de cita especializada
**Entonces** la API responde con creación exitosa, la especialidad queda persistida en estado activo y aparece en el listado de especialidades.

### CA-02 — Duración distinta de 30 o 60 minutos rechazada

**Dado** una sesión autenticada con rol `ADMIN`
**Cuando** intenta crear o editar una especialidad con duración de 45 minutos, de 0 minutos o con un valor negativo
**Entonces** la API responde en los tres casos con un error de validación sobre el campo duración, no persiste ni modifica ninguna especialidad, y el mensaje indica que solo se admiten 30 o 60 minutos.

### CA-03 — El tipo de cita determina la política de aprobación

**Dado** una especialidad con tipo de cita general y otra con tipo de cita especializada
**Cuando** se consulta cada una de ellas
**Entonces** la respuesta indica para la general que no requiere aprobación de ADMIN y para la especializada que sí la requiere, y ese dato proviene del tipo de cita de la especialidad y no de ninguna otra configuración.

### CA-04 — Una especialidad referenciada no se borra físicamente

**Dado** una especialidad asociada a al menos una cita o asignada a al menos un profesional
**Cuando** ADMIN intenta borrarla
**Entonces** la API rechaza la operación con un error que explica que la especialidad está referenciada, el registro sigue existiendo en la base de datos y la única vía disponible es desactivarla.

### CA-05 — Desactivación sin pérdida del histórico

**Dado** una especialidad activa con citas ya registradas
**Cuando** ADMIN la desactiva
**Entonces** la API responde con éxito, la especialidad queda marcada como inactiva sin eliminarse, y las citas existentes siguen mostrando su especialidad al consultarlas.

### CA-06 — Una especialidad desactivada deja de ofrecerse para nuevas reservas

**Dado** una especialidad desactivada
**Cuando** un `USER` consulta la oferta de especialidades disponibles para reservar o intenta solicitar una cita con ella
**Entonces** la especialidad no aparece entre las opciones ofrecidas y la solicitud directa contra la API se rechaza indicando que la especialidad no está activa (RN-08).

### CA-07 — Reactivación de una especialidad

**Dado** una especialidad en estado inactivo
**Cuando** ADMIN la vuelve a activar
**Entonces** la API responde con éxito y la especialidad vuelve a aparecer entre las opciones ofrecidas para nuevas reservas.

### CA-08 — Solo ADMIN opera el CRUD

**Dado** sesiones autenticadas con rol `USER` y con rol `PROFESSIONAL`
**Cuando** cada una intenta crear, editar, activar o desactivar una especialidad
**Entonces** la API responde con un código de prohibido en todos los casos, no se modifica ningún registro, y la misma operación con rol `ADMIN` se ejecuta correctamente.

### CA-09 — La duración de la cita proviene siempre de la especialidad

**Dado** una especialidad con duración de 60 minutos
**Cuando** se crea una cita de esa especialidad enviando adicionalmente una duración distinta en la petición
**Entonces** la cita resultante tiene la duración definida por la especialidad y el valor enviado en la petición se ignora o se rechaza, sin que el profesional pueda alterarla (RF-09).

## Definition of Done

- [ ] Los criterios CA-01 a CA-09 están validados con evidencia concreta.
- [ ] Existe una migración Flyway versionada de la tabla de especialidades con nombre único y una restricción de base de datos que impide persistir duraciones distintas de 30 y 60 minutos.
- [ ] La invariante de duración está expresada en el dominio y no solo en la validación del DTO ni solo en la base de datos.
- [ ] El dominio de especialidad no depende de Spring ni de JPA, respetando la separación hexagonal.
- [ ] No existe ninguna ruta ni caso de uso que elimine físicamente una especialidad referenciada por citas o por asignaciones de profesional.
- [ ] Las rutas de escritura de especialidades exigen rol `ADMIN` en la configuración de Spring Security y están cubiertas por prueba.
- [ ] La pantalla de CRUD de especialidades de `citas-web` solo es accesible para ADMIN y consume la API mediante la URL configurable por entorno.
- [ ] Los nombres de especialidad cargados como ejemplo son sintéticos y no reproducen la oferta real de FCV.
- [ ] Existen pruebas automatizadas del rechazo de duración inválida, de la desactivación con referencias y de la exclusión de inactivas en la oferta, y pasan.
- [ ] El contrato del CRUD de especialidades está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [ ] La trazabilidad de esta HU y de [[EP-003-catalogos-del-sistema]] está actualizada en `docs/wiki/scrum/`.

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

- Incógnita abierta **INC-009** (ver [[EP-003-catalogos-del-sistema]]): RF-11 asume la existencia de `Medicina General`, pero RF-06 permite gestionar el catálogo libremente. No está decidido si `Medicina General` es una especialidad protegida contra desactivación o una más del catálogo configurable. Esta HU no la protege; si se decide protegerla, deberá añadirse el criterio correspondiente.
- Incógnita abierta **INC-010** (ver [[EP-003-catalogos-del-sistema]]): el PRD no define qué ocurre con los bloques de disponibilidad y las citas futuras ya aprobadas cuando se desactiva una especialidad. CA-06 solo exige bloquear nuevas reservas y CA-05 solo exige preservar el histórico; el tratamiento de las citas futuras queda pendiente de decisión y no se resuelve por inferencia.
- Incógnita abierta **INC-011** (ver [[EP-003-catalogos-del-sistema]]): RF-06 solo prohíbe el borrado físico de un catálogo referenciado. No está decidido si una especialidad nunca referenciada puede borrarse físicamente o si también debe limitarse a desactivación. CA-04 solo cubre el caso referenciado.
- Incógnita abierta **INC-012** (ver [[EP-003-catalogos-del-sistema]]): el PRD no indica si el tipo de cita de una especialidad puede cambiarse cuando ya tiene citas asociadas, lo que alteraría retroactivamente la política de aprobación. CA-03 no cubre ese cambio.
