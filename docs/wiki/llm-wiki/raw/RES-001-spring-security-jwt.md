---
id: RES-001
tipo: investigacion
titulo: "JWT access+refresh en Spring Boot 3.5.x"
fecha: 2026-09-16
autor: subagente-investigacion
estado: Verificada
fuentes:
  - https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
  - https://docs.spring.io/spring-security/site/docs/6.5.x/api/org/springframework/security/oauth2/jwt/NimbusJwtEncoder.html
  - https://docs.spring.io/spring-security/site/docs/6.5.x/api/org/springframework/security/oauth2/jwt/NimbusJwtDecoder.html
  - https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/oauth2/jwt/NimbusJwtEncoder.html
  - https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html
  - https://docs.spring.io/spring-boot/3.5/reference/web/spring-security.html
  - https://docs.spring.io/spring-security/reference/6.5/whats-new.html
  - https://docs.spring.io/spring-security/reference/6.5/migration-7/configuration.html
  - https://docs.spring.io/spring-security/reference/features/exploits/csrf.html
  - https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html
  - https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html
  - https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html
  - https://datatracker.ietf.org/doc/rfc9700/
  - https://www.rfc-editor.org/rfc/rfc7518#section-3.2
  - https://auth0.com/docs/secure/tokens/refresh-tokens/refresh-token-rotation
  - https://github.com/jwtk/jjwt
---

# RES-001 — JWT access+refresh en Spring Boot 3.5.x

> **Convención de este documento**
> - `[EVIDENCIA]` = afirmación respaldada por una fuente citada (documentación oficial o RFC).
> - `[INFERENCIA]` = recomendación propia del subagente, derivada de la evidencia + las restricciones del proyecto.
> - `[INCIERTO]` = no se pudo confirmar en fuente primaria; requiere verificación al implementar.

## Pregunta

Cómo implementar autenticación JWT con **access token de corta duración + refresh token separado, revocable**, en un backend Spring Boot 3.5.x / Spring Security 6.5.x con arquitectura hexagonal, MySQL 8.4, Flyway y secreto simétrico. Cinco sub-preguntas: (1) librería JWT, (2) arquitectura de filtros y mapeo de roles, (3) patrón de refresh token, (4) trampas de versión, (5) encaje hexagonal.

---

## Recomendación (resumen ejecutivo)

**[INFERENCIA]**

1. **Librería: `spring-boot-starter-oauth2-resource-server`** (Nimbus, soporte nativo de Spring Security). No añadir `jjwt`.
   - Razón decisiva: la *validación* del access token en cada request no se escribe a mano — el `BearerTokenAuthenticationFilter` del resource server ya la hace, produce un `JwtAuthenticationToken`, integra con `@PreAuthorize`, y devuelve `401`/`403` con cabeceras `WWW-Authenticate` conformes a RFC 6750. Con `jjwt` hay que escribir un `OncePerRequestFilter` propio y reimplementar ese manejo de errores.
   - **La emisión** (firmar el access token) se hace con `JwtEncoder` / `NimbusJwtEncoder`, que viene en el mismo `spring-security-oauth2-jose`. No hace falta una segunda librería.

2. **Patrón de refresh: rotating refresh tokens con familia (token family) y detección de reuso**, persistidos en MySQL **solo como hash SHA-256** (no BCrypt — ver §3), con revocación en cascada de toda la familia al detectar reuso. El refresh token **no es un JWT**: es un valor opaco aleatorio de 256 bits. Esto simplifica revocación y evita que el resource server lo acepte por error como access token.

3. **Trampas top-3** (detalle en §"Trampas y advertencias"):
   - `NimbusJwtEncoder.withSecretKey(...)` **NO existe en Spring Security 6.5.x** (es `@since 7.0`). En 6.5.x hay que usar el constructor `new NimbusJwtEncoder(new ImmutableSecret<>(secretKey))`.
   - El secreto HMAC debe tener **≥ 256 bits** (RFC 7518 lo exige como `MUST`); un `application.yml` con un secreto corto revienta en runtime al firmar.
   - Los roles del claim **no llevan prefijo `ROLE_` automáticamente**: `JwtGrantedAuthoritiesConverter` usa por defecto el claim `scope`/`scp` y el prefijo `SCOPE_`. Hay que configurarlo explícitamente o `hasRole('ADMIN')` nunca coincidirá.

---

## Hallazgos

### 1. Librería JWT

#### Evidencia

**[EVIDENCIA]** La documentación oficial de Spring Security es explícita sobre qué artefactos hacen falta:

> "Most Resource Server support is collected into `spring-security-oauth2-resource-server`. However, the support for decoding and verifying JWTs is in `spring-security-oauth2-jose`, meaning that both are necessary in order to have a working resource server that supports JWT-encoded Bearer Tokens."
> — <https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html>

**[EVIDENCIA]** Spring Boot 3.5.x agrupa ambos en el starter `spring-boot-starter-oauth2-resource-server`, y la auto-configuración se retira si defines tu propio bean:

> "The same properties are applicable for both servlet and reactive applications. Alternatively, you can define your own `JwtDecoder` bean for servlet applications..."
> — <https://docs.spring.io/spring-boot/3.5/reference/web/spring-security.html>

Las propiedades de auto-config (`spring.security.oauth2.resourceserver.jwt.jwk-set-uri`, `.issuer-uri`, `.public-key-location`, `.audiences`) están pensadas para **proveedores externos / clave asimétrica**. Con secreto simétrico HMAC **ninguna aplica**, por lo que el `JwtDecoder` es obligatoriamente un bean propio. Ver misma fuente.

**[EVIDENCIA]** Versiones gestionadas por Spring Boot 3.5.16 (última 3.5.x documentada al 2026-09):
- `spring-security-oauth2-resource-server` → **6.5.11**
- `spring-security-oauth2-jose` → **6.5.11**
- `spring-security-core` → **6.5.11**
- `flyway-core` / `flyway-mysql` → **11.7.2**
- `com.mysql:mysql-connector-j` → **9.7.0**
— <https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html>

