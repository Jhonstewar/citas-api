---
id: EP-009
tipo: epica
titulo: "Trazabilidad y contrato REST"
estado: Borrador
requisitos: [RF-19, RF-20]
historias:
  - "[[HU-032-auditar-cambios-de-estado-de-cita]]"
  - "[[HU-033-publicar-contrato-rest-documentado]]"
dependencias:
  - "[[EP-001-identidad-y-acceso-seguro]]"
---

# EP-009 — Trazabilidad y contrato REST

## Objetivo

Garantizar que todo cambio de estado de una cita quede registrado de forma auditable y no editable como CRUD normal, y que el contrato REST entre `citas-web` y `citas-api` esté diseñado, documentado y sea la única vía de comunicación entre ambos repositorios.

## Valor esperado

La auditoría es la única forma de responder "quién cambió qué y por qué" sin adivinar, y es además la base sobre la que las automatizaciones n8n de S5/S6 podrán detectar cambios de estado. El contrato REST documentado permite que dos repositorios independientes evolucionen sin romperse.

## Actores

- SYSTEM (origen automático de transiciones)
- USER
- ADMIN
- PROFESSIONAL
- Equipo de desarrollo (consumidor del contrato)

## Alcance

- Registro de historial por cada cambio de estado de cita con cita, estado nuevo, actor cuando existe, origen `SYSTEM`/`USER`/`ADMIN`, fecha y hora, y motivo opcional (RF-19).
- Protección del historial frente a edición y borrado por operaciones CRUD normales (RN-12).
- Consulta del historial de una cita por los perfiles autorizados.
- Diseño y documentación del contrato REST JSON que consume el frontend directamente, sin Express ni BFF (RF-20, restricciones técnicas).
- URL del backend configurable por entorno en `citas-web` (restricciones técnicas).
- CORS explícito entre `citas-web` y `citas-api` (PRD §8).

## Fuera de alcance

- Los workflows n8n de S5 y S6: se añaden después sin cambiar el núcleo funcional (PRD §10) y se versionan en `citas-api/automations/n8n/`.
- CI/CD, declarado fuera de alcance por el PRD (PRD §9).
- Auditoría de entidades distintas de la cita (usuarios, catálogos, bloques): RF-19 solo exige la cita.

## Reglas de negocio

- RF-19: cada cambio de estado guarda cita, estado nuevo, actor cuando existe, origen, fecha y hora y motivo opcional.
- RN-11: las transiciones de estado son explícitas y verificables; no se permiten cambios de estado arbitrarios.
- RN-12: los datos de auditoría no se modifican como CRUD normal.
- RN-04: cuando la transición proviene de un rechazo administrativo, el motivo es obligatorio y se conserva en el historial.
- El origen es `SYSTEM` cuando la transición no la ejecuta una persona, como la aprobación automática de una cita general (RN-02).
- RF-20: no existe Express ni BFF; el frontend consume la API REST de Spring Boot directamente.
- Ningún contrato expone contraseñas ni tokens en sus respuestas ni en logs (PRD §8).

## Dependencias

- [[EP-001-identidad-y-acceso-seguro]] — el actor de la auditoría proviene del contexto de autenticación.
- Es transversal a: [[EP-006-busqueda-de-disponibilidad-y-reserva]], [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]], [[EP-008-operacion-administrativa-de-solicitudes]], [[EP-005-agenda-del-profesional]].

## Historias de usuario

- [[HU-032-auditar-cambios-de-estado-de-cita]]
- [[HU-033-publicar-contrato-rest-documentado]]

## Criterio de completitud de la épica

- [ ] Todas las HU obligatorias de esta épica están `Completada`.
- [ ] Toda transición de estado producida por cualquier épica deja exactamente un registro de historial con sus seis campos de RF-19.
- [ ] No existe un endpoint que permita editar o borrar un registro de historial.
- [ ] El contrato REST está documentado y actualizado para todos los endpoints publicados, y `citas-web` consume ese contrato sin capa intermedia.
- [ ] La URL del backend es configurable por entorno en `citas-web` y no está escrita en el código.
- [ ] No quedan dependencias bloqueantes dentro del alcance de la épica.

## Riesgos e incógnitas

- **INC-037** — El PRD no define quién puede consultar el historial de una cita (¿solo ADMIN, también el paciente propietario, también el profesional asignado?).
- **INC-038** — El PRD no define el formato de documentación del contrato REST (OpenAPI generado, documento markdown mantenido a mano, o ambos). RF-20 solo dice que debe diseñarse y documentarse.
- **INC-039** — El PRD no define una política de versionado del contrato REST entre los repos `citas-api` y `citas-web`, que son independientes.
- **INC-040** — El PRD no define un formato estándar de error de la API. Sin decidirlo, cada HU inventará el suyo y el frontend no podrá tratar los errores de forma uniforme.
