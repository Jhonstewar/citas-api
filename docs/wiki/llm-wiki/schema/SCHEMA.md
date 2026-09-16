---
id: SCHEMA
tipo: schema
titulo: "Convenciones y workflows de la LLM Wiki"
estado: Vigente
actualizado: 2026-09-16
---

# SCHEMA — LLM Wiki del proyecto FCV Citas

Este archivo le dice al agente **cómo** mantener la wiki. Es la configuración que convierte al
agente en un bibliotecario disciplinado en vez de un chatbot que escribe markdown. El usuario y
el agente lo co-evolucionan: si una convención estorba, se cambia aquí, no se ignora.

## 1. Las tres capas

```
llm-wiki/
├── raw/      FUENTES. Curadas, inmutables. El agente lee; nunca reescribe.
├── wiki/     SÍNTESIS. Propiedad del agente. Páginas + index.md + log.md.
└── schema/   CONVENCIONES. Este archivo.
```

**Quién escribe qué:** el usuario aporta y curó las fuentes de `raw/`. El agente es dueño
absoluto de `wiki/`. El usuario lee `wiki/` en Obsidian; rara vez lo edita a mano.

**Por qué esta separación importa:** la wiki no es un índice de búsqueda sobre las fuentes. Es
conocimiento ya compilado. Cuando llega una fuente nueva, el agente la integra en las páginas
existentes en vez de dejarla como un documento suelto que habrá que releer en cada pregunta. Las
referencias cruzadas ya están puestas; las contradicciones ya están marcadas; la síntesis ya
refleja todo lo leído. La wiki se enriquece con cada fuente y con cada pregunta.

## 2. Tipos de página en `wiki/`

| Tipo | Prefijo | Qué contiene |
|---|---|---|
| Dominio | `dominio-` | Conceptos del negocio: cita, slot, disponibilidad, afiliación, profesional |
| Arquitectura | `arq-` | Decisiones estructurales: capas hexagonales, módulos, flujo de datos |
| Contrato | `contrato-` | Endpoints REST, DTO, códigos de error, versionado |
| Decisión | `dec-` | Una decisión tomada, con alternativas descartadas y motivo (estilo ADR) |
| Datos | `datos-` | Modelo, normalización, migraciones, invariantes de esquema |
| Riesgo | `riesgo-` | Riesgo abierto, su impacto y su mitigación |
| Síntesis | `sintesis-` | Visión transversal que conecta varias páginas |

Nombre de archivo: `<prefijo><slug-kebab-case>.md`, sin tildes ni mayúsculas.
Ejemplos: `dominio-cita.md`, `arq-hexagonal.md`, `dec-001-libreria-jwt.md`.

## 3. Frontmatter obligatorio

Toda página de `wiki/` empieza con:

```yaml
---
titulo: "Nombre legible de la página"
tipo: dominio | arquitectura | contrato | decision | datos | riesgo | sintesis
estado: Vigente | Provisional | Obsoleta
actualizado: YYYY-MM-DD
fuentes: ["[[RES-001-...]]", "PRD.md §4"]
tags: [dominio, citas]
---
```

`estado: Provisional` significa que la página contiene inferencia no verificada.
`estado: Obsoleta` se usa en vez de borrar: la página queda y apunta a la que la supersede.

## 4. Reglas de enlace

1. Enlaza con `[[nombre-de-archivo-sin-extension]]`. Enlaza **generosamente**.
2. Un `[[link]]` a una página que aún no existe es válido y deseable: marca algo que vale la
   pena escribir. No es un error.
3. Cuando una relación es importante en ambos sentidos, enlázala en las dos páginas.
4. Toda afirmación técnica cita su respaldo: `[[RES-001-...]]`, `PRD.md §4`, o
   `citas-api/src/.../Archivo.java:42`.
5. No dependas de plugins de la comunidad de Obsidian. Markdown y wikilinks bastan.

## 5. `index.md` — catálogo por contenido

Lista **toda** página de `wiki/`, agrupada por tipo, con enlace y una línea de resumen. Es lo
primero que lee el agente al empezar una sesión o al responder una pregunta: desde el índice
decide en qué páginas entrar. Se actualiza en cuanto cambia la estructura.

No dupliques el contenido de las páginas en el índice. Una línea por página.

## 6. `log.md` — registro cronológico

Append-only. Cada entrada empieza con un encabezado de formato fijo, para que sea parseable:

```
## [YYYY-MM-DD] <operacion> | <asunto>
```

