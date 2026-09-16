---
titulo: "Decisión 001 — Librería JWT: resource-server de Spring, no jjwt"
tipo: decision
estado: Provisional
actualizado: 2026-09-16
fuentes: ["[[RES-001-spring-security-jwt]]", "RESTRICCIONES_TECNICAS.md §Backend"]
tags: [decision, seguridad, jwt, backend]
---

# Decisión 001 — Librería JWT

## Decisión

`citas-api` emite y valida JWT con **`spring-boot-starter-oauth2-resource-server`**.
Se descarta `io.jsonwebtoken:jjwt`.

## Alternativas consideradas

| Opción | Por qué se descartó |
|---|---|
| `io.jsonwebtoken:jjwt` 0.13.0 | Gana solo en ergonomía de API. Obliga a escribir un filtro de autenticación propio y a integrar a mano con el contexto de seguridad. No compensa. |

## Motivo

El starter trae `spring-security-oauth2-resource-server` y `-jose` (gestionados por el BOM de
Boot 3.5.x) con Nimbus transitivo, y aporta ya resuelto el filtro de bearer token, el
`JwtAuthenticationToken` y la integración con `@PreAuthorize`. El **mismo artefacto** incluye
también el `JwtEncoder` para emitir, así que no hacen falta dos librerías.

## Consecuencia importante

Este proyecto usa **secreto simétrico HMAC**, no claves asimétricas. Con HMAC **ninguna**
propiedad `spring.security.oauth2.resourceserver.jwt.*` aplica: los beans `JwtDecoder` y
`JwtEncoder` son **obligatorios y propios**. Configurarlos por `application.yml` no funciona.

Ver [[riesgo-spring-security-65-trampas]] antes de escribir esos beans: la API de construcción
del encoder cambió entre 6.5 y 7.0 y la mayoría de ejemplos publicados no compilan aquí.

## Relacionado

- [[dec-002-rotacion-refresh-tokens]] — el refresh token **no** es un JWT
- [[arq-hexagonal-seguridad]] — dónde viven los puertos y los adaptadores
- [[riesgo-spring-security-65-trampas]]

## Estado de verificación

`Provisional`: procede de [[RES-001-spring-security-jwt]] (18 fuentes citadas) pero **aún no está
implementada**. Pasa a `Vigente` cuando el vertical slice de auth compile y sus pruebas pasen.

## Historial

- 2026-09-16 — decisión registrada a partir de la investigación RES-001.
