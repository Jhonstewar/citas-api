---
tipo: indice
titulo: "Especificación Scrum — FCV Citas"
estado: Borrador
---

# Especificación Scrum — Sistema de Agendamiento de Citas (FCV Citas)

Punto de entrada del grafo Obsidian de la especificación. Cubre todo el alcance funcional del PRD, de RF-01 a RF-20, con sus doce reglas de negocio RN-01 a RN-12.

**Fuentes de verdad:** `PRD.md`, `RESTRICCIONES_TECNICAS.md` y el modelo de datos propio (`../llm-wiki/raw/MODELO-DATOS-3FN.md`).

> La especificación se generó sin ninguna historia `Aprobada`. Después, el agente orquestador de S2 pasó HU-001 a HU-004 a `Aprobada` como **aprobación delegada**: el usuario eligió ejecutar S2 en modo autónomo y autorizó al agente a asumir las aprobaciones (ver `AGENTS.md` §6). No hubo una aprobación humana HU por HU; el usuario puede confirmarlas o devolverlas a `Pendiente de aprobación`.

## Objetivo del producto

Construir una aplicación web de agendamiento de citas para un laboratorio de formación en desarrollo asistido por agentes. Un paciente se registra, busca disponibilidad, agenda una cita general aprobada al instante o solicita una especializada que un administrador resuelve, y gestiona sus cancelaciones y reprogramaciones. Un profesional publica su agenda y cierra la atención. Todo cambio de estado queda auditado.

Los datos de personas, profesionales, matrículas, EPS, agendas y citas son sintéticos. Nunca se usan datos reales de pacientes o profesionales de FCV.

## Stack detectado

| Repositorio | Stack |
|---|---|
| `citas-api` | Java 21 LTS · Spring Boot 3.5.x · Maven · arquitectura hexagonal (domain / application / infrastructure) · Spring Data JPA · MySQL 8.4 · Flyway · Spring Security + JWT access/refresh · API REST JSON |
| `citas-web` | React + TypeScript + Vite · Node 24 LTS · REST directo contra `citas-api`, sin Express ni BFF · URL de backend configurable por entorno |

Repositorios independientes con ramas `main` (estable) y `develop` (trabajo).

## Mapa de épicas

| Épica | Título | RF cubiertos | HU |
|---|---|---|---|
| [[EP-001-identidad-y-acceso-seguro]] | Identidad y acceso seguro | RF-01, RF-02, RF-03 | 7 |
| [[EP-002-perfil-y-afiliacion-del-paciente]] | Perfil y afiliación del paciente | RF-04 | 2 |
| [[EP-003-catalogos-del-sistema]] | Catálogos del sistema | RF-05, RF-06, RF-09 | 3 |
| [[EP-004-gestion-de-profesionales]] | Gestión de profesionales | RF-07 | 4 |
| [[EP-005-agenda-del-profesional]] | Agenda del profesional | RF-08, RF-09, RF-16, RF-17 | 5 |
| [[EP-006-busqueda-de-disponibilidad-y-reserva]] | Búsqueda de disponibilidad y reserva de citas | RF-10, RF-11, RF-12 | 3 |
| [[EP-007-ciclo-de-vida-de-las-citas-del-paciente]] | Ciclo de vida de las citas del paciente | RF-13, RF-14, RF-15 | 4 |
| [[EP-008-operacion-administrativa-de-solicitudes]] | Operación administrativa de solicitudes | RF-12, RF-15, RF-18 | 3 |
| [[EP-009-trazabilidad-y-contrato-rest]] | Trazabilidad y contrato REST | RF-19, RF-20 | 2 |

**Total: 9 épicas, 33 historias de usuario.**

## Candidatas a aprobación para S2

Estas cinco historias cubren RF-01 y RF-02. HU-001 a HU-004 están `Aprobada` (aprobación delegada, alcance de GOAL_01); HU-005 sigue en `Pendiente de aprobación`. El resto permanece en `Borrador`.

