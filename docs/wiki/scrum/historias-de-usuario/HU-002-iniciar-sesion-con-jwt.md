---
id: HU-002
tipo: historia-de-usuario
titulo: "Iniciar sesión con JWT"
estado: Aprobada
epica: "[[EP-001-identidad-y-acceso-seguro]]"
requisitos: [RF-02]
esfuerzo: "Alto"
sprint_sugerido: "Sprint 1"
dependencias:
  - "[[HU-001-registrar-cuenta-de-usuario]]"
relacionadas:
  - "[[HU-003-renovar-sesion-con-refresh-token]]"
  - "[[HU-004-cerrar-sesion-revocando-refresh-token]]"
  - "[[HU-005-autorizar-peticiones-por-rol-y-ownership]]"
---

# HU-002 — Iniciar sesión con JWT

## Historia de usuario

**COMO** usuario registrado del sistema
**QUIERO** autenticarme con mi email y mi contraseña y recibir un access token y un refresh token
**PARA** acceder a las funciones que me corresponden según mi rol

> Como usuario registrado del sistema, quiero autenticarme con mi email y mi contraseña y recibir un access token y un refresh token para acceder a las funciones que me corresponden según mi rol.

## Contexto y descripción

RF-02 exige login por email y contraseña, emisión de un access token de corta duración y un refresh token separado, y que los roles formen parte del contexto de autorización. Es la HU que habilita cualquier operación autenticada del producto para los tres actores.

Pertenece al alcance de la sesión S2.

## Alcance

- Pantalla de login en `citas-web` con email y contraseña.
- Endpoint público de autenticación en `citas-api`.
- Verificación de la contraseña contra el hash almacenado.
- Emisión de un access token JWT de corta duración que incluye la identidad y los roles del usuario.
- Emisión de un refresh token con vida más larga, persistido únicamente como hash.
- Rechazo del login de cuentas inactivas.
- Almacenamiento del access token en el cliente y envío en las peticiones posteriores.

## Fuera de alcance

- Renovación del access token, que se cubre en [[HU-003-renovar-sesion-con-refresh-token]].
- Cierre de sesión y revocación, que se cubre en [[HU-004-cerrar-sesion-revocando-refresh-token]].
- Protección de endpoints por rol, que se cubre en [[HU-005-autorizar-peticiones-por-rol-y-ownership]].
- Doble factor y bloqueo por intentos fallidos: no están en el PRD.

## Reglas de negocio

- El login se realiza con email y contraseña (RF-02).
- Access token y refresh token son tokens separados con vidas distintas (RF-02, PRD §8).
- Los roles del usuario forman parte del contexto de autorización transportado por el access token (RF-02).
- El refresh token se persiste solo como hash, nunca en claro.
- El secreto de firma JWT proviene de variable de entorno y nunca del repositorio (PRD §8).
- Ni la contraseña ni los tokens se escriben en logs (PRD §8).
- Un usuario inactivo no puede iniciar sesión.

## Dependencias y relaciones

