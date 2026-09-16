---
titulo: "Riesgo — Trampas de Spring Security 6.5 que rompen la build"
tipo: riesgo
estado: Provisional
actualizado: 2026-09-16
fuentes: ["[[RES-001-spring-security-jwt]]"]
tags: [riesgo, seguridad, backend, jwt]
---

# Riesgo — Trampas de Spring Security 6.5.x

Tres fallos concretos que la documentación y los tutoriales recientes inducen a cometer en este
proyecto. Los tres se detectaron **antes** de escribir código, en [[RES-001-spring-security-jwt]].

## 1. `NimbusJwtEncoder.withSecretKey(...)` no existe en 6.5.x

Es `@since 7.0`. Spring Boot 3.5.x trae Spring Security **6.5.x**, así que ese método **no
compila**. La forma correcta aquí es:

```java
new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key))
```

El *decoder* sí tiene `withSecretKey` en 6.5.x — de ahí la confusión. **Todo tutorial escrito
en 2026 asume 7.x y no compilará contra este proyecto.**

**Impacto:** build rota. **Detección:** inmediata, al compilar.

## 2. El secreto HMAC debe tener al menos 256 bits

Lo exige RFC 7518 §3.2 como `MUST`. Un secreto corto **no falla al arrancar**: falla en runtime,
en el primer login.

**Mitigación:** validar la longitud en el bean que construye la `SecretKey`, de modo que la
aplicación **se niegue a arrancar** con un secreto débil. Un fallo al arrancar es infinitamente
preferible a un fallo en el primer usuario real.

El secreto llega por variable de entorno (`JWT_ACCESS_SECRET`), nunca versionado. Los valores de
`.env.example` son placeholders `CHANGE_ME_...` y hay que sustituirlos.

## 3. Los roles no reciben el prefijo `ROLE_` automáticamente

Hay que configurar explícitamente:

```java
converter.setAuthoritiesClaimName("roles");
converter.setAuthorityPrefix("ROLE_");
```

Sin esto, `hasRole("ADMIN")` devuelve **403 en silencio**. No hay excepción ni log que lo explique:
simplemente todo el que debería tener permiso deja de tenerlo.

**Impacto:** alto y difícil de diagnosticar. **Detección:** solo con una prueba de autorización
por rol, que por eso es obligatoria en la DoD de la HU de login.

## Otras advertencias de la misma fuente

- **CORS** necesita un bean `CorsConfigurationSource`. El preflight no lleva credenciales y debe
  pasar antes que la cadena de seguridad.
- `csrf.disable()` es válido **solo** mientras el token viaje en la cabecera `Authorization`. Si
  algún día se mueve a una cookie, esta línea se convierte en una vulnerabilidad.
- Usar el lambda DSL sin `.and()`, que desaparece en 7.0.
- `PasswordEncoderFactories.createDelegatingPasswordEncoder()` da BCrypt sin dependencias extra.
  **Argon2 exigiría BouncyCastle**, cuya versión no gestiona el BOM de Boot. El PRD admite
  cualquiera de los dos, así que se elige BCrypt.

## Relacionado

- [[dec-001-libreria-jwt]]
- [[dec-002-rotacion-refresh-tokens]]
- [[arq-hexagonal-seguridad]]

## Historial

- 2026-09-16 — riesgo registrado a partir de la investigación RES-001, antes de implementar.
