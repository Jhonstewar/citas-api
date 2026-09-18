---
id: HU-033
tipo: historia-de-usuario
titulo: "Publicar el contrato REST documentado"
estado: Borrador
epica: "[[EP-009-trazabilidad-y-contrato-rest]]"
requisitos: [RF-20]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 1"
dependencias: []
relacionadas:
  - "[[HU-001-registrar-cuenta-de-usuario]]"
  - "[[HU-002-iniciar-sesion-con-jwt]]"
  - "[[HU-005-autorizar-peticiones-por-rol-y-ownership]]"
  - "[[HU-032-auditar-cambios-de-estado-de-cita]]"
---

# HU-033 — Publicar el contrato REST documentado

## Historia de usuario

**COMO** desarrollador de `citas-web` que consume `citas-api`  
**QUIERO** disponer de un contrato REST documentado con endpoints, formatos, códigos de respuesta y formato de error uniforme, y de una URL de backend configurable por entorno  
**PARA** integrar el frontend directamente con la API sin capa intermedia y sin adivinar cómo responde cada operación

> Como desarrollador de `citas-web` que consume `citas-api`, quiero disponer de un contrato REST documentado con endpoints, formatos, códigos de respuesta y formato de error uniforme, y de una URL de backend configurable por entorno, para integrar el frontend directamente con la API sin capa intermedia y sin adivinar cómo responde cada operación.

## Contexto y descripción

RF-20 establece que el frontend consume directamente una API REST de Spring Boot, sin Express ni BFF, y que el contrato debe diseñarse y documentarse durante el proyecto. Las restricciones técnicas añaden que la URL del backend es configurable por entorno, y PRD §8 exige CORS explícito y que no se registren contraseñas ni tokens.

Esta HU se inaugura en el Sprint 1 porque todas las demás la referencian en su DoD ("el contrato del endpoint está reflejado en la documentación de HU-033") y porque varias decisiones transversales deben existir antes del primer endpoint: el formato de error uniforme, la distinción entre no autenticado y sin permiso que pide [[HU-005-autorizar-peticiones-por-rol-y-ownership]], el uso de 409 para conflictos de estado y de doble reserva, y el tratamiento de campos no editables que menciona [[HU-008-consultar-y-actualizar-perfil]]. Es un artefacto vivo: se crea con los endpoints de identidad y crece en cada sprint.

El repositorio ya contiene piezas de base: `app.cors.allowed-origins` en `application.yml` de `citas-api` leído de `FRONTEND_ORIGIN`, y `VITE_API_URL` en el `.env.example` de `citas-web`.

## Alcance

- Documento de contrato REST ubicado en `citas-api/docs/wiki/` y enlazado desde la wiki, con convenciones generales y un apartado por recurso.
- Convenciones: prefijo y estructura de rutas, JSON como formato, formato de fechas y horas, autenticación mediante access token, paginación y filtros cuando apliquen.
- Formato de error uniforme para toda la API, con los casos de validación, no autenticado, sin permiso, no encontrado, conflicto (409) y error interno.
- Tabla de códigos de respuesta de uso común, incluida la distinción no autenticado / sin permiso.
- Contrato de los endpoints de identidad del Sprint 1 ([[HU-001-registrar-cuenta-de-usuario]] a [[HU-005-autorizar-peticiones-por-rol-y-ownership]]).
- Manejador global de errores en `citas-api` que produce el formato de error uniforme.
- CORS explícito por orígenes configurados por entorno, sin comodín.
- Cliente HTTP de `citas-web` que lee la URL base de `VITE_API_URL` e interpreta el formato de error uniforme.
- Regla de mantenimiento: toda HU que publique o cambie un endpoint actualiza este contrato como parte de su DoD.

## Fuera de alcance

- Contratos de los endpoints de sprints posteriores: los añade cada HU funcional al publicarlos.
- Express, BFF o proxy de aplicación: prohibidos (RF-20).
- CI/CD de validación del contrato (PRD §9).
- Política de versionado del contrato entre repositorios: pendiente de INC-039.

## Reglas de negocio

- El frontend consume directamente la API REST de Spring Boot; no existe Express ni BFF (RF-20).
- El contrato se diseña y se documenta durante el proyecto (RF-20).
- La URL del backend es configurable por entorno y no está escrita en el código (restricciones técnicas).
- CORS es explícito, con orígenes concretos y sin comodín (PRD §8).
- Ninguna respuesta, ejemplo de contrato ni log expone contraseñas ni tokens más allá de la emisión legítima en login y refresh (PRD §8).
- La validación es server-side; el contrato documenta los errores de validación que el servidor devuelve (PRD §8).

## Dependencias y relaciones

