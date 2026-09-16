---
id: HU-016
tipo: historia-de-usuario
titulo: "Activar o desactivar profesional"
estado: Borrador
epica: "[[EP-004-gestion-de-profesionales]]"
requisitos: [RF-07]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 3"
dependencias:
  - "[[HU-013-crear-profesional-con-datos-de-registro]]"
relacionadas:
  - "[[HU-022-buscar-disponibilidad-con-filtros]]"
  - "[[HU-017-crear-bloques-de-disponibilidad-con-slots]]"
  - "[[HU-030-aprobar-o-rechazar-cita-especializada]]"
---

# HU-016 — Activar o desactivar profesional

## Historia de usuario

**COMO** ADMIN  
**QUIERO** desactivar a un profesional que deja de atender y reactivarlo cuando vuelva  
**PARA** retirar su oferta de agenda sin borrar sus datos ni perder la trazabilidad de sus citas

> Como ADMIN, quiero desactivar a un profesional que deja de atender y reactivarlo cuando vuelva para retirar su oferta de agenda sin borrar sus datos ni perder la trazabilidad de sus citas.

## Contexto y descripción

RF-07 incluye la activación y desactivación del profesional entre las capacidades de ADMIN. [[EP-004-gestion-de-profesionales]] fija que un profesional referenciado por transacciones se desactiva y no se borra, aplicando por analogía la regla de RF-06 sobre catálogos y la preservación de trazabilidad de RN-12. La tabla `professionals` de V2 ya tiene el indicador `active`.

El efecto observable mínimo que exige la épica es que un profesional desactivado no aparezca como oferta en la búsqueda de disponibilidad. Lo que ocurre con sus bloques futuros y con sus citas ya comprometidas no está definido en el PRD (INC-014), por lo que esta HU no modifica citas existentes.

## Alcance

- Endpoints REST de desactivación y de activación de un profesional, restringidos a `ADMIN`.
- Transición explícita del indicador `active` con respuesta idempotente o error claro ante una transición redundante.
- Exclusión del profesional inactivo de la oferta de disponibilidad y de nuevas reservas.
- Filtro por estado activo/inactivo en el listado de profesionales de ADMIN.
- Acción de activar/desactivar con confirmación en la pantalla de gestión de profesionales de `citas-web`.

## Fuera de alcance

- Borrado físico del profesional: no se ofrece.
- Cancelación, rechazo o reasignación automática de citas futuras del profesional desactivado: pendiente de INC-014.
- Bloqueo del login del profesional desactivado: el PRD no lo define (ver notas).

## Reglas de negocio

- ADMIN puede activar y desactivar profesionales (RF-07).
- Un profesional se desactiva, no se borra, para preservar la trazabilidad de sus citas (EP-004, RN-12).
- Un profesional inactivo no se ofrece en la búsqueda de disponibilidad ni puede recibir nuevas citas (criterio de completitud de EP-004, RN-08 por analogía).
- Solo ADMIN ejecuta la operación (PRD §8).

## Dependencias y relaciones