**[EVIDENCIA]** `com.nimbusds:nimbus-jose-jwt` **no aparece** en la tabla de coordenadas gestionadas ni en la de version properties de Spring Boot 3.5.16. Llega como dependencia **transitiva** de `spring-security-oauth2-jose`.
— <https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/properties.html>
**[INFERENCIA]** No declarar `nimbus-jose-jwt` explícitamente en el `pom.xml`: dejar que la resuelva `spring-security-oauth2-jose`, para no romper la compatibilidad binaria que Spring Security testea.

**[EVIDENCIA]** `io.jsonwebtoken:jjwt` está en **0.13.0** (release 2025-08-20). La rama 0.13.x es la última que soporta Java 7; JJWT se declara maduro, bien mantenido y sin dependencias pesadas.
— <https://github.com/jwtk/jjwt> · <https://github.com/jwtk/jjwt/blob/main/CHANGELOG.md>
**[INCIERTO]** No se confirmó en fuente primaria si existe ya una 0.14.x en septiembre 2026; el changelog consultado indica 0.13.0 como estable. Verificar en Maven Central al implementar.

#### Comparativa

| Criterio | resource-server + jose (Nimbus) | jjwt 0.13.x |
|---|---|---|
| Validación en cada request | Filtro `BearerTokenAuthenticationFilter` incluido | Filtro propio a escribir |
| Manejo de 401/403 + `WWW-Authenticate` | `BearerTokenAuthenticationEntryPoint` incluido | Manual |
| Integración `@PreAuthorize` / `JwtAuthenticationToken` | Nativa | Manual (`UsernamePasswordAuthenticationToken` a mano) |
| Emisión (firmar) | `JwtEncoder` / `NimbusJwtEncoder` | `Jwts.builder()` (API más ergonómica) |
| Versionado | Gestionado por el BOM de Boot | Manual, hay que fijar versión |
| Migración futura a claves asimétricas / JWKS | Cambio de 2 beans | Reescritura |

#### Recomendación

**[INFERENCIA]** **Usar `spring-boot-starter-oauth2-resource-server`. Descartar jjwt.** Añadir jjwt duplicaría la funcionalidad (Nimbus ya está en el classpath como transitiva) y obligaría a mantener un filtro propio. La única ventaja real de jjwt es una API de construcción más agradable, que no compensa perder el filtro y el manejo de errores estándar. Para un laboratorio de formación, además, enseña el camino que Spring recomienda oficialmente.

---

### 2. Arquitectura de filtros y mapeo de roles

#### 2.1 SecurityFilterChain

**[EVIDENCIA]** Forma canónica según la documentación oficial (lambda DSL):

```java
@Configuration
@EnableWebSecurity
public class MyCustomSecurityConfiguration {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests((authorize) -> authorize
                .requestMatchers("/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer((oauth2) -> oauth2
                .jwt(Customizer.withDefaults())
            );
        return http.build();
    }
}
```
— <https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html>

