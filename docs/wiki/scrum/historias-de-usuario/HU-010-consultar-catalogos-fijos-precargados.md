---
id: HU-010
tipo: historia-de-usuario
titulo: "Consultar los catálogos fijos precargados"
estado: Borrador
epica: "[[EP-003-catalogos-del-sistema]]"
requisitos: [RF-05]
esfuerzo: "Medio"
sprint_sugerido: "Sprint 3"
dependencias:
  - "[[HU-002-iniciar-sesion-con-jwt]]"
relacionadas:
  - "[[HU-011-gestionar-especialidades-y-su-duracion]]"
  - "[[HU-022-buscar-disponibilidad-con-filtros]]"
---

# HU-010 — Consultar los catálogos fijos precargados

## Historia de usuario

**COMO** usuario autenticado del sistema
**QUIERO** consultar los catálogos fijos precargados
**PARA** que los formularios de búsqueda, agenda y afiliación ofrezcan siempre valores válidos y consistentes.

> Como usuario autenticado del sistema, quiero consultar los catálogos fijos precargados para que los formularios de búsqueda, agenda y afiliación ofrezcan siempre valores válidos y consistentes.

## Contexto y descripción

RF-05 define cinco catálogos fijos precargados y de solo lectura: roles, estados de cita, estados de reprogramación, regímenes y sedes. A diferencia de los catálogos configurables de RF-06, estos no se crean ni se modifican desde la aplicación: se cargan por seed y la API solo los expone para consulta.

Las dos sedes del laboratorio son fijas y quedan precargadas con su nombre y su dirección (PRD §3): Hospital Internacional de Colombia (HIC), Km 7 Autopista Bucaramanga–Piedecuesta, Valle de Menzulí, Santander; y Fundación Cardiovascular de Colombia / Instituto Cardiovascular (ICV), Calle 155A No. 23-58, Urbanización El Bosque, Floridablanca, Santander.

Esta HU es la base de datos maestros de todo el producto: la búsqueda de disponibilidad filtra por sede, la agenda del profesional selecciona sede por bloque, la afiliación del paciente escoge régimen, y el ciclo de vida de la cita y de la reprogramación se apoya en los catálogos de estado. Que el conjunto de estados sea cerrado y venga del seed es lo que hace verificable la exigencia de transiciones explícitas de RN-11.

## Alcance

- Migración Flyway que crea las tablas de los cinco catálogos fijos de RF-05 y su seed de valores.
- Seed de los roles `USER`, `PROFESSIONAL` y `ADMIN` (PRD §2), coordinado con el esquema de usuarios y roles de [[HU-001-registrar-cuenta-de-usuario]].
- Seed de los estados de cita usados por el producto: `REQUESTED`, `APPROVED`, `REJECTED`, `CANCELLED`, `COMPLETED` y `NO_SHOW`.
- Seed de los estados de reprogramación, incluido `PENDING` (RF-15).
- Seed de los regímenes utilizados por la afiliación del paciente (RF-04).
- Seed de las dos sedes fijas HIC e ICV con nombre, sigla y dirección (PRD §3).
- Endpoints de consulta autenticados para cada catálogo fijo.
- Consumo de estos catálogos desde `citas-web` para poblar selectores de sede, estado y régimen.

## Fuera de alcance

- Cualquier operación de escritura sobre estos catálogos: son de solo lectura (RF-05).
- Creación de sedes nuevas: el laboratorio trabaja con dos sedes fijas (PRD §3).
- Catálogos configurables de EPS, planes y especialidades, que se cubren en [[HU-011-gestionar-especialidades-y-su-duracion]] y [[HU-012-gestionar-eps-y-planes]].
- La lógica de transición entre estados de cita, que pertenece a las HU del ciclo de vida de la cita.
- Internacionalización o traducción de las etiquetas de los catálogos: no está en el PRD.

## Reglas de negocio

- Los catálogos fijos se precargan por seed y son de solo lectura desde la aplicación (RF-05).
- Los catálogos fijos son roles, estados de cita, estados de reprogramación, regímenes y sedes (RF-05).
- Las sedes del laboratorio son exactamente dos: HIC e ICV (PRD §3).
- Los catálogos fijos se cargan por seed versionado, no por inserciones manuales (restricciones técnicas, base de datos).
- La consulta de catálogos requiere sesión autenticada; no expone datos sensibles de personas.
- Los valores de estado de cita cubren el ciclo completo que el producto usa: `REQUESTED` (RF-12), `APPROVED` (RF-11, RF-12), `REJECTED` (RF-12), `CANCELLED` (RF-14), `COMPLETED` y `NO_SHOW` (RF-17).
- El catálogo de estados de reprogramación incluye `PENDING`, el estado en que nace toda solicitud de reprogramación (RF-15).