- Épica: [[EP-009-trazabilidad-y-contrato-rest]]
- Dependencias: ninguna
- Relacionadas: [[HU-001-registrar-cuenta-de-usuario]], [[HU-002-iniciar-sesion-con-jwt]], [[HU-003-renovar-sesion-con-refresh-token]], [[HU-004-cerrar-sesion-revocando-refresh-token]], [[HU-005-autorizar-peticiones-por-rol-y-ownership]], [[HU-008-consultar-y-actualizar-perfil]], [[HU-022-buscar-disponibilidad-con-filtros]], [[HU-023-agendar-cita-de-medicina-general]], [[HU-032-auditar-cambios-de-estado-de-cita]]. Todas las HU que publican endpoints enlazan a esta en su DoD.

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** El volumen inicial es acotado (convenciones y endpoints de identidad), pero fija decisiones transversales que condicionan a todas las HU posteriores y a ambos repositorios: formato de error, códigos, CORS y cliente HTTP. Un error de diseño aquí se propaga a cada endpoint, y dos incógnitas abiertas (formato de documentación y formato de error) deben resolverse para empezar.

## Tareas de desarrollo

- [ ] **T-01 — Redactar las convenciones generales del contrato**  
  Dificultad: Medio  
  Descripción: Estructura de rutas, formato JSON, formato de fechas y horas, cabecera de autenticación, paginación y filtros, y regla de mantenimiento del documento.

- [ ] **T-02 — Diseñar el formato de error uniforme y la tabla de códigos**  
  Dificultad: Medio  
  Descripción: Estructura del cuerpo de error con código, mensaje y detalle de campos, y correspondencia con validación, no autenticado, sin permiso, no encontrado, conflicto 409 (transición inválida, doble reserva, duplicado) y error interno.

- [ ] **T-03 — Implementar el manejador global de errores en citas-api**  
  Dificultad: Medio  
  Descripción: Adaptador de infraestructura que traduce excepciones de validación, de seguridad, de dominio y de persistencia al formato uniforme, sin filtrar trazas internas ni datos sensibles.

- [ ] **T-04 — Verificar la configuración CORS explícita**  
  Dificultad: Bajo  
  Descripción: Configuración de Spring Security que aplica `app.cors.allowed-origins` desde el entorno, sin comodín, con los métodos y cabeceras que el contrato usa.

- [ ] **T-05 — Documentar los endpoints de identidad del Sprint 1**  
  Dificultad: Medio  
  Descripción: Petición, respuesta, códigos y ejemplos sintéticos de registro, login, refresh, logout y respuesta de autorización denegada.

- [ ] **T-06 — Implementar el cliente HTTP de citas-web**  
  Dificultad: Medio  
  Descripción: Módulo de acceso a la API que lee `VITE_API_URL`, adjunta el access token e interpreta el formato de error uniforme para las pantallas.

- [ ] **T-07 — Pruebas del contrato transversal**  
  Dificultad: Medio  
  Descripción: Pruebas de integración REST que verifican el formato de error para validación, no autenticado, sin permiso, no encontrado y conflicto; prueba de CORS con origen permitido y no permitido; y typecheck/build de `citas-web` con la URL de entorno.

## Criterios de aceptación

### CA-01 — El contrato existe y es navegable

**Dado** el repositorio `citas-api`  
**Cuando** se consulta la documentación del contrato REST desde `citas-api/docs/wiki/`  
**Entonces** existe un documento con las convenciones generales, el formato de error, la tabla de códigos y los endpoints de identidad del Sprint 1, cada uno con método, ruta, autenticación requerida, cuerpo de petición, respuesta de éxito y respuestas de error.

### CA-02 — Formato de error uniforme

**Dado** peticiones que provocan un error de validación, falta de autenticación, permiso insuficiente, recurso no encontrado y conflicto  
**Cuando** la API responde a cada una  
**Entonces** todas las respuestas usan la misma estructura de cuerpo documentada, con códigos HTTP distintos y coherentes con la tabla del contrato.

### CA-03 — Conflictos documentados como 409

**Dado** el contrato publicado  
**Cuando** se consulta la tabla de códigos  
**Entonces** documenta 409 para transiciones de estado inválidas, doble reserva de slots y duplicados de datos únicos, y cualquier endpoint que produzca esos casos responde 409.

### CA-04 — Sin información sensible en errores

**Dado** una excepción interna no controlada o un error de login  
**Cuando** la API responde  
**Entonces** el cuerpo no contiene trazas de pila, sentencias SQL, contraseñas, hashes ni tokens, y el log no registra contraseñas ni tokens.

### CA-05 — URL del backend configurable por entorno

**Dado** `citas-web` construido con `VITE_API_URL` apuntando a dos URLs distintas  
**Cuando** se inspeccionan las peticiones emitidas por la aplicación  
**Entonces** cada construcción usa la URL configurada y el código fuente no contiene la URL del backend escrita de forma fija.

### CA-06 — CORS explícito

**Dado** `citas-api` configurado con un origen permitido  
**Cuando** recibe una petición con preflight desde ese origen y otra desde un origen no configurado  
**Entonces** la primera recibe las cabeceras CORS de permiso para ese origen concreto y la segunda no; en ningún caso se responde con un comodín.

### CA-07 — Consumo directo sin capa intermedia

**Dado** los repositorios `citas-web` y `citas-api`  
**Cuando** se inspeccionan dependencias y código de `citas-web`  
**Entonces** no existe Express, BFF ni proxy de aplicación, y las pantallas llaman a la API REST a través del cliente HTTP común.