**[INFERENCIA]** Adaptación para FCV Citas. Puntos clave marcados con comentario:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // habilita @PreAuthorize para el chequeo de ownership
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http,
                            JwtAuthenticationConverter jwtAuthConverter) throws Exception {
        http
            // CORS: requiere el bean CorsConfigurationSource (ver §Trampas)
            .cors(Customizer.withDefaults())
            // API stateless consumida por clientes no-navegador con Bearer token
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",          // el refresh NO lleva Bearer válido
                        "/api/v1/auth/password/forgot",
                        "/api/v1/auth/password/reset").permitAll()
                .requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
            );
        return http.build();
    }
}
```

**[INFERENCIA] Nota crítica sobre `/auth/refresh`:** debe ir en `permitAll()` **precisamente porque el access token ya expiró** cuando el cliente lo llama. La autenticación de ese endpoint la hace el servicio de aplicación validando el refresh token opaco contra la base de datos, no el filtro de Spring Security. Si se dejara `authenticated()`, el flujo de refresh sería imposible.

**[INFERENCIA] Nota sobre `/auth/logout`:** ese sí debe ir `authenticated()` (o aceptar el refresh token en el body). Recomendación: `authenticated()` + refresh token en el body, para que solo el dueño de la sesión pueda revocarla.

#### 2.2 Mapeo de roles a GrantedAuthority

**[EVIDENCIA]** La documentación oficial muestra cómo cambiar prefijo y nombre de claim:

```java
@Bean
public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
    grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
    grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");

    JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
    jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
    return jwtAuthenticationConverter;
}
```

> "As part of configuring a `JwtAuthenticationConverter`, you can supply a subsidiary converter to go from `Jwt` to a `Collection` of granted authorities."
> — <https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html>

**[INFERENCIA]** Con esa configuración, un token que lleve `"roles": ["PROFESSIONAL"]` produce la authority `ROLE_PROFESSIONAL`, y `hasRole('PROFESSIONAL')` funciona (`hasRole` añade el prefijo `ROLE_` por sí solo; `hasAuthority` no). Conviene además fijar el `principalClaimName` al id del usuario si se quiere que `authentication.getName()` devuelva el UUID en vez del `sub` textual:

```java
jwtAuthenticationConverter.setPrincipalClaimName("sub"); // sub = UUID del usuario
```

**[INFERENCIA] Ownership.** El chequeo por propietario no se resuelve con roles. Patrón recomendado: `@EnableMethodSecurity` + `@PreAuthorize("hasRole('ADMIN') or @citaAccessPolicy.esPropietario(#citaId, authentication)")`, donde `citaAccessPolicy` es un bean **de la capa adaptador** que consulta el puerto de repositorio. El dominio no ve la anotación.

---

### 3. Refresh token: rotación, persistencia y revocación

#### 3.1 Evidencia normativa

**[EVIDENCIA]** RFC 9700 (BCP 240, *Best Current Practice for OAuth 2.0 Security*, publicada enero 2025) establece que los refresh tokens **MUST** ser sender-constrained o usar rotación, y describe la rotación como estrategia defensiva: se emite un nuevo refresh token cada vez que se usa el actual, de modo que si uno es robado, el titular legítimo y el atacante acabarán colisionando, permitiendo al servidor **detectar la anomalía y revocar todas las sesiones asociadas**.
— <https://datatracker.ietf.org/doc/rfc9700/> · <https://www.ietf.org/rfc/rfc9700.pdf>

**[EVIDENCIA]** Limitación reconocida: la rotación **detecta** el reuso y permite revocar la familia activa, limitando la persistencia del atacante, pero **no impide el primer uso** de un bearer token robado.
— <https://datatracker.ietf.org/doc/rfc9700/>

**[EVIDENCIA]** Mecánica de rotación con detección automática de reuso (referencia de implementación):
- "every time an application exchanges a refresh token to get a new access token, a new refresh token is also returned"
- "As soon as the new pair is issued by Auth0, the refresh token used in the request is invalidated."
- Al detectar reuso, se bloquean "all refresh tokens in the same token family (all refresh tokens descending from the original refresh token issued for the client)".
— <https://auth0.com/docs/secure/tokens/refresh-tokens/refresh-token-rotation>

#### 3.2 Diseño recomendado para FCV Citas

**[INFERENCIA]**

**El refresh token NO es un JWT.** Es un valor opaco: 32 bytes de `SecureRandom`, codificados en Base64URL. Razones:
- Un JWT es autovalidable, lo que compite con la necesidad de revocación (habría que consultar la BD igual → el JWT no aporta nada).
- Si fuera un JWT firmado con la misma clave, el resource server podría aceptarlo como access token. Riesgo evitable por diseño.

**Esquema de tabla (Flyway):**

```sql
CREATE TABLE refresh_token (
    id             BINARY(16)   NOT NULL PRIMARY KEY,
    user_id        BINARY(16)   NOT NULL,
    family_id      BINARY(16)   NOT NULL,   -- lineaje: constante en toda la cadena
    token_hash     CHAR(64)     NOT NULL,   -- SHA-256 en hex. NUNCA el token en claro
    issued_at      DATETIME(6)  NOT NULL,
    expires_at     DATETIME(6)  NOT NULL,
    used_at        DATETIME(6)  NULL,       -- marca de consumo (rotación)
    revoked_at     DATETIME(6)  NULL,
    revoked_reason VARCHAR(40)  NULL,       -- LOGOUT | ROTATED | REUSE_DETECTED | ADMIN
    replaced_by_id BINARY(16)   NULL,       -- traza del lineaje
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    INDEX idx_refresh_token_family (family_id),
    INDEX idx_refresh_token_user (user_id)
) ENGINE=InnoDB;
```

**Por qué SHA-256 y no BCrypt para el refresh token.** **[INFERENCIA]** Esta es la diferencia clave respecto a las passwords:
- Las passwords son de baja entropía y adivinables → necesitan una función lenta y con salt (BCrypt/Argon2), tal y como recomienda Spring Security (§5 de este documento).
- Un refresh token de 256 bits generados con `SecureRandom` tiene entropía máxima: no es adivinable por fuerza bruta, así que no necesita una función lenta. Usar BCrypt aquí sería **contraproducente**: al llevar salt por hash, no se puede buscar en BD por hash (habría que traer todas las filas del usuario y compararlas una a una). SHA-256 es determinista → permite el `UNIQUE (token_hash)` y el lookup por índice en O(1).
- El requisito del proyecto ("solo hash, nunca en claro") se cumple igual: SHA-256 sigue siendo unidireccional.
- **[INFERENCIA]** Lo mismo aplica al token de reseteo de contraseña: valor aleatorio de alta entropía + SHA-256 + TTL corto (15 min) + un solo uso.

**Algoritmo de `/auth/refresh` (pseudo-código, en la capa de aplicación):**

```
1. hash = sha256(tokenRecibido)
2. fila = repo.buscarPorHash(hash)
3. si fila == null            -> 401 (token desconocido o ya purgado)
4. si fila.revoked_at != null -> 401
5. si fila.expires_at < now   -> 401
6. si fila.used_at != null:                       <-- REUSO DETECTADO
       repo.revocarFamilia(fila.family_id, "REUSE_DETECTED")
       (opcional: auditar / notificar al usuario)
       -> 401
7. en transacción:
       fila.used_at = now
       fila.revoked_reason = "ROTATED"
       nuevo = generar(32 bytes SecureRandom)
       repo.guardar(nuevo con MISMO family_id, mismo user_id)
       fila.replaced_by_id = nuevo.id
