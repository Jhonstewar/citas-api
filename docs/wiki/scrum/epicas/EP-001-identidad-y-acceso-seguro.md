---
id: EP-001
tipo: epica
titulo: "Identidad y acceso seguro"
estado: Borrador
requisitos: [RF-01, RF-02, RF-03]
historias:
  - "[[HU-001-registrar-cuenta-de-usuario]]"
  - "[[HU-002-iniciar-sesion-con-jwt]]"
  - "[[HU-003-renovar-sesion-con-refresh-token]]"
  - "[[HU-004-cerrar-sesion-revocando-refresh-token]]"
  - "[[HU-005-autorizar-peticiones-por-rol-y-ownership]]"
  - "[[HU-006-solicitar-recuperacion-de-contrasena]]"
  - "[[HU-007-restablecer-contrasena-con-token]]"
dependencias: []
---

# EP-001 — Identidad y acceso seguro

## Objetivo

Permitir que una persona cree su propia cuenta `USER`, inicie sesión, mantenga y cierre su sesión, y recupere el acceso cuando olvide su contraseña, sobre un mecanismo de autenticación y autorización basado en JWT con roles.

## Valor esperado

Sin identidad no existe ninguna otra capacidad del producto: las citas, la agenda y la bandeja administrativa dependen de saber quién actúa y con qué rol. Esta épica entrega la puerta de entrada del sistema y la base de autorización que el resto de épicas reutiliza.

## Actores

- Visitante (persona no autenticada)
- USER
- PROFESSIONAL
- ADMIN

## Alcance

- Registro autónomo de cuentas `USER` con datos mínimos y unicidad de email y documento (RF-01).
- Login por email y contraseña con emisión de access token de corta duración y refresh token (RF-02).
- Renovación de sesión mediante refresh token (RF-02).
- Cierre de sesión con revocación del refresh token (RF-02).
- Roles como parte del contexto de autorización y control de ownership sobre recursos propios (RF-02, PRD §8).
- Solicitud de recuperación de contraseña con token temporal de un solo uso (RF-03).
- Restablecimiento de contraseña que consume e invalida el token (RF-03).
- Almacenamiento de contraseñas con hash adaptativo y de refresh/reset tokens únicamente como hash (PRD §8).
- CORS explícito y validación server-side sobre los endpoints de esta épica (PRD §8).

## Fuera de alcance

- Creación de cuentas `PROFESSIONAL` y `ADMIN`: la creación del profesional pertenece a [[EP-004-gestion-de-profesionales]] y la cuenta ADMIN se asume precargada por seed.
- Envío real de correo por SMTP: el PRD lo declara opcional y fuera del alcance obligatorio (PRD §9).
- Verificación de email, doble factor y bloqueo por intentos fallidos: no están en el PRD.
- Gestión de datos de perfil y afiliación, que vive en [[EP-002-perfil-y-afiliacion-del-paciente]].

## Reglas de negocio

- Email y número de documento son únicos entre usuarios (RF-01).
- Las contraseñas nunca se almacenan ni se registran en texto plano (RF-01, PRD §8).
- El access token es de corta duración y el refresh token permite renovar la sesión; son tokens separados (RF-02, PRD §8).
- Los roles del usuario forman parte del contexto de autorización de cada petición (RF-02).
- La autorización combina rol y ownership sobre el recurso (PRD §8).
- El token de recuperación es temporal y de un solo uso; cambiar la contraseña lo consume (RF-03).
- Refresh tokens y reset tokens se persisten solo como hash, nunca en claro.
- No se registran contraseñas ni tokens en logs (PRD §8).

## Dependencias

- Ninguna. Es la épica fundacional del producto.
- Habilita a: [[EP-002-perfil-y-afiliacion-del-paciente]], [[EP-003-catalogos-del-sistema]], [[EP-004-gestion-de-profesionales]], [[EP-005-agenda-del-profesional]], [[EP-006-busqueda-de-disponibilidad-y-reserva]], [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]], [[EP-008-operacion-administrativa-de-solicitudes]].

## Historias de usuario

- [[HU-001-registrar-cuenta-de-usuario]]
- [[HU-002-iniciar-sesion-con-jwt]]
- [[HU-003-renovar-sesion-con-refresh-token]]
- [[HU-004-cerrar-sesion-revocando-refresh-token]]
- [[HU-005-autorizar-peticiones-por-rol-y-ownership]]
- [[HU-006-solicitar-recuperacion-de-contrasena]]
- [[HU-007-restablecer-contrasena-con-token]]

## Criterio de completitud de la épica

- [ ] Todas las HU obligatorias de esta épica están `Completada`.
- [ ] Un visitante puede registrarse, autenticarse, renovar y cerrar su sesión de extremo a extremo desde `citas-web` contra `citas-api`.
- [ ] Un usuario puede recuperar el acceso perdido sin intervención manual sobre la base de datos.
- [ ] Ningún endpoint protegido de la API responde a una petición sin token válido o con rol insuficiente.
- [ ] No quedan dependencias bloqueantes dentro del alcance de la épica.

## Riesgos e incógnitas

- **INC-001** — El PRD no define una política de complejidad mínima de contraseña (longitud, caracteres requeridos). Requiere decisión humana antes de fijar criterios de aceptación de validación.
- **INC-002** — El PRD no define la vigencia del token de recuperación (RF-03) ni la duración concreta de access y refresh token (RF-02). Falta decidir los valores y si serán configurables por entorno.
- **INC-003** — El PRD no indica si `PROFESSIONAL` y `ADMIN` se autentican por el mismo formulario de login que `USER` o por una entrada separada. Afecta a las pantallas obligatorias (PRD §6).
- **INC-004** — El PRD no define cómo se crea la primera cuenta `ADMIN`. Se asume precargada por seed, de forma coherente con la carga de catálogos fijos de RF-05; requiere confirmación.
- **INC-005** — RF-03 permite exponer el token de recuperación "de forma segura en log/respuesta controlada" en desarrollo. Falta decidir el mecanismo exacto y cómo se desactiva fuera de desarrollo.