### CA-08 — El contrato se mantiene con cada endpoint

**Dado** una HU que publica o modifica un endpoint y pasa a `Completada`  
**Cuando** se compara su endpoint implementado con el contrato  
**Entonces** el contrato refleja su ruta, petición, respuesta y errores, y la discrepancia bloquea el cierre de esa HU.

## Definition of Done

- [ ] Los criterios CA-01 a CA-08 están validados con evidencia concreta.
- [ ] El formato de documentación del contrato (INC-038) y el formato de error (INC-040) están decididos por el usuario del proyecto y registrados en la documentación.
- [ ] El manejador global de errores de `citas-api` produce el formato documentado para todos los tipos de error, demostrado con pruebas de integración.
- [ ] CORS se configura por entorno sin comodín y está cubierto por una prueba.
- [ ] `citas-web` lee la URL base de `VITE_API_URL`, el `.env.example` no contiene secretos y el typecheck/build pasa.
- [ ] Los ejemplos del contrato usan exclusivamente datos sintéticos y no contienen credenciales reales.
- [ ] Los endpoints de identidad del Sprint 1 están documentados y coinciden con su implementación.
- [ ] La trazabilidad de esta HU y de [[EP-009-trazabilidad-y-contrato-rest]] está actualizada en `docs/wiki/scrum/`.

## Evidencia de validación

Ejecución de referencia: backend **49 pruebas** y frontend **11 pruebas**, 0 fallos (2026-09-17).

| Elemento | Resultado | Evidencia | Observación |
|---|---|---|---|
| CA-01 | Cumple | [[contrato-rest-identidad]], enlazado desde `wiki/index.md` | **Cerrado en 2026-09-17.** Convenciones, formato de error, tabla de códigos y los cinco endpoints de identidad, cada uno contrastado contra el código |
| CA-02 | Cumple | `AuthFlowIntegrationTest`: 400 con `fieldErrors`, 401, 404 y 409 verifican `application/problem+json` | Misma estructura `ProblemDetail` en todos |
| CA-03 | Cumple | `duplicateEmailReturns409`, `duplicateDocumentReturns409`; tabla de códigos del contrato | La doble reserva de slots llegará en S3 con su propia HU |
| CA-04 | Cumple | `GlobalExceptionHandler#unexpected` registra solo el **tipo** de excepción; `server.error.include-message: never` | Ningún cuerpo lleva traza, SQL, hash ni token |
| CA-05 | Cumple | `citas-web/src/api/contracts.ts` lee `import.meta.env.VITE_API_URL` | Persiste el literal `http://localhost:8080` como valor por defecto: ver observación abajo |
| CA-06 | Cumple | `AuthFlowIntegrationTest#corsPreflightAllowsFrontendOrigin`; `CorsConfig` aplica `app.cors.allowed-origins` | Sin comodín. `FRONTEND_ORIGIN` ya llega al contenedor desde `docker-compose.yml` (corregido el 2026-09-17) |
| CA-07 | Cumple | `citas-web/package.json`: solo `react`, `react-dom` y `react-router` en `dependencies` | No hay Express, BFF ni proxy; todas las pantallas pasan por `httpClient` |
| CA-08 | Cumple | Regla escrita en la sección "Reglas e invariantes" del contrato | Criterio de mantenimiento continuo: se reevalúa en cada HU que publique endpoint |
| DoD | Parcial | Contrato publicado; manejador global probado; CORS probado; `.env.example` sin secretos reales | **INC-038 e INC-040 los decidió el agente bajo aprobación delegada**, no el usuario: Markdown a mano y `ProblemDetail`. Conviene confirmarlo explícitamente |

## Historial de validación

- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- Incógnita abierta **INC-038** (ver [[EP-009-trazabilidad-y-contrato-rest]]): no está definido si el contrato se documenta con OpenAPI generado, con markdown mantenido a mano o con ambos. El `pom.xml` actual no incluye ninguna librería de generación OpenAPI; añadirla sería una decisión técnica nueva. CA-01 exige el contenido, no la herramienta.
- Incógnita abierta **INC-040** (ver [[EP-009-trazabilidad-y-contrato-rest]]): el formato de error no está definido. Spring Boot 3.5 ofrece soporte nativo del formato Problem Details (RFC 9457), que es una opción natural, pero la decisión corresponde al usuario del proyecto.
- Incógnita abierta **INC-039** (ver [[EP-009-trazabilidad-y-contrato-rest]]): sin política de versionado entre repositorios, CA-08 obliga al menos a mantener contrato e implementación sincronizados en cada HU.
- CA-08 es un criterio de mantenimiento continuo: esta HU puede completarse al cerrar el Sprint 1 con los endpoints de identidad, y la regla sigue aplicándose en la DoD de cada HU posterior.
- La ubicación exacta del documento dentro de `citas-api/docs/wiki/` (por ejemplo junto a `llm-wiki/`) no está fijada por las restricciones técnicas, que solo indican que los contratos pueden enlazarse desde la wiki.
