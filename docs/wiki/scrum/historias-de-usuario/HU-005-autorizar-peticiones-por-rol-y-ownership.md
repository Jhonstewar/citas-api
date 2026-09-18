---
id: HU-005
tipo: historia-de-usuario
titulo: "Autorizar peticiones por rol y ownership"
estado: Aprobada
epica: "[[EP-001-identidad-y-acceso-seguro]]"
requisitos: [RF-02]
esfuerzo: "Alto"
sprint_sugerido: "Sprint 1"
dependencias:
  - "[[HU-002-iniciar-sesion-con-jwt]]"
relacionadas:
  - "[[HU-025-consultar-mis-citas-y-detalle]]"
  - "[[HU-020-consultar-agenda-de-citas-aprobadas]]"
  - "[[HU-029-consultar-bandeja-administrativa]]"
---

# HU-005 — Autorizar peticiones por rol y ownership

## Historia de usuario

**COMO** responsable de la seguridad del sistema
**QUIERO** que cada petición se autorice por el rol del usuario y por la propiedad del recurso
**PARA** que nadie acceda a datos o acciones que no le corresponden

> Como responsable de la seguridad del sistema, quiero que cada petición se autorice por el rol del usuario y por la propiedad del recurso para que nadie acceda a datos o acciones que no le corresponden.

## Contexto y descripción

RF-02 exige que los roles formen parte del contexto de autorización y el PRD §8 declara la autorización por rol y ownership como seguridad mínima obligatoria. [[HU-002-iniciar-sesion-con-jwt]] ya deja los roles disponibles en el contexto de seguridad de cada petición; esta HU define qué se hace con ellos.

Son dos comprobaciones distintas y complementarias. La primera, por rol, responde a "¿este tipo de actor puede ejecutar esta operación?": solo ADMIN gestiona catálogos, profesionales y decisiones administrativas; solo PROFESSIONAL gestiona su propia agenda; solo USER opera sus citas. La segunda, por ownership, responde a "¿este recurso concreto le pertenece a quien lo pide?": tener rol USER no habilita a leer las citas de otro USER, y RF-16 prohíbe expresamente que un PROFESSIONAL vea datos de usuarios ajenos a sus propias citas.

Esta es la HU transversal de autorización: describe el mecanismo reutilizable —la declaración de rol requerido por operación y la verificación de propiedad sobre el recurso— y no la protección concreta de cada endpoint, que cada HU funcional aplica en su propio alcance. Por eso el resto de épicas la referencian como dependencia.

También fija la distinción observable entre no estar autenticado y estar autenticado con rol o propiedad insuficientes, para que el frontend pueda reaccionar de forma diferente: en un caso llevar al login, en el otro informar de falta de permiso.

Pertenece al alcance de la sesión S2.

## Alcance

- Mecanismo de autorización por rol aplicable de forma declarativa sobre las operaciones de `citas-api`.
- Mecanismo de verificación de ownership sobre recursos que pertenecen a un usuario concreto.
- Regla de rol para ADMIN sobre catálogos configurables, gestión de profesionales y decisiones administrativas sobre solicitudes.
- Regla de rol para PROFESSIONAL sobre la gestión de su propia agenda y la consulta de sus propias citas.
- Regla de rol para USER sobre la operación de sus propias citas.
- Restricción de RF-16: un PROFESSIONAL no accede a datos de usuarios que no estén asociados a sus propias citas.
- Respuestas diferenciadas para petición no autenticada frente a rol o propiedad insuficientes.
- Manejo en `citas-web` de ambas respuestas y ocultación de las acciones que el rol del usuario no puede ejecutar.
- Denegación por defecto: una operación sin regla de autorización declarada queda inaccesible.

## Fuera de alcance

- La protección endpoint por endpoint de cada funcionalidad: cada HU funcional aplica este mecanismo dentro de su propio alcance y lo declara en su DoD.
- La autenticación y la emisión de tokens, que pertenecen a [[HU-002-iniciar-sesion-con-jwt]].
- Permisos de grano fino distintos del rol (por ejemplo permisos individuales por operación): el PRD solo define roles.
- Delegación o suplantación entre usuarios: no está en el PRD.
- Reglas de negocio sobre estados de cita, que viven en sus propias épicas aunque se apoyen en esta autorización.

## Reglas de negocio

- Los roles del usuario forman parte del contexto de autorización de cada petición (RF-02).
- La autorización combina rol y ownership sobre el recurso (PRD §8).
- Solo ADMIN gestiona catálogos configurables, profesionales y decisiones administrativas sobre solicitudes y reprogramaciones (RF-06, RF-07, RF-12, RF-15, RF-18).
- Solo PROFESSIONAL gestiona su propia agenda y consulta su propio calendario (RF-08, RF-16).
- Solo USER opera sus propias citas (RF-11, RF-13, RF-14, RF-15).
- Un USER no lee ni modifica recursos pertenecientes a otro USER (PRD §8).
- Un PROFESSIONAL no accede a datos de usuarios fuera de sus propias citas (RF-16).
- Una petición sin autenticación válida y una petición autenticada con rol o propiedad insuficientes producen respuestas distinguibles entre sí.
- Toda operación que no declare explícitamente su regla de autorización se deniega por defecto.

