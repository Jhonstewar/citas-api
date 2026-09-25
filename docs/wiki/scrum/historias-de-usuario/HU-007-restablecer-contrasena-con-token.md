---
id: HU-007
tipo: historia-de-usuario
titulo: "Restablecer contraseña con token"
estado: Aprobada
epica: "[[EP-001-identidad-y-acceso-seguro]]"
requisitos: [RF-03]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 2"
dependencias:
  - "[[HU-006-solicitar-recuperacion-de-contrasena]]"
relacionadas:
  - "[[HU-002-iniciar-sesion-con-jwt]]"
---

# HU-007 — Restablecer contraseña con token

## Historia de usuario

**COMO** usuario que solicitó recuperar su contraseña
**QUIERO** definir una contraseña nueva presentando el token recibido
**PARA** volver a entrar al sistema

> Como usuario que solicitó recuperar su contraseña, quiero definir una contraseña nueva presentando el token recibido para volver a entrar al sistema.

## Contexto y descripción

RF-03 cierra el flujo de recuperación exigiendo que cambiar la contraseña invalide y consuma el token. Esta HU cubre esa segunda mitad: el usuario llega con el token generado en [[HU-006-solicitar-recuperacion-de-contrasena]], escribe una contraseña nueva y recupera el acceso.

La validación del token sigue el mismo patrón que el resto de la épica: se localiza el registro comparando contra el hash almacenado y se comprueba que no esté expirado ni consumido. La condición de un solo uso es verificable de forma directa, repitiendo la operación con el mismo token y comprobando que la segunda vez se rechaza.

La contraseña nueva se guarda con hash adaptativo, igual que en [[HU-001-registrar-cuenta-de-usuario]], y la prueba definitiva del éxito es de extremo a extremo: tras el cambio el usuario se autentica con la contraseña nueva y no con la anterior.

Queda abierta una decisión que el PRD no define: si restablecer la contraseña debe además revocar los refresh tokens vigentes del usuario. Es relevante porque, si alguien había robado la sesión, cambiar la contraseña sin revocar no le expulsa.

Esta HU cubre la pantalla de cambio de contraseña, una de las pantallas obligatorias del PRD §6.

## Alcance

- Pantalla de cambio de contraseña en `citas-web`, accesible sin sesión, que recibe el token y la contraseña nueva.
- Endpoint público de restablecimiento en `citas-api`.
- Validación del token contra su hash almacenado, su fecha de expiración y su marca de consumo.
- Rechazo de token inexistente, expirado o ya usado.
- Consumo e invalidación del token al completar el cambio, comprobable reintentando.
- Almacenamiento de la contraseña nueva únicamente como hash adaptativo.
- Verificación de extremo a extremo de que el usuario se autentica con la contraseña nueva y no con la anterior.
- Validación server-side de la contraseña recibida.

## Fuera de alcance

- Generación y entrega del token de recuperación, que se cubre en [[HU-006-solicitar-recuperacion-de-contrasena]].
- Revocación de los refresh tokens vigentes al restablecer: no está definida en el PRD; se registra como decisión pendiente en las notas.
- ~~Política de complejidad de contraseña, no definida en el PRD (INC-001).~~ Entra en alcance por D29 (ver notas y CA-08).
- Cambio de contraseña por un usuario autenticado que recuerda la actual: no está descrito en el PRD.
- Notificación al usuario de que su contraseña fue cambiada: el PRD no la exige.

## Reglas de negocio

- El cambio de contraseña se realiza presentando el token temporal recibido (RF-03).
- Cambiar la contraseña invalida y consume el token (RF-03).
- Un token inexistente, expirado o ya consumido no permite restablecer la contraseña (RF-03).
- El token se valida contra su hash almacenado; nunca se compara ni se guarda en claro (PRD §8).
- La contraseña nueva se almacena con hash adaptativo compatible con Spring Security y nunca en texto plano (RF-01, PRD §8).
- Ni la contraseña nueva ni el token aparecen en logs (PRD §8).
- La validación de los datos enviados se ejecuta también en el servidor (PRD §8).
- Tras el cambio, la contraseña anterior deja de ser válida para iniciar sesión.
- La contraseña nueva tiene al menos 8 caracteres, con al menos una letra y un número, validado en el servidor (D29).

## Dependencias y relaciones

