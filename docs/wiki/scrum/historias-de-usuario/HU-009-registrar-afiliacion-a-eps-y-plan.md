---
id: HU-009
tipo: historia-de-usuario
titulo: "Registrar la afiliación a EPS y plan"
estado: Aprobada
epica: "[[EP-002-perfil-y-afiliacion-del-paciente]]"
requisitos: [RF-04]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 2"
dependencias:
  - "[[HU-008-consultar-y-actualizar-perfil]]"
  - "[[HU-012-gestionar-eps-y-planes]]"
relacionadas:
  - "[[HU-024-solicitar-cita-especializada]]"
---

# HU-009 — Registrar la afiliación a EPS y plan

## Historia de usuario

**COMO** USER autenticado
**QUIERO** asociar mi afiliación seleccionando un plan de EPS
**PARA** que mi cobertura quede registrada y pueda asociarse a mis citas

> Como USER autenticado, quiero asociar mi afiliación seleccionando un plan de EPS para que mi cobertura quede registrada y pueda asociarse a mis citas.

## Contexto y descripción

RF-04 permite al USER asociar su EPS, plan y régimen mediante una afiliación, y exige que la aplicación evite duplicar esa combinación dentro del usuario. Esta HU completa [[EP-002-perfil-y-afiliacion-del-paciente]] y depende del catálogo de EPS y planes que administra el ADMIN en [[HU-012-gestionar-eps-y-planes]].

La decisión de diseño que gobierna toda la historia es de normalización. La afiliación referencia un plan y nada más: la EPS a la que pertenece ese plan y el régimen asociado se conocen navegando desde el plan, y no se copian dentro del registro del usuario. Duplicarlos rompería la tercera forma normal exigida por las restricciones técnicas y abriría la puerta a que los datos del usuario contradijeran al catálogo.

De esa decisión se derivan dos consecuencias visibles. La primera es que la prevención de duplicados de RF-04 se resuelve impidiendo que un mismo usuario registre dos veces el mismo plan, porque el plan ya determina la EPS y el régimen. La segunda es que la selección disponible al usuario se limita a los planes activos del catálogo, en coherencia con la desactivación en lugar de borrado que exige RF-06.

~~Esta HU introduce la tabla de afiliaciones, por lo que requiere una migración Flyway propia.~~
**Corregido el 2026-09-23:** la tabla `affiliations` ya existe desde `V2__configurable_catalogs_and_professionals.sql`, con la restricción `uq_affiliations_user_plan`, igual que `eps` y `eps_plans`. Esta HU **no** lleva migración: solo faltaba el código (entidad, repositorio, caso de uso y endpoints).

## Alcance

- Selección de un plan de EPS desde el catálogo para registrar la afiliación del usuario autenticado.
- Afiliación que referencia únicamente al plan, del que se derivan la EPS y el régimen.
- Consulta de la afiliación registrada por el propio usuario.
- Prevención de que un mismo usuario duplique la combinación de EPS, régimen y plan.
- Restricción de la oferta de selección a los planes activos del catálogo.
- Ownership estricto sobre la afiliación, apoyado en [[HU-005-autorizar-peticiones-por-rol-y-ownership]].
- Migración Flyway que crea la tabla de afiliaciones con la restricción que impide la duplicidad.
- Pantalla de afiliación en `citas-web` integrada con la de perfil.

### Recorte acordado el 2026-09-23 — primer corte: solo el registro

El usuario aprobó esta HU para desbloquear la afiliación **durante el registro público**, que es
un momento distinto al que describe la historia (allí el usuario ya está autenticado). Este primer
corte implementa únicamente:

- `GET /api/catalogs/insurance-plans`, **público**, con los planes activos de EPS activas. Es la
  única lectura de catálogo sin token, porque el formulario de registro no tiene sesión.
- `POST /api/auth/register` con `insurancePlanId` **opcional**: si viene, crea la afiliación en la
  misma transacción que el usuario; si no viene, el registro queda exactamente como estaba.
- Plan inexistente, inactivo o de EPS inactiva → `422` con `INSURANCE_PLAN_UNAVAILABLE`. Los tres
  casos comparten código para no revelar si el plan existe.

Queda para un corte posterior de esta misma HU: consultar y cambiar la afiliación desde el perfil
del usuario autenticado, que es lo que dependía de [[HU-008-consultar-y-actualizar-perfil]].

**El selector de plan vive solo en el registro público de pacientes.** El alta de profesionales que
hace el ADMIN no lo lleva: un profesional se da de alta por su rol, no por su cobertura.

### Dependencia de HU-012 sustituida por una semilla

La historia dependía de [[HU-012-gestionar-eps-y-planes]] para tener catálogo. El usuario decidió el
2026-09-23 que los datos se crean **por script**, no por CRUD: `scripts/seed-eps-plans.ps1` en la
raíz del workspace, con EPS y planes ficticios e idempotente. HU-012 sigue fuera de alcance.
## Fuera de alcance