| HU | Título | Esfuerzo | RF |
|---|---|---|---|
| [[HU-001-registrar-cuenta-de-usuario]] | Registrar cuenta de usuario | Medio | RF-01 |
| [[HU-002-iniciar-sesion-con-jwt]] | Iniciar sesión con JWT | Alto | RF-02 |
| [[HU-003-renovar-sesion-con-refresh-token]] | Renovar sesión con refresh token | Medio | RF-02 |
| [[HU-004-cerrar-sesion-revocando-refresh-token]] | Cerrar sesión revocando el refresh token | Bajo | RF-02 |
| [[HU-005-autorizar-peticiones-por-rol-y-ownership]] | Autorizar peticiones por rol y ownership | Alto | RF-02 |

Antes de aprobarlas conviene resolver **INC-001** (política de complejidad de contraseña) y **INC-002** (vigencia de los tokens), porque afectan directamente a sus criterios de aceptación.

## Propuesta de sprints

Cada sprint es un incremento funcional comprobable, no una caja de tiempo. No se asigna duración, fecha, capacidad, velocidad ni puntos. El trabajo se ordena de forma secuencial porque lo ejecuta un solo desarrollador.

| Sprint | Incremento funcional | Historias |
|---|---|---|
| Sprint 1 | Una persona puede registrarse, entrar, mantener y cerrar su sesión, y la API queda protegida por rol y ownership sobre un contrato REST documentado | [[HU-001-registrar-cuenta-de-usuario]], [[HU-002-iniciar-sesion-con-jwt]], [[HU-003-renovar-sesion-con-refresh-token]], [[HU-004-cerrar-sesion-revocando-refresh-token]], [[HU-005-autorizar-peticiones-por-rol-y-ownership]], [[HU-033-publicar-contrato-rest-documentado]] |
| Sprint 2 | Un usuario recupera el acceso perdido y mantiene su perfil y su afiliación | [[HU-006-solicitar-recuperacion-de-contrasena]], [[HU-007-restablecer-contrasena-con-token]], [[HU-008-consultar-y-actualizar-perfil]], [[HU-009-registrar-afiliacion-a-eps-y-plan]] |
| Sprint 3 | El sistema tiene datos maestros vigentes y profesionales configurados con especialidades y sedes | [[HU-010-consultar-catalogos-fijos-precargados]], [[HU-011-gestionar-especialidades-y-su-duracion]], [[HU-012-gestionar-eps-y-planes]], [[HU-013-crear-profesional-con-datos-de-registro]], [[HU-014-asignar-especialidades-y-especialidad-primaria]], [[HU-015-asignar-sedes-al-profesional]], [[HU-016-activar-o-desactivar-profesional]] |
| Sprint 4 | Existe oferta real: el profesional publica agenda y el paciente ve solo franjas que alojan la duración completa | [[HU-017-crear-bloques-de-disponibilidad-con-slots]], [[HU-018-editar-y-eliminar-bloques-futuros]], [[HU-019-consultar-calendario-de-disponibilidad]], [[HU-022-buscar-disponibilidad-con-filtros]] |
| Sprint 5 | El paciente obtiene citas: la general queda aprobada al instante y la especializada queda solicitada con sus slots retenidos, todo auditado | [[HU-032-auditar-cambios-de-estado-de-cita]], [[HU-023-agendar-cita-de-medicina-general]], [[HU-024-solicitar-cita-especializada]], [[HU-025-consultar-mis-citas-y-detalle]] |
| Sprint 6 | El administrador desbloquea la agenda resolviendo las solicitudes especializadas | [[HU-029-consultar-bandeja-administrativa]], [[HU-030-aprobar-o-rechazar-cita-especializada]] |
| Sprint 7 | El paciente cancela y reprograma sin perder su cita original, y el administrador resuelve la reprogramación | [[HU-026-cancelar-una-cita-futura]], [[HU-027-solicitar-reprogramacion-de-cita-aprobada]], [[HU-031-aprobar-o-rechazar-reprogramacion]], [[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]] |
| Sprint 8 | El profesional consulta su agenda del día y cierra el resultado de cada atención | [[HU-020-consultar-agenda-de-citas-aprobadas]], [[HU-021-registrar-cierre-de-atencion]] |

