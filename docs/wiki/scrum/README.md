---
tipo: indice
titulo: "Especificación Scrum — FCV Citas"
estado: Borrador
---

# Especificación Scrum — Sistema de Agendamiento de Citas (FCV Citas)

Punto de entrada del grafo Obsidian de la especificación. Cubre todo el alcance funcional del PRD, de RF-01 a RF-20, con sus doce reglas de negocio RN-01 a RN-12.

**Fuentes de verdad:** `PRD.md`, `RESTRICCIONES_TECNICAS.md` y el modelo de datos propio (`../llm-wiki/raw/MODELO-DATOS-3FN.md`).

> La especificación se generó sin ninguna historia `Aprobada`. Después, el agente orquestador de S2 pasó HU-001 a HU-004 a `Aprobada` como **aprobación delegada**: el usuario eligió ejecutar S2 en modo autónomo y autorizó al agente a asumir las aprobaciones (ver `AGENTS.md` §6). No hubo una aprobación humana HU por HU; el usuario puede confirmarlas o devolverlas a `Pendiente de aprobación`. El 2026-09-17 las cuatro pasaron a `Completada`, con la matriz de evidencia completa y toda su DoD en `Cumple` según la verificación independiente de backend y frontend.

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
| [[EP-010-automatizaciones-n8n]] | Automatizaciones n8n (`Aprobada`) | PRD §10, RF-19, RF-20 | 3 |

**Total: 10 épicas, 36 historias de usuario.**

## Candidatas a aprobación para S2

Estas cinco historias cubren RF-01 y RF-02. HU-001 a HU-004 están `Completada` (aprobación delegada, alcance de GOAL_01; cierre del 2026-09-17). HU-005 se aprobó por delegación en S3, se implementó y quedó en `En validación` al cerrar S3: siete de sus nueve criterios cumplen, pero RF-16 depende de [[HU-020-consultar-agenda-de-citas-aprobadas]].

| HU | Título | Esfuerzo | RF | Estado |
|---|---|---|---|---|
| [[HU-001-registrar-cuenta-de-usuario]] | Registrar cuenta de usuario | Medio | RF-01 | `Completada` |
| [[HU-002-iniciar-sesion-con-jwt]] | Iniciar sesión con JWT | Alto | RF-02 | `Completada` |
| [[HU-003-renovar-sesion-con-refresh-token]] | Renovar sesión con refresh token | Medio | RF-02 | `Completada` |
| [[HU-004-cerrar-sesion-revocando-refresh-token]] | Cerrar sesión revocando el refresh token | Bajo | RF-02 | `Completada` |
| [[HU-005-autorizar-peticiones-por-rol-y-ownership]] | Autorizar peticiones por rol y ownership | Alto | RF-02 | `En validación` |

**INC-001** (política de complejidad de contraseña) quedó resuelta en S4 por D29 (provisional bajo delegación): mínimo 8 caracteres con letra y número también en el servidor; obliga a revisar la matriz de HU-001. **INC-002** (vigencia de los tokens) solo está resuelta para el token de recuperación (D27, 30 min configurables); la vigencia de access y refresh sigue abierta. HU-001 a HU-004 se cerraron con criterios definidos sobre el mecanismo y no sobre un umbral; HU-001, HU-002 y HU-003 registran en sus notas qué revisar.

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
| Sprint 9 (propuesto, S5) | n8n lee con clave dedicada las citas `APPROVED` próximas y el flujo de recordatorios WF-001 queda validado | [[HU-034-consultar-citas-para-automatizacion]], WF-001 de [[HU-036-versionar-y-documentar-los-flujos-n8n]] |
| Sprint 10 (propuesto, S6) | Spring publica eventos de cambio de estado y WF-002 (y WF-003 opcional) notifica por correo; flujos versionados con riesgos por escrito | [[HU-035-publicar-eventos-de-cambio-de-estado-a-n8n]], resto de [[HU-036-versionar-y-documentar-los-flujos-n8n]] |

Notas de ordenación:

