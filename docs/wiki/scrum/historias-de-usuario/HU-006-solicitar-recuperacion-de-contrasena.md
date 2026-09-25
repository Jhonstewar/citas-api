---
id: HU-006
tipo: historia-de-usuario
titulo: "Solicitar recuperación de contraseña"
estado: Aprobada
epica: "[[EP-001-identidad-y-acceso-seguro]]"
requisitos: [RF-03]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 2"
dependencias:
  - "[[HU-001-registrar-cuenta-de-usuario]]"
relacionadas:
  - "[[HU-007-restablecer-contrasena-con-token]]"
---

# HU-006 — Solicitar recuperación de contraseña

## Historia de usuario

**COMO** usuario que olvidó su contraseña
**QUIERO** solicitar la recuperación indicando mi email
**PARA** recibir un medio temporal que me permita definir una contraseña nueva

> Como usuario que olvidó su contraseña, quiero solicitar la recuperación indicando mi email para recibir un medio temporal que me permita definir una contraseña nueva.

## Contexto y descripción

RF-03 exige que el usuario pueda solicitar la recuperación por email y que el sistema cree un token temporal de un solo uso. Esta HU cubre la primera mitad del flujo —pedir la recuperación y generar el token—; el consumo del token para fijar la nueva contraseña vive en [[HU-007-restablecer-contrasena-con-token]].

El endpoint es público, porque quien lo usa precisamente no puede autenticarse. Eso obliga a dos cuidados. Primero, la respuesta debe ser idéntica exista o no el email indicado: si el sistema contestara de forma distinta, cualquiera podría usar el formulario para averiguar qué correos están registrados. Segundo, el token se persiste únicamente como hash, igual que el refresh token de [[HU-002-iniciar-sesion-con-jwt]], de modo que ni siquiera con acceso a la base de datos pueda reutilizarse.

El PRD §9 deja el envío real de correo fuera del alcance obligatorio y RF-03 autoriza a exponer el token "de forma segura en log/respuesta controlada" en desarrollo para poder completar el ejercicio. El mecanismo exacto y su desactivación fuera de desarrollo están abiertos como INC-005.

Esta HU introduce la tabla de tokens de recuperación, por lo que requiere una migración Flyway propia.

## Alcance

- Pantalla de solicitud de recuperación en `citas-web` con el campo de email, accesible sin sesión.
- Endpoint público de solicitud de recuperación en `citas-api`.
- Generación de un token de recuperación temporal, de un solo uso y con suficiente aleatoriedad.
- Persistencia del token únicamente como hash, con referencia al usuario, fecha de expiración y marca de consumo.
- Respuesta idéntica exista o no el email indicado en el sistema.
- Entrega del token en entorno de laboratorio únicamente en la respuesta controlada, activable por una variable de entorno y desactivada por omisión; nunca en logs (D27, ver notas).
- Migración Flyway que crea la tabla de tokens de recuperación.
- Validación server-side del formato del email recibido.

## Fuera de alcance

- Consumo del token y cambio efectivo de la contraseña, que se cubre en [[HU-007-restablecer-contrasena-con-token]].
- Envío real de correo por SMTP: el PRD lo declara opcional y fuera del alcance obligatorio (PRD §9, RF-03).
- Limitación de frecuencia de solicitudes por email o por origen: el PRD no la exige; queda anotada como incógnita.
- Recuperación mediante preguntas de seguridad, SMS o cualquier canal distinto del email (PRD §9).
- Cambio de contraseña por un usuario que sí recuerda la actual: no está descrito en el PRD.

## Reglas de negocio

- La recuperación se solicita indicando el email de la cuenta (RF-03).
- El token creado es temporal y de un solo uso (RF-03).
- El token se persiste únicamente como hash, nunca en claro (PRD §8).
- La respuesta del endpoint es la misma exista o no el email, para no revelar qué correos están registrados (PRD §8).
- El envío real de correo es opcional; en desarrollo el token puede exponerse de forma controlada para completar el ejercicio (RF-03, PRD §9).
- La exposición del token en desarrollo debe poder desactivarse por configuración fuera de ese entorno (PRD §8).
- El token no se escribe en logs cuando la exposición controlada está desactivada (PRD §8).
- La validación del email se ejecuta también en el servidor (PRD §8).

## Dependencias y relaciones