8. devolver { accessToken: nuevoJwt(15 min), refreshToken: nuevo (en claro, solo aquí) }
```

**[INFERENCIA] Paso 6 es el corazón del patrón.** Sin él, la rotación es cosmética. La revocación debe ser **de toda la familia**, no solo del token reusado, porque en ese momento no se sabe si quien presenta el token viejo es la víctima o el atacante.

**[INFERENCIA] Condición de carrera.** Dos peticiones de refresh concurrentes con el mismo token (típico en SPAs con varias pestañas) dispararán un falso positivo de reuso. Mitigaciones, en orden de preferencia para un laboratorio:
- (a) `SELECT ... FOR UPDATE` sobre la fila + `UPDATE ... WHERE used_at IS NULL` (compare-and-set); si el `UPDATE` afecta 0 filas, es reuso real o carrera.
- (b) Ventana de gracia: aceptar el token reusado si `now - used_at < 10s` y devolver el mismo par ya emitido (idempotencia). Más tolerante, menos estricto.
- **[INCIERTO]** No se localizó una recomendación normativa que zanje (a) vs (b); RFC 9700 no prescribe el manejo de la carrera. Para el laboratorio, (a) es más didáctico y más seguro.

**Logout / revocación.** **[INFERENCIA]**
- `POST /auth/logout` (autenticado) con el refresh token en el body → `revoked_at = now`, `revoked_reason = 'LOGOUT'` para ese token, o para toda la familia si se quiere cerrar el dispositivo entero.
- `POST /auth/logout-all` → revoca todas las familias del `user_id`.
- **El access token NO se puede revocar** (es autovalidable). Por eso su TTL debe ser corto — **[INFERENCIA]** 10–15 minutos. Es el trade-off inherente a JWT sin introspección. Si el requisito fuese revocación inmediata del access token, habría que añadir una denylist por `jti` en Redis/BD consultada en cada request, lo que anula la ventaja de statelessness. **No recomendado para este proyecto.**
- Tarea programada (`@Scheduled`) que purgue filas con `expires_at < now() - 30 días`, para que la tabla no crezca sin límite.

**TTLs sugeridos** **[INFERENCIA]**: access 15 min; refresh 7–14 días con expiración absoluta (no deslizante indefinida: la familia entera debe tener un `absolute_expires_at` o al menos no renovar el `expires_at` más allá de N días desde el login original).

---

### 4. Trampas conocidas

Ver sección dedicada más abajo ("Trampas y advertencias").

---

### 5. Encaje en arquitectura hexagonal

**[INFERENCIA]** Ninguna fuente oficial de Spring prescribe hexagonal; todo lo de esta sección es diseño propio, coherente con la restricción "dominio/aplicación independientes de adaptadores".

**Regla de oro:** el módulo de dominio no debe importar nada de `org.springframework.security.*` ni de `com.nimbusds.*`. Si el `pom.xml` del módulo de dominio no declara esas dependencias, la regla se vuelve imposible de violar por accidente (y se puede verificar con ArchUnit).

#### Puertos (dominio / aplicación — interfaces puras)

```java
// domain/port/out/PasswordHasher.java
public interface PasswordHasher {
    HashedPassword hash(RawPassword raw);
    boolean matches(RawPassword raw, HashedPassword hashed);
}

// domain/port/out/AccessTokenIssuer.java
public interface AccessTokenIssuer {
    AccessToken issue(UserId userId, Set<Role> roles, Duration ttl);
}

// domain/port/out/SecureTokenGenerator.java   (refresh + reset password)
public interface SecureTokenGenerator {
    OpaqueToken generate();          // valor en claro, solo en memoria
    TokenDigest digest(OpaqueToken t); // SHA-256
}

// domain/port/out/RefreshTokenRepository.java
public interface RefreshTokenRepository {
    Optional<RefreshTokenRecord> findByDigest(TokenDigest digest);
    void save(RefreshTokenRecord record);
    void revokeFamily(FamilyId familyId, RevocationReason reason, Instant at);
}

// domain/port/in/AuthenticateUseCase.java  (driving port)
public interface AuthenticateUseCase {
    TokenPair login(LoginCommand cmd);
    TokenPair refresh(RefreshCommand cmd);
    void logout(LogoutCommand cmd);
}
```

`AccessToken`, `HashedPassword`, `TokenDigest`, `Role`, `UserId` son **value objects del dominio** — `record`s de Java 21, sin anotaciones de framework.

#### Adaptadores (infraestructura)

| Puerto | Adaptador | Ubicación |
|---|---|---|
| `PasswordHasher` | `SpringSecurityPasswordHasher` (envuelve `PasswordEncoder`) | `infrastructure/security/` |
| `AccessTokenIssuer` | `NimbusAccessTokenIssuer` (envuelve `JwtEncoder`) | `infrastructure/security/` |
| `SecureTokenGenerator` | `SecureRandomTokenGenerator` (`SecureRandom` + `MessageDigest`) | `infrastructure/security/` |
| `RefreshTokenRepository` | `JpaRefreshTokenRepositoryAdapter` (envuelve `RefreshTokenJpaRepository` + `RefreshTokenEntity`) | `infrastructure/persistence/` |
| `AuthenticateUseCase` | consumido por `AuthController` (`@RestController`) | `infrastructure/web/` |

**[INFERENCIA] Detalle importante:** `SecurityConfig`, `JwtAuthenticationConverter`, `JwtDecoder`, `CorsConfigurationSource` son **puramente adaptadores de entrada**. No necesitan puerto porque el dominio nunca los llama: son el "cómo llega la petición", no el "qué hace el negocio". La `@PreAuthorize` de ownership vive en el controller o en un bean de política en infraestructura, nunca en el servicio de dominio.

**[INFERENCIA]** La `RefreshTokenEntity` (JPA, con `@Entity`, `@Table`) es distinta del `RefreshTokenRecord` (dominio). El mapeo entre ambas ocurre en el adaptador. Duplicar así evita que `@Entity` y el ciclo de vida de JPA se filtren al dominio — es la parte del hexagonal que más cuesta mantener, pero la que más valor didáctico tiene.

---

## Coordenadas Maven recomendadas

**[INFERENCIA]** basadas en las versiones gestionadas confirmadas en <https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html>.

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.5.16</version>   <!-- última 3.5.x documentada a 2026-09; ajustar a la vigente -->
  <relativePath/>
</parent>

<properties>
  <java.version>21</java.version>
</properties>

<dependencies>

  <!-- Seguridad: filtros, PasswordEncoder, @PreAuthorize -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
  </dependency>

  <!-- Resource Server: BearerTokenAuthenticationFilter + JwtDecoder/JwtEncoder (Nimbus).
       Arrastra spring-security-oauth2-resource-server 6.5.11,
       spring-security-oauth2-jose 6.5.11 y nimbus-jose-jwt (transitiva). -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
  </dependency>

  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>

  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
  </dependency>

  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
  </dependency>

  <!-- Flyway 11.7.2 (gestionada). flyway-mysql es OBLIGATORIO para MySQL 8.4 -->
  <dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
  </dependency>
  <dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
  </dependency>

  <!-- MySQL Connector/J 9.7.0 (gestionada) -->
  <dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
  </dependency>

  <!-- SOLO si se elige Argon2 en vez de BCrypt. Con BCrypt NO hace falta.
       Versión NO gestionada por el BOM de Boot 3.5 -> hay que fijarla a mano. -->
  <!--
  <dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk18on</artifactId>
    <version>1.86</version>   [INCIERTO] verificar la vigente en Maven Central
  </dependency>
  -->

  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
  </dependency>

</dependencies>
```