## Dependencias y relaciones

- Épica: [[EP-003-catalogos-del-sistema]]
- Dependencias: [[HU-002-iniciar-sesion-con-jwt]]
- Relacionadas: [[HU-011-gestionar-especialidades-y-su-duracion]], [[HU-012-gestionar-eps-y-planes]], [[HU-015-asignar-sedes-al-profesional]], [[HU-022-buscar-disponibilidad-con-filtros]], [[HU-033-publicar-contrato-rest-documentado]]

## Esfuerzo

**Nivel:** Medio

**Justificación de dificultad:** Las reglas son simples y no hay lógica de negocio compleja, pero la HU fija el vocabulario de estados de todo el producto y crea cinco tablas de referencia con su seed versionado. Un error aquí se propaga a la cita, la reprogramación, la agenda y la afiliación, por lo que la dificultad está en acertar el modelo y en dejar el seed reproducible sobre base vacía, no en el código.

## Tareas de desarrollo

- [ ] **T-01 — Modelar los catálogos fijos en el dominio**
  Dificultad: Bajo
  Descripción: Representar en la capa de dominio los conjuntos cerrados de rol, estado de cita, estado de reprogramación y régimen, y la entidad sede con su nombre, sigla y dirección, sin dependencias de framework y sin operaciones de modificación.

- [ ] **T-02 — Crear la migración Flyway de los catálogos fijos**
  Dificultad: Medio
  Descripción: Migración versionada que crea las tablas de roles, estados de cita, estados de reprogramación, regímenes y sedes, con clave natural única por código, e inserta el seed completo, incluidas las dos sedes fijas con su dirección literal del PRD §3.

- [ ] **T-03 — Implementar los casos de uso de consulta de catálogos**
  Dificultad: Bajo
  Descripción: Casos de uso de aplicación que devuelven cada catálogo completo a través de puertos de lectura, sin exponer ninguna operación de creación, edición o borrado.

- [ ] **T-04 — Exponer los adaptadores REST de consulta**
  Dificultad: Bajo
  Descripción: Controladores de solo lectura para cada catálogo fijo, con respuesta que incluya código y etiqueta legible, y en el caso de sedes también sigla y dirección.

- [ ] **T-05 — Restringir la escritura en la configuración de seguridad**
  Dificultad: Bajo
  Descripción: Declarar las rutas de catálogos fijos como accesibles solo a usuarios autenticados y asegurar que no exista ningún manejador de método de escritura sobre ellas.

- [ ] **T-06 — Consumir los catálogos fijos desde citas-web**
  Dificultad: Medio
  Descripción: Capa de acceso a datos en React + TypeScript que obtiene los catálogos mediante la URL configurable por entorno, los tipa y los reutiliza en los selectores de sede, estado y régimen de las pantallas del producto.

- [ ] **T-07 — Pruebas de seed y de consulta de catálogos**
  Dificultad: Medio
  Descripción: Pruebas de integración que arrancan sobre una base vacía, verifican el contenido del seed de los cinco catálogos y comprueban que los endpoints de consulta responden autenticados y que las peticiones de escritura no están soportadas.

## Criterios de aceptación

### CA-01 — Seed aplicado por migración sobre base vacía

**Dado** una base de datos MySQL 8.4 completamente vacía
**Cuando** se arranca `citas-api`
**Entonces** Flyway crea las tablas de roles, estados de cita, estados de reprogramación, regímenes y sedes, inserta sus valores, y el arranque finaliza sin error y sin requerir ninguna carga manual.

### CA-02 — Las dos sedes fijas quedan precargadas con su dirección

**Dado** el seed aplicado
**Cuando** se consulta el catálogo de sedes
**Entonces** devuelve exactamente dos sedes: Hospital Internacional de Colombia (HIC) con dirección «Km 7 Autopista Bucaramanga–Piedecuesta, Valle de Menzulí, Santander» y Fundación Cardiovascular de Colombia / Instituto Cardiovascular (ICV) con dirección «Calle 155A No. 23-58, Urbanización El Bosque, Floridablanca, Santander».