- Épica: [[EP-001-identidad-y-acceso-seguro]]
- Dependencias: [[HU-001-registrar-cuenta-de-usuario]]
- Relacionadas: [[HU-007-restablecer-contrasena-con-token]], [[HU-002-iniciar-sesion-con-jwt]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** La generación y persistencia del token es directa y reutiliza el patrón de hash ya establecido para los refresh tokens, pero la HU añade una migración Flyway nueva, un endpoint público adicional en la configuración de seguridad y un comportamiento de respuesta uniforme que es fácil de romper sin darse cuenta. Además obliga a introducir una conmutación por entorno para la entrega del token.

## Tareas de desarrollo

- [ ] **T-01 — Modelar el token de recuperación en el dominio**
  Dificultad: Bajo
  Descripción: Definir el token de recuperación como concepto de dominio con su usuario asociado, su vigencia y su condición de un solo uso, y las invariantes que lo hacen utilizable, sin dependencias de framework.

- [ ] **T-02 — Crear la migración Flyway de tokens de recuperación**
  Dificultad: Medio
  Descripción: Migración versionada que crea la tabla de tokens de recuperación con referencia al usuario, hash del token, fecha de expiración y marca de consumo, con las restricciones e índices necesarios para localizar el token por su hash.

- [ ] **T-03 — Implementar el caso de uso de solicitud de recuperación**
  Dificultad: Medio
  Descripción: Caso de uso de aplicación que busca al usuario por email, y si existe genera un token aleatorio, almacena su hash con la fecha de expiración y lo entrega al puerto de notificación; si no existe, termina sin efecto pero con el mismo resultado observable.

- [ ] **T-04 — Definir el puerto de entrega del token y su adaptador de desarrollo**
  Dificultad: Medio
  Descripción: Puerto de notificación de recuperación y un adaptador para entorno de desarrollo que expone el token de forma controlada, activable por configuración de entorno y desactivado por omisión.

- [ ] **T-05 — Exponer el adaptador REST de solicitud**
  Dificultad: Bajo
  Descripción: Controlador público con DTO validado que recibe el email, responde siempre lo mismo y no incluye el token en la respuesta salvo que la exposición de desarrollo esté explícitamente activada.

- [ ] **T-06 — Declarar el endpoint como público en la configuración de seguridad**
  Dificultad: Bajo
  Descripción: Añadir la ruta de solicitud de recuperación a las rutas accesibles sin autenticación, manteniendo el resto de la API protegida y el CORS explícito hacia `citas-web`.

- [ ] **T-07 — Construir la pantalla de solicitud en citas-web**
  Dificultad: Bajo
  Descripción: Formulario accesible desde el login con el campo de email, validación de cliente, consumo del endpoint mediante la URL configurable por entorno y mensaje de confirmación uniforme que no informa de la existencia de la cuenta.

- [ ] **T-08 — Pruebas de la solicitud de recuperación**
  Dificultad: Medio
  Descripción: Pruebas del caso de uso y de integración REST para email existente, email inexistente, email con formato inválido, verificación de que el token se persiste solo como hash y de que la exposición controlada está desactivada por omisión.

## Criterios de aceptación

### CA-01 — Solicitud con email registrado crea un token

**Dado** un usuario registrado con el email `paciente.demo@example.com`
**Cuando** solicita la recuperación indicando ese email
**Entonces** la API responde con éxito y queda creado un registro de token de recuperación asociado a ese usuario, con fecha de expiración futura y sin marca de consumo.

### CA-02 — Respuesta idéntica para un email no registrado

**Dado** el email `no.registrado@example.com`, que no corresponde a ninguna cuenta
**Cuando** se solicita la recuperación con ese email y, por separado, con el de un usuario registrado
**Entonces** ambas peticiones devuelven el mismo código de respuesta y el mismo cuerpo, sin ninguna diferencia observable, y en el caso no registrado no se crea ningún token.

### CA-03 — Token persistido solo como hash

**Dado** una solicitud de recuperación completada para un usuario registrado
**Cuando** se consulta el registro del token en la base de datos
**Entonces** el valor almacenado no coincide con el token entregado y corresponde a su hash, y no existe ninguna columna que guarde el token en claro.

### CA-04 — Token de un solo uso y con vigencia limitada

**Dado** un token de recuperación recién creado
**Cuando** se inspecciona su registro
**Entonces** tiene una fecha de expiración posterior al instante de creación y una marca de consumo que permite invalidarlo tras su primer uso.

### CA-05 — Entrega del token en desarrollo

**Dado** la aplicación arrancada con la variable de entorno de exposición controlada del token activada
**Cuando** un usuario registrado solicita la recuperación
**Entonces** la respuesta de la API contiene el token, la salida de log de la aplicación no lo contiene, y el token permite continuar el flujo de [[HU-007-restablecer-contrasena-con-token]] sin consultar la base de datos (D27).

### CA-06 — Exposición del token desactivada fuera de desarrollo

**Dado** la aplicación arrancada con la exposición controlada desactivada, que es el valor por omisión
**Cuando** un usuario registrado solicita la recuperación
**Entonces** la respuesta de la API no contiene el token y la salida de log de la aplicación tampoco lo contiene.

### CA-07 — Email inválido rechazado en el servidor

**Dado** una petición de solicitud enviada directamente a la API con el valor `correo-sin-arroba` en el campo email o con el campo ausente
**Cuando** la API procesa la petición
**Entonces** responde con un error de validación sobre el campo email y no crea ningún token.

### CA-08 — Esquema creado por migración versionada

**Dado** una base de datos MySQL 8.4 con las migraciones anteriores aplicadas
**Cuando** se arranca `citas-api`
**Entonces** Flyway aplica la migración que crea la tabla de tokens de recuperación con hash, expiración y marca de consumo, y el arranque finaliza sin error.

## Definition of Done

- [ ] Los criterios CA-01 a CA-08 están validados con evidencia concreta.
- [ ] Existe una migración Flyway versionada que crea la tabla de tokens de recuperación y se aplica de forma incremental sobre el esquema existente sin recrear las tablas anteriores.
- [ ] El token se genera con una fuente de aleatoriedad criptográficamente segura y se almacena solo como hash.
- [ ] La respuesta del endpoint es indistinguible entre email existente e inexistente, verificado comparando código y cuerpo de ambas respuestas.
- [ ] La exposición del token en desarrollo está gobernada por una variable de entorno documentada en `.env.example`, con valor por omisión que la mantiene desactivada.
- [ ] El dominio del token de recuperación no depende de Spring ni de JPA, y la entrega del token se realiza a través de un puerto, de forma que un adaptador de correo pueda añadirse después sin tocar el caso de uso.
- [ ] El endpoint de solicitud está declarado como público en la configuración de seguridad y el resto de la API sigue protegida.
- [ ] La pantalla de solicitud es accesible desde el login de `citas-web` y consume el endpoint usando la URL del backend leída de la configuración de entorno.
- [ ] Existen pruebas automatizadas de solicitud con email existente, con email inexistente y con email inválido, y pasan.
- [ ] El contrato del endpoint de solicitud está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
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

- 2026-09-25 — CA-05 ajustado a D27: el token viaja solo en la respuesta cuando la variable de laboratorio lo activa y nunca en el log (antes decía "por el medio configurado", que admitía el log). Se ajusta en el mismo sentido el punto de alcance sobre la entrega del token.
- 2026-09-25 — Aprobada por **aprobación delegada** del usuario para S4 (D15, PLAN_RETOMA_S4.md). Alcance en `PLAN_RETOMA_S4.md` §3 (bloque «Cuenta», fase F7) y decisiones D15–D30 en [[dec-006-decisiones-s4-ciclo-de-vida]]. El usuario puede devolverla a `Pendiente de aprobación`.
- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- **Resuelta (D27, provisional bajo delegación):** INC-002 (ver [[EP-001-identidad-y-acceso-seguro]]) en lo que toca al token de recuperación: vigencia de **30 minutos, configurable por entorno** ([[dec-006-decisiones-s4-ciclo-de-vida]]). CA-04 sigue exigiendo solo una expiración futura; el valor de 30 min se comprueba contra la configuración por defecto al validar. La vigencia de access y refresh token no la toca D27.
- **Resuelta (D27, provisional bajo delegación):** INC-005 (ver [[EP-001-identidad-y-acceso-seguro]]): el token viaja **en la respuesta** solo si una variable de entorno de laboratorio lo activa, apagada por defecto, y **nunca en logs** ([[dec-006-decisiones-s4-ciclo-de-vida]]). Motivo: un log con tokens contradice PRD §8. CA-05 y CA-06 lo reflejan.
- Esquema ya existente: la tabla `password_reset_tokens` existe desde `V1__identity_and_fixed_catalogs.sql` (`token_hash CHAR(64)` único, `expires_at`, `used_at`, `revoked_at`). T-02 no requiere migración nueva y CA-08 se valida citando V1 (`PLAN_RETOMA_S4.md` F7).
- Incógnita abierta: el PRD no indica qué ocurre cuando un mismo usuario solicita la recuperación varias veces seguidas, si los tokens anteriores se invalidan o conviven. Esta HU no lo resuelve y debe confirmarse antes de implementar.
- Incógnita abierta: el PRD no exige limitación de frecuencia sobre este endpoint público. No se añade ningún criterio al respecto para no inventar requisitos, pero queda señalado como riesgo.