**NO añadir:**
```xml
<!-- ❌ innecesario: Nimbus ya está vía spring-security-oauth2-jose -->
<!-- <dependency><groupId>io.jsonwebtoken</groupId><artifactId>jjwt-api</artifactId></dependency> -->
<!-- ❌ no declarar la versión de nimbus-jose-jwt a mano -->
```

### Beans de JWT para secreto simétrico (Spring Security 6.5.x)

**[INFERENCIA]**, construido sobre las APIs confirmadas en los javadocs 6.5.x.

```java
@Configuration
public class JwtCryptoConfig {

    // Secreto Base64 de >= 32 bytes, inyectado por variable de entorno. NUNCA en el repo.
    @Bean
    SecretKey jwtSigningKey(@Value("${app.security.jwt.secret}") String base64Secret) {
        byte[] key = Base64.getDecoder().decode(base64Secret);
        if (key.length < 32) {                       // RFC 7518 §3.2: >= 256 bits
            throw new IllegalStateException("app.security.jwt.secret debe tener >= 256 bits");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)    // fija el algoritmo esperado
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        // ⚠ En 6.5.x NO existe NimbusJwtEncoder.withSecretKey(...) — es @since 7.0.
        // ImmutableSecret viene de com.nimbusds.jose.jwk.source (transitiva de -jose).
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key));
    }
}
```

Emisión del access token en el adaptador `NimbusAccessTokenIssuer`:

```java
JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer("fcv-citas-api")
        .subject(userId.value().toString())
        .issuedAt(now)
        .expiresAt(now.plus(ttl))
        .id(UUID.randomUUID().toString())          // jti, útil para auditoría
        .claim("roles", roles.stream().map(Role::name).toList())
        .build();
String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
```

**[INFERENCIA]** Pasar el `JwsHeader` explícitamente es importante: sin él, `NimbusJwtEncoder` intenta deducir el algoritmo del `JWKSource`, lo que con `ImmutableSecret` puede no resolverse como se espera. **[INCIERTO]** no se verificó el comportamiento exacto del `JwsHeader` por defecto con `ImmutableSecret` en 6.5.11; pasarlo explícito elimina la duda.

---

## Trampas y advertencias

### T1 — `NimbusJwtEncoder.withSecretKey(...)` NO existe en Spring Security 6.5.x ⚠⚠⚠

**[EVIDENCIA]** El javadoc de la rama **6.5.x** de `NimbusJwtEncoder` lista **un único constructor** `NimbusJwtEncoder(JWKSource<SecurityContext>)` y **ningún método estático**.
— <https://docs.spring.io/spring-security/site/docs/6.5.x/api/org/springframework/security/oauth2/jwt/NimbusJwtEncoder.html>

**[EVIDENCIA]** En cambio, el javadoc de la rama **current (7.x)** sí documenta `public static NimbusJwtEncoder.SecretKeyJwtEncoderBuilder withSecretKey(SecretKey secretKey)`, marcado **`@since 7.0`** (junto a `withKeyPair(RSA...)` y `withKeyPair(EC...)`, también `@since 7.0`).
— <https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/oauth2/jwt/NimbusJwtEncoder.html>

**Impacto.** La mayoría de tutoriales y respuestas de LLM que se encuentren en 2026 estarán escritos contra Spring Security 7.x y usarán `NimbusJwtEncoder.withSecretKey(...)`. **Ese código no compila en Boot 3.5.x.** Usar el constructor con `ImmutableSecret` (ver bloque de código arriba).

Asimetría importante: `NimbusJwt**Decoder**.withSecretKey(SecretKey)` **sí existe** en 6.5.x (junto a `withJwkSetUri`, `withIssuerLocation` `@since 6.1`, `withPublicKey`). La trampa es solo del **encoder**.
— <https://docs.spring.io/spring-security/site/docs/6.5.x/api/org/springframework/security/oauth2/jwt/NimbusJwtDecoder.html>

### T2 — El secreto HMAC debe tener ≥ 256 bits ⚠⚠⚠

**[EVIDENCIA]** RFC 7518 §3.2:

> "A key of the same size as the hash output (for instance, 256 bits for 'HS256') or larger MUST be used with this algorithm."
> — <https://www.rfc-editor.org/rfc/rfc7518#section-3.2>

**[INFERENCIA]** Nimbus hace cumplir este `MUST` y lanza excepción al firmar. El fallo aparece **en runtime, en el primer login**, no al arrancar. Mitigación: validar la longitud en el bean `SecretKey` (como en el código de arriba) para que la app falle rápido al arrancar. **El secreto va en variable de entorno**, nunca en `application.yml` versionado. En el contenedor `maven:3.9-eclipse-temurin-21`, pasarlo con `-e APP_SECURITY_JWT_SECRET=...`.

### T3 — Los roles NO llevan prefijo `ROLE_` por defecto ⚠⚠⚠