- Sprint 1: [[HU-001-registrar-cuenta-de-usuario]] a [[HU-004-cerrar-sesion-revocando-refresh-token]] están `Completada`; [[HU-005-autorizar-peticiones-por-rol-y-ownership]] y [[HU-033-publicar-contrato-rest-documentado]] siguen abiertas tras el cierre de S3.
- [[HU-033-publicar-contrato-rest-documentado]] se inaugura en el Sprint 1 pero es un artefacto vivo: crece con cada endpoint publicado en los sprints posteriores. Por eso **no se cierra al terminar un sprint**; su matriz de evidencia se rehace en cada corte y se mantiene en `En desarrollo`.
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
| RF-19 | [[HU-032-auditar-cambios-de-estado-de-cita]], [[HU-035-publicar-eventos-de-cambio-de-estado-a-n8n]] (complementa la auditoría, no la sustituye) |
| RF-20 | [[HU-033-publicar-contrato-rest-documentado]], [[HU-034-consultar-citas-para-automatizacion]], [[HU-035-publicar-eventos-de-cambio-de-estado-a-n8n]], [[HU-036-versionar-y-documentar-los-flujos-n8n]] (contrato de automatización y evento) |
| PRD §10 | [[HU-034-consultar-citas-para-automatizacion]], [[HU-035-publicar-eventos-de-cambio-de-estado-a-n8n]], [[HU-036-versionar-y-documentar-los-flujos-n8n]] |

## Estados documentales

`Borrador` → `Pendiente de aprobación` → `Aprobada` → `En desarrollo` → `En validación` → `Completada`. `Bloqueada` se usa ante un impedimento real.

Solo el usuario humano mueve una historia a `Aprobada`. Una historia pasa a `Completada` únicamente cuando todos sus criterios de aceptación y toda su DoD aplicable están en `Cumple` con evidencia registrada en su matriz.

## Decisiones e incógnitas pendientes

Las cuarenta incógnitas detectadas están registradas en la épica correspondiente como `INC-NNN`. Estas son las que más condicionan el trabajo y conviene resolver antes de aprobar las historias afectadas:

| Incógnita | Pregunta abierta | Épica | Bloquea |
|---|---|---|---|
| INC-001 | ¿Qué política de complejidad mínima debe cumplir una contraseña? | [[EP-001-identidad-y-acceso-seguro]] | **Resuelta** por D29 (S4, provisional) |
| INC-002 | ¿Qué vigencia tienen el access token, el refresh token y el token de recuperación? | [[EP-001-identidad-y-acceso-seguro]] | Token de recuperación **resuelto** por D27 (S4, provisional); access y refresh siguen abiertos |
| INC-003 | ¿ADMIN y PROFESSIONAL entran por el mismo login que USER? | [[EP-001-identidad-y-acceso-seguro]] | S2 |
| INC-006 | ¿Qué campos del perfil son editables y cuáles quedan fijos? | [[EP-002-perfil-y-afiliacion-del-paciente]] | **Resuelta** por D25 (S4, provisional) |
| INC-007 | ¿Un usuario puede tener varias afiliaciones simultáneas o solo una vigente? | [[EP-002-perfil-y-afiliacion-del-paciente]] | **Resuelta** por D26 (S4, provisional) |
| INC-009 | ¿`Medicina General` es una especialidad protegida o una más del CRUD? | [[EP-003-catalogos-del-sistema]] | Sprint 3 |
| INC-010 | ¿Qué ocurre con citas y bloques futuros al desactivar una especialidad? | [[EP-003-catalogos-del-sistema]] | Sprint 3 |
| INC-013 | ¿Cómo recibe su contraseña inicial un profesional creado por ADMIN? | [[EP-004-gestion-de-profesionales]] | Sprint 3 |
| INC-014 | ¿Qué ocurre con bloques y citas futuras al desactivar un profesional? | [[EP-004-gestion-de-profesionales]] | Sprint 3 |
| INC-017 | ¿Cuál es la zona horaria de referencia del sistema? | [[EP-005-agenda-del-profesional]] | Sprint 4 |
| INC-018 | ¿Desde qué momento una cita puede cerrarse como `COMPLETED` o `NO_SHOW`? | [[EP-005-agenda-del-profesional]] | **Resuelta** por D19 (S4, respondida por el usuario) |
| INC-022 | ¿Existe una antelación mínima para reservar? | [[EP-006-busqueda-de-disponibilidad-y-reserva]] | Sprint 5 |
| INC-024 | ¿Caduca la retención de slots de una cita `REQUESTED` sin decisión de ADMIN? | [[EP-006-busqueda-de-disponibilidad-y-reserva]] | Sprint 5 |
| INC-032 | ¿Qué pasa si al aprobar una cita la franja retenida ya no es válida? | [[EP-008-operacion-administrativa-de-solicitudes]] | Sprint 6 |
| INC-038 | ¿El contrato REST se documenta con OpenAPI generado, markdown a mano, o ambos? | [[EP-009-trazabilidad-y-contrato-rest]] | Sprint 1 |
| INC-040 | ¿Cuál es el formato estándar de error de la API? | [[EP-009-trazabilidad-y-contrato-rest]] | Sprint 1 |