Notas de ordenación:

- [[HU-033-publicar-contrato-rest-documentado]] se inaugura en el Sprint 1 pero es un artefacto vivo: crece con cada endpoint publicado en los sprints posteriores.
- [[HU-032-auditar-cambios-de-estado-de-cita]] se adelanta al Sprint 5, antes de la primera creación de citas, para que ninguna transición nazca sin historial.
- [[HU-022-buscar-disponibilidad-con-filtros]] pertenece a EP-006 pero se planifica en el Sprint 4 porque cierra el incremento de disponibilidad y no tiene sentido sin bloques publicados.
- [[HU-020-consultar-agenda-de-citas-aprobadas]] y [[HU-021-registrar-cierre-de-atencion]] pertenecen a EP-005 pero se planifican al final porque requieren citas ya aprobadas.

## Trazabilidad de requisitos

| RF | Historias |
|---|---|
| RF-01 | [[HU-001-registrar-cuenta-de-usuario]] |
| RF-02 | [[HU-002-iniciar-sesion-con-jwt]], [[HU-003-renovar-sesion-con-refresh-token]], [[HU-004-cerrar-sesion-revocando-refresh-token]], [[HU-005-autorizar-peticiones-por-rol-y-ownership]] |
| RF-03 | [[HU-006-solicitar-recuperacion-de-contrasena]], [[HU-007-restablecer-contrasena-con-token]] |
| RF-04 | [[HU-008-consultar-y-actualizar-perfil]], [[HU-009-registrar-afiliacion-a-eps-y-plan]] |
| RF-05 | [[HU-010-consultar-catalogos-fijos-precargados]] |
| RF-06 | [[HU-011-gestionar-especialidades-y-su-duracion]], [[HU-012-gestionar-eps-y-planes]] |
| RF-07 | [[HU-013-crear-profesional-con-datos-de-registro]], [[HU-014-asignar-especialidades-y-especialidad-primaria]], [[HU-015-asignar-sedes-al-profesional]], [[HU-016-activar-o-desactivar-profesional]] |
| RF-08 | [[HU-017-crear-bloques-de-disponibilidad-con-slots]], [[HU-018-editar-y-eliminar-bloques-futuros]], [[HU-019-consultar-calendario-de-disponibilidad]] |
| RF-09 | [[HU-011-gestionar-especialidades-y-su-duracion]], [[HU-017-crear-bloques-de-disponibilidad-con-slots]], [[HU-022-buscar-disponibilidad-con-filtros]] |
| RF-10 | [[HU-022-buscar-disponibilidad-con-filtros]] |
| RF-11 | [[HU-023-agendar-cita-de-medicina-general]] |
| RF-12 | [[HU-024-solicitar-cita-especializada]], [[HU-030-aprobar-o-rechazar-cita-especializada]] |
| RF-13 | [[HU-025-consultar-mis-citas-y-detalle]] |
| RF-14 | [[HU-026-cancelar-una-cita-futura]] |
| RF-15 | [[HU-027-solicitar-reprogramacion-de-cita-aprobada]], [[HU-031-aprobar-o-rechazar-reprogramacion]], [[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]] |
| RF-16 | [[HU-020-consultar-agenda-de-citas-aprobadas]] |
| RF-17 | [[HU-021-registrar-cierre-de-atencion]] |
| RF-18 | [[HU-029-consultar-bandeja-administrativa]] |
| RF-19 | [[HU-032-auditar-cambios-de-estado-de-cita]] |
| RF-20 | [[HU-033-publicar-contrato-rest-documentado]] |

## Estados documentales

`Borrador` → `Pendiente de aprobación` → `Aprobada` → `En desarrollo` → `En validación` → `Completada`. `Bloqueada` se usa ante un impedimento real.

