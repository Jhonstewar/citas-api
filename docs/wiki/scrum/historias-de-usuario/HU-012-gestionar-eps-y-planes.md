---
id: HU-012
tipo: historia-de-usuario
titulo: "Gestionar EPS y planes"
estado: Aprobada
epica: "[[EP-003-catalogos-del-sistema]]"
requisitos: [RF-06]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 3"
dependencias:
  - "[[HU-010-consultar-catalogos-fijos-precargados]]"
  - "[[HU-005-autorizar-peticiones-por-rol-y-ownership]]"
relacionadas:
  - "[[HU-009-registrar-afiliacion-a-eps-y-plan]]"
---

# HU-012 — Gestionar EPS y planes

## Historia de usuario

**COMO** ADMIN
**QUIERO** crear, editar, activar y desactivar EPS y sus planes
**PARA** que los pacientes puedan declarar su afiliación sobre datos maestros vigentes.

> Como ADMIN, quiero crear, editar, activar y desactivar EPS y sus planes para que los pacientes puedan declarar su afiliación sobre datos maestros vigentes.

## Contexto y descripción

RF-06 incluye la EPS y sus planes entre los catálogos configurables que ADMIN gestiona mediante CRUD. Son los datos maestros sobre los que se apoya RF-04: un `USER` asocia su EPS, su plan y su régimen mediante una afiliación, y la aplicación debe evitar duplicar esa combinación dentro del usuario.

El plan es un catálogo dependiente: pertenece a una EPS concreta y se clasifica por un régimen tomado del catálogo fijo precargado en [[HU-010-consultar-catalogos-fijos-precargados]]. Esta jerarquía es la que permite que el formulario de afiliación ofrezca solo combinaciones coherentes.

Como el resto de catálogos configurables, una EPS o un plan ya referenciados por afiliaciones no se borran físicamente: se desactivan, para no romper la trazabilidad de los datos declarados por los pacientes. Todas las EPS y planes del laboratorio son de demostración y sintéticos, nunca entidades ni datos reales asociados a FCV (PRD §9).

## Alcance

- Migración Flyway de las tablas de EPS y de planes de EPS, con la referencia del plan a su EPS y a un régimen del catálogo fijo.
- Creación, edición, activación y desactivación de EPS por ADMIN.
- Creación, edición, activación y desactivación de planes por ADMIN, siempre dentro de una EPS.
- Listado y consulta de EPS y de sus planes, con filtro por estado de activación.
- Exclusión de EPS y planes desactivados de la oferta para declarar una afiliación nueva.
- Validación server-side de la pertenencia del plan a una EPS y a un régimen existente.
- Pantalla de CRUD de EPS y planes en `citas-web`, restringida a ADMIN.

## Fuera de alcance

- Declaración de la afiliación por parte del paciente, que se cubre en [[HU-009-registrar-afiliacion-a-eps-y-plan]].
- Gestión del catálogo de regímenes: es catálogo fijo de solo lectura (RF-05).
- Gestión de especialidades, que se cubre en [[HU-011-gestionar-especialidades-y-su-duracion]].
- Validación de la afiliación contra sistemas externos o reales: el PRD lo excluye (PRD §9).
- Coberturas, tarifas o autorizaciones por plan: no están en el PRD.

## Reglas de negocio

- Solo ADMIN gestiona los catálogos configurables de EPS y planes (RF-06, PRD §8 autorización por rol).
- Un plan pertenece a una EPS y a un régimen del catálogo fijo (RF-04, RF-05, RF-06).
- No se permite borrar físicamente una EPS o un plan referenciados por afiliaciones; se desactivan (RF-06).
- Un plan desactivado deja de ofrecerse al declarar una afiliación nueva.
- Las EPS y los planes del laboratorio son de demostración y sintéticos; nunca se usan datos reales (PRD §9).
- Toda validación se realiza también en el servidor (PRD §8).

## Dependencias y relaciones