Ninguna de estas incógnitas impide empezar: cada historia afectada las registra en sus notas y define sus criterios sobre el mecanismo, no sobre un umbral inventado.

## Fuera de alcance del producto

Según PRD §9: historia clínica, facturación real, pagos, diagnósticos y tratamientos, datos reales de FCV, integración con sistemas clínicos, CI/CD obligatorio, SMS y WhatsApp, y SMTP obligatorio para la recuperación de contraseña.

Las automatizaciones n8n de S5 y S6 (recordatorios, notificación de cambio de estado y resumen operativo diario) se añaden después sin modificar el núcleo funcional y se versionan en `citas-api/automations/n8n/`. Su especificación es [[EP-010-automatizaciones-n8n]].

## Alcance de S3 (aprobación delegada del 2026-09-18)

El usuario pidió continuar S3 dejando las decisiones de diseño a criterio del agente. Pasaron a `Aprobada` por **aprobación delegada**: HU-005, HU-010, HU-011, HU-013 a HU-019, HU-022 a HU-025, HU-029, HU-030, HU-032 y HU-033. Cada una lo registra en su historial. Plan y decisiones D5–D13: `PLAN_RETOMA_S3.md` (raíz) y `../llm-wiki/wiki/dec-004-decisiones-s3-reserva.md`.

## Cierre de S3 (2026-09-23)

Validación de las 18 HU en desarrollo, criterio por criterio, con la evidencia recolectada del repositorio: backend **242 pruebas en verde** en 22 clases, frontend **88 pruebas en verde** con typecheck, lint y build, verificación independiente y ejecución contra la API real (`EVIDENCIAS_S3.md` §8, §10 y §11). Cada HU tiene su matriz completa en su propia nota.

**12 pasan a `Completada`:**

| HU | Título |
|---|---|
| [[HU-010-consultar-catalogos-fijos-precargados]] | Consultar los catálogos fijos precargados |
| [[HU-013-crear-profesional-con-datos-de-registro]] | Crear profesional con datos de registro |
| [[HU-014-asignar-especialidades-y-especialidad-primaria]] | Asignar especialidades y especialidad primaria |
| [[HU-015-asignar-sedes-al-profesional]] | Asignar sedes al profesional |
| [[HU-017-crear-bloques-de-disponibilidad-con-slots]] | Crear bloques de disponibilidad con slots |
| [[HU-018-editar-y-eliminar-bloques-futuros]] | Editar y eliminar bloques futuros |
| [[HU-019-consultar-calendario-de-disponibilidad]] | Consultar calendario de disponibilidad |
| [[HU-023-agendar-cita-de-medicina-general]] | Agendar cita de Medicina General |
| [[HU-024-solicitar-cita-especializada]] | Solicitar cita especializada (**GOAL_02**) |
| [[HU-025-consultar-mis-citas-y-detalle]] | Consultar mis citas y su detalle |
| [[HU-030-aprobar-o-rechazar-cita-especializada]] | Aprobar o rechazar una cita especializada |
| [[HU-032-auditar-cambios-de-estado-de-cita]] | Auditar cambios de estado de cita |

