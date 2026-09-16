---
id: HU-008
tipo: historia-de-usuario
titulo: "Consultar y actualizar el perfil"
estado: Borrador
epica: "[[EP-002-perfil-y-afiliacion-del-paciente]]"
requisitos: [RF-04]
esfuerzo: "Bajo"
sprint_sugerido: "Sprint 2"
dependencias:
  - "[[HU-001-registrar-cuenta-de-usuario]]"
  - "[[HU-005-autorizar-peticiones-por-rol-y-ownership]]"
relacionadas:
  - "[[HU-009-registrar-afiliacion-a-eps-y-plan]]"
---

# HU-008 — Consultar y actualizar el perfil

## Historia de usuario

**COMO** USER autenticado
**QUIERO** consultar mis datos personales y actualizar los que el sistema permite
**PARA** mantener mi información de contacto correcta

> Como USER autenticado, quiero consultar mis datos personales y actualizar los que el sistema permite para mantener mi información de contacto correcta.

## Contexto y descripción

RF-04 establece que el USER consulta y actualiza los "datos permitidos" de su perfil. Es la primera capacidad de [[EP-002-perfil-y-afiliacion-del-paciente]] y se apoya en los datos que el usuario introdujo al registrarse en [[HU-001-registrar-cuenta-de-usuario]].

La comprobación central de esta HU es el ownership: el perfil es un recurso estrictamente personal y ningún usuario puede leer ni editar el de otro. El mecanismo de verificación viene de [[HU-005-autorizar-peticiones-por-rol-y-ownership]]; aquí se aplica sobre las operaciones de lectura y actualización del perfil.

El PRD no enumera qué campos son "datos permitidos", lo que está registrado como INC-006 en la épica. Por eso esta HU no inventa la lista: describe el mecanismo —existe un conjunto de campos editables declarado de forma explícita, lo que está dentro se actualiza y lo que está fuera se rechaza— y deja que la composición concreta del conjunto sea una decisión humana. Si finalmente algún identificador único como el email o el documento resultara editable, la unicidad que ya garantiza el registro debe seguir preservándose.

## Alcance

- Endpoint de lectura del perfil propio del usuario autenticado en `citas-api`.
- Endpoint de actualización del perfil propio en `citas-api`.
- Aplicación del ownership estricto sobre ambas operaciones, apoyada en [[HU-005-autorizar-peticiones-por-rol-y-ownership]].
- Conjunto de campos editables declarado de forma explícita en el servidor, con rechazo de los campos no editables.
- Validación server-side de los datos enviados.
- Preservación de la unicidad de cualquier identificador que resulte editable.
- Pantalla de perfil en `citas-web` para consultar y editar los datos permitidos.

## Fuera de alcance

- Registro y gestión de la afiliación a EPS y plan, que se cubre en [[HU-009-registrar-afiliacion-a-eps-y-plan]].
- Cambio de contraseña, que vive en [[HU-007-restablecer-contrasena-con-token]].
- Edición del perfil de un usuario por parte de un ADMIN: el PRD no la contempla para pacientes.
- Datos profesionales (código, matrícula, especialidades, sedes), que administra ADMIN en la épica de gestión de profesionales.
- Eliminación de la cuenta por el propio usuario: no está en el PRD.
- Historial de cambios sobre el perfil: RF-19 exige auditoría de estados de cita, no de perfil.

## Reglas de negocio

- El USER consulta y actualiza únicamente los datos permitidos de su propio perfil (RF-04).
- El usuario solo accede y modifica su propio perfil; el ownership es estricto (PRD §8).
- El conjunto de campos editables está declarado de forma explícita en el servidor y no depende de lo que envíe el cliente (PRD §8).
- Un campo no editable enviado en la petición no modifica el dato almacenado.
- Toda validación de los datos enviados se ejecuta también en el servidor (PRD §8).
- Si un identificador único resulta editable, se mantiene la unicidad que RF-01 exige para email y número de documento.
- La respuesta del perfil no incluye la contraseña ni su hash (PRD §8).