- Épica: [[EP-004-gestion-de-profesionales]]
- Dependencias: [[HU-013-crear-profesional-con-datos-de-registro]]
- Relacionadas: [[HU-014-asignar-especialidades-y-especialidad-primaria]], [[HU-015-asignar-sedes-al-profesional]], [[HU-017-crear-bloques-de-disponibilidad-con-slots]], [[HU-022-buscar-disponibilidad-con-filtros]], [[HU-023-agendar-cita-de-medicina-general]], [[HU-024-solicitar-cita-especializada]], [[HU-030-aprobar-o-rechazar-cita-especializada]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** El cambio de estado en sí es sencillo, pero su efecto es transversal: la búsqueda de disponibilidad y los casos de uso de reserva deben respetar el indicador, y la decisión abierta sobre bloques y citas futuras obliga a acotar con cuidado lo que la HU hace y lo que deja intacto.

## Tareas de desarrollo

- [ ] **T-01 — Modelar las transiciones de activación en el dominio**  
  Dificultad: Bajo  
  Descripción: Operaciones explícitas de activar y desactivar sobre el agregado profesional, con la regla de transición redundante definida, sin dependencias de framework.

- [ ] **T-02 — Implementar los casos de uso de activación y desactivación**  
  Dificultad: Bajo  
  Descripción: Casos de uso que recuperan el profesional, aplican la transición y persisten el nuevo estado sin tocar usuario, especialidades, sedes, bloques ni citas.

- [ ] **T-03 — Exponer los endpoints REST y el filtro por estado**  
  Dificultad: Bajo  
  Descripción: Endpoints restringidos a `ADMIN` para activar y desactivar, y parámetro de filtro por estado en el listado de profesionales.

- [ ] **T-04 — Publicar la condición "profesional activo" para la oferta y la reserva**  
  Dificultad: Medio  
  Descripción: Consulta o especificación reutilizable que la búsqueda de disponibilidad y los casos de uso de reserva aplicarán para excluir profesionales inactivos.

- [ ] **T-05 — Añadir la acción de activar/desactivar en citas-web**  
  Dificultad: Bajo  
  Descripción: Botón con diálogo de confirmación en la gestión de profesionales, indicador visual de estado y filtro por estado.

- [ ] **T-06 — Pruebas de activación y desactivación**  
  Dificultad: Medio  
  Descripción: Pruebas de dominio de las transiciones e integración para la desactivación, la reactivación, la conservación de citas existentes y la restricción de rol.

## Criterios de aceptación

### CA-01 — Desactivación de un profesional activo

**Dado** un profesional activo  
**Cuando** ADMIN lo desactiva  
**Entonces** la API responde con éxito, el profesional figura como inactivo en el listado y su usuario, especialidades y sedes siguen existiendo.

### CA-02 — Reactivación de un profesional inactivo

**Dado** un profesional inactivo  
**Cuando** ADMIN lo activa  
**Entonces** la API responde con éxito y el profesional vuelve a figurar como activo con sus especialidades y sedes previas.

### CA-03 — Un profesional inactivo no se ofrece como disponibilidad

**Dado** un profesional con slots libres publicados en una fecha futura  
**Cuando** ADMIN lo desactiva y un paciente busca disponibilidad con filtros que lo incluirían  
**Entonces** ningún slot de ese profesional aparece en los resultados, verificado contra [[HU-022-buscar-disponibilidad-con-filtros]].

### CA-04 — Un profesional inactivo no recibe nuevas citas

**Dado** un profesional inactivo y el identificador de uno de sus slots libres futuros  
**Cuando** un paciente intenta reservar directamente ese slot  
**Entonces** la API rechaza la reserva con un error de regla de negocio y no se crea cita ni reserva de slot.

### CA-05 — Las citas existentes se conservan

**Dado** un profesional con citas y registros de historial existentes  
**Cuando** ADMIN lo desactiva  
**Entonces** ninguna cita cambia de estado, no se libera ningún slot reservado y no se borra ningún registro de historial.

### CA-06 — No existe borrado físico

**Dado** cualquier profesional  
**Cuando** se inspeccionan los endpoints publicados de gestión de profesionales  
**Entonces** no existe ninguna operación que elimine físicamente al profesional o a su usuario.

### CA-07 — Solo ADMIN activa o desactiva

**Dado** un usuario autenticado con rol `USER` o `PROFESSIONAL`  
**Cuando** invoca la activación o desactivación de cualquier profesional  
**Entonces** la API responde con un error de autorización y el estado no cambia.

## Definition of Done

- [ ] Los criterios CA-01 a CA-07 están validados con evidencia concreta.
- [ ] Activar y desactivar son operaciones explícitas del dominio, no una actualización genérica del campo `active`.
- [ ] La condición de profesional activo está centralizada y la reutilizan la búsqueda y la reserva, sin copias divergentes.
- [ ] No se introduce borrado físico ni se alteran citas, reservas de slot ni historial.
- [ ] Los endpoints exigen rol `ADMIN` aplicando [[HU-005-autorizar-peticiones-por-rol-y-ownership]].
- [ ] La acción de `citas-web` requiere confirmación explícita.
- [ ] Existen pruebas automatizadas de desactivación, reactivación, conservación de citas y rol, y pasan; CA-03 y CA-04 se verifican cuando existan [[HU-022-buscar-disponibilidad-con-filtros]] y [[HU-023-agendar-cita-de-medicina-general]].
- [ ] El contrato de los endpoints de activación está reflejado en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [ ] La trazabilidad de esta HU y de [[EP-004-gestion-de-profesionales]] está actualizada en `docs/wiki/scrum/`.

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
| DoD | Pendiente | — | — |

## Historial de validación

- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- Incógnita abierta **INC-014** (ver [[EP-004-gestion-de-profesionales]]): el PRD no define qué ocurre con bloques futuros y citas `REQUESTED` o `APPROVED` de un profesional desactivado. CA-05 fija el comportamiento conservador (no se toca nada) hasta que exista decisión humana.
- Incógnita abierta **INC-032** (ver [[EP-008-operacion-administrativa-de-solicitudes]]): si ADMIN desactiva a un profesional con citas `REQUESTED`, no está definido si esas solicitudes pueden aprobarse después. Afecta a [[HU-030-aprobar-o-rechazar-cita-especializada]].
- El PRD no define si un profesional inactivo puede seguir iniciando sesión, consultar su agenda o cerrar atenciones pasadas. Esta HU no modifica la autenticación; debe decidirse antes de aprobarla.
- CA-03 y CA-04 dependen de HU planificadas en sprints posteriores; la HU puede validarse en su sprint con los demás criterios y completar esos dos cuando existan la búsqueda y la reserva, o bien planificarse su validación final en el Sprint 5.