**6 quedaron abiertas al cierre de S3.** Los bloqueos de cinco de ellas ya los resolvió el código en S4 y se retiran de esta tabla (el estado vigente de cada una está en «Verificación independiente de F10», más abajo); solo sigue como bloqueo de S3 la última:

| HU | Estado | Qué falta |
|---|---|---|
| [[HU-033-publicar-contrato-rest-documentado]] | `En desarrollo` | **Abierta por diseño:** artefacto vivo que crece con cada endpoint. Al corte de S3 cubre identidad y todo S3; le faltan recuperación de contraseña, perfil, EPS y planes, agenda de citas aprobadas, cierre de atención, cancelación y reprogramación. Además, INC-038 e INC-040 los decidió el agente y conviene que el usuario los confirme |

**Bloqueos de S3 retirados el 2026-10-04** (ya resueltos por el código de S4; no implican que la HU esté cerrada): [[HU-005-autorizar-peticiones-por-rol-y-ownership]] (componente `Ownership`, CA-06 con la agenda, escritura de CA-05), [[HU-011-gestionar-especialidades-y-su-duracion]] (nombre único con `V8`; la HU no se reevalúa aquí), [[HU-016-activar-o-desactivar-profesional]] (operaciones de dominio y prueba de conservación de citas; ahora `Completada`), [[HU-022-buscar-disponibilidad-con-filtros]] (productor de retenciones de reprogramación y equivalencia de la regla de 60 minutos) y [[HU-029-consultar-bandeja-administrativa]] (mitad de reprogramaciones; ahora `Completada`).

[[HU-009-registrar-afiliacion-a-eps-y-plan]] no entró en esta validación: su primer corte (afiliación opcional al registrarse) se implementó el 2026-09-23, pero su alcance incluye consulta y edición desde el perfil, que todavía no existe.

Ninguna de las seis abiertas bloquea el incremento funcional de S3: GOAL_02 (cita especializada que nace `REQUESTED` y retiene su franja) está cerrado con evidencia, y las carencias son de completitud de especificación o de alcance de S4.

## Alcance de S4 (aprobación delegada del 2026-09-25)

El usuario **delegó la aprobación de S4** (D15) y respondió directamente D18 (cancelar con reprogramación `PENDING`), D19 (cierre de atención desde la hora de inicio) y la elección del LOOP_03. El resto de decisiones (D16, D17, D20–D30) son **provisionales bajo aprobación delegada**: el usuario puede revertir cualquiera y reabrir la HU afectada. Cada HU lo registra en su historial y cita en sus notas la decisión que resuelve cada incógnita. Plan y decisiones: `PLAN_RETOMA_S4.md` (raíz) §2–§4 y `../llm-wiki/wiki/dec-006-decisiones-s4-ciclo-de-vida.md`.

**Pasan a `Aprobada` (10):**

| HU | Título | Esfuerzo | Fase del plan | Decisiones que la afectan |
|---|---|---|---|---|
| [[HU-006-solicitar-recuperacion-de-contrasena]] | Solicitar recuperación de contraseña | Medio | F7 | D27 |
| [[HU-007-restablecer-contrasena-con-token]] | Restablecer contraseña con token | Medio | F7 | D27, D29 |
| [[HU-008-consultar-y-actualizar-perfil]] | Consultar y actualizar el perfil | Bajo | F6 | D25 |
| [[HU-012-gestionar-eps-y-planes]] | Gestionar EPS y planes | Medio | F6 | D28 |
| [[HU-020-consultar-agenda-de-citas-aprobadas]] | Consultar la agenda de citas aprobadas | Medio | F4 | — |
| [[HU-021-registrar-cierre-de-atencion]] | Registrar el cierre de atención | Medio | F4 | D19 |
| [[HU-026-cancelar-una-cita-futura]] | Cancelar una cita futura | Medio | F3 (CA-09 en F5) | D16, D17, D18 |
| [[HU-027-solicitar-reprogramacion-de-cita-aprobada]] | Solicitar la reprogramación de una cita aprobada | Alto | F5 | D18, D20, D21 |
| [[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]] | Decidir sobre la cita tras el rechazo | Medio | F5 | D17, D20 |
| [[HU-031-aprobar-o-rechazar-reprogramacion]] | Aprobar o rechazar una reprogramación | Alto | F5 | D18, D21, D22 → **D39**, D23 |

