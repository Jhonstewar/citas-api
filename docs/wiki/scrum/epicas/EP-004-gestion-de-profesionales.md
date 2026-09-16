---
id: EP-004
tipo: epica
titulo: "Gestión de profesionales"
estado: Borrador
requisitos: [RF-07]
historias:
  - "[[HU-013-crear-profesional-con-datos-de-registro]]"
  - "[[HU-014-asignar-especialidades-y-especialidad-primaria]]"
  - "[[HU-015-asignar-sedes-al-profesional]]"
  - "[[HU-016-activar-o-desactivar-profesional]]"
dependencias:
  - "[[EP-001-identidad-y-acceso-seguro]]"
  - "[[EP-003-catalogos-del-sistema]]"
---

# EP-004 — Gestión de profesionales

## Objetivo

Permitir que ADMIN dé de alta profesionales ficticios con su código y matrícula sintéticos, les asigne una o varias especialidades con una primaria, les habilite una o ambas sedes, y los active o desactive.

## Valor esperado

Sin profesionales configurados no existe agenda ni oferta de citas. Esta épica define quién puede atender, de qué y dónde, que es la precondición de toda la disponibilidad del sistema.

## Actores

- ADMIN
- PROFESSIONAL (sujeto de la gestión; no se autogestiona)

## Alcance

- Creación del usuario `PROFESSIONAL` por parte de ADMIN (RF-07).
- Registro del código profesional y de la matrícula ficticia (RF-07).
- Asignación de una o varias especialidades y marcado de la especialidad primaria (RF-07).
- Asignación de una o ambas sedes del laboratorio (RF-07).
- Activación y desactivación del profesional (RF-07).
- Consulta y listado de profesionales por ADMIN.

## Fuera de alcance

- Autorregistro del profesional: solo ADMIN lo crea (PRD §2).
- Aprobación de citas por el profesional: el PRD lo excluye explícitamente (PRD §2).
- Creación de bloques de agenda, que pertenece a [[EP-005-agenda-del-profesional]].
- Creación de especialidades y sedes, que pertenece a [[EP-003-catalogos-del-sistema]].

## Reglas de negocio

- El profesional es primero un usuario con rol `PROFESSIONAL`; sus datos profesionales se agregan aparte.
- Código profesional y matrícula son únicos y sintéticos; nunca se usan datos reales de FCV (PRD §8, §9).
- Un profesional puede tener varias especialidades, y exactamente una de ellas es la primaria (RF-07).
- Un profesional puede trabajar en una o en ambas sedes fijas (RF-07).
- RN-07: un profesional solo publica agenda en las sedes que tiene asignadas.
- RN-08: la especialidad debe estar activa y asociada al profesional para poder reservarse.
- Un profesional referenciado por transacciones se desactiva, no se borra (RF-06 aplicado por analogía y RN-12 sobre preservación de trazabilidad).

## Dependencias

- [[EP-001-identidad-y-acceso-seguro]] — la creación de la cuenta reutiliza el registro de usuario y el hash de contraseña.
- [[EP-003-catalogos-del-sistema]] — requiere especialidades y sedes disponibles.
- Habilita a: [[EP-005-agenda-del-profesional]], [[EP-006-busqueda-de-disponibilidad-y-reserva]].

## Historias de usuario

- [[HU-013-crear-profesional-con-datos-de-registro]]
- [[HU-014-asignar-especialidades-y-especialidad-primaria]]
- [[HU-015-asignar-sedes-al-profesional]]
- [[HU-016-activar-o-desactivar-profesional]]

## Criterio de completitud de la épica

- [ ] Todas las HU obligatorias de esta épica están `Completada`.
- [ ] ADMIN puede crear un profesional completo (cuenta, datos de registro, especialidades con primaria y sedes) desde `citas-web`.
- [ ] Un profesional creado puede autenticarse con rol `PROFESSIONAL`.
- [ ] Un profesional desactivado no aparece como oferta en la búsqueda de disponibilidad.
- [ ] No quedan dependencias bloqueantes dentro del alcance de la épica.

## Riesgos e incógnitas

- **INC-013** — El PRD no define cómo se entrega la contraseña inicial al profesional creado por ADMIN (contraseña temporal, flujo de recuperación de RF-03, o valor fijado por ADMIN).
- **INC-014** — El PRD no define qué ocurre con los bloques de disponibilidad y las citas futuras cuando ADMIN desactiva a un profesional o le retira una sede o una especialidad.
- **INC-015** — El PRD no indica si ADMIN puede editar los datos de un profesional ya creado (código, matrícula) ni si estos son inmutables una vez registrados.
- **INC-016** — El PRD no define si un profesional puede quedar sin especialidad primaria de forma transitoria cuando se le retira la especialidad marcada como primaria.
