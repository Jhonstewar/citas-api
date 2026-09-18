---
titulo: "Arquitectura — Encaje de la seguridad en la hexagonal"
tipo: arquitectura
estado: Vigente
actualizado: 2026-09-17
fuentes: ["[[RES-001-spring-security-jwt]]", "RESTRICCIONES_TECNICAS.md §Backend"]
tags: [arquitectura, hexagonal, seguridad, backend]
---

# Arquitectura — Seguridad dentro de la hexagonal

## La regla que lo gobierna todo

`domain/` y `application/` **no importan nada de Spring, JPA ni Jackson**. Cuando el dominio
necesita una capacidad externa, declara un **puerto** (una interfaz) y el adaptador la implementa
en `infrastructure/`. `java.time` y el JDK son libres.

Esto no es purismo: es lo que permite probar las reglas de negocio sin levantar un contexto de
Spring, y lo que impide que una decisión de framework se filtre a las reglas del producto.

**Se vigila con ArchUnit** (`HexagonalArchitectureTest`), que analiza el bytecode, no los imports:
también detecta una referencia con nombre completo. Tiene cuatro reglas:

- `domain` y `application` no dependen de frameworks;
- tampoco dependen de `infrastructure`;
- `domain` no depende de `application`;
- ninguna clase escribe en la salida estándar, porque un `System.out` puede filtrar tokens.

La verificación del 2026-09-17 las probó por mutación: una referencia a Spring dentro de `User` y
otra de `application` a `infrastructure.security` hacen fallar la prueba.

## Puertos de seguridad (en el dominio)

| Puerto | Qué abstrae |
|---|---|
| `PasswordHasher` | Hashear y verificar una contraseña |
| `AccessTokenIssuer` | Emitir el access token JWT |
| `SecureTokenGenerator` | Generar el valor opaco del refresh token |
| `RefreshTokenRepository` | Persistir, buscar por hash y revocar familias |

Un caso de uso de login orquesta puertos. **No importa `BCryptPasswordEncoder`.**

## Adaptadores (en `infrastructure/security`)

`SecurityConfig`, el `SecurityFilterChain`, los converters de authorities, los beans `JwtDecoder`
y `JwtEncoder` y el `CorsConfigurationSource` son **adaptadores puros**: no implementan ningún
puerto del dominio, son configuración del framework. No hay que inventarles una interfaz en el
dominio solo por simetría.

Esa distinción importa: envolver `SecurityConfig` tras un puerto no aporta testabilidad, solo
indirección. Lo mismo vale para `PublicEndpointsBearerTokenResolver` y
`ProblemJsonSecurityHandlers`: son configuración de la cadena de seguridad (ver
[[riesgo-spring-security-65-trampas]], trampa 4, y [[contrato-rest-identidad]]).

## Dónde se comprueba la autorización

- **Rol** — en la cadena de seguridad y con `@PreAuthorize` en el adaptador REST.
- **Ownership** — en el **caso de uso**, no en el controlador. Que un `USER` solo vea sus propias
  citas es una regla del producto, no un detalle de transporte; debe seguir cumpliéndose aunque
  mañana la API se exponga por otro canal.

## Relacionado

- [[dec-001-libreria-jwt]]
- [[dec-002-rotacion-refresh-tokens]]
- [[riesgo-spring-security-65-trampas]]

## Historial

- 2026-09-17 — la regla de capas pasa a vigilarse con ArchUnit en vez de con expresiones
  regulares sobre los imports.
- 2026-09-16 — página creada a partir de RES-001, antes de implementar el slice de auth.