**Ampliada sin cambiar de estado:** [[HU-009-registrar-afiliacion-a-eps-y-plan]] sigue `Aprobada`; su primer corte fue aprobación directa del usuario y el segundo corte —consultar, cambiar y quitar la afiliación desde el perfil, fase F6, D26— se añade por aprobación delegada.

**Abiertas de S3 que se retoman, sin cambio de estado:**

| HU | Estado | Qué la cierra en S4 |
|---|---|---|
| [[HU-005-autorizar-peticiones-por-rol-y-ownership]] | `En validación` | F2 (R3, componente único de ownership), F4 (CA-06 con HU-020), F3 y F5 (escritura de CA-05); verificación en F10 |
| [[HU-011-gestionar-especialidades-y-su-duracion]] | `En validación` | F2 (R1, `V8` con `UNIQUE(name)`) |
| [[HU-016-activar-o-desactivar-profesional]] | `En validación` | F2 (R2, activación en el dominio y prueba de conservación de citas) |
| [[HU-022-buscar-disponibilidad-con-filtros]] | `En validación` | F5 (CA-03, retenciones de reprogramación) y F8 (LOOP_03, regla de 60 min en un solo sitio); verificación en F10 |
| [[HU-029-consultar-bandeja-administrativa]] | `En validación` | F5 (mitad de reprogramaciones, filtros según D24); verificación en F10 |
| [[HU-033-publicar-contrato-rest-documentado]] | `En desarrollo` | F9 (corte S4 del contrato); sigue abierta por diseño |

**Criterios ajustados o añadidos al aprobar** (cada uno con su línea de historial en la HU):

- Ajustados a una decisión: HU-006 CA-05 (D27), HU-007 CA-08 (D29), HU-021 CA-04 (D19), HU-031 CA-06 (D22; **reescrito el 2026-09-30 para ejecutar D39**, que refina D22) y CA-07 (D18).
- Añadidos porque la decisión no tenía ningún criterio verificable: HU-009 CA-09 y CA-10 (D26), HU-012 CA-09 (D28), HU-026 CA-09 (D18), HU-027 CA-10 (D20), HU-031 CA-09 (D23).

**Divergencias anotadas y sin resolver** (no se reescribieron en silencio; hay que decidirlas antes de implementar la fase indicada):

- HU-027 CA-03 y CA-09 piden la "fecha y hora anterior" en la solicitud, y `reschedule_requests` (V3) no tiene esas columnas (F5). **Resuelta por D31:** la migración `V10` las añade y ningún criterio se reescribió.
- HU-009: `uq_affiliations_user_plan` (V2) impide volver a un plan que ya se tuvo, algo que D26 no contempla (F6).
- HU-012: la DoD pide nombre único de EPS y plan, y V2 impone unicidad por código (F6).
- HU-007: `PLAN_RETOMA_S4.md` F7 revoca los refresh tokens al restablecer, pero ninguna decisión D15–D30 lo registra y la HU lo tiene fuera de alcance (F7).
- D29 figura como decisión que afecta a HU-008, que no cambia contraseñas; no se añadió ningún criterio.

**Decisiones que afectan a HU ya `Completada`:** D29 obliga a revisar la matriz de [[HU-001-registrar-cuenta-de-usuario]], y D22 a revisar [[HU-032-auditar-cambios-de-estado-de-cita]]. No se reabren aquí: se revisan en la verificación de F10.

Estado global tras esta aprobación: ninguna de las 33 HU queda en `Borrador` —16 `Completada`, 11 `Aprobada`, 5 `En validación` y 1 `En desarrollo`—. Ninguna HU de S4 está `Completada`: el cierre exige matriz de evidencia completa y verificación independiente (F10).

## Verificación del LOOP_02 de S4 — reprogramación (2026-09-30)