donde `<operacion>` es `ingest`, `query`, `learn` o `lint`. Bajo el encabezado, 1–4 líneas: qué
entró, qué páginas se tocaron, qué quedó abierto.

Las últimas entradas se consultan con:
`grep "^## \[" log.md | tail -5`

Nunca edites ni borres entradas pasadas. El log es la historia de cómo evolucionó el
entendimiento del proyecto.

## 7. Los cuatro workflows

### INGEST — entra una fuente nueva

1. Lee la fuente completa desde `raw/`.
2. Extrae lo relevante y **decide dónde vive**: la respuesta por defecto es *actualizar páginas
   existentes*, no crear una página nueva. Crea página solo si el concepto tiene entidad propia y
   va a recibir enlaces entrantes.
3. Integra: actualiza cada página afectada, refuerza o corrige lo que ya decía, añade los
   wikilinks nuevos en ambas direcciones.
4. Si la fuente **contradice** algo ya escrito: registra ambas versiones, di cuál supersede y por
   qué, y pon la página superada en `estado: Obsoleta` o corrígela citando el cambio. **No borres
   silenciosamente.**
5. Actualiza `index.md` si cambió la estructura.
6. Añade la entrada a `log.md`.

Una sola fuente puede tocar 10–15 páginas. Eso es señal de que la integración funciona.

### QUERY — se pregunta algo

1. Lee `index.md`, elige las páginas relevantes y entra en ellas.
2. **Verifica contra la realidad** antes de responder: el código, la migración o el documento
   actual mandan sobre lo que dice la wiki. Una página puede haber envejecido.
3. Responde separando **evidencia** (con cita concreta) de **inferencia** (tu razonamiento).
4. Si la respuesta tiene valor duradero —una comparación, un análisis, una conexión nueva—
   **archívala como página**. Una buena respuesta no debe evaporarse en el historial del chat.
5. Si la wiki estaba desactualizada, corrígela en el mismo paso y anótalo.

### LEARN — cerrar una interacción sustancial

Se ejecuta **al final de cada interacción que produzca conocimiento durable**.

1. Extrae solo lo durable. Estado temporal, comandos de depuración y conversación **no entran**.
2. Clasifica cada pieza:
   - `HECHO` — afirmación verificable sobre el sistema. **Verifícala contra el código o el
     documento real antes de persistirla.** Si no la verificaste, no es un hecho.
   - `DECISIÓN` — se eligió A sobre B. Registra el motivo y las alternativas descartadas.
   - `PREFERENCIA` — cómo quiere trabajar el usuario. Registra el porqué, para poder juzgar los
     casos límite después.
   - `PREGUNTA ABIERTA` — algo sin resolver. Vale tanto como una respuesta: evita que se
     re-descubra el mismo hueco tres veces.
3. Integra en las páginas existentes. Actualiza `index.md` si hizo falta.
4. Añade la línea a `log.md`.

Si la interacción no produjo nada durable, **no escribas nada**. Una wiki que crece con ruido
pierde el valor que la hacía útil.

### LINT — revisión periódica de salud

Busca y reporta:
- contradicciones entre páginas;
- claims obsoletos que una fuente o un commit posterior ya superó;
- páginas huérfanas, sin ningún enlace entrante;
- conceptos mencionados muchas veces que merecerían página propia;
- referencias cruzadas que faltan;
- wikilinks rotos hacia páginas que nunca se escribieron y ya no tienen sentido;
- decisiones registradas como cerradas que el usuario nunca aprobó;
- **cualquier secreto, token, credencial o PII** que se haya colado.

El lint también propone: qué preguntas convendría investigar y qué fuentes convendría buscar.

## 8. Prohibiciones absolutas

- **Nunca** persistas passwords, secretos JWT, tokens, cadenas de conexión con credenciales,
  credenciales OAuth ni tokens MCP. Si una fuente los trae, redáctalos al ingerir.
- Nunca copies datos personales reales ni información privada de FCV. El dominio es ficticio.
- Nunca conviertas la wiki en transcript de la conversación.
- Nunca marques algo como `HECHO` sin haberlo verificado.

## 9. Escala

Mientras la wiki quepa en el orden de unos cientos de páginas, `index.md` es suficiente para
navegar y no hace falta búsqueda vectorial. Si algún día deja de serlo, la señal será que el
agente empieza a leer el índice completo sin encontrar lo que busca; entonces toca añadir una
herramienta de búsqueda sobre los markdown, no complicar este esquema.
