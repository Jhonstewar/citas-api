---
id: HU-008
tipo: historia-de-usuario
titulo: "Consultar y actualizar el perfil"
estado: Completada
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
- [ ] Nadie accede ni modifica el perfil de otro usuario: no existe ninguna ruta que reciba un identificador de usuario ajeno, y la comprobación de titularidad —allí donde puede fallar— usa el componente único de ownership de [[HU-005-autorizar-peticiones-por-rol-y-ownership]] y no una reimplementación local.
- [ ] La respuesta del perfil nunca incluye la credencial del usuario en ninguna forma.
- [ ] Los campos que `citas-web` presenta como editables coinciden exactamente con los que `citas-api` acepta modificar, sin divergencia entre cliente y servidor.
- [ ] Existen pruebas automatizadas de consulta propia, actualización permitida, intento sobre campo no editable e intento sobre el perfil de otro usuario, y pasan.
- [ ] El contrato de los endpoints de perfil está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [ ] La trazabilidad de esta HU y de [[EP-002-perfil-y-afiliacion-del-paciente]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Ejecución de referencia del **2026-09-30**: el `backend-verifier`, agente independiente que no escribió el código, reejecutó la suite completa de `citas-api` → **484 pruebas, 0 fallos, 0 errores, `BUILD SUCCESS`**. El `frontend-verifier` reejecutó la de `citas-web` → **218 pruebas**, con typecheck, `oxlint` y build limpios, y dejó **14 hallazgos abiertos** (3 en reparación y 4 pruebas que faltan).

Rutas abreviadas: **PIT** = `src/test/java/com/fcv/citas/infrastructure/rest/ProfileIntegrationTest.java`; **PPT** = `src/test/java/com/fcv/citas/domain/user/ProfilePolicyTest.java`.

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | PIT:107 `readsTheOwnProfileWithoutCredentials`: `GET /api/me` devuelve id, nombres, apellidos, email, tipo y número de documento, teléfono y roles del propio usuario, y el cuerpo **crudo** no contiene `password`, `hash` ni el `password_hash` leído de la tabla; PIT:267 `theResponseShapeIsTheUserResponse` cierra la lista de campos de la respuesta | La ausencia de credencial se asevera contra el valor real almacenado, no contra el nombre del campo |
| CA-02 | Cumple | PIT:147 `updatesTheEditableFields`: `PUT /api/me` con nombres, apellidos y teléfono → 200 con los valores nuevos, `GET /api/me` posterior los devuelve, y en la tabla `email`, `document_number` y `password_hash` quedan intactos; `ProfilePolicy:22` declara `EDITABLE = [firstNames, lastNames, phone]` (D25); PPT:50 | Los tres campos editables de D25 están cubiertos en la misma prueba |
| CA-03 | Cumple | PIT:175 `anyFixedFieldIsRejectedAndNothingChanges`: `email`, `documentType`, `documentNumber`, `password` y `roles` → 400 `FIELD_NOT_EDITABLE` con el `field` que sobra, y **nada** cambia (ni los editables que viajaban en la misma petición); incluye el caso `"email": null` en JSON literal, porque estar presente ya es intentar tocarlo; `ProfilePolicy:22-38` (`FIXED` y `requireOnlyEditable`); PPT:38 | El contrato eligió **error explícito**, no ignorar en silencio, y así está documentado (ver DoD de contrato) |
| CA-04 | Cumple | PIT:210 `nobodyReadsOrUpdatesSomeoneElsesProfile`: `GET /api/users/{otroId}` → 403 por la denegación por omisión de `SecurityConfig:94`; no existe ninguna ruta de lectura de perfil ajeno; `MeController:85` resuelve el titular con `CurrentUser.id(auth)`, del token | No hay un 404 de ownership porque no hay ruta que llegue a comprobarlo: el recurso ajeno no es direccionable |
| CA-05 | Cumple | PIT:210: `PUT /api/users/{otroId}` → 403, y un `PUT /api/me` con `id` y `userId` del otro usuario en el cuerpo responde 200 pero actualiza **el propio** (`$.id` es el del token); la fila del otro usuario queda byte a byte idéntica; `MeController:91` y `ProfileUseCase:55` (`Ownership.requireOwned`) | Cubre las dos vías de ataque: ruta ajena e id inyectado en el cuerpo |
| CA-06 | Cumple | PIT:232 `invalidValuesAreRejectedPerField`: vacío, en blanco, por encima del límite y nulo en cada uno de los tres campos editables → 400 con `fieldErrors.<campo>`; un cuerpo que no es JSON → 400; la fila queda idéntica tras los 11 intentos; PPT:64 `withContactValidatesLikeTheRegistration` | Los límites son los mismos que el registro, no unos inventados para el perfil |
| CA-07 | Cumple | PIT:175 (`email` → 400 `FIELD_NOT_EDITABLE`, nunca llega a haber conflicto de unicidad) | Se resuelve por su propia rama: D25 deja el email y el documento **fijos**, así que ningún identificador único es editable y el criterio se cierra vía CA-03, como su texto prevé |
| CA-08 | Cumple | `citas-web/src/pages/patient/ProfilePage.tsx` y `citas-web/src/profileAndPassword.test.tsx:66` («se abre desde el menú y el inicio; editables y de solo lectura según el servidor»), más `:99` (validación en cliente), `:119` (mensaje de `FIELD_NOT_EDITABLE` con su campo) y `:132` (`fieldErrors` en su campo); verificación independiente del 2026-10-04 (F10): vitest 248/248, typecheck, lint y build limpios | Verificado por el `frontend-verifier` criterio a criterio (2026-10-04, resultado PASS) |
| DoD — CA-01 a CA-08 validados con evidencia concreta | Cumple | CA-01 a CA-08 en `Cumple` | Cierra con la verificación independiente de F10 del 2026-10-04 |
| DoD — Sin cambios de esquema; opera sobre la tabla de usuarios de [[HU-001-registrar-cuenta-de-usuario]] | Cumple | Ninguna migración posterior a V1 toca las columnas del perfil; D25 no añadió campos, así que la excepción prevista en el ítem («salvo que INC-006 obligue a añadir algún campo») no se activó; `ProfileUseCase` solo usa `UserRepository#updateContact` | — |
| DoD — Conjunto editable declarado en el servidor y nada fuera de él se aplica, aunque el cliente lo envíe | Cumple | `ProfilePolicy:22-38` (dos listas explícitas, `EDITABLE` y `FIXED`, en el dominio); `ProfileUseCase:54` llama a `ProfilePolicy.requireOnlyEditable(sentFields)` **antes** de abrir transacción; PIT:175; PPT:21 `theEditableAndFixedFieldsAreDeclared` | La política vive en el dominio, no en el DTO ni en el controlador |
| DoD — Ambas operaciones resuelven el usuario desde el contexto de seguridad, no de un identificador del cliente | Cumple | `MeController:85` (`me`) y `:91` (`update`), los dos con `CurrentUser.id(auth)`; PIT:210 (el `id` del cuerpo se ignora) | — |
| DoD — Nadie accede ni modifica el perfil ajeno; la comprobación de titularidad usa el componente único de ownership | Cumple | No hay ruta con identificador de usuario ajeno (PIT:210 → 403 en las dos, por `SecurityConfig:94`); `ProfileUseCase:55` usa `application/shared/Ownership#requireOwned`, el componente único de [[HU-005-autorizar-peticiones-por-rol-y-ownership]]; `ProfileUseCase:41` (`get`) **no** lo invoca, porque el id ya sale del token y la comprobación sería una tautología | Ítem reescrito el 2026-09-30 (ver historial): antes exigía que «**ambas** operaciones» invocaran el componente, lo que describía el mecanismo y no el resultado. El comportamiento es correcto: el perfil ajeno no es alcanzable por ninguna vía |
| DoD — La respuesta del perfil nunca incluye la credencial en ninguna forma | Cumple | PIT:107 (el cuerpo crudo no contiene el `password_hash` real) y PIT:267 (la lista de campos de `UserResponse` es cerrada y no incluye ninguno de credencial) | — |
| DoD — Los campos que `citas-web` presenta como editables coinciden exactamente con los que `citas-api` acepta | Cumple | `citas-web/src/profileAndPassword.test.tsx:66` (la pantalla separa editables y de solo lectura según el servidor) y `:108` («DoD HU-008: no es más estricta que el servidor»); `ProfilePolicy:22` (`EDITABLE`); verificación independiente del 2026-10-04 | El `frontend-verifier` confirmó la correspondencia en F10 (PASS) |
| DoD — Pruebas de consulta propia, actualización permitida, campo no editable y perfil ajeno, y pasan | Cumple | PIT:107, :147, :175 y :210; PPT (5 pruebas de dominio); suite completa 484/484 `BUILD SUCCESS` reejecutada por el `backend-verifier` | — |
| DoD — Contrato de los endpoints de perfil reflejado en [[HU-033-publicar-contrato-rest-documentado]] | Cumple | `citas-api/docs/wiki/llm-wiki/wiki/contrato-rest-identidad.md:340` (`GET /api/me` con `affiliation`), `:341` (`PUT /api/me` → 200 · 400 `VALIDATION` · 400 `FIELD_NOT_EDITABLE` con `field` para `email`, `documentType`, `documentNumber`, `password` y `roles`, D25) y `:346` (ampliación de `SecurityConfig` a `/api/me/**`) | Documenta la opción elegida ante un campo no editable —error explícito— que CA-03 dejaba a decidir. `llm-wiki/` queda fuera del límite de escritura de esta skill |
| DoD — Trazabilidad de esta HU y de [[EP-002-perfil-y-afiliacion-del-paciente]] actualizada | Cumple | Esta matriz, el historial de validación y las notas; en [[EP-002-perfil-y-afiliacion-del-paciente]] las anotaciones de INC-006 e INC-007 con la decisión que las cierra; `docs/wiki/scrum/README.md` | — |

## Historial de validación

- 2026-10-04 — **Verificación independiente de F10.** Backend verificado leyendo código y pruebas (suite 513/513) y frontend con vitest 248/248, typecheck, lint y build limpios: CA-01 a CA-08 y toda la DoD en `Cumple` (PASS). Estado: `En validación` → `Completada`. **Decisión directa del usuario (2026-10-04):** confirma la reescritura del ítem de DoD de ownership del 2026-09-30 (resultado y componente único donde la comprobación puede fallar, no «ambas operaciones»).
- 2026-09-30 — **Matriz de evidencia recolectada del repositorio.** CA-01 a CA-07 y toda la DoD de backend, de contrato y de trazabilidad en `Cumple`. Estado: `Aprobada` → `En validación`. **No pasa a `Completada`**: CA-08 (pantalla de perfil) y el ítem de DoD sobre la coincidencia exacta de campos editables entre cliente y servidor quedan en `Pendiente`, y el ítem «CA-01 a CA-08 validados» queda en `No cumple` por arrastre de CA-08. Falta la verificación de frontend criterio a criterio y la prueba manual en navegador de F10.
- 2026-09-30 — El ítem de DoD del ownership exigía que «**ambas** operaciones invocan el componente de verificación». `ProfileUseCase.update:55` lo invoca; `ProfileUseCase.get:41` no, porque el id del usuario ya sale del token y comparar ese id contra sí mismo sería una tautología. El ítem estaba escrito sobre el **mecanismo** y no sobre el resultado, así que se reescribe en términos del resultado —nadie accede ni modifica el perfil ajeno— conservando la exigencia de usar el componente único allí donde la comprobación puede fallar. No se relaja nada: CA-04 y CA-05 siguen exigiendo el rechazo, y PIT:210 lo demuestra por las dos vías. Aprobación **directa del usuario**.
- 2026-09-25 — Aprobada por **aprobación delegada** del usuario para S4 (D15, PLAN_RETOMA_S4.md). Alcance en `PLAN_RETOMA_S4.md` §3 (bloque «Cuenta», fase F6) y decisiones D15–D30 en [[dec-006-decisiones-s4-ciclo-de-vida]]. Ningún criterio contradice D25: CA-02 y CA-03 se escribieron sobre el mecanismo, y CA-07 se resuelve por su propia rama "si el identificador no es editable" (CA-03). El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- **Resuelta (D25, provisional bajo delegación):** INC-006 (ver [[EP-002-perfil-y-afiliacion-del-paciente]]): son **editables** nombres, apellidos y teléfono; quedan **fijos** el email (es la credencial de login) y el tipo y número de documento (identidad única de la tabla `users`, V1) ([[dec-006-decisiones-s4-ciclo-de-vida]]). En CA-02 el campo editable de la prueba es uno de esos tres; en CA-03 el no editable es el email o el documento; CA-07 queda cubierto por CA-03, porque ningún identificador único es editable. La nota sobre sesiones y tokens de recuperación ligados a un email cambiado deja de aplicar.
- D29 (política de contraseña) figura en `PLAN_RETOMA_S4.md` §2 como decisión que afecta a esta HU, pero HU-008 no cambia contraseñas (el cambio vive en [[HU-007-restablecer-contrasena-con-token]], ver "Fuera de alcance"). No se añade ningún criterio de contraseña aquí; si el usuario quiere cambio de contraseña desde el perfil, es alcance nuevo y requiere su propia HU.
- CA-03 admitía dos comportamientos válidos ante un campo no editable —error explícito o ignorar el valor— porque el PRD no fija cuál. **Elegido el error explícito** y documentado: 400 `FIELD_NOT_EDITABLE` con el campo en `field` (`ProfilePolicy:22-38`, `contrato-rest-identidad.md:341`). Cualquier campo **desconocido** sí se ignora, como en el resto de la API. Ambas ramas están probadas en `ProfileIntegrationTest:175`.
- ~~Si la decisión sobre INC-006 deja el email como editable, habrá que definir qué ocurre con las sesiones abiertas y con los tokens de recuperación asociados al email anterior; el PRD no lo trata.~~ **No aplica:** D25 dejó el email **fijo** (es la credencial de login), así que ninguna sesión ni token de recuperación puede quedar huérfano por un cambio de email desde el perfil.
- El PRD no exige auditoría de cambios sobre el perfil: RF-19 limita la auditoría a los cambios de estado de las citas.