La iteración 2 del LOOP_02 terminó con el comportamiento del backend en **PASS limpio** (480 pruebas, 0 fallos, `BUILD SUCCESS`, suite reejecutada por el `backend-verifier`, que no escribió el código) y con **FAIL por Definition of Done documental**: [[HU-031-aprobar-o-rechazar-reprogramacion]] seguía redactada en D22 mientras el código implementaba D39 a propósito, y tres matrices de evidencia estaban vacías. La iteración 3 corrige la documentación, sin tocar código.

| HU | Estado | Qué cumple | Qué queda |
|---|---|---|---|
| [[HU-027-solicitar-reprogramacion-de-cita-aprobada]] | `En validación` | CA-01 a CA-10 y toda la DoD de backend, con matriz completa | La DoD de la pantalla de `citas-web`, en `Pendiente`: sin verificación criterio a criterio ni prueba manual en navegador (F10) |
| [[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]] | `En validación` | CA-01 a CA-06 y la DoD de backend y de contrato | La DoD del flujo de `citas-web`, en `Pendiente`: el `frontend-verifier` dejó abiertos dos defectos del propio aviso (el de una solicitud `CANCELLED` por D38 no muestra el motivo; se pierde el foco al cerrarlo) |
| [[HU-031-aprobar-o-rechazar-reprogramacion]] | `En validación` | CA-01 a CA-09, con **CA-06 reescrito para ejecutar D39**, y la DoD de backend, de contrato REST y de trazabilidad | Solo la DoD de la pantalla de ADMIN, en `Pendiente` |
| [[HU-032-auditar-cambios-de-estado-de-cita]] | `Completada` (sin cambio) | Los ocho criterios siguen en `Cumple` | Su ítem de DoD «contrato del historial reflejado» estuvo obsoleto (el contrato no declaraba `event?: 'RESCHEDULED'`) y volvió a `Cumple` cuando el agente del contrato lo alineó el mismo día. Reabrir una HU cerrada es decisión del usuario |

**Ninguna pasa a `Completada`.** El `backend-verifier` fue explícito en que no procede cerrarlas en esta iteración. Con el contrato REST y CA-06 ya alineados con D38 y D39, lo que falta es de interfaz y de verificación: el `frontend-verifier` solo cubrió el alcance de su iteración 2, dejó defectos de producto abiertos y no existe la prueba manual en navegador con los tres roles que pide F10. Dos límites más quedan anotados en las matrices: la carrera «cerrar la atención» contra «decidir la reprogramación» que abre D38 no tiene prueba concurrente, y la derivación del evento `RESCHEDULED` se apoya en un invariante que el esquema no protege.

**Deriva respecto del PRD, corregida con aprobación directa del usuario (2026-09-30).** Cinco textos decían que **cada decisión** de ADMIN sobre una reprogramación se registra en el historial: «Contexto y descripción», la viñeta de «Alcance» y la tarea T-03 de [[HU-031-aprobar-o-rechazar-reprogramacion]], y en [[EP-008-operacion-administrativa-de-solicitudes]] la regla de RF-19 y la casilla del criterio de completitud. **RF-19 no dice eso:** dice «Todo cambio de estado de cita guarda: cita; estado nuevo; actor cuando existe; fuente `SYSTEM`, `USER` o `ADMIN`; fecha/hora; motivo opcional» (`PRD.md` §RF-19). Rechazar una reprogramación no cambia el estado de la cita —sigue `APPROVED`—, de modo que RF-19 nunca pidió una fila para el rechazo: la exigencia la había introducido la épica al reformular el requisito, y fue la que justificó D22. Los cinco textos se alinearon citando RF-19 y registrando D39 como la decisión que lo respeta. **No se relajó ningún requisito: se volvió al PRD.** [[HU-030-aprobar-o-rechazar-cita-especializada]] no cambia, porque allí la decisión sí cambia el estado de la cita.

Estado global tras esta iteración: 16 `Completada`, 8 `Aprobada`, 8 `En validación` y 1 `En desarrollo`.

## Verificación independiente de F10 (2026-10-04)

Backend verificado leyendo código y pruebas (suite **513/513**) y frontend con vitest **248/248**, typecheck, lint y build limpios. Regla aplicada: una HU pasa a `Completada` solo con la matriz completa y **toda** la DoD en `Cumple`; con un ítem `No verificable` o en FAIL se queda `En validación` con la causa escrita en su ficha.