Solo el usuario humano mueve una historia a `Aprobada`. Una historia pasa a `Completada` únicamente cuando todos sus criterios de aceptación y toda su DoD aplicable están en `Cumple` con evidencia registrada en su matriz.

## Decisiones e incógnitas pendientes

Las cuarenta incógnitas detectadas están registradas en la épica correspondiente como `INC-NNN`. Estas son las que más condicionan el trabajo y conviene resolver antes de aprobar las historias afectadas:

| Incógnita | Pregunta abierta | Épica | Bloquea |
|---|---|---|---|
| INC-001 | ¿Qué política de complejidad mínima debe cumplir una contraseña? | [[EP-001-identidad-y-acceso-seguro]] | S2 |
| INC-002 | ¿Qué vigencia tienen el access token, el refresh token y el token de recuperación? | [[EP-001-identidad-y-acceso-seguro]] | S2 |
| INC-003 | ¿ADMIN y PROFESSIONAL entran por el mismo login que USER? | [[EP-001-identidad-y-acceso-seguro]] | S2 |
| INC-006 | ¿Qué campos del perfil son editables y cuáles quedan fijos? | [[EP-002-perfil-y-afiliacion-del-paciente]] | Sprint 2 |
| INC-007 | ¿Un usuario puede tener varias afiliaciones simultáneas o solo una vigente? | [[EP-002-perfil-y-afiliacion-del-paciente]] | Sprint 2 |
| INC-009 | ¿`Medicina General` es una especialidad protegida o una más del CRUD? | [[EP-003-catalogos-del-sistema]] | Sprint 3 |
| INC-010 | ¿Qué ocurre con citas y bloques futuros al desactivar una especialidad? | [[EP-003-catalogos-del-sistema]] | Sprint 3 |
| INC-013 | ¿Cómo recibe su contraseña inicial un profesional creado por ADMIN? | [[EP-004-gestion-de-profesionales]] | Sprint 3 |
| INC-014 | ¿Qué ocurre con bloques y citas futuras al desactivar un profesional? | [[EP-004-gestion-de-profesionales]] | Sprint 3 |
| INC-017 | ¿Cuál es la zona horaria de referencia del sistema? | [[EP-005-agenda-del-profesional]] | Sprint 4 |
| INC-018 | ¿Desde qué momento una cita puede cerrarse como `COMPLETED` o `NO_SHOW`? | [[EP-005-agenda-del-profesional]] | Sprint 8 |
| INC-022 | ¿Existe una antelación mínima para reservar? | [[EP-006-busqueda-de-disponibilidad-y-reserva]] | Sprint 5 |
| INC-024 | ¿Caduca la retención de slots de una cita `REQUESTED` sin decisión de ADMIN? | [[EP-006-busqueda-de-disponibilidad-y-reserva]] | Sprint 5 |
| INC-032 | ¿Qué pasa si al aprobar una cita la franja retenida ya no es válida? | [[EP-008-operacion-administrativa-de-solicitudes]] | Sprint 6 |
| INC-038 | ¿El contrato REST se documenta con OpenAPI generado, markdown a mano, o ambos? | [[EP-009-trazabilidad-y-contrato-rest]] | Sprint 1 |
| INC-040 | ¿Cuál es el formato estándar de error de la API? | [[EP-009-trazabilidad-y-contrato-rest]] | Sprint 1 |

Ninguna de estas incógnitas impide empezar: cada historia afectada las registra en sus notas y define sus criterios sobre el mecanismo, no sobre un umbral inventado.

## Fuera de alcance del producto

Según PRD §9: historia clínica, facturación real, pagos, diagnósticos y tratamientos, datos reales de FCV, integración con sistemas clínicos, CI/CD obligatorio, SMS y WhatsApp, y SMTP obligatorio para la recuperación de contraseña.

Las automatizaciones n8n de S5 y S6 (recordatorios, notificación de cambio de estado y resumen operativo diario) se añaden después sin modificar el núcleo funcional y se versionan en `citas-api/automations/n8n/`.