## Dependencias y relaciones

- Épica: [[EP-002-perfil-y-afiliacion-del-paciente]]
- Dependencias: [[HU-001-registrar-cuenta-de-usuario]], [[HU-005-autorizar-peticiones-por-rol-y-ownership]]
- Relacionadas: [[HU-009-registrar-afiliacion-a-eps-y-plan]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Bajo

**Justificación de dificultad:** Son dos operaciones sobre una entidad que ya existe, sin cambios de esquema, sin reglas de negocio complejas y reutilizando por completo el mecanismo de ownership construido en la HU de autorización. El único punto de atención es declarar el conjunto editable en el servidor en lugar de aceptar lo que llegue del cliente.

## Tareas de desarrollo

- [ ] **T-01 — Definir el conjunto de campos editables del perfil**
  Dificultad: Bajo
  Descripción: Expresar en el dominio qué atributos del usuario admiten modificación por su titular y cuáles permanecen fijos, de modo que la regla sea explícita y verificable, dejando la composición concreta sujeta a la decisión pendiente INC-006.

- [ ] **T-02 — Implementar el caso de uso de consulta del perfil propio**
  Dificultad: Bajo
  Descripción: Caso de uso de aplicación que, a partir de la identidad del usuario autenticado, recupera su perfil y devuelve una proyección sin datos de credencial.

- [ ] **T-03 — Implementar el caso de uso de actualización del perfil propio**
  Dificultad: Medio
  Descripción: Caso de uso que valida los datos recibidos, aplica únicamente los campos declarados editables, comprueba la unicidad de los identificadores afectados si alguno lo es y persiste el resultado.

- [ ] **T-04 — Exponer los adaptadores REST de perfil**
  Dificultad: Bajo
  Descripción: Controladores de lectura y actualización que resuelven el usuario desde el contexto de seguridad y no desde un identificador enviado por el cliente, con DTO validado y respuesta sin datos sensibles.

- [ ] **T-05 — Aplicar el ownership sobre las operaciones de perfil**
  Dificultad: Bajo
  Descripción: Invocar el componente de verificación de propiedad de [[HU-005-autorizar-peticiones-por-rol-y-ownership]] en ambas operaciones, de forma que cualquier intento de operar sobre el perfil de otro usuario se rechace.

- [ ] **T-06 — Construir la pantalla de perfil en citas-web**
  Dificultad: Bajo
  Descripción: Vista que muestra los datos del perfil, permite editar solo los campos permitidos, presenta los no editables en modo consulta, valida en cliente y consume los endpoints mediante la URL configurable por entorno.

- [ ] **T-07 — Pruebas de consulta y actualización del perfil**
  Dificultad: Medio
  Descripción: Pruebas del caso de uso y de integración REST para consulta propia, actualización de un campo editable, intento de modificar un campo no editable, intento de acceder al perfil de otro usuario y conflicto de unicidad si aplica.

## Criterios de aceptación

### CA-01 — Consulta del perfil propio

**Dado** un usuario `USER` autenticado
**Cuando** solicita su perfil
**Entonces** la API devuelve sus datos personales registrados y la respuesta no incluye la contraseña ni su hash.

### CA-02 — Actualización de un campo permitido

**Dado** un usuario `USER` autenticado y un campo declarado como editable en el servidor
**Cuando** envía una actualización con un valor válido para ese campo
**Entonces** la API responde con éxito y una consulta posterior del perfil devuelve el valor nuevo.

### CA-03 — Rechazo de un campo no permitido

**Dado** un usuario `USER` autenticado y un campo declarado como no editable en el servidor
**Cuando** envía una actualización que incluye un valor distinto para ese campo
**Entonces** el dato almacenado permanece sin cambios y la operación no lo modifica, independientemente de que la API responda con un error de campo no editable o lo ignore de forma explícita según el contrato acordado.

### CA-04 — Ownership en la lectura

**Dado** dos usuarios `USER` distintos
**Cuando** el primero, con su access token válido, intenta consultar el perfil del segundo
**Entonces** la API rechaza la petición y no devuelve ningún dato del segundo usuario.

### CA-05 — Ownership en la actualización

**Dado** dos usuarios `USER` distintos
**Cuando** el primero, con su access token válido, intenta actualizar el perfil del segundo
**Entonces** la API rechaza la petición y los datos del segundo usuario permanecen sin cambios.

### CA-06 — Validación server-side de los datos enviados

**Dado** una petición de actualización enviada directamente a la API con un valor inválido en un campo editable, por ejemplo un valor vacío en un campo obligatorio
**Cuando** la API procesa la petición
**Entonces** responde con un error de validación que identifica el campo y no persiste ningún cambio.

### CA-07 — Unicidad preservada si el identificador es editable

**Dado** que el conjunto de campos editables incluye un identificador único y que existe otro usuario que ya usa el valor `paciente.demo@example.com`
**Cuando** un usuario intenta actualizar su perfil con ese mismo valor
**Entonces** la API responde con un error de conflicto y no modifica ningún dato; si el identificador no es editable, este criterio se resuelve mediante CA-03.

### CA-08 — Pantalla de perfil coherente con el servidor

**Dado** un usuario `USER` autenticado en `citas-web`
**Cuando** abre la pantalla de perfil
**Entonces** los campos editables se presentan como modificables y los no editables en modo consulta, en correspondencia con el conjunto declarado en el servidor.

## Definition of Done

- [ ] Los criterios CA-01 a CA-08 están validados con evidencia concreta.
- [ ] Esta HU opera sobre la tabla de usuarios creada en [[HU-001-registrar-cuenta-de-usuario]]; no introduce cambios de esquema y por tanto no requiere migración Flyway, salvo que la decisión sobre INC-006 obligue a añadir algún campo nuevo, en cuyo caso se incorporará una migración versionada.
- [ ] El conjunto de campos editables está declarado explícitamente en el servidor y la actualización no aplica ningún atributo fuera de ese conjunto, aunque el cliente lo envíe.
- [ ] Ambas operaciones resuelven el usuario a partir del contexto de seguridad y no de un identificador enviado por el cliente.
- [ ] Ambas operaciones invocan el componente de verificación de ownership de [[HU-005-autorizar-peticiones-por-rol-y-ownership]] en lugar de reimplementar la comprobación.
- [ ] La respuesta del perfil nunca incluye la credencial del usuario en ninguna forma.
- [ ] Los campos que `citas-web` presenta como editables coinciden exactamente con los que `citas-api` acepta modificar, sin divergencia entre cliente y servidor.
- [ ] Existen pruebas automatizadas de consulta propia, actualización permitida, intento sobre campo no editable e intento sobre el perfil de otro usuario, y pasan.
- [ ] El contrato de los endpoints de perfil está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
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

## Notas y decisiones

- Incógnita abierta **INC-006** (ver [[EP-002-perfil-y-afiliacion-del-paciente]]): RF-04 habla de "datos permitidos" sin enumerarlos. Esta HU no inventa la lista. Los criterios se escriben sobre el mecanismo —se actualiza lo declarado editable, no se modifica lo declarado fijo—, de modo que siguen siendo válidos sea cual sea la composición final. La lista exacta requiere decisión humana antes de implementar, y son especialmente discutibles el email por ser credencial de login y el tipo y número de documento por ser identificador de la persona.
- CA-03 admite dos comportamientos válidos ante un campo no editable —error explícito o ignorar el valor— porque el PRD no fija cuál. La opción elegida debe quedar documentada en [[HU-033-publicar-contrato-rest-documentado]] y ser la misma en toda la API.
- Si la decisión sobre INC-006 deja el email como editable, habrá que definir qué ocurre con las sesiones abiertas y con los tokens de recuperación asociados al email anterior; el PRD no lo trata.
- El PRD no exige auditoría de cambios sobre el perfil: RF-19 limita la auditoría a los cambios de estado de las citas.