**Decisiones directas del usuario (2026-10-04):** HU-009 CA-03 se confirma como 200 idempotente (reescritura por D26), y con ella las reescrituras de HU-012 CA-03, HU-008 (DoD de ownership) y HU-006 (DoD de migración); HU-012 CA-02 se reformula (EPS inexistente = `404 NOT_FOUND`, porque la EPS viaja en la ruta) y pasa a PASS; HU-032 CA-07 se cumple solo por API y la pantalla de administrador para el historial queda **fuera de alcance**.

**Pasan a `Completada` (7):** [[HU-008-consultar-y-actualizar-perfil]], [[HU-016-activar-o-desactivar-profesional]], [[HU-020-consultar-agenda-de-citas-aprobadas]], [[HU-021-registrar-cierre-de-atencion]], [[HU-026-cancelar-una-cita-futura]], [[HU-029-consultar-bandeja-administrativa]] y [[HU-031-aprobar-o-rechazar-reprogramacion]].

**Siguen `En validación`, con su causa:**

| HU | Causa |
|---|---|
| [[HU-005-autorizar-peticiones-por-rol-y-ownership]] | Backend completo (CA-05, CA-06 y DoD de ownership y RF-16 en PASS). CA-08 solo falta en navegador real: escribir a mano la URL de una ruta de otro rol |
| [[HU-007-restablecer-contrasena-con-token]] | Todo PASS salvo la prueba e2e en navegador de la pantalla (el backend la cubre por API en `PasswordRecoveryExposedTokenIntegrationTest`) |
| [[HU-009-registrar-afiliacion-a-eps-y-plan]] | CA-05 y el ítem de DoD de la lista de planes: no se puede confirmar sin navegador que un plan inactivo no aparece. Paso manual: desactivar un plan y abrir `/paciente/perfil` y `/registro` |
| [[HU-012-gestionar-eps-y-planes]] | CA-04, mitad de interfaz, por la misma causa que HU-009 |
| [[HU-022-buscar-disponibilidad-con-filtros]] | PASS con matiz: la búsqueda tiene su propia codificación SQL de la regla de 60 minutos, atada al dominio por `SearchBookingEquivalenceIntegrationTest:90` (64 escenarios), no una única implementación. **Decisión pendiente del usuario** |
| [[HU-028-decidir-sobre-cita-tras-rechazo-de-reprogramacion]] | CA-02 sin operación de «conservar» (solo se prueba que consultar no escribe) y diferencia visual de franjas solo comprobable en navegador |
| [[HU-011-gestionar-especialidades-y-su-duracion]] | Segunda pasada: backend PASS (CA-02, CA-09 y la migración `V8` de nombre único). CA-06 y CA-07 en frontend no se pueden confirmar sin navegador (`BookingPage.tsx:63` usa `getActiveSpecialties` sin prueba del filtrado). Paso manual: desactivar una especialidad y abrir `/paciente/agendar` |

**Segunda pasada (2026-10-04, suites vigentes: backend 521/521, frontend 256/256):** [[HU-006-solicitar-recuperacion-de-contrasena]] y [[HU-027-solicitar-reprogramacion-de-cita-aprobada]] pasan a `Completada`. HU-006 registra la confirmación directa del usuario de la reescritura de su DoD de migración; HU-027 anota el matiz de que `FlywayV10BackfillTest` valida el backfill de V10 (D31), no la V3 que cita la DoD.

**[[HU-032-auditar-cambios-de-estado-de-cita]]** sigue `Completada` (PASS con D39): se corrigieron la fila de la «única puerta» (`rescheduleTo` es una segunda puerta deliberada) y la nota de «dos transiciones». El riesgo R-1 (filas históricas rotuladas «Reprogramada») sigue no verificable sin consultar la base de datos.