**[INFERENCIA]** `JwtGrantedAuthoritiesConverter` sin configurar lee los claims de scope OAuth2 y aplica el prefijo `SCOPE_`. Si el token lleva `"roles": ["ADMIN"]` y no se configura `setAuthoritiesClaimName("roles")` + `setAuthorityPrefix("ROLE_")`, las authorities resultantes serán vacías y **`hasRole('ADMIN')` devolverá `false` siempre** → 403 silencioso y difícil de diagnosticar. La documentación oficial muestra ambos setters como configuración explícita, confirmando que no es el comportamiento por defecto.
— <https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html>

Recordar también: `hasRole('X')` ⇒ busca `ROLE_X`; `hasAuthority('X')` ⇒ busca `X` literal. No mezclar.

### T4 — `csrf.disable()` es legítimo aquí, pero hay que saber por qué

**[EVIDENCIA]** Guía oficial:

> "Our recommendation is to use CSRF protection for any request that could be processed by a browser by normal users. If you are creating a service that is used only by non-browser clients, you likely want to disable CSRF protection."

y la advertencia:

> "What if my application is stateless? That does not necessarily mean you are protected. In fact, if a user does not need to perform any actions in the web browser for a given request, they are likely still vulnerable to CSRF attacks."
> — <https://docs.spring.io/spring-security/reference/features/exploits/csrf.html>

y desde la página de servlet:

> "Before disabling CSRF protection, consider whether it makes sense for your application."
> — <https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html>

**[INFERENCIA]** Para FCV Citas, deshabilitar CSRF es correcto **solo si** el access token viaja en la cabecera `Authorization: Bearer` y **nunca en una cookie**. El razonamiento oficial es preciso: CSRF explota la **autenticación ambiental** que el navegador adjunta sola (cookies, Basic). Una cabecera `Authorization` que el JS debe poner explícitamente no es ambiental → no hay vector CSRF. Si en algún momento se decidiera guardar el refresh token en una cookie `HttpOnly` (opción defendible), **habría que reactivar CSRF en el endpoint `/auth/refresh`**. Documentar esta decisión.

### T5 — CORS: el bean `CorsConfigurationSource` es obligatorio, y el orden importa

**[EVIDENCIA]**

> "CORS must be processed before Spring Security, because the pre-flight request does not contain any cookies (that is, the `JSESSIONID`). If the request does not contain any cookies and Spring Security is first, the request determines that the user is not authenticated (since there are no cookies in the request) and rejects it."
> — <https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html>

**[EVIDENCIA]** Spring Security configura CORS automáticamente si hay un bean `UrlBasedCorsConfigurationSource`; `http.cors(withDefaults())` lo activa. Deshabilitar CORS en Spring Security **no** quita la protección CORS del navegador, solo impide que el backend responda correctamente al preflight. Misma fuente.

**[INFERENCIA]** Trampa práctica adicional: `setAllowedOrigins(List.of("*"))` combinado con `setAllowCredentials(true)` es rechazado por la especificación CORS. Con orígenes explícitos (requisito del proyecto: "CORS explícito") esto no ocurre, pero conviene usar `setAllowedOriginPatterns` si hace falta comodín en desarrollo. Recordar `setAllowedHeaders(List.of("Authorization","Content-Type"))` — sin `Authorization` el preflight falla.

### T6 — Lambda DSL: no usar `.and()`

**[EVIDENCIA]** Guía de migración 6.5 → 7.0: el Lambda DSL es obligatorio en 7.0 y el método `.and()` se elimina; `HttpSecurity` se devuelve automáticamente tras el lambda. También: sustituir `.apply()` por `.with()` en DSLs personalizados, y `shouldFilterAllDispatcherTypes(false)` por `dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()`.
— <https://docs.spring.io/spring-security/reference/6.5/migration-7/configuration.html>

**[INFERENCIA]** Aunque `.and()` todavía compila en 6.5.x, escribirlo condena el proyecto a una refactorización al migrar. Escribir lambda DSL desde el día uno.

### T7 — Spring Security 6.5 es la última 6.x; el proyecto queda en una rama de mantenimiento

**[EVIDENCIA]** Spring Security 6.5.0 se anunció (2025-05-19) como **la última release minor de la rama 6**; se recomienda explícitamente empezar a revisar la guía de migración a 7.x.
— <https://spring.io/blog/2025/05/19/spring-security-6-5-0-is-out/>
**[EVIDENCIA]** Spring Security 7.0.0 salió en noviembre de 2025, y la rama 6.5.x sigue recibiendo parches (6.5.7 en 2025-11).
— <https://spring.io/blog/2025/11/17/spring-security-releases/>

**[INFERENCIA]** Consecuencia para el laboratorio: Boot 3.5.x / Security 6.5.x es una elección estable y con parches, pero **la documentación "current" de docs.spring.io apunta a 7.x**. Al buscar javadocs, usar siempre URLs con `/6.5.x/` en la ruta, o se leerán APIs que no existen (ver T1). Esta es la causa raíz de la trampa más peligrosa de este documento.

### T8 — `SessionCreationPolicy.STATELESS` no es automático

**[INFERENCIA]** El resource server autentica por request, pero Spring Security seguirá creando `HttpSession` si algo la toca (por ejemplo, el `SecurityContextRepository` por defecto). Declarar `.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))` explícitamente. No confirmado en fuente primaria que sea estrictamente necesario con resource server puro → **[INCIERTO]**, pero es inocuo y clarifica la intención.

### T9 — La auto-configuración de resource server no aporta nada con HMAC

**[EVIDENCIA]** Las propiedades `spring.security.oauth2.resourceserver.jwt.*` documentadas son `jwk-set-uri`, `issuer-uri`, `public-key-location` y `audiences`.
— <https://docs.spring.io/spring-boot/3.5/reference/web/spring-security.html>