- Épica: [[EP-003-catalogos-del-sistema]]
- Dependencias: [[HU-010-consultar-catalogos-fijos-precargados]], [[HU-005-autorizar-peticiones-por-rol-y-ownership]]
- Relacionadas: [[HU-009-registrar-afiliacion-a-eps-y-plan]], [[HU-011-gestionar-especialidades-y-su-duracion]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** Son dos CRUD encadenados con una jerarquía padre-hijo y una referencia a catálogo fijo. La lógica es simple, pero la desactivación debe propagarse de forma coherente entre EPS y planes, y la comprobación de referencias en afiliaciones obliga a coordinar con un agregado que construye otra HU. El trabajo de frontend crece por tratarse de dos entidades anidadas en una misma pantalla.

## Tareas de desarrollo

- [ ] **T-01 — Modelar EPS y plan en el dominio**
  Dificultad: Bajo
  Descripción: Entidades de dominio de EPS y de plan con nombre, estado de activación y, en el plan, la referencia obligatoria a su EPS y a un régimen, con las invariantes de obligatoriedad expresadas sin dependencias de framework.

- [ ] **T-02 — Crear la migración Flyway de EPS y planes**
  Dificultad: Medio
  Descripción: Migración versionada que crea la tabla de EPS con nombre único, la tabla de planes con clave foránea a EPS y al régimen del catálogo fijo, unicidad del nombre del plan dentro de su EPS, y columnas de activación con valor por defecto activo.

- [ ] **T-03 — Implementar los casos de uso de CRUD de EPS**
  Dificultad: Medio
  Descripción: Casos de uso de creación, edición, activación y desactivación de EPS, con comprobación de referencias en afiliaciones para impedir el borrado físico de una EPS referenciada.

- [ ] **T-04 — Implementar los casos de uso de CRUD de planes**
  Dificultad: Medio
  Descripción: Casos de uso de creación, edición, activación y desactivación de planes, validando que la EPS y el régimen indicados existen, y con la misma comprobación de referencias en afiliaciones antes de cualquier borrado.

- [ ] **T-05 — Exponer los adaptadores REST de EPS y planes**
  Dificultad: Medio
  Descripción: Controladores con DTO validados, listados con filtro por activación, consulta de los planes de una EPS, y errores diferenciados para nombre duplicado, EPS o régimen inexistente y borrado no permitido por referencias.

- [ ] **T-06 — Restringir el CRUD a ADMIN en la configuración de seguridad**
  Dificultad: Bajo
  Descripción: Declarar las rutas de escritura de EPS y planes como accesibles solo con rol `ADMIN`, dejando la consulta disponible para el formulario de afiliación del `USER`.

- [ ] **T-07 — Construir la pantalla de CRUD de EPS y planes en citas-web**
  Dificultad: Medio
  Descripción: Vista React + TypeScript visible solo para ADMIN con listado de EPS, gestión de los planes de la EPS seleccionada, selector de régimen poblado desde el catálogo fijo, conmutación de activación y presentación de los errores de la API.

- [ ] **T-08 — Pruebas de dominio, aplicación e integración de EPS y planes**
  Dificultad: Medio
  Descripción: Pruebas de la obligatoriedad de EPS y régimen en el plan, del rechazo del borrado de un catálogo referenciado por afiliaciones, de la exclusión de los desactivados en la oferta de afiliación y de la restricción de rol en el adaptador REST.

## Criterios de aceptación

### CA-01 — Alta de una EPS y de sus planes

**Dado** una sesión autenticada con rol `ADMIN`
**Cuando** crea una EPS con un nombre no existente y a continuación crea un plan dentro de esa EPS indicando un régimen del catálogo fijo
**Entonces** la API responde con creación exitosa en ambos casos, la EPS y el plan quedan persistidos en estado activo, y la consulta de los planes de esa EPS devuelve el plan creado.

### CA-02 — Un plan exige EPS y régimen existentes

**Dado** una sesión autenticada con rol `ADMIN`
**Cuando** intenta crear un plan sin indicar EPS, con una EPS inexistente, sin indicar régimen o con un régimen que no pertenece al catálogo fijo
**Entonces** la API responde con un error de validación en cada caso, el mensaje identifica el campo inválido y no se persiste ningún plan.

### CA-03 — Una EPS o un plan referenciados no se borran físicamente

**Dado** una EPS y un plan declarados en al menos una afiliación de un usuario
**Cuando** ADMIN intenta borrarlos
**Entonces** la API rechaza ambas operaciones con un error que explica que están referenciados por afiliaciones, los registros siguen existiendo en la base de datos, y la única vía disponible es desactivarlos.

### CA-04 — Un plan desactivado deja de ofrecerse en una afiliación nueva

**Dado** un plan en estado inactivo
**Cuando** un `USER` abre el formulario de afiliación e intenta declarar ese plan enviando la petición directamente a la API
**Entonces** el plan no aparece entre las opciones ofrecidas y la API rechaza la declaración indicando que el plan no está activo.

### CA-05 — Desactivar una EPS retira su oferta sin perder las afiliaciones existentes

**Dado** una EPS activa con planes y con afiliaciones ya declaradas
**Cuando** ADMIN desactiva la EPS
**Entonces** la EPS y sus planes dejan de ofrecerse para declarar una afiliación nueva, ningún registro se elimina, y las afiliaciones ya declaradas siguen mostrando su EPS y su plan al consultarlas.

### CA-06 — Reactivación de una EPS o de un plan

**Dado** una EPS o un plan en estado inactivo
**Cuando** ADMIN los vuelve a activar
**Entonces** la API responde con éxito y vuelven a aparecer entre las opciones ofrecidas para declarar una afiliación nueva.

### CA-07 — Solo ADMIN opera el CRUD

**Dado** sesiones autenticadas con rol `USER` y con rol `PROFESSIONAL`
**Cuando** cada una intenta crear, editar, activar o desactivar una EPS o un plan
**Entonces** la API responde con un código de prohibido en todos los casos, no se modifica ningún registro, y la misma operación con rol `ADMIN` se ejecuta correctamente.

### CA-08 — Los datos cargados son sintéticos

**Dado** el conjunto de EPS y planes presentes en el entorno de laboratorio, ya provengan del seed o de altas de ADMIN
**Cuando** se revisan sus nombres y demás datos
**Entonces** todos corresponden a entidades de demostración inventadas para el ejercicio y ninguno reproduce una EPS, un plan o un dato real vinculado a FCV (PRD §9).

### CA-09 — Una EPS o un plan no referenciados se borran físicamente

**Dado** un plan sin ninguna afiliación que lo referencie, y una EPS sin planes ni afiliaciones que la referencien
**Cuando** ADMIN borra cada uno
**Entonces** la API responde con éxito, el registro deja de existir en la base de datos y deja de aparecer en los listados; y si el mismo borrado se intenta sobre un registro referenciado, la API responde 409 y ofrece desactivarlo, como en CA-03 (D28).

## Definition of Done

- [ ] Los criterios CA-01 a CA-09 están validados con evidencia concreta.
- [ ] Existe una migración Flyway versionada que crea las tablas de EPS y planes con la clave foránea del plan a su EPS y al régimen del catálogo fijo.
- [ ] El nombre de la EPS es único y el nombre del plan es único dentro de su EPS, con restricción a nivel de base de datos.
- [ ] El dominio de EPS y plan no depende de Spring ni de JPA, respetando la separación hexagonal.
- [ ] No existe ninguna ruta ni caso de uso que elimine físicamente una EPS o un plan referenciados por afiliaciones.
- [ ] Las consultas que alimentan el formulario de afiliación devuelven únicamente EPS y planes activos.
- [ ] Las rutas de escritura de EPS y planes exigen rol `ADMIN` en la configuración de Spring Security y están cubiertas por prueba.
- [ ] La pantalla de CRUD de EPS y planes de `citas-web` solo es accesible para ADMIN y consume la API mediante la URL configurable por entorno.
- [ ] Los datos de ejemplo cargados son sintéticos y ningún nombre real de EPS ni de FCV aparece en el repositorio.
- [ ] Existen pruebas automatizadas del rechazo del borrado con referencias y de la exclusión de los desactivados en la oferta de afiliación, y pasan.
- [ ] El contrato del CRUD de EPS y planes está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
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

- 2026-09-25 — Se añade CA-09 por D28 (borrado físico de lo no referenciado), que ningún criterio cubría; la DoD pasa a CA-01 a CA-09. CA-03 no cambia: ya exigía el rechazo del borrado referenciado.
- 2026-09-25 — Aprobada por **aprobación delegada** del usuario para S4 (D15, PLAN_RETOMA_S4.md). Alcance en `PLAN_RETOMA_S4.md` §3 (bloque «Catálogos», fase F6) y decisiones D15–D30 en [[dec-006-decisiones-s4-ciclo-de-vida]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- **Resuelta (D28, provisional bajo delegación):** INC-011 (ver [[EP-003-catalogos-del-sistema]]): una EPS o un plan que nada referencia se **borran físicamente**; si algo los referencia, 409 y se ofrece desactivar, igual que las especialidades de [[HU-011-gestionar-especialidades-y-su-duracion]] ([[dec-006-decisiones-s4-ciclo-de-vida]]). CA-03 y CA-09 lo cubren.
- Esquema ya existente, con una divergencia respecto a la DoD: `eps` y `eps_plans` existen desde `V2__configurable_catalogs_and_professionals.sql`, así que T-02 no crea tablas. Pero la unicidad del esquema es sobre el **código** (`uq_eps_code`, `uq_eps_plans_eps_code (eps_id, code)`), no sobre el **nombre** que pide el ítem de DoD «nombre de la EPS único y nombre del plan único dentro de su EPS». No se reescribe la DoD en silencio: al implementar F6 hay que decidir si la unicidad por código satisface la intención (y ajustar la DoD con registro) o si hace falta una migración posterior a V7, como R1 hizo para las especialidades.
- El PRD no define si desactivar una EPS debe desactivar en cascada sus planes o si los planes conservan su estado propio quedando inaccesibles por su EPS. CA-05 solo exige que dejen de ofrecerse, sin fijar el mecanismo; la decisión queda pendiente del usuario del proyecto.
- El PRD no define qué ocurre con una afiliación ya declarada cuando su plan se desactiva: si sigue vigente o debe marcarse para actualización. CA-05 solo exige preservar el registro. Esta duda debe resolverse junto con [[HU-009-registrar-afiliacion-a-eps-y-plan]].
- RF-04 exige evitar duplicar EPS, régimen y plan dentro del usuario; esa restricción pertenece a la afiliación y no a este catálogo, por lo que no se cubre aquí.