**HU-001 a HU-004** siguen `Completada`: el código estaba bien y el texto mentía. Se corrigieron HU-003 y HU-004 (el refresh token ya no va en el cuerpo ni en `@RequestBody`: va en la cookie `HttpOnly` `fcv_refresh`, D36), se añadió D36 a HU-002, HU-003 y HU-004, y en HU-001 se corrigió la afirmación de que no se aplica política de contraseña (falso desde D29) y su matriz (`@PasswordPolicyCompliant` en lugar de `@BcryptPasswordLength`; suite vigente en lugar de «104 pruebas»). En HU-006 y HU-007 se corrigieron las líneas de `PasswordRecoveryIntegrationTest` (+8 de desfase).

**Observaciones sin cambio de estado:** la carrera «cerrar atención» contra «decidir reprogramación» no tiene prueba concurrente ([[HU-021-registrar-cierre-de-atencion]], [[HU-031-aprobar-o-rechazar-reprogramacion]]; `dec-006` la declara abierta), y el `.env.example` de la raíz trae contraseñas de MySQL de laboratorio concretas ([[HU-001-registrar-cuenta-de-usuario]] ya lo anota como riesgo no bloqueante).

**Estado global tras la segunda pasada:** 25 `Completada`, 7 `En validación` (HU-005, 007, 009, 011, 012, 022 y 028), 1 `En desarrollo` ([[HU-033-publicar-contrato-rest-documentado]], abierta por diseño) y 3 `Borrador` (HU-034 a HU-036); total 36.

## EP-010 — Automatizaciones n8n (especificada el 2026-09-30; alcance S5 aprobado el 2026-10-04)

Se especificaron [[EP-010-automatizaciones-n8n]] y sus tres historias. El 2026-10-04 el usuario aprobó con su mensaje «aprobdo lo del s5» (alcance interpretado por el agente principal): EP-010 y [[HU-034-consultar-citas-para-automatizacion]] pasan a `Aprobada`, junto con D-C, D-D y D-I. HU-035 y HU-036 siguen en `Borrador` hasta que el usuario las confirme explícitamente antes de la Fase 5.

**Estado global tras la verificación de HU-034 (2026-10-04):** 25 `Completada`, 8 `En validación` (las 7 anteriores más HU-034), 1 `En desarrollo` (HU-033) y 2 `Borrador` (HU-035 y HU-036); total 36. Fuente: `PLAN_S5_S6_N8N.md` (raíz), PRD §8–§10 y RF-19, `GUIA_SESIONES_S2_S6.md` (S5, S6) y `RESTRICCIONES_TECNICAS.md`.

| HU | Título | Esfuerzo | Estado |
|---|---|---|---|
| [[HU-034-consultar-citas-para-automatizacion]] | Consultar citas para automatización (clave dedicada, `/api/automation/**`) | Alto | `En validación` (CA-01 a CA-10 en PASS, suite 565/565; CA-11 y T-05 fuera de entrega de S5; espera la F9) |
| [[HU-035-publicar-eventos-de-cambio-de-estado-a-n8n]] | Publicar eventos de cambio de estado a n8n | Alto | `Borrador` |
| [[HU-036-versionar-y-documentar-los-flujos-n8n]] | Versionar y documentar los flujos n8n | Medio | `Borrador` |

**Decididas por el usuario (2026-09-30):** D-A (clave de API dedicada de solo lectura, cabecera `X-Automation-Key`, cadena de seguridad propia), D-B (túnel temporal), D-H (workflows nuevos con prefijo `jhonNuñez-`, paths de webhook sin ñ).

**Aprobadas el 2026-10-04 con «aprobdo lo del s5» (alcance S5):** D-C (ventana de 24 h revisada cada hora), D-D (anti-duplicado en Data Table de n8n), D-I (webhook a demanda además del Schedule en WF-001).

**Propuesta vigente, pendiente de confirmar explícitamente antes de la Fase 5 (S6):** D-E (eventos: aprobación/rechazo de especializada, aprobación/rechazo de reprogramación, cancelación), D-F (entrega *best-effort* sin *outbox*), D-G (WF-003 bonus).

**Precondición (resuelta para S5 por la autorización del 2026-10-04):** la épica dependía de que S4 esté cerrada o de que el usuario autorice expresamente abrir S5. Varias HU de las que depende siguen en `En validación` o `En desarrollo` (HU-026, HU-027, HU-028, HU-031, HU-033).

Las incógnitas INC-041 a INC-046 están en la épica.