### CA-03 — El catálogo de estados de cita cubre el ciclo completo

**Dado** el seed aplicado
**Cuando** se consulta el catálogo de estados de cita
**Entonces** contiene los valores `REQUESTED`, `APPROVED`, `REJECTED`, `CANCELLED`, `COMPLETED` y `NO_SHOW`.

### CA-04 — El catálogo de estados de reprogramación incluye PENDING

**Dado** el seed aplicado
**Cuando** se consulta el catálogo de estados de reprogramación
**Entonces** contiene el valor `PENDING` como estado inicial de toda solicitud de reprogramación, junto con los estados de resolución que el producto usa.

### CA-05 — Roles y regímenes precargados y consultables

**Dado** el seed aplicado
**Cuando** se consultan los catálogos de roles y de regímenes
**Entonces** el de roles contiene `USER`, `PROFESSIONAL` y `ADMIN`, y el de regímenes devuelve al menos un valor utilizable por el formulario de afiliación de [[HU-009-registrar-afiliacion-a-eps-y-plan]].

### CA-06 — No existen endpoints de escritura sobre catálogos fijos

**Dado** la API en ejecución con una sesión `ADMIN` válida
**Cuando** se intenta crear, editar o borrar un elemento de cualquiera de los cinco catálogos fijos mediante las rutas de catálogo
**Entonces** la API responde con un error de método o de ruta no soportada en todos los casos, y el contenido de los catálogos permanece idéntico al del seed.

### CA-07 — La consulta exige autenticación

**Dado** la API en ejecución
**Cuando** se consulta cualquier catálogo fijo sin cabecera de autorización
**Entonces** la API responde con un código de no autorizado, y con un access token válido de cualquiera de los tres roles responde con el catálogo completo.

### CA-08 — Los selectores del frontend se pueblan desde la API

**Dado** un usuario autenticado en `citas-web`
**Cuando** abre una pantalla que ofrece selección de sede, de estado de cita o de régimen
**Entonces** las opciones mostradas provienen de la respuesta de la API y no de valores escritos en el código del frontend.

## Definition of Done

- [ ] Los criterios CA-01 a CA-08 están validados con evidencia concreta.
- [ ] Existe una migración Flyway versionada que crea las cinco tablas de catálogo fijo y su seed, y se aplica correctamente sobre una base vacía sin pasos manuales.
- [ ] Las dos sedes precargadas reproducen el nombre, la sigla y la dirección literales del PRD §3.
- [ ] El código no contiene ninguna operación de escritura, ni en aplicación ni en adaptador REST, sobre roles, estados de cita, estados de reprogramación, regímenes o sedes.
- [ ] El dominio que representa los catálogos no depende de Spring ni de JPA, respetando la separación hexagonal.
- [ ] `citas-web` obtiene los catálogos mediante la URL del backend leída de la configuración de entorno y no duplica sus valores en constantes locales.
- [ ] Existen pruebas automatizadas que verifican el contenido del seed y el acceso autenticado de consulta, y pasan.
- [ ] Los contratos de consulta de catálogos están reflejados en la documentación de [[HU-033-publicar-contrato-rest-documentado]].
- [ ] La trazabilidad de esta HU y de [[EP-003-catalogos-del-sistema]] está actualizada en `docs/wiki/scrum/`.

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
| CA-08 | Pendiente | — | — |
| DoD | Pendiente | — | — |

## Historial de validación

- Sesión S2 — HU creada en estado `Borrador`.

## Notas y decisiones

- El PRD no enumera explícitamente los valores del catálogo de estados de cita en una lista única; CA-03 los deriva de los estados que RF-11, RF-12, RF-14 y RF-17 exigen usar. Si el usuario del proyecto necesita estados adicionales, debe decidirse antes de fijar el seed.
- El PRD no enumera los valores concretos del catálogo de regímenes ni los estados de resolución de una reprogramación distintos de `PENDING`; CA-04 y CA-05 solo exigen los valores que el PRD sí obliga a usar, y el resto queda a confirmación del usuario del proyecto.
- El catálogo de roles se comparte con el esquema creado en [[HU-001-registrar-cuenta-de-usuario]]; debe evitarse duplicar la tabla o el seed entre ambas migraciones.
- El PRD no define un catálogo de tipos de documento dentro de RF-05, pese a que RF-01 exige tipo de documento; esta HU no lo incluye y la duda queda registrada en [[HU-001-registrar-cuenta-de-usuario]].