**[INFERENCIA]** Ninguna acepta un secreto simétrico. No existe `spring.security.oauth2.resourceserver.jwt.secret`. Si no se define el bean `JwtDecoder`, la aplicación arrancará pero **todo request autenticado fallará** (o el contexto fallará al no poder crear el filtro). El bean `JwtDecoder` propio no es opcional.

### T10 — Argon2 exige BouncyCastle, cuya versión NO gestiona el BOM

**[EVIDENCIA]** Spring Security recomienda `DelegatingPasswordEncoder` como default, que codifica los nuevos passwords con **BCrypt**; `Argon2PasswordEncoder` está documentado como la opción moderna ganadora del Password Hashing Competition pero **requiere BouncyCastle**.
— <https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html>
**[EVIDENCIA]** El issue oficial spring-projects/spring-security#8842 se titula "The usage of Argon2PasswordEncoder requires additional dependencies", confirmando la dependencia de BouncyCastle (`org.bouncycastle:bcprov-jdk18on`).
— <https://github.com/spring-projects/spring-security/issues/8842>
**[EVIDENCIA]** `bcprov-jdk18on` **no** figura en las version properties gestionadas de Boot 3.5.16.
— <https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/properties.html>

**[INFERENCIA]** Para un laboratorio de formación, **elegir BCrypt**: cero dependencias extra, cero versión que fijar a mano, y es lo que el `DelegatingPasswordEncoder` oficial usa por defecto. Declarar el bean como:

```java
@Bean
PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder(); // {bcrypt}$2a$10$...
}
```
Esto guarda el prefijo `{bcrypt}` en la columna, lo que permite migrar a Argon2 más adelante sin invalidar los hashes existentes — exactamente el problema que `DelegatingPasswordEncoder` existe para resolver, según la propia documentación. La columna de BD debe ser `VARCHAR(100)` como mínimo (BCrypt con prefijo ≈ 68 chars; Argon2 con prefijo ≈ 110 → usar `VARCHAR(255)` y olvidarse).

**[EVIDENCIA]** La documentación recomienda ajustar el work factor "to take about one second to verify a password on your system" (default de BCrypt: strength 10).
— <https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html>
**[INFERENCIA]** Strength 10–12 es razonable; dentro del contenedor Maven, medirlo antes de subirlo — un strength alto ralentiza los tests de integración notablemente.

### T11 — El access token no es revocable

**[EVIDENCIA]** RFC 9700 señala que la rotación detecta reuso y permite revocar la familia, pero **no impide el primer uso** de un bearer token robado.
— <https://datatracker.ietf.org/doc/rfc9700/>

**[INFERENCIA]** Tras un `logout`, el access token emitido sigue siendo válido hasta que expire. Es el comportamiento esperado de JWT stateless y debe documentarse como decisión consciente (ADR), no descubrirse en la demo. TTL corto (15 min) es la mitigación.

---

## Incertidumbres / información contradictoria

1. **Versión exacta de Spring Boot 3.5.x.** La documentación consultada renderiza **3.5.16** (Security **6.5.11**, Flyway **11.7.2**, MySQL Connector/J **9.7.0**). Esas son las versiones vigentes en el momento de esta investigación (2026-09-16); al implementar, confirmar contra <https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html> por si hay un patch posterior. **No fijar versiones de Spring Security a mano** — dejarlas al BOM del parent.

2. **Contradicción documentación vs. tutoriales: `NimbusJwtEncoder.withSecretKey`.** Esta es la contradicción más peligrosa encontrada. El javadoc "current" (7.x) documenta el método, el javadoc 6.5.x no lo lista, y el javadoc 7.x lo marca `@since 7.0`. Las tres fuentes son coherentes entre sí; lo contradictorio será el material de terceros. **Resolución: en Boot 3.5.x, usar el constructor con `ImmutableSecret`.** Si al implementar el IDE ofrece autocompletado de `withSecretKey`, significa que el classpath tiene Security 7.x, no 6.5.x → revisar el `pom.xml`.

3. **Versión de jjwt.** Se confirmó **0.13.0** (2025-08-20) en el CHANGELOG oficial. No se verificó si existe una 0.14.x a septiembre de 2026. Irrelevante si se sigue la recomendación de no usar jjwt.

4. **Versión de `nimbus-jose-jwt` y de BouncyCastle.** Ninguna de las dos aparece en las tablas de dependencias gestionadas de Boot 3.5.16 consultadas. Nimbus llega transitiva (sin versión a declarar). BouncyCastle, si se usara Argon2, habría que fijarlo a mano; la versión "1.86" citada procede de una búsqueda web, no de fuente primaria → **[INCIERTO]**, verificar en Maven Central.

5. **Longitud mínima del secreto: quién la impone.** RFC 7518 lo exige normativamente (`MUST`). No se localizó la línea exacta del código de Nimbus ni el mensaje de excepción literal; se infiere que Nimbus lo hace cumplir, lo cual es consistente con el comportamiento ampliamente reportado. La validación defensiva propuesta en el bean `SecretKey` hace que esto sea irrelevante en la práctica.

6. **Manejo de la carrera en el refresh concurrente.** RFC 9700 no prescribe cómo resolver dos refresh simultáneos con el mismo token. Se proponen dos estrategias (CAS con `FOR UPDATE`, o ventana de gracia) sin que haya una fuente normativa que zanje. Decisión del equipo; recomendación del subagente: CAS.

7. **SHA-256 vs BCrypt para el hash del refresh token.** El razonamiento (alta entropía ⇒ no hace falta función lenta; determinismo ⇒ permite índice único) es **[INFERENCIA]** propia y es práctica común en la industria, pero no se localizó una fuente normativa de Spring o IETF que lo prescriba explícitamente. El requisito del proyecto ("solo hash, nunca en claro") se satisface con cualquiera de las dos; SHA-256 es la que hace el lookup viable.