- Creación, edición, activación y desactivación de EPS y planes, que corresponde a ADMIN en [[HU-012-gestionar-eps-y-planes]].
- Uso de la afiliación como condición para solicitar una cita: depende de una decisión pendiente (INC-008) y se resolvería en la épica de reserva.
- Validación de la vigencia real de la afiliación contra sistemas externos: el PRD excluye la integración con sistemas clínicos (PRD §9).
- Gestión del catálogo de regímenes, que es catálogo fijo de solo lectura (RF-05).
- Datos personales del perfil, que se cubren en [[HU-008-consultar-y-actualizar-perfil]].

## Reglas de negocio

- El usuario puede asociar su EPS, plan y régimen mediante una afiliación (RF-04).
- La afiliación referencia un plan; desde el plan se conocen la EPS y el régimen, que no se almacenan repetidos dentro del usuario (RF-04, normalización 3FN de las restricciones técnicas).
- La aplicación impide duplicar EPS, régimen y plan dentro del mismo usuario (RF-04).
- Solo se ofrecen y se aceptan planes activos del catálogo (RF-06).
- El régimen procede del catálogo fijo de solo lectura (RF-05).
- El usuario solo consulta y modifica su propia afiliación (PRD §8).
- Toda validación de los datos enviados se ejecuta también en el servidor (PRD §8).

## Dependencias y relaciones