- Épica: [[EP-001-identidad-y-acceso-seguro]]
- Dependencias: [[HU-001-registrar-cuenta-de-usuario]]
- Relacionadas: [[HU-003-renovar-sesion-con-refresh-token]], [[HU-004-cerrar-sesion-revocando-refresh-token]], [[HU-005-autorizar-peticiones-por-rol-y-ownership]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Alto

**Justificación de dificultad:** Introduce la cadena de filtros de Spring Security, la generación y firma de dos tipos de token, una nueva tabla persistida con hash, la gestión de sesión en el cliente y la configuración por entorno del secreto. Es un cambio transversal entre capas y repositorios con reglas de seguridad que no admiten aproximaciones.

## Tareas de desarrollo

- [ ] **T-01 — Definir los puertos de autenticación en el dominio y la aplicación**
  Dificultad: Medio
  Descripción: Caso de uso de autenticación que recibe credenciales, delega la verificación del hash y la emisión de tokens a puertos, y devuelve el par de tokens junto con la identidad y los roles.

- [ ] **T-02 — Crear la migración Flyway de refresh tokens**
  Dificultad: Bajo
  Descripción: Tabla de refresh tokens con referencia al usuario, hash del token, fecha de expiración y marca de revocación.

- [ ] **T-03 — Implementar el adaptador de emisión y firma de JWT**
  Dificultad: Alto
  Descripción: Componente que emite el access token con identidad y roles como reclamaciones, y el refresh token, ambos firmados con el secreto leído de variable de entorno y con vidas configurables.

- [ ] **T-04 — Implementar el filtro de autenticación de Spring Security**
  Dificultad: Alto
  Descripción: Filtro que valida el access token de cada petición, reconstruye el contexto de seguridad con los roles y rechaza tokens ausentes, malformados, con firma inválida o expirados.

- [ ] **T-05 — Exponer el adaptador REST de login**
  Dificultad: Medio
  Descripción: Controlador público de autenticación con DTO validado, respuesta con el par de tokens, y respuesta de error uniforme e indistinguible para credenciales incorrectas.

- [ ] **T-06 — Construir la pantalla de login y la gestión de sesión en citas-web**
  Dificultad: Medio
  Descripción: Formulario de login, almacenamiento del par de tokens, envío automático del access token en cada petición, redirección según rol y estado de sesión compartido en la aplicación.

- [ ] **T-07 — Pruebas de autenticación**
  Dificultad: Medio
  Descripción: Pruebas del caso de uso y de integración REST para login correcto, credenciales inválidas, usuario inactivo, y acceso a un endpoint protegido con y sin access token.

## Criterios de aceptación

### CA-01 — Login correcto devuelve el par de tokens

**Dado** un usuario registrado y activo con email y contraseña conocidos
**Cuando** envía sus credenciales correctas al endpoint de login
**Entonces** la API responde con éxito devolviendo un access token y un refresh token distintos entre sí, y la respuesta no incluye la contraseña ni su hash.

### CA-02 — El access token transporta identidad y roles

**Dado** un login correcto de un usuario con rol `USER`
**Cuando** se decodifica el access token emitido
**Entonces** contiene el identificador del usuario y la lista de sus roles, y su tiempo de expiración es estrictamente menor que el del refresh token.

### CA-03 — Credenciales incorrectas rechazadas sin revelar la causa

**Dado** un email inexistente en un caso y un email existente con contraseña incorrecta en otro
**Cuando** se intenta iniciar sesión en ambos casos
**Entonces** la API responde en los dos con el mismo código de no autorizado y el mismo mensaje, sin indicar cuál de los dos datos era incorrecto y sin emitir ningún token.

### CA-04 — Usuario inactivo no puede iniciar sesión

**Dado** un usuario cuya cuenta está marcada como inactiva
**Cuando** envía sus credenciales correctas
**Entonces** la API responde con un error de autenticación y no emite ningún token.

### CA-05 — Refresh token persistido solo como hash

**Dado** un login correcto
**Cuando** se consulta el registro del refresh token en la base de datos
**Entonces** el valor almacenado no coincide con el refresh token entregado al cliente y corresponde a su hash.

### CA-06 — Endpoint protegido exige access token válido

**Dado** un endpoint de la API que requiere autenticación
**Cuando** se invoca sin cabecera de autorización, con un token malformado y con un token expirado
**Entonces** la API responde en los tres casos con un código de no autorizado y no ejecuta la operación.

### CA-07 — El secreto de firma proviene del entorno

**Dado** el repositorio `citas-api`
**Cuando** se busca el secreto de firma JWT en el código y en los archivos versionados
**Entonces** no aparece ningún valor real, la configuración lo lee de una variable de entorno y `.env.example` documenta la variable sin valor sensible.

### CA-08 — Ausencia de tokens y contraseñas en logs

**Dado** un login correcto y un login fallido
**Cuando** se inspecciona la salida de log de la aplicación para ambas peticiones
**Entonces** no aparece la contraseña ni ninguno de los dos tokens emitidos.

### CA-09 — Sesión utilizable desde el frontend

**Dado** un usuario que inicia sesión desde la pantalla de login de `citas-web`
**Cuando** navega a una vista que consulta un endpoint protegido
**Entonces** la petición incluye automáticamente el access token y la vista muestra los datos devueltos sin pedir credenciales de nuevo.

## Definition of Done

- [ ] Los criterios CA-01 a CA-09 están validados con evidencia concreta.
- [ ] Existe una migración Flyway versionada para la tabla de refresh tokens con hash y expiración.
- [ ] La cadena de filtros de Spring Security valida el access token en todas las rutas salvo las declaradas explícitamente como públicas.
- [ ] Las duraciones de access token y refresh token son configurables por entorno y están documentadas en `.env.example`.
- [ ] El dominio y la aplicación acceden a la emisión y verificación de tokens a través de puertos, sin depender del adaptador JWT.
- [ ] CORS está declarado explícitamente para el origen de `citas-web` y no se usa una configuración permisiva abierta.
- [ ] Existen pruebas automatizadas de login correcto, credenciales inválidas y acceso a endpoint protegido sin token, y pasan.
- [ ] El contrato de login está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
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

- 2026-09-16 — Estado `Aprobada` por **aprobación delegada**: el usuario eligió ejecutar S2 en modo autónomo, autorizando al agente a asumir las aprobaciones de HU. Alcance: GOAL_01 (registro + login JWT + refresh + logout).
- Sesión S2 — HU creada y dejada en estado `Pendiente de aprobación` como candidata al alcance de S2.

## Notas y decisiones

- Incógnita abierta **INC-002**: las duraciones concretas de access y refresh token no están definidas en el PRD. CA-02 solo exige la relación de orden entre ambas hasta que se fijen valores.
- Incógnita abierta **INC-003**: si `ADMIN` y `PROFESSIONAL` usan este mismo formulario de login o una entrada separada. Esta HU asume un único endpoint de login para los tres roles.
- El mecanismo de almacenamiento del token en el cliente debe evitar exponerlo en logs o en la URL; la elección concreta queda a decisión del desarrollador.
