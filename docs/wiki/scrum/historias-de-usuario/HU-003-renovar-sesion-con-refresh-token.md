---
id: HU-003
tipo: historia-de-usuario
titulo: "Renovar sesión con refresh token"
estado: Aprobada
epica: "[[EP-001-identidad-y-acceso-seguro]]"
requisitos: [RF-02]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 1"
dependencias:
  - "[[HU-002-iniciar-sesion-con-jwt]]"
relacionadas:
  - "[[HU-004-cerrar-sesion-revocando-refresh-token]]"
  - "[[HU-033-publicar-contrato-rest-documentado]]"
---

# HU-003 — Renovar sesión con refresh token

## Historia de usuario

**COMO** usuario autenticado
**QUIERO** renovar mi access token expirado presentando mi refresh token
**PARA** seguir trabajando sin volver a escribir mis credenciales

> Como usuario autenticado, quiero renovar mi access token expirado presentando mi refresh token para seguir trabajando sin volver a escribir mis credenciales.

## Contexto y descripción

RF-02 exige que el sistema permita refresh además del login. El access token emitido en [[HU-002-iniciar-sesion-con-jwt]] es de corta duración; sin un mecanismo de renovación, el usuario quedaría expulsado a mitad de una gestión de citas y tendría que autenticarse de nuevo constantemente.

Esta HU añade el endpoint de renovación en `citas-api` y la renovación transparente en `citas-web`: cuando la API responde no autorizado por expiración del access token, el cliente intenta una renovación con el refresh token guardado y reintenta la petición original; si la renovación falla, la sesión termina y el usuario vuelve al login.

El refresh token se persiste únicamente como hash (regla heredada de HU-002), por lo que la validación consiste en localizar el registro correspondiente y comparar contra su hash, además de comprobar que no esté expirado ni revocado.

Pertenece al alcance de la sesión S2.

## Alcance

- Endpoint de renovación de sesión en `citas-api` que recibe el refresh token en el cuerpo de la petición.
- Validación del refresh token contra su hash almacenado, su fecha de expiración y su marca de revocación.
- Emisión de un nuevo access token con la identidad y los roles vigentes del usuario.
- Rechazo diferenciado y sin filtración de información para refresh token inexistente, expirado o revocado.
- Renovación transparente desde `citas-web` ante una respuesta de no autorizado por expiración, con reintento de la petición original.
- Finalización de la sesión en el cliente y redirección al login cuando la renovación es rechazada.

## Fuera de alcance

- Revocación explícita del refresh token por cierre de sesión, que se cubre en [[HU-004-cerrar-sesion-revocando-refresh-token]].
- Rotación del refresh token en cada renovación: el PRD no la exige; queda registrada como decisión pendiente en las notas.
- Emisión inicial del par de tokens, que pertenece a [[HU-002-iniciar-sesion-con-jwt]].
- Autorización por rol y ownership de los endpoints renovados, que vive en [[HU-005-autorizar-peticiones-por-rol-y-ownership]].

## Reglas de negocio

- El refresh token permite obtener un nuevo access token sin volver a presentar credenciales (RF-02).
- Un refresh token inexistente, expirado o revocado no renueva la sesión (RF-02).
- El refresh token se valida contra su hash almacenado; nunca se guarda ni se compara en claro (PRD §8).
- El nuevo access token conserva la corta duración y transporta los roles vigentes del usuario (RF-02).
- Ni el refresh token ni el access token emitido se escriben en logs (PRD §8).
- El refresh token no viaja en la URL ni en parámetros de consulta, para no quedar registrado en historiales ni en trazas de servidor (PRD §8).
- Un usuario desactivado no puede renovar su sesión aunque conserve un refresh token vigente.

## Dependencias y relaciones