## Dependencias y relaciones

- Épica: [[EP-001-identidad-y-acceso-seguro]]
- Dependencias: [[HU-002-iniciar-sesion-con-jwt]]
- Relacionadas: [[HU-025-consultar-mis-citas-y-detalle]], [[HU-020-consultar-agenda-de-citas-aprobadas]], [[HU-029-consultar-bandeja-administrativa]], [[HU-008-consultar-y-actualizar-perfil]]

## Esfuerzo

**Nivel:** Alto

**Justificación de dificultad:** Es una capacidad transversal que condiciona a todas las épicas posteriores. Exige decidir y montar dos mecanismos distintos —autorización declarativa por rol y verificación de propiedad sobre el recurso, esta última dependiente de consultar el dato antes de responder—, sin filtrar por el código de respuesta si el recurso existe. Añade la diferenciación entre no autenticado y no autorizado en toda la cadena de seguridad y su reflejo en la navegación y la interfaz del frontend. Un error aquí se propaga a cada funcionalidad construida encima.

## Tareas de desarrollo

- [ ] **T-01 — Definir el modelo de roles y permisos de operación**
  Dificultad: Medio
  Descripción: Enumerar en el dominio los roles del sistema y expresar qué familia de operaciones corresponde a cada uno, de forma que la regla sea consultable desde la aplicación sin depender del framework de seguridad.

- [ ] **T-02 — Expresar la regla de ownership en el dominio**
  Dificultad: Medio
  Descripción: Modelar qué significa que un recurso pertenece a un usuario y qué relación habilita a un PROFESSIONAL a ver datos de un paciente, es decir, la existencia de una cita propia que los vincule (RF-16).

- [ ] **T-03 — Configurar la autorización por rol en Spring Security**
  Dificultad: Alto
  Descripción: Establecer la política de denegación por defecto, declarar el rol requerido por familia de operaciones y asegurar que los roles del access token se traduzcan a autoridades reconocidas por la cadena de filtros.

- [ ] **T-04 — Implementar la verificación de ownership en la capa de aplicación**
  Dificultad: Alto
  Descripción: Componente reutilizable que, dado el usuario autenticado y el recurso solicitado, determina si existe propiedad o vínculo autorizado, y que los casos de uso invocan antes de devolver o modificar el recurso.

- [ ] **T-05 — Unificar el manejo de errores de autenticación y autorización**
  Dificultad: Medio
  Descripción: Manejadores que produzcan una respuesta de no autenticado cuando falta o es inválido el access token y una respuesta distinta de permiso insuficiente cuando el rol o la propiedad no alcanzan, ambas con cuerpo de error uniforme y sin detalles internos.

- [ ] **T-06 — Adaptar la navegación y la interfaz de citas-web a los roles**
  Dificultad: Medio
  Descripción: Restringir las rutas de la aplicación según el rol de la sesión, ocultar las acciones no permitidas y reaccionar de forma distinta a la respuesta de no autenticado (llevar al login) y a la de permiso insuficiente (informar sin cerrar la sesión).

- [ ] **T-07 — Verificar la ausencia de fugas de datos en las respuestas**
  Dificultad: Medio
  Descripción: Revisar que las consultas filtren por el usuario autenticado en origen y que la respuesta ante un recurso ajeno no permita deducir su existencia ni su contenido.

- [ ] **T-08 — Pruebas de autorización por rol y ownership**
  Dificultad: Alto
  Descripción: Pruebas de integración que recorran, para una operación representativa de cada rol, los casos de acceso permitido, acceso sin autenticación, acceso con rol insuficiente y acceso a un recurso de otro usuario, incluyendo el caso de PROFESSIONAL frente a un paciente sin cita propia.

## Criterios de aceptación

### CA-01 — Petición sin autenticación diferenciada de rol insuficiente

**Dado** una operación reservada a ADMIN
**Cuando** se invoca primero sin access token y después con el access token válido de un USER
**Entonces** la primera respuesta indica falta de autenticación y la segunda indica permiso insuficiente, son códigos distintos entre sí y en ninguno de los dos casos se ejecuta la operación.

### CA-02 — Solo ADMIN ejecuta operaciones administrativas

**Dado** una operación de gestión de catálogos configurables, de gestión de profesionales o de decisión administrativa sobre una solicitud
**Cuando** se invoca con el access token de un USER y con el de un PROFESSIONAL
**Entonces** ambas peticiones se rechazan por permiso insuficiente, y la misma operación invocada con el access token de un ADMIN se ejecuta.

### CA-03 — Solo PROFESSIONAL gestiona su agenda

**Dado** una operación de gestión de la agenda de un profesional
**Cuando** se invoca con el access token de un USER y con el de un ADMIN
**Entonces** ambas peticiones se rechazan por permiso insuficiente, y la misma operación invocada por el PROFESSIONAL propietario de esa agenda se ejecuta.

### CA-04 — Solo USER opera sus propias citas

