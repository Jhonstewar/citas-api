---
id: HU-001
tipo: historia-de-usuario
titulo: "Registrar cuenta de usuario"
estado: Aprobada
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

- [ ] Los criterios CA-01 a CA-08 están validados con evidencia concreta.
- [ ] Existe una migración Flyway versionada que crea el esquema de usuarios y roles con las restricciones de unicidad, y se aplica sobre una base vacía.
- [ ] El dominio de usuario no depende de Spring ni de JPA, respetando la separación hexagonal exigida por las restricciones técnicas.
- [ ] El codificador de contraseñas configurado es un hash adaptativo (BCrypt o Argon2) provisto por Spring Security.
- [ ] El endpoint de registro está declarado como público en la configuración de seguridad y el resto de la API sigue protegida.
- [ ] La pantalla de registro de `citas-web` consume el endpoint usando la URL del backend leída de la configuración de entorno, no escrita en el código.
- [ ] Existen pruebas automatizadas que cubren el alta correcta, el conflicto por email y el conflicto por documento, y pasan.
- [ ] El contrato del endpoint de registro está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [ ] No se ha subido ningún secreto al repositorio y `.env.example` no contiene valores reales.
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

- Incógnita abierta **INC-001** (ver [[EP-001-identidad-y-acceso-seguro]]): no hay política de complejidad de contraseña definida en el PRD. Hasta que se decida, CA-04 solo exige obligatoriedad, no fortaleza.
- El tipo de documento se trata como catálogo; si no existe un catálogo de tipos de documento definido, debe confirmarse con el usuario del proyecto antes de implementar.
