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

| HU | Estado |
|---|---|
| [[HU-001-registrar-cuenta-de-usuario]] | `Completada` |
| [[HU-002-iniciar-sesion-con-jwt]] | `Completada` |
| [[HU-003-renovar-sesion-con-refresh-token]] | `Completada` |
| [[HU-004-cerrar-sesion-revocando-refresh-token]] | `Completada` |
| [[HU-005-autorizar-peticiones-por-rol-y-ownership]] | `En validación` |
| [[HU-006-solicitar-recuperacion-de-contrasena]] | `Aprobada` |
| [[HU-007-restablecer-contrasena-con-token]] | `Aprobada` |

Avance: 4 de 7 HU `Completada` (cierre del 2026-09-17, con verificación independiente de backend y frontend).

## Criterio de completitud de la épica

- [ ] Todas las HU obligatorias de esta épica están `Completada`.
- [ ] Un visitante puede registrarse, autenticarse, renovar y cerrar su sesión de extremo a extremo desde `citas-web` contra `citas-api`.
- [ ] Un usuario puede recuperar el acceso perdido sin intervención manual sobre la base de datos.
- [ ] Ningún endpoint protegido de la API responde a una petición sin token válido o con rol insuficiente.
- [ ] No quedan dependencias bloqueantes dentro del alcance de la épica.

## Riesgos e incógnitas

- **INC-001** — ~~El PRD no define una política de complejidad mínima de contraseña (longitud, caracteres requeridos).~~ **Cerrada por D29** (provisional bajo delegación, [[dec-006-decisiones-s4-ciclo-de-vida]]): mínimo 8 caracteres con al menos una letra y un número, **también en el servidor**, y máximo 72 bytes en UTF-8; se aplica solo al fijar una contraseña, así que las cuentas existentes siguen entrando. Vive en `domain/auth/PasswordPolicy` y se aplica con `@PasswordPolicyCompliant`. Criterios afectados: [[HU-007-restablecer-contrasena-con-token]] CA-08 (ajustado) y la matriz de [[HU-001-registrar-cuenta-de-usuario]], que está `Completada` y debe revisarse.
- **INC-002** — **Cerrada solo en parte.** El token de recuperación: **D27** fija 30 minutos, configurable por entorno (`PASSWORD_RESET_MINUTES`, `application.yml:82`, `.env.example:37`). La vigencia de access y refresh token **sigue abierta**: ninguna decisión D15–D39 la fija, y [[HU-002-iniciar-sesion-con-jwt]], [[HU-003-renovar-sesion-con-refresh-token]] y [[HU-004-cerrar-sesion-revocando-refresh-token]] cerraron con criterios sobre el mecanismo y no sobre un umbral.
- **INC-003** — El PRD no indica si `PROFESSIONAL` y `ADMIN` se autentican por el mismo formulario de login que `USER` o por una entrada separada. Afecta a las pantallas obligatorias (PRD §6). **Sigue abierta**; en la práctica hay un único login y el rol decide el destino, pero ninguna decisión lo registra.
- **INC-004** — El PRD no define cómo se crea la primera cuenta `ADMIN`. Se asume precargada por seed, de forma coherente con la carga de catálogos fijos de RF-05; requiere confirmación. **Sigue abierta.**
- **INC-005** — ~~RF-03 permite exponer el token de recuperación "de forma segura en log/respuesta controlada" en desarrollo. Falta decidir el mecanismo exacto y cómo se desactiva fuera de desarrollo.~~ **Cerrada por D27** (provisional bajo delegación): el token viaja **solo en la respuesta**, y solo si `PASSWORD_RESET_EXPOSE_TOKEN` está encendida —apagada por omisión (`application.yml:83`)—, y **nunca en logs**, porque un log con tokens contradice PRD §8. Criterios afectados: [[HU-006-solicitar-recuperacion-de-contrasena]] CA-05 y CA-06.

## Historial

- 2026-09-30 — **Incógnitas anotadas con la decisión que las cierra**, sin cambiar el estado de la épica. INC-001 → **D29**; INC-005 → **D27**; INC-002 → **D27 solo para el token de recuperación**, y access y refresh siguen abiertos. INC-003 e INC-004 siguen abiertas y quedan marcadas como tales. Las HU de esta épica que se validaron ese día —[[HU-006-solicitar-recuperacion-de-contrasena]] y [[HU-007-restablecer-contrasena-con-token]]— pasaron de `Aprobada` a `En validación` con su matriz de evidencia completa en backend; **ninguna a `Completada`**, porque les falta la verificación de frontend criterio a criterio y la prueba manual en navegador de la fase F10 de `PLAN_RETOMA_S4.md`.
- 2026-09-30 — Registrada **D34** en [[HU-007-restablecer-contrasena-con-token]]: restablecer la contraseña revoca todas las familias de refresh del usuario. Cierra la que era la única decisión declarada como pendiente dentro de esta épica en S4.