- Épica: [[EP-002-perfil-y-afiliacion-del-paciente]]
- Dependencias: [[HU-008-consultar-y-actualizar-perfil]], [[HU-012-gestionar-eps-y-planes]]
- Relacionadas: [[HU-024-solicitar-cita-especializada]], [[HU-005-autorizar-peticiones-por-rol-y-ownership]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** Añade una tabla nueva con su migración y una restricción de unicidad que debe sostenerse tanto en base de datos como en la validación de la aplicación. Obliga a modelar correctamente la derivación de EPS y régimen desde el plan sin desnormalizar, y a coordinar el frontend con el catálogo para ofrecer solo planes activos. No hay lógica algorítmica compleja, pero sí varias piezas que deben encajar entre catálogo, perfil y persistencia.

## Tareas de desarrollo

- [ ] **T-01 — Modelar la afiliación en el dominio**
  Dificultad: Medio
  Descripción: Definir la afiliación como referencia del usuario a un plan, con la invariante de no duplicidad dentro del usuario y la derivación de EPS y régimen a partir del plan, sin replicar esos datos ni depender de framework.

- [ ] **T-02 — Crear la migración Flyway de afiliaciones**
  Dificultad: Medio
  Descripción: Migración versionada que crea la tabla de afiliaciones con referencia al usuario y al plan, claves foráneas hacia ambos y una restricción de unicidad que impide repetir el mismo plan para un mismo usuario.

- [ ] **T-03 — Implementar el caso de uso de registro de afiliación**
  Dificultad: Medio
  Descripción: Caso de uso que valida que el plan exista y esté activo, comprueba que el usuario no tenga ya una afiliación a ese plan y persiste la afiliación asociada al usuario autenticado.

- [ ] **T-04 — Implementar el caso de uso de consulta de la afiliación propia**
  Dificultad: Bajo
  Descripción: Caso de uso que devuelve la afiliación del usuario autenticado resolviendo, a partir del plan referenciado, los datos de EPS y régimen para presentarlos sin haberlos duplicado en almacenamiento.

- [ ] **T-05 — Exponer los adaptadores REST de afiliación**
  Dificultad: Bajo
  Descripción: Controladores de registro y consulta que resuelven el usuario desde el contexto de seguridad, con DTO validado, respuesta que incluye EPS y régimen derivados, y error de conflicto ante la duplicidad.

- [ ] **T-06 — Exponer la consulta de planes activos para la selección**
  Dificultad: Bajo
  Descripción: Operación de lectura que devuelve los planes activos del catálogo con su EPS y su régimen, para alimentar la selección del usuario sin exponer los planes desactivados.

- [ ] **T-07 — Construir la pantalla de afiliación en citas-web**
  Dificultad: Medio
  Descripción: Vista integrada con el perfil que permite seleccionar un plan entre los activos, muestra la EPS y el régimen derivados al elegirlo, presenta la afiliación registrada y comunica el error de duplicidad.

- [ ] **T-08 — Pruebas de afiliación**
  Dificultad: Medio
  Descripción: Pruebas del caso de uso y de integración REST y de persistencia para alta correcta, intento de duplicar el mismo plan, intento con un plan desactivado, intento con un plan inexistente y consulta de la afiliación de otro usuario.

## Criterios de aceptación

### CA-01 — Registro correcto de la afiliación

**Dado** un usuario `USER` autenticado sin afiliación registrada y un plan activo del catálogo
**Cuando** registra su afiliación seleccionando ese plan
**Entonces** la API responde con éxito y la afiliación queda persistida asociada al usuario y al plan elegido.

### CA-02 — EPS y régimen derivados del plan, no duplicados

**Dado** una afiliación registrada
**Cuando** se consulta la afiliación del usuario y se inspecciona su registro en la base de datos
**Entonces** la respuesta incluye la EPS y el régimen correspondientes al plan, y el registro almacenado referencia únicamente al plan sin columnas propias que repitan la EPS ni el régimen.

### CA-03 — Duplicidad impedida dentro del mismo usuario

**Dado** un usuario que ya registró su afiliación a un plan concreto
**Cuando** intenta registrar otra afiliación al mismo plan
**Entonces** la API responde con un error de conflicto, no se crea un segundo registro y el mensaje indica que esa afiliación ya está registrada.

### CA-04 — Duplicidad impedida también en la base de datos

**Dado** la tabla de afiliaciones creada por la migración
**Cuando** se intenta insertar directamente dos filas con el mismo usuario y el mismo plan
**Entonces** la segunda inserción es rechazada por la restricción de unicidad del esquema.

### CA-05 — Solo se ofrecen planes activos

**Dado** un catálogo con al menos un plan activo y un plan desactivado
**Cuando** el usuario abre la selección de planes en `citas-web`
**Entonces** la lista presentada contiene el plan activo y no contiene el desactivado.

### CA-06 — Plan desactivado o inexistente rechazado en el servidor

**Dado** una petición de registro de afiliación enviada directamente a la API referenciando un plan desactivado en un caso y un plan inexistente en otro
**Cuando** la API procesa cada petición
**Entonces** responde con un error de validación en ambos casos y no crea ninguna afiliación.

### CA-07 — Ownership sobre la afiliación

**Dado** dos usuarios `USER` distintos, cada uno con su afiliación registrada
**Cuando** el primero, con su access token válido, intenta consultar o modificar la afiliación del segundo
**Entonces** la API rechaza la petición y no devuelve ni altera ningún dato del segundo usuario.

### CA-08 — Esquema creado por migración versionada

**Dado** una base de datos MySQL 8.4 con las migraciones anteriores aplicadas
**Cuando** se arranca `citas-api`
**Entonces** Flyway aplica la migración que crea la tabla de afiliaciones con sus claves foráneas hacia usuario y plan y la restricción de unicidad, y el arranque finaliza sin error.

## Definition of Done

- [ ] Los criterios CA-01 a CA-08 están validados con evidencia concreta.
- [ ] Existe una migración Flyway versionada que crea la tabla de afiliaciones y se aplica de forma incremental sobre el esquema existente.
- [ ] La tabla de afiliaciones no contiene columnas que repliquen la EPS ni el régimen: ambos se obtienen navegando desde el plan, y esta decisión queda justificada en el análisis de normalización del proyecto.
- [ ] La prevención de duplicados está garantizada en dos niveles: la validación del caso de uso y la restricción de unicidad del esquema.
- [ ] El dominio de la afiliación no depende de Spring ni de JPA, respetando la separación hexagonal.
- [ ] Las operaciones de afiliación resuelven el usuario desde el contexto de seguridad y aplican el ownership de [[HU-005-autorizar-peticiones-por-rol-y-ownership]].
- [ ] La lista de planes que `citas-web` presenta contiene exclusivamente planes activos y coincide con lo que `citas-api` acepta, sin divergencia entre cliente y servidor.
- [ ] Existen pruebas automatizadas de alta correcta, duplicidad rechazada, plan desactivado rechazado y acceso a la afiliación de otro usuario, y pasan.
- [ ] El contrato de los endpoints de afiliación y de consulta de planes activos está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [ ] La trazabilidad de esta HU y de [[EP-002-perfil-y-afiliacion-del-paciente]] está actualizada en `docs/wiki/scrum/`.

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

- Sesión S2 — HU creada en estado `Borrador`.
- 2026-09-23 — **el usuario la aprueba** y la adelanta fuera del Sprint 2 para cubrir la afiliación opcional durante el registro. Aprobación directa del usuario, no delegada. Se recorta el alcance a ese primer corte y se sustituye la dependencia de [[HU-012-gestionar-eps-y-planes]] por una semilla por script.

## Notas y decisiones

- Incógnita abierta **INC-007** (ver [[EP-002-perfil-y-afiliacion-del-paciente]]): el PRD no define si un usuario puede mantener varias afiliaciones simultáneas o solo una vigente, ni cómo se marcaría la que se asocia a una cita. Esta HU escribe los criterios sobre la no duplicidad de la combinación EPS, régimen y plan, que es lo único que RF-04 exige, y no impone ni prohíbe la multiplicidad. Si se decide una sola afiliación vigente, habrá que añadir una restricción adicional y definir el reemplazo.
- Incógnita abierta **INC-008** (ver [[EP-002-perfil-y-afiliacion-del-paciente]]): el PRD no define si la afiliación es obligatoria para solicitar una cita. Ningún criterio de esta HU bloquea el agendamiento; si se decidiera que es obligatoria, la regla se escribiría en la épica de reserva y afectaría a [[HU-024-solicitar-cita-especializada]].
- El PRD no indica si el usuario puede eliminar o cambiar una afiliación ya registrada. Esta HU solo cubre registrar y consultar; la modificación y la baja requieren decisión humana previa.
- La decisión de no duplicar EPS ni régimen dentro de la afiliación se apoya en la exigencia de 3FN de las restricciones técnicas y debe quedar reflejada en la justificación de claves y dependencias funcionales del modelo de datos.
