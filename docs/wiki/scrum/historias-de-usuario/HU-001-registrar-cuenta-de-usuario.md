---
id: HU-001
tipo: historia-de-usuario
titulo: "Registrar cuenta de usuario"
estado: Completada
epica: "[[EP-001-identidad-y-acceso-seguro]]"
requisitos: [RF-01]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 1"
dependencias: []
relacionadas:
  - "[[HU-002-iniciar-sesion-con-jwt]]"
  - "[[HU-013-crear-profesional-con-datos-de-registro]]"
  - "[[HU-033-publicar-contrato-rest-documentado]]"
---

# HU-001 — Registrar cuenta de usuario

## Historia de usuario

**COMO** visitante del portal de agendamiento
**QUIERO** crear yo mismo una cuenta con mis datos personales y una contraseña
**PARA** poder acceder al sistema y solicitar citas sin depender de un administrador

> Como visitante del portal de agendamiento, quiero crear yo mismo una cuenta con mis datos personales y una contraseña para poder acceder al sistema y solicitar citas sin depender de un administrador.

## Contexto y descripción

RF-01 establece que un visitante puede crear una cuenta con rol `USER` de forma autónoma. Es la primera capacidad del producto y la única puerta de entrada de pacientes al sistema. Los datos mínimos son nombres, apellidos, tipo y número de documento, email, teléfono y contraseña. El email y el número de documento identifican de forma única a la persona, y la contraseña se almacena con hash adaptativo compatible con Spring Security.

Esta HU pertenece al alcance de la sesión S2 junto con [[HU-002-iniciar-sesion-con-jwt]].

## Alcance

- Formulario de registro en `citas-web` con los siete datos mínimos de RF-01.
- Endpoint público de registro en `citas-api`.
- Validación server-side de formato y obligatoriedad de cada campo.
- Verificación de unicidad de email y de número de documento.
- Asignación automática del rol `USER` a la cuenta creada.
- Persistencia de la contraseña únicamente como hash adaptativo.
- Esquema de base de datos para usuarios, roles y la relación entre ambos, creado mediante migración Flyway.

## Fuera de alcance

- Verificación del email mediante enlace de confirmación: no está en el PRD.
- Creación de cuentas `PROFESSIONAL` o `ADMIN`, que se cubre en [[HU-013-crear-profesional-con-datos-de-registro]].
- Registro de la afiliación a EPS, que se cubre en [[HU-009-registrar-afiliacion-a-eps-y-plan]].
- Inicio de sesión automático tras el registro, salvo decisión posterior del usuario del proyecto.

## Reglas de negocio

- El email es único entre todos los usuarios (RF-01).
- El número de documento es único entre todos los usuarios (RF-01).
- La contraseña nunca se almacena en texto plano; se usa hash adaptativo BCrypt o Argon2 compatible con Spring Security (RF-01, PRD §8).
- La contraseña nunca aparece en logs ni en la respuesta del endpoint (PRD §8).
- Toda validación ocurre en el servidor además de en el cliente (PRD §8).
- La cuenta creada recibe el rol `USER`; el tipo de documento proviene del catálogo correspondiente.

## Dependencias y relaciones

