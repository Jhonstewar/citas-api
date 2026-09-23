---
titulo: "Riesgo — Desfase de 5 h en columnas TIME por la zona horaria del JVM"
tipo: riesgo
estado: Vigente
actualizado: 2026-09-23
fuentes: ["citas-api/src/main/java/com/fcv/citas/CitasApiApplication.java", "citas-api/pom.xml", "EVIDENCIAS_S3.md"]
tags: [riesgo, zona-horaria, hibernate, jdbc, mysql, resuelto]
---

# Riesgo — Desfase de 5 h en columnas `TIME` por la zona horaria del JVM

**Estado: mitigado en S3 (F6).** Se deja como `Vigente` porque cualquier cambio en cómo arranca
la aplicación puede reintroducirlo.

## Síntoma

En la suite completa, una cita pedida a las 08:30 se leía a las **03:30**. Un bloque de
08:00 a 12:00 no "solapaba" con otro de 11:30 a 13:00. El desfase era de −5 h, la diferencia
entre Bogotá y UTC. **Dependía del orden de las pruebas**: la misma suite pasó en F5 y falló en
F6 al añadir una clase de prueba que se ejecutó primero.

## Causa

`CitasApiApplication` fijaba `TimeZone.setDefault(America/Bogota)` en un `@PostConstruct`, es
decir, **después** de que Hikari y Flyway abrieran conexiones con la zona anterior (UTC en el
contenedor `maven:3.9-eclipse-temurin-21`). Hibernate convierte `LocalTime` a `java.sql.Time`
con la zona por defecto del JVM en el momento de la llamada, y Connector/J la captura al crear
cada conexión. Unas conexiones y conversiones usaban UTC y otras Bogotá. Cada contexto de
Spring de las pruebas (hay varios por `@TestBean` y por las pruebas de secreto JWT) volvía a
cambiar la zona a mitad de camino.

## Mitigación aplicada

1. La zona se fija en `main`, **antes** de `SpringApplication.run`, no en `@PostConstruct`.
2. El JVM de pruebas arranca con `-Duser.timezone=America/Bogota` (`maven-surefire-plugin`).
3. Las lecturas JDBC de `DATE`, `TIME` y `DATETIME` usan `rs.getObject(col, LocalTime.class)`, sin
   pasar por `java.sql.Time`/`Timestamp`, que dependen de la zona del JVM.
4. Vigilancia: `ScheduleIntegrationTest` lee con SQL crudo (`TIME_FORMAT`) la hora guardada y
   `BookingIntegrationTest` compara las horas devueltas. Si vuelve el desfase, fallan.

Tras la corrección, la suite completa pasó dos veces seguidas con el orden que fallaba. **Eran
210 pruebas el 2026-09-18**; la cifra se deja como evidencia de aquel momento, no como estado
actual — hoy la suite del backend son **242** (`EVIDENCIAS_S3.md` §11).

## Regla para el futuro

- No fijar la zona horaria en un bean. Si se despliega fuera de Docker, arrancar el JVM con
  `-Duser.timezone=America/Bogota`, o dejar que `main` lo haga antes de todo.
- En JDBC, leer siempre tipos `java.time` con `getObject(..., Clase.class)`.

## Relacionado

- [[riesgo-prueba-intermitente-flyway]]
- [[datos-modelo-3fn]] — las columnas `TIME` afectadas (`availability_blocks`, `availability_slots`)
- [[dec-004-decisiones-s3-reserva]]
- [[contrato-rest-citas]] — el contrato que publica esas horas como `"08:30"` en `America/Bogota`

## Historial

- 2026-09-23 (LINT) — la cifra de 210 pruebas queda fechada como evidencia del 2026-09-18, no
  como estado actual; añadidos los enlaces entrantes que faltaban (la página era huérfana).
- 2026-09-18 — detectado y mitigado en F6 de S3.