- Épica: [[EP-001-identidad-y-acceso-seguro]]
- Dependencias: [[HU-006-solicitar-recuperacion-de-contrasena]]
- Relacionadas: [[HU-002-iniciar-sesion-con-jwt]], [[HU-001-registrar-cuenta-de-usuario]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** El caso de uso reutiliza la tabla y el patrón de hash creados en la HU anterior y el codificador de contraseñas ya configurado, así que no introduce infraestructura nueva. La dificultad está en encadenar correctamente la validación, el cambio y el consumo del token como una sola operación consistente, y en montar la verificación de extremo a extremo que atraviesa recuperación y login.

## Tareas de desarrollo

- [ ] **T-01 — Modelar el consumo del token en el dominio**
  Dificultad: Bajo
  Descripción: Expresar en el dominio las condiciones que hacen utilizable un token de recuperación y la transición a consumido, junto con la regla de que un token consumido no vuelve a servir, sin dependencias de framework.

- [ ] **T-02 — Implementar el caso de uso de restablecimiento**
  Dificultad: Medio
  Descripción: Caso de uso de aplicación que localiza el token por su hash, comprueba vigencia y no consumo, valida la contraseña recibida, actualiza la credencial del usuario mediante el puerto de hashing y marca el token como consumido como parte de la misma operación consistente.

- [ ] **T-03 — Exponer el adaptador REST de restablecimiento**
  Dificultad: Bajo
  Descripción: Controlador público con DTO validado que recibe el token en el cuerpo de la petición y nunca en la ruta, responde sin devolver datos sensibles y emite un error uniforme ante cualquier token no utilizable.

- [ ] **T-04 — Declarar el endpoint como público en la configuración de seguridad**
  Dificultad: Bajo
  Descripción: Añadir la ruta de restablecimiento a las accesibles sin autenticación, manteniendo protegida el resto de la API y el CORS explícito hacia `citas-web`.

- [ ] **T-05 — Construir la pantalla de cambio de contraseña en citas-web**
  Dificultad: Medio
  Descripción: Pantalla accesible sin sesión que recoge el token y la contraseña nueva con confirmación, valida en cliente, consume el endpoint mediante la URL configurable por entorno, presenta los errores de token no utilizable y redirige al login tras el cambio.

- [ ] **T-06 — Verificar la ausencia de token y contraseña en logs**
  Dificultad: Bajo
  Descripción: Revisar la configuración de trazas del backend y del cliente para que ningún nivel de log imprima el token de recuperación ni la contraseña enviada.

- [ ] **T-07 — Pruebas de restablecimiento**
  Dificultad: Medio
  Descripción: Pruebas del caso de uso y de integración REST para cambio correcto, token inexistente, token expirado, token ya consumido y contraseña inválida, más una prueba de extremo a extremo que encadene solicitud, cambio y login con la contraseña nueva y con la anterior.

## Criterios de aceptación

### CA-01 — Restablecimiento correcto

**Dado** un usuario con un token de recuperación vigente y no consumido
**Cuando** envía ese token junto con una contraseña nueva válida
**Entonces** la API responde con éxito, la credencial del usuario queda actualizada y la respuesta no incluye la contraseña ni el token.

### CA-02 — El token queda consumido tras el cambio

**Dado** un token de recuperación con el que ya se completó un restablecimiento
**Cuando** se intenta usar el mismo token para fijar otra contraseña
**Entonces** la API rechaza la petición, no modifica la credencial del usuario y el registro del token permanece marcado como consumido.

### CA-03 — Token inexistente rechazado

**Dado** una cadena con forma de token de recuperación que no corresponde a ningún registro almacenado
**Cuando** se invoca el endpoint de restablecimiento con ella
**Entonces** la API responde con un error y no modifica ninguna credencial.

### CA-04 — Token expirado rechazado

**Dado** un token de recuperación cuyo registro tiene una fecha de expiración anterior al instante actual
**Cuando** se invoca el endpoint de restablecimiento con él
**Entonces** la API responde con un error y no modifica ninguna credencial.

### CA-05 — Autenticación con la contraseña nueva

**Dado** un usuario que completó el restablecimiento
**Cuando** intenta iniciar sesión con la contraseña nueva
**Entonces** el login de [[HU-002-iniciar-sesion-con-jwt]] tiene éxito y se emite el par de tokens.

### CA-06 — La contraseña anterior deja de servir

**Dado** un usuario que completó el restablecimiento
**Cuando** intenta iniciar sesión con la contraseña que tenía antes del cambio
**Entonces** la API responde con el error de credenciales inválidas y no emite ningún token.

### CA-07 — Contraseña nueva almacenada solo como hash

**Dado** un restablecimiento completado correctamente
**Cuando** se consulta la credencial del usuario en la base de datos
**Entonces** el valor almacenado no coincide con la contraseña enviada, corresponde a un hash adaptativo verificable por Spring Security y es distinto del hash que había antes del cambio.

### CA-08 — Contraseña ausente, vacía o fuera de política rechazada en el servidor

**Dado** peticiones de restablecimiento enviadas directamente a la API con un token válido y, en cada una, sin contraseña nueva, con ella vacía, con menos de 8 caracteres (`abc123`), sin ningún número (`abcdefgh`) o sin ninguna letra (`12345678`)
**Cuando** la API procesa cada petición
**Entonces** responde con un error de validación sobre el campo de contraseña, no modifica la credencial y el token no queda consumido (D29).

### CA-09 — Ausencia de token y contraseña en logs

**Dado** un restablecimiento correcto y otro rechazado por token no utilizable
**Cuando** se inspecciona la salida de log de la aplicación para ambas peticiones
**Entonces** no aparece el token de recuperación ni la contraseña enviada.

## Definition of Done

- [ ] Los criterios CA-01 a CA-09 están validados con evidencia concreta.
- [ ] Esta HU reutiliza la tabla de tokens de recuperación creada en [[HU-006-solicitar-recuperacion-de-contrasena]]; no introduce cambios de esquema y por tanto no requiere migración Flyway propia.
- [ ] La actualización de la credencial y el marcado del token como consumido ocurren de forma consistente: no existe un resultado en el que la contraseña cambie y el token siga utilizable, ni al revés.
- [ ] La contraseña nueva se cifra con el mismo codificador adaptativo configurado en [[HU-001-registrar-cuenta-de-usuario]].
- [ ] El dominio del token y de la credencial no depende de Spring ni de JPA, respetando la separación hexagonal.
- [ ] El endpoint de restablecimiento está declarado como público en la configuración de seguridad y el token viaja en el cuerpo de la petición, nunca en la ruta.
- [ ] Existe una prueba automatizada de extremo a extremo que encadena solicitud de recuperación, restablecimiento, login con la contraseña nueva y login fallido con la anterior, cubriendo la coherencia entre `citas-web` y `citas-api` en el flujo completo de recuperación.
- [ ] Existe una prueba automatizada que demuestra el rechazo del segundo uso del mismo token.
- [ ] La pantalla de cambio de contraseña de `citas-web` consume el endpoint usando la URL del backend leída de la configuración de entorno, cubriendo la pantalla obligatoria de recuperación/cambio de contraseña del PRD §6.
- [ ] El contrato del endpoint de restablecimiento está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
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

- 2026-09-25 — CA-08 ajustado a D29: además de ausente o vacía, rechaza una contraseña con menos de 8 caracteres, sin letra o sin número. El punto de "Fuera de alcance" sobre la política de contraseña queda tachado por la misma decisión.
- 2026-09-25 — Aprobada por **aprobación delegada** del usuario para S4 (D15, PLAN_RETOMA_S4.md). Alcance en `PLAN_RETOMA_S4.md` §3 (bloque «Cuenta», fase F7) y decisiones D15–D30 en [[dec-006-decisiones-s4-ciclo-de-vida]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- Decisión pendiente, **no cubierta por D15–D30**: el PRD no define si restablecer la contraseña debe revocar los refresh tokens vigentes del usuario. `PLAN_RETOMA_S4.md` F7 lo incluye como tarea («refresh tokens del usuario revocados»), pero ninguna decisión de [[dec-006-decisiones-s4-ciclo-de-vida]] lo registra, y esta HU lo tiene en "Fuera de alcance". Revocarlos expulsaría cualquier sesión abierta con la contraseña anterior, lo que es coherente con el propósito de recuperar el acceso tras un olvido o una sospecha de robo; no revocarlos evita cerrar sesiones legítimas del propio usuario. Hasta que el usuario (o una decisión registrada) lo confirme, no se escribe ningún criterio que lo exija ni que lo prohíba, y la implementación de F7 que lo haga queda fuera de lo verificable por esta HU.
- **Resuelta (D29, provisional bajo delegación):** INC-001 (ver [[EP-001-identidad-y-acceso-seguro]]): mínimo 8 caracteres con al menos una letra y un número, **también en el servidor**, igual que el cliente. Se aplica solo al fijar una contraseña; las cuentas existentes siguen entrando ([[dec-006-decisiones-s4-ciclo-de-vida]]). CA-08 lo refleja. La misma decisión obliga a revisar la matriz de [[HU-001-registrar-cuenta-de-usuario]] (`Completada`).
- **Resuelta (D27, provisional bajo delegación):** INC-002 (ver [[EP-001-identidad-y-acceso-seguro]]) en lo que toca al token de recuperación: vigencia de 30 minutos, configurable por entorno ([[dec-006-decisiones-s4-ciclo-de-vida]]). CA-04 se sigue apoyando en la fecha de expiración almacenada, no en el valor.
- El PRD no especifica cómo llega el token a la pantalla de cambio de contraseña (escrito por el usuario o transportado en el enlace). Mientras no exista envío real de correo (PRD §9), la pantalla debe admitir que el usuario lo introduzca manualmente.
