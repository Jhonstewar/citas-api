# AGENTS.md — Agente principal de `citas-api`

Generado en S2 (paso 2) con `../prompts/agents/PROMPT_AGENT_CITAS_API.md`, a partir del
código real del repositorio. La gobernanza global está en `../AGENTS.md`; este archivo la
concreta para el backend y **no** la contradice.

## 1. Qué es este repo

Backend REST del sistema ficticio de citas FCV. Es un repositorio Git independiente dentro del
workspace `FCV_Proyecto_Citas_v1`.

| Aspecto | Valor real (verificado en el repo) |
|---|---|
| Lenguaje / build | Java 21 · Maven (`pom.xml`, `java.version=21`) |
| Framework | Spring Boot **3.5.16** (web, data-jpa, validation, security, oauth2-resource-server, actuator) |
| Base de datos | MySQL 8.4 · `mysql-connector-j` · Flyway (`flyway-core` + `flyway-mysql`) |
| Seguridad | Spring Security + JWT HS256 firmado con Nimbus; refresh token opaco rotativo guardado con hash |
| Pruebas | JUnit 5 · `spring-boot-starter-test` · `spring-security-test` · ArchUnit 1.4.1 |
| Paquete raíz | `com.fcv.citas` |

Java y Maven **no** se ejecutan en el host: corren en el contenedor `citas-api-dev`
(`maven:3.9-eclipse-temurin-21`) definido en `../docker-compose.yml`.

## 2. Arquitectura hexagonal (cómo está organizado de verdad)

```
src/main/java/com/fcv/citas/
├── domain/          Modelo y puertos. Java puro: sin Spring, JPA ni HTTP.
│   ├── user/        User, Role, UserRepository (puerto), excepciones de negocio
│   ├── auth/        RefreshToken, PasswordHasher, AccessTokenIssuer, RefreshTokenRepository… (puertos)
│   └── shared/      DomainException
├── application/     Casos de uso (RegisterUserUseCase, LoginUseCase, RefreshSessionUseCase,
│                    LogoutUseCase, GetCurrentUserUseCase) y TransactionRunner (puerto)
└── infrastructure/  Adaptadores
    ├── rest/        Controladores, DTO de entrada/salida, GlobalExceptionHandler (ProblemDetail)
    ├── persistence/ Entidades JPA (*JpaEntity), repos Spring Data y adaptadores de los puertos
    ├── security/    SecurityConfig, emisión JWT (Nimbus), hash BCrypt, handlers problem+json
    └── config/      CORS y cableado de casos de uso (UseCaseConfig)
```

Reglas que **ya se comprueban** con `HexagonalArchitectureTest` (ArchUnit) y que no se relajan:

- `domain` y `application` no dependen de Spring, JPA, Jakarta ni HTTP.
- `domain` y `application` no dependen de `infrastructure`.
- `domain` no depende de `application`.

Además:

- Los casos de uso son clases planas registradas como beans en `infrastructure/config/UseCaseConfig`;
  no se anotan con `@Service` ni `@Transactional` (usan el puerto `TransactionRunner`).
- Los controladores solo traducen HTTP ↔ comando/resultado. Una regla de negocio crítica
  (slots, doble reserva, transiciones de estado, ownership) vive en el dominio, nunca solo en el
  controlador.
- Las entidades JPA no salen de `infrastructure/persistence`; el dominio usa sus propios tipos.
- Los errores se devuelven como `ProblemDetail` (`application/problem+json`) y en español.

## 3. Base de datos y Flyway

- Flyway es la **única** fuente del esquema: `src/main/resources/db/migration/V1..V4`.
  Hibernate corre con `ddl-auto: validate`; nunca `update` ni `create`.
- Un cambio de esquema = **nueva** migración `V{n}__descripcion.sql` con justificación en el
  commit y en la wiki. Nunca se edita una migración ya aplicada.
- `clean-disabled: true`. No se ejecuta `flyway clean` en ningún entorno.
- La no-doble-reserva se garantiza por restricción de base de datos (ver
  `docs/wiki/llm-wiki/wiki/dec-003-libro-unico-slot-reservations.md`), no solo por código.
- Zona horaria: `America/Bogota` en JDBC, Hibernate y Jackson. Locale fijo `es_CO`.

## 4. Seguridad

- Todo secreto se lee de variables de entorno (`.env.example` las enumera). `application.yml`
  no contiene valores reales; la app rechaza un `JWT_ACCESS_SECRET` que sea el marcador `CHANGE_ME`.
- Nunca registres en logs contraseñas, access tokens ni refresh tokens.
- Contraseñas con BCrypt; límite de 72 **bytes** validado (`BcryptPasswordLength`).
- Refresh token rotativo con revocación por familia ante reuso (dec-002).
- CORS con orígenes exactos desde `FRONTEND_ORIGIN`; nunca `*`.
- Actuator expone solo `health`, sin detalles.

## 5. Comandos

Desde la **raíz del workspace** (`..`), con `.env` creado a partir de `.env.example`:

```powershell
docker compose up -d mysql
.\scripts\init-test-db.ps1                                   # una vez por equipo: base de pruebas aislada
docker compose run --rm citas-api-dev mvn -B test            # suite completa
docker compose run --rm citas-api-dev mvn -B test -Dtest=HexagonalArchitectureTest
docker compose run --rm --service-ports citas-api-dev mvn spring-boot:run   # API en :8080
```

Las pruebas de integración usan la base `citas_fcv_training_test` (perfil
`src/test/resources/application-test.yml`), nunca la de desarrollo.

## 6. Modo de trabajo

1. Localiza la HU `Aprobada` y su DoD en `docs/wiki/scrum/`. Sin HU aprobada no se implementa.
2. Identifica reglas del PRD y contratos REST afectados (`docs/wiki/llm-wiki/wiki/contrato-rest-identidad.md`).
3. Propón un plan (archivos y capas) antes de editar.
4. Implementa el mínimo coherente, de dentro hacia fuera: dominio → aplicación → adaptadores → migración.
5. Ejecuta las pruebas relevantes y la de arquitectura.
6. Verifica contra CA y DoD. La verificación formal la hace `backend-verifier`, no quien implementó.
7. Resume la evidencia y deja explícito lo que **no** se verificó.

## 7. Límites

- No edites `citas-web` desde este agente. Si el contrato REST cambia, repórtalo al orquestador
  para que coordine el cambio cross-repo.
- No acoples el backend a React/Angular (nada de rutas, textos o formatos pensados para una UI concreta).
- No inventes requerimientos fuera del PRD o de las HU aprobadas; regístralos como pregunta abierta.
- No mantengas una LLM Wiki propia: `docs/wiki/llm-wiki/` es la wiki **global** del workspace y
  la mantiene el orquestador (`wiki-keeper`) según `docs/wiki/llm-wiki/schema/SCHEMA.md`.
- Git: trabajo en `develop`, Conventional Commits en español con prefijo de sesión
  (`feat(s3): ...`). Nunca stagear `.env`, `target/` ni secretos. Sin push sin confirmación del usuario.
