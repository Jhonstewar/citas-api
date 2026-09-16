---
id: EP-005
tipo: epica
titulo: "Agenda del profesional"
estado: Borrador
requisitos: [RF-08, RF-09, RF-16, RF-17]
historias:
  - "[[HU-017-crear-bloques-de-disponibilidad-con-slots]]"
  - "[[HU-018-editar-y-eliminar-bloques-futuros]]"
  - "[[HU-019-consultar-calendario-de-disponibilidad]]"
  - "[[HU-020-consultar-agenda-de-citas-aprobadas]]"
  - "[[HU-021-registrar-cierre-de-atencion]]"
dependencias:
  - "[[EP-004-gestion-de-profesionales]]"
  - "[[EP-003-catalogos-del-sistema]]"
---

# EP-005 — Agenda del profesional

## Objetivo

Permitir que un PROFESSIONAL publique su disponibilidad mediante bloques por día y sede que se discretizan en slots de 30 minutos, la mantenga mientras no haya citas comprometidas, consulte su calendario y su agenda de citas aprobadas, y cierre la atención de las citas que le corresponden.

## Valor esperado

La agenda del profesional es la oferta que el paciente consume. Esta épica convierte la voluntad del profesional ("atiendo de 08:00 a 12:00 en HIC") en unidades reservables verificables, y cierra el ciclo con el registro de asistencia o inasistencia.

## Actores

- PROFESSIONAL

## Alcance

- Creación de múltiples bloques de disponibilidad por día, cada uno con su sede (RF-08).
- Discretización automática del bloque en slots atómicos de 30 minutos (RF-08, RF-09).
- Edición y eliminación de bloques futuros que no tengan citas comprometidas (RF-08).
- Consulta del calendario propio de disponibilidad (RF-08).
- Consulta de la agenda de citas `APPROVED` por día o semana y por sede (RF-16).
- Marcado de una cita como `COMPLETED` o `NO_SHOW` con registro en el historial (RF-17).

## Fuera de alcance

- Aprobación o rechazo de citas: el PRD lo asigna a ADMIN y lo excluye del profesional (PRD §2, RF-12).
- Definición de la duración de la cita: la fija la especialidad y el profesional no la sobrescribe (RF-09).
- Acceso a datos de usuarios ajenos a sus propias citas (RF-16).
- Creación o cancelación de citas por el profesional; no está en el PRD.

## Reglas de negocio

- RN-06: no se permiten bloques ni citas en el pasado.
- No se permite solapamiento entre bloques del mismo profesional (RF-08).
- RN-07: el profesional debe estar habilitado en la sede del bloque.
- El bloque se discretiza en slots de 30 minutos (RF-08).
- Un bloque con citas comprometidas no se edita ni se elimina (RF-08).
- RN-05: una especialidad de 60 minutos consume dos slots consecutivos; una de 30 minutos, uno.
- El profesional solo ve sus propias citas y no accede a datos de usuarios fuera de ellas (RF-16).
- RN-11 y RF-19: el cierre de atención es una transición de estado explícita que se audita.

## Dependencias

- [[EP-004-gestion-de-profesionales]] — el profesional debe existir, estar activo y tener sedes y especialidades asignadas.
- [[EP-003-catalogos-del-sistema]] — sedes y duración por especialidad.
- [[EP-009-trazabilidad-y-contrato-rest]] — el cierre de atención escribe historial de estados.
- Habilita a: [[EP-006-busqueda-de-disponibilidad-y-reserva]].

## Historias de usuario

- [[HU-017-crear-bloques-de-disponibilidad-con-slots]]
- [[HU-018-editar-y-eliminar-bloques-futuros]]
- [[HU-019-consultar-calendario-de-disponibilidad]]
- [[HU-020-consultar-agenda-de-citas-aprobadas]]
- [[HU-021-registrar-cierre-de-atencion]]

## Criterio de completitud de la épica

- [ ] Todas las HU obligatorias de esta épica están `Completada`.
- [ ] Un profesional puede publicar un día con dos bloques en la misma sede (por ejemplo 08:00–12:00 y 14:00–17:00) y los slots resultantes quedan disponibles para reserva.
- [ ] Ningún bloque solapado, pasado o en sede no asignada llega a persistirse.
- [ ] El profesional consulta su agenda de citas aprobadas filtrada por día, semana y sede.
- [ ] Toda transición a `COMPLETED` o `NO_SHOW` deja registro en el historial de estados.
- [ ] No quedan dependencias bloqueantes dentro del alcance de la épica.

## Riesgos e incógnitas

- **INC-017** — El PRD no define la zona horaria de referencia del sistema ni si las horas se manejan en hora local de Colombia. Afecta a la regla "no crear bloques en el pasado" y a los filtros por fecha.
- **INC-018** — RF-17 dice "cita pasada/aplicable" sin precisar la condición. Falta decidir desde qué momento una cita `APPROVED` puede cerrarse (al terminar su franja, al finalizar el día, u otro) y si existe un plazo máximo para cerrarla.
- **INC-019** — El PRD no define si un bloque puede editarse parcialmente cuando solo algunos de sus slots tienen citas, o si la restricción aplica al bloque completo.
- **INC-020** — El PRD no indica si el profesional puede publicar disponibilidad por especialidad concreta o si todos sus slots quedan abiertos a cualquiera de sus especialidades.
- **INC-021** — El PRD no define un horizonte máximo hacia el futuro para publicar bloques.
