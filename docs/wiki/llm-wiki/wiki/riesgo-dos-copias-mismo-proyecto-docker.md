---
titulo: "Riesgo — Dos copias del laboratorio compartían proyecto Docker"
tipo: riesgo
estado: Vigente
actualizado: 2026-09-23
fuentes:
  [
    'docker-compose.yml',
    '.env.example',
    'citas-web/vite.config.ts',
    'docker inspect fcv-citas-api-dev (2026-09-23)',
  ]
tags: [riesgo, docker, entorno, cors]
---

# Riesgo — Dos copias del laboratorio compartían proyecto Docker

## Qué pasó

El 2026-09-23 el registro de usuario fallaba en el navegador con *"No pudimos contactar al
servidor"*. La causa no estaba en el código: **los contenedores que corrían no eran de este
workspace**.

En la misma máquina existe otra copia del laboratorio en
`C:\Users\IA ACADEMY 7\Documents\FCV_DES_AND\citas`, parada en el commit inicial de S2. Ambas
copias traían el mismo `COMPOSE_PROJECT_NAME` (`fcv-citas-training`) y los mismos
`container_name` fijos, así que Docker las trataba como **el mismo proyecto**: los contenedores
los poseía la última carpeta que hubiera hecho `docker compose up`, y era la otra.

Resultado: el `8080` servía un backend de S2 (1 migración aplicada, 3 repositorios JPA) mientras
este repo tenía 7 migraciones y 14 repositorios. Nada en el arranque lo advertía; había que mirar
`docker inspect` para ver el montaje real.

## `run` y `exec` no son lo mismo (esto es lo importante)

**HECHO verificado.** La diferencia explica por qué el problema pasó tanto tiempo inadvertido:

| Comando | Qué contenedor usa | Qué monta |
|---|---|---|
| `docker compose run --rm citas-api-dev …` | crea uno **nuevo** desde este `docker-compose.yml` | siempre **este** repo |
| `docker compose exec citas-api-dev …` | se engancha al contenedor **que ya existe** | lo que montara quien lo creó |

El hook pre-commit de `citas-api` usa `run --rm`, así que sus 210 pruebas siempre corrieron
contra el código correcto y nunca delataron nada. El diagnóstico manual, hecho con `exec`, fue el
que se equivocó de carpeta. Ver [[riesgo-prueba-intermitente-flyway]]: aquel fallo intermitente
es **otro** problema, porque el hook usa `run`.

## Mitigación aplicada

Este workspace tiene identidad propia, así que las dos copias pueden convivir:

| | Antes (compartido) | Este workspace |
|---|---|---|
| Proyecto Docker | `fcv-citas-training` | `fcv-citas-v1` |
| Contenedores | `fcv-citas-*` | `fcv-citas-v1-*` |
| MySQL (host) | 3307 | **3308** |
| API (host) | 8080 | **8081** |
| Servidor de desarrollo del host | 5173 | **5174** (fijo en `vite.config.ts`) |
| Contenedor web / Angular | 5173 / 4200 | **5175 / 4201** |

`docker-compose.yml` lleva además un `name:` de nivel superior como red de seguridad, aunque
`COMPOSE_PROJECT_NAME` del `.env` tiene prioridad sobre él y debe valer lo mismo.

`scripts/init-test-db.ps1` ya no busca el contenedor por nombre fijo: lo resuelve con
`docker compose ps -q mysql`.

## `strictPort` en Vite, por la misma razón

**DECISIÓN.** El servidor de desarrollo se fija al 5174 con `strictPort: true`. Si el puerto está
ocupado, Vite debe **fallar**, no saltar al siguiente: saltando, el origen deja de coincidir con
`FRONTEND_ORIGIN` y el backend rechaza la petición por CORS. En el navegador eso se ve igual que
un backend caído — *"No pudimos contactar al servidor"*— y manda a depurar donde no es.

`FRONTEND_ORIGIN` vale `http://localhost:5174` y el preflight se verificó devolviendo
`Access-Control-Allow-Origin: http://localhost:5174`.

## Corolario: matar `docker compose exec` no mata el proceso de dentro

**HECHO verificado el 2026-09-23.** Si la API se arranca con
`docker compose exec citas-api-dev mvn spring-boot:run` y se corta el comando del host, el JVM
**sigue vivo dentro del contenedor** y retiene el 8080. El arranque siguiente falla con
*"Port 8080 was already in use"* y el puerto lo sigue sirviendo el **código viejo**, así que se ve
como si un cambio recién hecho no funcionara. Pasó exactamente así al verificar HU-009: un
`GET` público devolvía 401 pese a que su prueba de integración daba 200.

Para reiniciar de verdad hay que matarlo dentro:

```bash
docker compose exec citas-api-dev bash -lc "pkill -f '[C]itasApiApplication'"
```

Los corchetes del patrón evitan que `pkill` se encuentre a sí mismo en su propia línea de
comandos y se suicide antes de matar al objetivo.

## Cómo reconocerlo la próxima vez

Si la API responde pero se comporta como una versión vieja, antes de tocar código:

```bash
docker compose ls                                   # ¿qué carpeta posee el proyecto?
docker inspect <contenedor> --format '{{range .Mounts}}{{.Source}}{{println}}{{end}}'
docker compose exec citas-api-dev git log --oneline -1
```

Si el montaje o el commit no son los de este repo, no hay bug que buscar.

## Relacionado

- [[riesgo-prueba-intermitente-flyway]]
- [[contrato-rest-identidad]] — el CORS de origen exacto que hace visible este fallo
- [[datos-modelo-3fn]] — las 7 migraciones que la base vieja no tenía