- Épica: [[EP-001-identidad-y-acceso-seguro]]
- Dependencias: [[HU-002-iniciar-sesion-con-jwt]]
- Relacionadas: [[HU-004-cerrar-sesion-revocando-refresh-token]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** El caso de uso del backend es acotado y reutiliza la tabla y el adaptador de tokens ya introducidos por HU-002. La dificultad real está en el cliente: interceptar la respuesta de no autorizado, disparar una única renovación aunque varias peticiones fallen a la vez y reintentar sin duplicar efectos es un comportamiento delicado de implementar y de probar.

## Tareas de desarrollo

- [ ] **T-01 — Modelar la validación del refresh token en el dominio**
  Dificultad: Bajo
  Descripción: Expresar en el dominio las condiciones que hacen utilizable un refresh token —existente, no expirado y no revocado— y el resultado de la renovación, sin dependencias de framework.

- [ ] **T-02 — Implementar el caso de uso de renovación**
  Dificultad: Medio
  Descripción: Caso de uso de aplicación que localiza el refresh token por su hash mediante un puerto de repositorio, comprueba vigencia y revocación, verifica que el usuario siga activo y solicita al puerto de tokens un nuevo access token con los roles actuales.

- [ ] **T-03 — Exponer el adaptador REST de renovación**
  Dificultad: Bajo
  Descripción: Controlador que recibe el refresh token en el cuerpo de la petición, nunca en la ruta ni en la cadena de consulta, devuelve el nuevo access token y responde con un error de no autorizado uniforme ante cualquier token no utilizable.

- [ ] **T-04 — Ajustar la configuración de seguridad para el endpoint de renovación**
  Dificultad: Bajo
  Descripción: Declarar el endpoint de renovación como accesible sin access token válido pero sujeto a la validación del refresh token, manteniendo el resto de rutas protegidas y el CORS explícito hacia `citas-web`.

- [ ] **T-05 — Implementar la renovación transparente en citas-web**
  Dificultad: Alto
  Descripción: Interceptor del cliente REST que, ante una respuesta de no autorizado por expiración, lanza una sola renovación compartida entre las peticiones concurrentes, reintenta la petición original con el nuevo access token y, si la renovación se rechaza, limpia la sesión y redirige al login.

- [ ] **T-06 — Verificar la ausencia del refresh token en logs y URL**
  Dificultad: Bajo
  Descripción: Revisar y ajustar la configuración de trazas del backend y del cliente para que ningún nivel de log imprima el refresh token y para que no se construya ninguna ruta que lo incluya.

- [ ] **T-07 — Pruebas de renovación**
  Dificultad: Medio
  Descripción: Pruebas del caso de uso con dobles de prueba y pruebas de integración REST para renovación correcta, token inexistente, token expirado, token revocado y usuario desactivado.

## Criterios de aceptación

### CA-01 — Renovación correcta emite un nuevo access token

**Dado** un usuario activo que inició sesión y conserva su refresh token vigente y no revocado
**Cuando** invoca el endpoint de renovación presentando ese refresh token
**Entonces** la API responde con éxito devolviendo un access token nuevo, distinto del anterior, cuya expiración es posterior a la del token que sustituye.

### CA-02 — El access token renovado sirve para operar

**Dado** un access token obtenido mediante renovación
**Cuando** se invoca con él un endpoint protegido de la API
**Entonces** la petición se atiende y el contexto de seguridad contiene la misma identidad y los mismos roles vigentes del usuario.

### CA-03 — Refresh token inexistente rechazado

**Dado** una cadena con forma de refresh token que no corresponde a ningún registro almacenado
**Cuando** se invoca el endpoint de renovación con ella
**Entonces** la API responde con un código de no autorizado, no emite ningún token y el mensaje no revela si el token existió alguna vez.

### CA-04 — Refresh token expirado rechazado

**Dado** un refresh token cuyo registro tiene una fecha de expiración anterior al instante actual
**Cuando** se invoca el endpoint de renovación con él
**Entonces** la API responde con un código de no autorizado y no emite ningún access token.

### CA-05 — Refresh token revocado rechazado

**Dado** un refresh token cuyo registro está marcado como revocado
**Cuando** se invoca el endpoint de renovación con él
**Entonces** la API responde con un código de no autorizado y no emite ningún access token.

### CA-06 — Renovación transparente desde el frontend

**Dado** un usuario con sesión iniciada en `citas-web` cuyo access token ha expirado y cuyo refresh token sigue vigente
**Cuando** la aplicación ejecuta una petición a un endpoint protegido y recibe una respuesta de no autorizado por expiración
**Entonces** el cliente renueva el access token, reintenta la misma petición, muestra el resultado y en ningún momento pide credenciales al usuario.

### CA-07 — Fallo de renovación termina la sesión en el cliente

**Dado** un usuario con sesión iniciada en `citas-web` cuyo refresh token está revocado o expirado
**Cuando** la aplicación intenta renovar tras una respuesta de no autorizado
**Entonces** la renovación es rechazada, el cliente elimina el estado de sesión almacenado y redirige a la pantalla de login.

### CA-08 — El refresh token no aparece en logs ni en la URL

**Dado** una renovación correcta y una renovación rechazada
**Cuando** se inspeccionan la salida de log de `citas-api`, la salida de consola de `citas-web` y las rutas invocadas
**Entonces** el valor del refresh token no aparece en ninguna traza y ninguna petición lo transporta en la ruta ni en la cadena de consulta.

## Definition of Done

- [ ] Los criterios CA-01 a CA-08 están validados con evidencia concreta.
- [ ] La renovación reutiliza la tabla de refresh tokens creada en [[HU-002-iniciar-sesion-con-jwt]]; esta HU no introduce cambios de esquema y por tanto no requiere una migración Flyway adicional.
- [ ] El caso de uso de renovación vive en la capa de aplicación y accede al repositorio de tokens y al emisor de JWT a través de puertos, sin depender del adaptador concreto.
- [ ] Las tres causas de rechazo —token inexistente, expirado y revocado— producen la misma respuesta observable desde fuera, sin distinguir el motivo al cliente.
- [ ] El interceptor de `citas-web` no dispara renovaciones simultáneas duplicadas cuando varias peticiones fallan a la vez por expiración.
- [ ] La URL del backend usada por el interceptor proviene de la configuración de entorno de `citas-web`, no está escrita en el código.
- [ ] Existen pruebas automatizadas de renovación correcta y de los tres casos de rechazo, y pasan.
- [ ] El contrato del endpoint de renovación está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
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
| DoD | Pendiente | — | — |

## Historial de validación

- 2026-09-16 — Estado `Aprobada` por **aprobación delegada**: el usuario eligió ejecutar S2 en modo autónomo, autorizando al agente a asumir las aprobaciones de HU. Alcance: GOAL_01 (registro + login JWT + refresh + logout).
- Sesión S2 — HU creada y dejada en estado `Pendiente de aprobación` como candidata al alcance de S2.

## Notas y decisiones

- Incógnita abierta **INC-002** (ver [[EP-001-identidad-y-acceso-seguro]]): el PRD no fija la duración concreta de access ni de refresh token. CA-01 solo exige que el token renovado expire después del sustituido, sin comprometer valores.
- Incógnita abierta: el PRD no indica si la renovación debe rotar el refresh token emitiendo uno nuevo y revocando el anterior. Esta HU asume que el refresh token se reutiliza hasta su expiración o revocación; si se decide rotar, CA-01 debe ampliarse para cubrir la entrega y la invalidación del token anterior.
- Incógnita abierta: el PRD no define el comportamiento ante un refresh token reutilizado tras haber sido revocado (posible indicio de robo). No se especifica ninguna medida de detección hasta que exista decisión humana.
- El mecanismo de almacenamiento del refresh token en el cliente se hereda de [[HU-002-iniciar-sesion-con-jwt]] y debe mantener la restricción de no exponerlo en logs ni en la URL.
