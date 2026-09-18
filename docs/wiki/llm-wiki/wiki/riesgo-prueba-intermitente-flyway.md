---
titulo: "Riesgo — Prueba intermitente de migraciones desde vacío"
tipo: riesgo
estado: Provisional
actualizado: 2026-09-18
fuentes: ["EVIDENCIAS_S3.md §3", "citas-api/src/test/java/com/fcv/citas/infrastructure/persistence/FlywayMigratesEmptySchemaTest.java:83"]
tags: [riesgo, pruebas, flyway, hooks]
---

# Riesgo — Prueba intermitente de migraciones desde vacío

## Qué pasó

El 2026-09-18, en un commit que solo cambiaba `application.yml` y `.env.example`, el hook
pre-commit de `citas-api` falló con:

```
FlywayException: Unable to obtain inputstream for resource: db/migration/V1__identity_and_fixed_catalogs.sql
  at FlywayMigratesEmptySchemaTest.migrateFromEmptySchema:83
```

La misma prueba pasó sola dos veces seguidas y la suite completa pasó al reintentar el commit.

## Hipótesis (sin verificar)

`target/` vive en el volumen montado desde Windows (`./citas-api:/workspace`). Al cambiar un
recurso, `maven-resources` reescribe `target/classes` y la lectura inmediata desde el classpath
puede encontrar el archivo a medio escribir o con la caché del montaje desfasada. Encaja con que
fallara justo cuando cambió un recurso y no en las ejecuciones sin cambios.

## Impacto

Una prueba intermitente erosiona la red de S3: si falla sin motivo, la gente se acostumbra a
reintentar o a saltarse el hook con `--no-verify`.

## Mitigación

- Si se repite: mover `target/` a un volumen de Docker (`-Dproject.build.directory` o un volumen
  nombrado sobre `/workspace/target`) y comprobar si desaparece.
- Nunca `--no-verify`: reintentar una vez y, si falla de nuevo, tratarlo como rojo real.

## Relacionado

- [[datos-modelo-3fn]]
- [[dec-004-decisiones-s3-reserva]]

## Historial

- 2026-09-18 — creada tras el primer fallo intermitente en el hook de S3.