- Épica: [[EP-001-identidad-y-acceso-seguro]]
- Dependencias: ninguna
- Relacionadas: [[HU-002-iniciar-sesion-con-jwt]], [[HU-008-consultar-y-actualizar-perfil]], [[HU-013-crear-profesional-con-datos-de-registro]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** Es una única capacidad de alta con reglas simples, pero arrastra la puesta en marcha del esqueleto hexagonal del backend, la primera migración Flyway del proyecto, la configuración de Spring Security para permitir un endpoint público y el primer consumo REST desde el frontend. La complejidad está en las piezas fundacionales que inaugura, no en la regla de negocio.

## Tareas de desarrollo

- [ ] **T-01 — Modelar el dominio de usuario y rol**
  Dificultad: Bajo
  Descripción: Definir en la capa de dominio las entidades y objetos de valor de usuario, documento, email y credencial, con sus invariantes de unicidad y obligatoriedad, sin dependencias de framework.

- [ ] **T-02 — Crear la migración Flyway de usuarios y roles**
  Dificultad: Medio
  Descripción: Migración que crea las tablas de usuarios, roles y su relación, con restricciones de unicidad sobre email y número de documento, y el seed de los roles fijos.

- [ ] **T-03 — Implementar el caso de uso de registro**
  Dificultad: Medio
  Descripción: Caso de uso de aplicación que valida los datos, comprueba unicidad, cifra la contraseña mediante un puerto de hashing y persiste el usuario con rol `USER`.

- [ ] **T-04 — Exponer el adaptador REST de registro**
  Dificultad: Bajo
  Descripción: Controlador con DTO de entrada validado, mapeo a comando de aplicación, respuesta sin datos sensibles y manejo de error de conflicto por email o documento duplicado.

- [ ] **T-05 — Configurar Spring Security para el endpoint público**
  Dificultad: Medio
  Descripción: Declarar el endpoint de registro como accesible sin autenticación, configurar el codificador de contraseñas adaptativo y el CORS explícito hacia `citas-web`.

- [ ] **T-06 — Construir la pantalla de registro en citas-web**
  Dificultad: Medio
  Descripción: Formulario React + TypeScript con validación de cliente, consumo del endpoint mediante la URL configurable por entorno, presentación de errores de campo y de conflicto, y navegación al login tras el alta.

- [ ] **T-07 — Pruebas de dominio, aplicación e integración del registro**
  Dificultad: Medio
  Descripción: Pruebas de las invariantes de dominio, del caso de uso con dobles de prueba y de integración REST/persistencia para el alta correcta y los conflictos de unicidad.

## Criterios de aceptación

### CA-01 — Alta correcta de una cuenta USER

**Dado** un visitante no autenticado con un email y un número de documento que no existen en el sistema
**Cuando** envía el formulario de registro con nombres, apellidos, tipo y número de documento, email, teléfono y contraseña válidos
**Entonces** la API responde con un código de creación exitosa, el usuario queda persistido con el rol `USER` y la respuesta no incluye la contraseña ni su hash.

### CA-02 — Email duplicado rechazado

**Dado** que ya existe un usuario registrado con el email `paciente.demo@example.com`
**Cuando** un visitante intenta registrarse con ese mismo email
**Entonces** la API responde con un error de conflicto, no se crea ningún usuario nuevo y el mensaje indica que el email ya está registrado.

### CA-03 — Número de documento duplicado rechazado

**Dado** que ya existe un usuario registrado con un tipo y número de documento concretos
**Cuando** un visitante intenta registrarse con ese mismo tipo y número de documento
**Entonces** la API responde con un error de conflicto, no se crea ningún usuario nuevo y el mensaje indica que el documento ya está registrado.

### CA-04 — Campos obligatorios validados en el servidor

**Dado** una petición de registro enviada directamente a la API sin pasar por el formulario
**Cuando** falta cualquiera de los campos nombres, apellidos, tipo de documento, número de documento, email, teléfono o contraseña
**Entonces** la API responde con un error de validación que enumera los campos inválidos y no persiste ningún usuario.

### CA-05 — Formato de email validado en el servidor

**Dado** una petición de registro con el valor `correo-sin-arroba` en el campo email
**Cuando** la API procesa la petición
**Entonces** responde con un error de validación sobre el campo email y no persiste ningún usuario.

### CA-06 — Contraseña almacenada solo como hash

**Dado** un registro completado correctamente
**Cuando** se consulta el registro del usuario en la base de datos
**Entonces** el valor almacenado no coincide con la contraseña enviada, corresponde a un hash adaptativo verificable por Spring Security, y no existe ninguna columna que guarde la contraseña en claro.

### CA-07 — Ausencia de contraseña en logs y respuestas

**Dado** un registro completado correctamente y un registro fallido por validación
**Cuando** se inspeccionan la respuesta HTTP y la salida de log de la aplicación para ambas peticiones
**Entonces** en ninguna de las dos aparece el valor de la contraseña ni su hash.

### CA-08 — Esquema creado por migración versionada

**Dado** una base de datos MySQL 8.4 vacía
**Cuando** se arranca `citas-api`
**Entonces** Flyway aplica la migración que crea las tablas de usuarios, roles y su relación con las restricciones de unicidad sobre email y documento, y el arranque finaliza sin error.

## Definition of Done

- [x] Los criterios CA-01 a CA-08 están validados con evidencia concreta.
- [x] Existe una migración Flyway versionada que crea el esquema de usuarios y roles con las restricciones de unicidad, y se aplica sobre una base vacía.
- [x] El dominio de usuario no depende de Spring ni de JPA, respetando la separación hexagonal exigida por las restricciones técnicas.
- [x] El codificador de contraseñas configurado es un hash adaptativo (BCrypt o Argon2) provisto por Spring Security.
- [x] El endpoint de registro está declarado como público en la configuración de seguridad y el resto de la API sigue protegida.
- [x] La pantalla de registro de `citas-web` consume el endpoint usando la URL del backend leída de la configuración de entorno, no escrita en el código.
- [x] Existen pruebas automatizadas que cubren el alta correcta, el conflicto por email y el conflicto por documento, y pasan.
- [x] El contrato del endpoint de registro está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [x] No se ha subido ningún secreto al repositorio y `.env.example` no contiene valores reales.
- [x] La trazabilidad de esta HU y de [[EP-001-identidad-y-acceso-seguro]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Verificación independiente del 2026-09-17 por `backend-verifier` y `frontend-verifier` (agentes que no implementaron el código), con prueba de mutación: backend `docker compose run --rm citas-api-dev mvn -B test` → **104 pruebas, 0 fallos** (11 mutantes, mueren los 11); frontend `npm test` en `citas-web` → **42 pruebas, 0 fallos**, con typecheck, lint y build en verde (29 mutantes, mueren todos salvo uno cosmético).
Condición del verificador frontend cumplida: la evidencia de `citas-web` está commiteada en `develop` (commit `f3e7989`, comprobado con `git log`). La evidencia de `citas-api` se versiona en `develop` en el mismo commit que el cambio de estado a `Completada`.

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | Backend: `AuthFlowIntegrationTest#registerCreatesUserWithRoleUserAndStoresOnlyPasswordHash`; `RegisterUserUseCaseTest#registersActiveUserWithRoleUserAndHashedPassword`. Frontend: `citas-web/src/sessionFlow.test.tsx > registro en la aplicación (HU-001) > CA-01: envía los siete campos, sin la confirmación ni token, y lleva al login` | Usuario activo con rol `USER` y contraseña solo como hash. El formulario envía los siete campos, sin la confirmación, y lleva al login |
| CA-02 | Cumple | Backend: `AuthFlowIntegrationTest#duplicateEmailReturns409`; `RegisterUserUseCaseTest#rejectsDuplicateEmailIgnoringCase`. Frontend: `citas-web/src/sessionFlow.test.tsx > registro en la aplicación (HU-001) > CA-02/CA-03: un 409 muestra el mensaje del servidor y deja reintentar` | 409 `application/problem+json` también con el email en mayúsculas distintas; 0 filas nuevas. El cliente muestra el mensaje del servidor y deja reintentar |
| CA-03 | Cumple | Backend: `AuthFlowIntegrationTest#duplicateDocumentReturns409`; `RegisterUserUseCaseTest#rejectsDuplicateDocument`. Frontend: la misma prueba `CA-02/CA-03` de `sessionFlow.test.tsx` | La unicidad es global sobre el número de documento (`uq_users_document_number`), no por tipo + número. En el cliente la prueba ejercita el mensaje del email; el del documento pasa por la misma rama (`RegistroFailure` en `RegistroPage.tsx`) |
| CA-04 | Cumple | Backend: `AuthFlowIntegrationTest#missingFieldsReturn400WithFieldErrors`. Frontend (complemento de UI): `citas-web/src/sessionFlow.test.tsx > registro en la aplicación (HU-001) > CA-04: los fieldErrors del 400 se muestran en su campo` | 400 con `fieldErrors` de los siete campos; `@Valid` corta antes del controlador y no persiste nada |
| CA-05 | Cumple | `AuthFlowIntegrationTest#invalidEmailFormatReturns400AndPersistsNothing` | Solo backend (fuera del alcance del verificador frontend) |
| CA-06 | Cumple | `AuthFlowIntegrationTest#registerCreatesUserWithRoleUserAndStoresOnlyPasswordHash`; `V1__identity_and_fixed_catalogs.sql` (línea 51: solo `password_hash`) | Valor almacenado con prefijo `{bcrypt}$2` y verificado con `PasswordEncoder.matches`. Solo backend |
| CA-07 | Cumple | `CapturedOutput` en `AuthFlowIntegrationTest#registerCreatesUserWithRoleUserAndStoresOnlyPasswordHash` y `#invalidEmailFormatReturns400AndPersistsNothing`; `RegisterRequest#toString` enmascara `password` | Cubre el alta correcta y la fallida por validación. Solo backend. Ver riesgo R1 en "Notas y decisiones" |
| CA-08 | Cumple | `FlywayMigratesEmptySchemaTest` (4 pruebas) | Esquema desechable vacío, V1–V4 aplicadas y validadas, 24 tablas. Solo backend |
| DoD — CA-01 a CA-08 validados con evidencia concreta | Cumple | Filas CA-01 a CA-08 de esta tabla | — |
| DoD — Migración Flyway versionada con unicidad, aplicada sobre base vacía | Cumple | `V1__identity_and_fixed_catalogs.sql`; `FlywayMigratesEmptySchemaTest` | — |
| DoD — Dominio sin Spring ni JPA | Cumple | `HexagonalArchitectureTest` (ArchUnit) | No es vacuo: el mutante que introduce Spring en `application/` muere |
| DoD — Codificador adaptativo de Spring Security | Cumple | `SecurityConfig#passwordEncoder` (`PasswordEncoderFactories.createDelegatingPasswordEncoder()`); `AuthFlowIntegrationTest#registerRejectsPasswordOver72Utf8BytesWith400FieldError` (×5), `#registerAcceptsPasswordOfExactly72Utf8BytesAndItWorksForLogin` (×3) | BCrypt (`{bcrypt}$2`, ver CA-06). El tope de 72 bytes UTF-8 queda probado por los dos lados; el mutante que retira `@BcryptPasswordLength` muere |
| DoD — Registro público y resto de la API protegida | Cumple | `SecurityConfig`; `AuthFlowIntegrationTest#registerAndRefreshIgnoreAnInvalidBearer`, `#protectedEndpointRejectsMissingMalformedAndExpiredTokens` | Un Bearer inválido no bloquea el registro; las rutas protegidas siguen respondiendo 401 |
| DoD — Pantalla de registro con URL del backend desde el entorno | Cumple | `citas-web/src/api/contracts.test.ts > API_BASE_URL > toma VITE_API_URL y le quita la barra final`; `… > falla al cargar si VITE_API_URL está vacía, en vez de usar un valor por defecto` | Sin valor por defecto escrito en el código |
| DoD — Pruebas de alta, conflicto por email y conflicto por documento, en verde | Cumple | `AuthFlowIntegrationTest#registerCreatesUserWithRoleUserAndStoresOnlyPasswordHash`, `#duplicateEmailReturns409`, `#duplicateDocumentReturns409` | Dentro de la suite de 104 pruebas, 0 fallos |
| DoD — Contrato de registro documentado | Cumple | Sección de registro de [[contrato-rest-identidad]] | Coincide con lo implementado, incluido el mensaje del tope de 72 bytes |
| DoD — Sin secretos versionados; `.env.example` sin valores reales | Cumple | Backend: `.env.example` con marcadores. Frontend: solo `.env.example` versionado y `.env` en `.gitignore` | Matiz: el `.env.example` de la raíz trae contraseñas de MySQL de laboratorio concretas (preexistente; riesgo no bloqueante, ver "Notas y decisiones") |
| DoD — Trazabilidad de esta HU y de [[EP-001-identidad-y-acceso-seguro]] actualizada | Cumple | Esta tabla y el historial de validación | — |

## Historial de validación

- 2026-09-17 — Estado `Completada`: matriz de evidencia completa y toda la DoD en `Cumple`, según la verificación independiente de backend y frontend (con prueba de mutación). Cierre dentro de la aprobación delegada de S2 (`AGENTS.md` §6).
- 2026-09-17 — Estado `En validación` (paso previo al cierre): se comprueba con `git log` que la evidencia de `citas-web` está commiteada en `develop` (commit `f3e7989`), condición que había puesto el verificador frontend. La matriz no cambia: 18 de 18 filas en `Cumple`. Se marcan los ítems de la DoD, cada uno respaldado por su fila de la matriz.
- 2026-09-17 — Tabla de evidencia reescrita a partir de la verificación independiente (backend-verifier y frontend-verifier, con prueba de mutación). Estado sin cambios (`Aprobada`). 18 filas (8 CA y 10 ítems de DoD), todas `Cumple`. El único mutante frontend que sobrevive (g1, `maxLength` de los campos del registro) es cosmético según el verificador. Se corrige en "Notas y decisiones" la nota de la contraseña (tope de 72 bytes UTF-8 con `@BcryptPasswordLength`, no de 72 caracteres; el login no valida longitud) y se registran los riesgos no bloqueantes de la verificación.
- 2026-09-17 — Enmienda de especificación dentro de la **aprobación delegada** de S2 (`AGENTS.md` §6): se añade en "Notas y decisiones" que `citas-web` aplica una política de contraseña (mínimo 8 caracteres, letra y número) que `citas-api` no aplica, pendiente de INC-001. No cambia ningún criterio de aceptación ni el alcance. Estado sin cambios (`Aprobada`); la tabla de evidencia no se modifica y queda pendiente de la verificación independiente.
- 2026-09-16 — Estado `Aprobada` por **aprobación delegada**: el usuario eligió ejecutar S2 en modo autónomo, autorizando al agente a asumir las aprobaciones de HU. Alcance: GOAL_01 (registro + login JWT + refresh + logout).
- Sesión S2 — HU creada y dejada en estado `Pendiente de aprobación` como candidata al alcance de S2.

## Notas y decisiones

- Incógnita abierta **INC-001** (ver [[EP-001-identidad-y-acceso-seguro]]): no hay política de complejidad de contraseña definida en el PRD. Hasta que se decida, CA-04 solo exige obligatoriedad, no fortaleza.
- Divergencia de política de contraseña pendiente de **INC-001**: el formulario de registro de `citas-web` exige hoy un mínimo de 8 caracteres con al menos una letra y un número (`citas-web/src/validation/authValidation.ts`), pero `citas-api` no aplica esa política: en el registro solo exige que la contraseña no esté vacía y no supere **72 bytes en UTF-8**, el límite de BCrypt (`@NotBlank @BcryptPasswordLength` en `RegisterRequest`). Se cuentan bytes, no caracteres: la ñ y las vocales con tilde ocupan 2 bytes, así que una contraseña con ellas admite menos de 72 caracteres. Es un tope técnico, no una política de complejidad, y el cliente no lo replica. El login no valida longitud (`LoginRequest` solo lleva `@NotBlank`): una contraseña de más de 72 bytes recibe el mismo 401 `Credenciales inválidas` que cualquier otra credencial incorrecta. Una petición directa a la API acepta contraseñas que el formulario rechaza, lo que choca con la regla "toda validación ocurre en el servidor además de en el cliente". Al decidir INC-001 debe alinearse una de las dos capas; hasta entonces ningún CA de esta HU exige la política del cliente.
- El tipo de documento se trata como catálogo; si no existe un catálogo de tipos de documento definido, debe confirmarse con el usuario del proyecto antes de implementar.
- Riesgo no bloqueante (verificación del 2026-09-17, R1): `CapturedOutput` acumula la salida de toda la clase de prueba y MockMvc imprime los cuerpos —con la contraseña— cuando una prueba falla, así que un fallo arrastra en cascada las comprobaciones de CA-07 de esta HU y de CA-08 de [[HU-002-iniciar-sesion-con-jwt]]; afecta solo a las pruebas.
- Riesgo no bloqueante (R3): toda HU futura que fije o cambie contraseñas —p. ej. [[HU-007-restablecer-contrasena-con-token]] o [[HU-013-crear-profesional-con-datos-de-registro]]— debe validar con `@BcryptPasswordLength`, no con `@Size`.
- Riesgo no bloqueante (preexistente): el `.env.example` de la raíz del workspace trae contraseñas de MySQL de laboratorio concretas.