**Dado** una operación sobre una cita de un paciente
**Cuando** se invoca con el access token de un PROFESSIONAL o de un ADMIN que no tienen atribuida esa operación
**Entonces** la petición se rechaza por permiso insuficiente, y la misma operación invocada por el USER titular de la cita se ejecuta.

### CA-05 — Un USER no accede a recursos de otro USER

**Dado** dos usuarios `USER` distintos, cada uno con recursos propios en el sistema
**Cuando** el primero invoca, con su access token válido, una lectura y luego una modificación de un recurso perteneciente al segundo
**Entonces** ambas peticiones se rechazan sin devolver ningún dato del recurso ajeno y sin modificarlo.

### CA-06 — Un PROFESSIONAL no ve datos de usuarios ajenos a sus citas

**Dado** un PROFESSIONAL y dos pacientes, uno con una cita asignada a ese profesional y otro sin ninguna cita con él
**Cuando** el profesional consulta datos de ambos pacientes
**Entonces** obtiene los datos del paciente vinculado por una cita propia y la consulta del paciente sin cita con él se rechaza sin devolver ningún dato (RF-16).

### CA-07 — Denegación por defecto

**Dado** un endpoint de la API que no declara explícitamente una regla de autorización
**Cuando** se invoca con un access token válido de cualquier rol
**Entonces** la petición se rechaza por permiso insuficiente en lugar de atenderse.

### CA-08 — La interfaz refleja el rol de la sesión

**Dado** usuarios autenticados en `citas-web` con rol USER, PROFESSIONAL y ADMIN respectivamente
**Cuando** cada uno navega por la aplicación
**Entonces** cada sesión solo muestra las rutas y las acciones correspondientes a su rol, y el acceso manual a una ruta de otro rol no presenta la vista.

### CA-09 — El frontend distingue sesión caducada de permiso insuficiente

**Dado** una sesión activa en `citas-web`
**Cuando** la API responde falta de autenticación en un caso y permiso insuficiente en otro
**Entonces** en el primero la aplicación lleva al usuario al login y en el segundo muestra un aviso de permiso insuficiente manteniendo la sesión abierta.

## Definition of Done

- [ ] Los criterios CA-01 a CA-09 están validados con evidencia concreta.
- [ ] Esta HU no altera el esquema de datos y por tanto no incorpora migración Flyway; se apoya en las tablas de usuarios y roles creadas en [[HU-001-registrar-cuenta-de-usuario]].
- [ ] La cadena de seguridad aplica denegación por defecto: ninguna ruta queda accesible por omisión de regla.
- [ ] Existe un componente reutilizable de verificación de ownership invocable desde cualquier caso de uso, documentado para que las HU funcionales posteriores lo usen en lugar de reimplementar la comprobación.
- [ ] La regla de rol y la de ownership están expresadas en dominio y aplicación, sin que la lógica de propiedad quede únicamente en anotaciones del adaptador REST.
- [ ] Las respuestas de no autenticado y de permiso insuficiente usan códigos distintos y un cuerpo de error uniforme que no expone detalles internos ni la existencia de recursos ajenos.
- [ ] La restricción de RF-16 sobre los datos de usuarios visibles al PROFESSIONAL está implementada y probada.
- [ ] El control de rutas y acciones por rol en `citas-web` es coherente con las reglas aplicadas por `citas-api`: ninguna acción visible en el cliente es rechazada por rol en el servidor y ninguna acción oculta es ejecutable saltándose el cliente.
- [ ] Existen pruebas automatizadas de acceso permitido, no autenticado, rol insuficiente y recurso de otro usuario, y pasan.
- [ ] La trazabilidad de esta HU y de [[EP-001-identidad-y-acceso-seguro]] está actualizada en `docs/wiki/scrum/`.

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
- Sesión S2 — HU creada y dejada en estado `Pendiente de aprobación` como candidata al alcance de S2.

## Notas y decisiones

- Esta HU describe el mecanismo transversal de autorización, no la protección de endpoints concretos. Cada HU funcional declara en su propia DoD que aplica este mecanismo sobre sus operaciones.
- Incógnita abierta: el PRD no define si un ADMIN puede consultar los datos personales completos de los pacientes o solo los necesarios para decidir sobre una solicitud (RF-12, RF-18). Los criterios de esta HU no atribuyen a ADMIN acceso a datos de perfil de paciente hasta que exista decisión humana.
- Incógnita abierta: el PRD no aclara si un ADMIN puede cancelar o reprogramar una cita en nombre de un USER. CA-04 se limita a comprobar que la operación del paciente exige ser su titular, sin cerrar la puerta a una atribución administrativa futura.
- Incógnita abierta **INC-004** (ver [[EP-001-identidad-y-acceso-seguro]]): al asumirse que la primera cuenta ADMIN se precarga por seed, las pruebas de rol administrativo dependen de esa carga inicial.
- El PRD no fija los códigos de respuesta concretos; los criterios solo exigen que no autenticado y permiso insuficiente sean distinguibles entre sí y coherentes en toda la API. La elección concreta debe quedar documentada en [[HU-033-publicar-contrato-rest-documentado]].