8. **`SessionCreationPolicy.STATELESS` con resource server.** No se confirmó en documentación oficial si es estrictamente necesario cuando solo hay autenticación por Bearer token. Se recomienda declararlo igualmente.

9. **CSRF: matiz oficial.** La documentación oficial es más cauta de lo que sugieren los tutoriales ("stateless does not necessarily mean you are protected"). La justificación válida para deshabilitarlo en este proyecto **no es** "la API es stateless", sino "la credencial viaja en una cabecera que el navegador no adjunta automáticamente, y los clientes son no-navegador o SPA con `Authorization` explícito". Si cambia el transporte de la credencial a cookie, la decisión se invalida.

---

## Fuentes

- [OAuth 2.0 Resource Server JWT :: Spring Security (reference)](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) — artefactos requeridos (`-resource-server` + `-jose`), `NimbusJwtDecoder.withSecretKey`, `SecurityFilterChain` con `oauth2ResourceServer`, `JwtAuthenticationConverter` / `JwtGrantedAuthoritiesConverter` con `setAuthorityPrefix` y `setAuthoritiesClaimName`.
- [NimbusJwtEncoder — javadoc rama 6.5.x](https://docs.spring.io/spring-security/site/docs/6.5.x/api/org/springframework/security/oauth2/jwt/NimbusJwtEncoder.html) — confirma que en 6.5.x solo existe el constructor `NimbusJwtEncoder(JWKSource)` y **ningún** método estático.
- [NimbusJwtEncoder — javadoc rama current (7.x)](https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/oauth2/jwt/NimbusJwtEncoder.html) — confirma `withSecretKey(SecretKey)` como `@since 7.0`; también `setJwkSelector` `@since 6.5`.
- [NimbusJwtDecoder — javadoc rama 6.5.x](https://docs.spring.io/spring-security/site/docs/6.5.x/api/org/springframework/security/oauth2/jwt/NimbusJwtDecoder.html) — confirma que `withSecretKey(SecretKey)` **sí** existe en el decoder en 6.5.x, junto a `withJwkSetUri`, `withIssuerLocation` (`@since 6.1`), `withPublicKey`, `setJwtValidator`.
- [Spring Boot 3.5.16 — Managed Dependency Coordinates](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html) — Security 6.5.11, Flyway 11.7.2, mysql-connector-j 9.7.0.
- [Spring Boot 3.5.16 — Version Properties](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/properties.html) — confirma que `nimbus-jose-jwt` y BouncyCastle NO están gestionados.
- [Spring Boot 3.5 — Reference / Spring Security](https://docs.spring.io/spring-boot/3.5/reference/web/spring-security.html) — propiedades `spring.security.oauth2.resourceserver.jwt.*` y back-off de la auto-config ante un bean `JwtDecoder` propio.
- [What's New in Spring Security 6.5](https://docs.spring.io/spring-security/reference/6.5/whats-new.html) — DPoP, PKCE para clientes confidenciales, passkeys JDBC; deprecaciones preparadas para 7.0; cambio de clave de métrica de observabilidad.
- [Spring Security 6.5 → 7.0 Migration: Configuration](https://docs.spring.io/spring-security/reference/6.5/migration-7/configuration.html) — Lambda DSL obligatorio en 7.0, eliminación de `.and()`, `.apply()` → `.with()`, `shouldFilterAllDispatcherTypes` → `dispatcherTypeMatchers`.
- [CSRF — Spring Security Features ("When to use CSRF protection")](https://docs.spring.io/spring-security/reference/features/exploits/csrf.html) — cita literal sobre clientes no-navegador y la advertencia de que "stateless" no implica inmunidad.
- [CSRF — Spring Security Servlet (disabling)](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html) — `csrf(csrf -> csrf.disable())` y la advertencia previa a deshabilitarlo.
- [CORS :: Spring Security](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html) — bean `UrlBasedCorsConfigurationSource`, `http.cors(withDefaults())`, y la advertencia de que CORS debe procesarse antes que Spring Security por el preflight sin cookies.
- [Password Storage :: Spring Security](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html) — `DelegatingPasswordEncoder` como recomendación oficial, BCrypt por defecto, Argon2 requiere BouncyCastle, y el criterio de "un segundo" para el work factor.
- [RFC 9700 — Best Current Practice for OAuth 2.0 Security (BCP 240)](https://datatracker.ietf.org/doc/rfc9700/) — rotación de refresh tokens obligatoria (o sender-constraining), detección de anomalía y revocación de sesiones asociadas, y la limitación de que la rotación no impide el primer uso.
- [RFC 7518 §3.2 — HMAC with SHA-2 Functions](https://www.rfc-editor.org/rfc/rfc7518#section-3.2) — "A key of the same size as the hash output (for instance, 256 bits for 'HS256') or larger MUST be used".
- [Auth0 — Refresh Token Rotation](https://auth0.com/docs/secure/tokens/refresh-tokens/refresh-token-rotation) — mecánica de token family, invalidación inmediata del token usado y revocación en cascada de la familia al detectar reuso.
- [jwtk/jjwt — GitHub y CHANGELOG](https://github.com/jwtk/jjwt) — versión 0.13.0 (2025-08-20) y política de soporte de la rama.
- [Spring Security 6.5.0 Is Out! (blog oficial)](https://spring.io/blog/2025/05/19/spring-security-6-5-0-is-out/) — 6.5 es la última minor de la rama 6; recomendación de revisar la guía de migración a 7.
- [Spring Security 2025-11 Releases (blog oficial)](https://spring.io/blog/2025/11/17/spring-security-releases/) — 7.0.0 disponible; 6.5.x sigue con parches.
- [spring-projects/spring-security#8842](https://github.com/spring-projects/spring-security/issues/8842) — confirma que `Argon2PasswordEncoder` requiere dependencias adicionales (BouncyCastle).
