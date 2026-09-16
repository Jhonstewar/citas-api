---
id: EP-002
tipo: epica
titulo: "Perfil y afiliación del paciente"
estado: Borrador
requisitos: [RF-04]
historias:
  - "[[HU-008-consultar-y-actualizar-perfil]]"
  - "[[HU-009-registrar-afiliacion-a-eps-y-plan]]"
dependencias:
  - "[[EP-001-identidad-y-acceso-seguro]]"
  - "[[EP-003-catalogos-del-sistema]]"
---

# EP-002 — Perfil y afiliación del paciente

## Objetivo

Permitir que un `USER` consulte y mantenga sus datos personales permitidos y declare su afiliación a una EPS mediante un plan, de forma que la afiliación quede normalizada y sin duplicar EPS, régimen ni plan dentro del usuario.

## Valor esperado

El paciente mantiene su información de contacto al día y el sistema conoce su cobertura para poder asociarla a las citas que solicite, sin replicar datos de catálogo dentro del registro del usuario.

## Actores

- USER

## Alcance

- Consulta de los datos de perfil propios (RF-04).
- Actualización de los datos de perfil que el sistema permite modificar (RF-04).
- Registro y consulta de la afiliación del usuario a un plan de EPS, desde el cual se derivan EPS y régimen (RF-04).
- Prevención de duplicidad de EPS, régimen y plan dentro del usuario (RF-04).

## Fuera de alcance

- Creación o edición del catálogo de EPS, planes y regímenes: pertenece a [[EP-003-catalogos-del-sistema]].
- Cambio de contraseña, que vive en [[EP-001-identidad-y-acceso-seguro]].
- Perfil profesional (código, matrícula, especialidades, sedes), que administra ADMIN en [[EP-004-gestion-de-profesionales]].

## Reglas de negocio

- El usuario solo accede y modifica su propio perfil (ownership, PRD §8).
- La afiliación referencia un plan de EPS; desde el plan se conocen la EPS y el régimen, y estos no se almacenan repetidos en el usuario (RF-04).
- La aplicación evita que un mismo usuario duplique EPS, régimen y plan (RF-04).
- Toda validación de los datos enviados se ejecuta también en el servidor (PRD §8).
- Solo pueden seleccionarse planes de EPS activos del catálogo (RF-06).

## Dependencias

- [[EP-001-identidad-y-acceso-seguro]] — se requiere sesión autenticada y ownership.
- [[EP-003-catalogos-del-sistema]] — la afiliación depende de EPS, planes y regímenes existentes.

## Historias de usuario

- [[HU-008-consultar-y-actualizar-perfil]]
- [[HU-009-registrar-afiliacion-a-eps-y-plan]]

## Criterio de completitud de la épica

- [ ] Todas las HU obligatorias de esta épica están `Completada`.
- [ ] Un `USER` autenticado puede ver su perfil, modificar los campos permitidos y declarar su afiliación desde `citas-web`.
- [ ] No existe forma de registrar dos veces la misma combinación de EPS, régimen y plan para un mismo usuario.
- [ ] No quedan dependencias bloqueantes dentro del alcance de la épica.

## Riesgos e incógnitas

- **INC-006** — RF-04 dice "datos permitidos" sin enumerarlos. Falta decidir qué campos son editables por el usuario y cuáles quedan fijos (por ejemplo tipo y número de documento, o email por ser credencial de login).
- **INC-007** — El PRD no define si un usuario puede mantener varias afiliaciones simultáneas o solo una vigente, ni cómo se marca la afiliación que se asociará a una cita.
- **INC-008** — El PRD no define si la afiliación es obligatoria para solicitar una cita. Requiere confirmación antes de escribir criterios de bloqueo en [[EP-006-busqueda-de-disponibilidad-y-reserva]].
