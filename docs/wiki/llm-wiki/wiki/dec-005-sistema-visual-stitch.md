---
titulo: "Sistema visual: el DESIGN.md de Stitch es la fuente de verdad del frontend"
tipo: decision
estado: Vigente
actualizado: 2026-09-23
fuentes:
  [
    'citas-web/docs/diseno/stitch/RETOMA_REDISENO.md',
    'citas-web/docs/diseno/stitch/stitch_fcv_citas_ui_design.zip → calm_clinical_clarity/DESIGN.md',
    'prompts/diseno/PROMPT_STITCH_S4_REDISENO.md',
    'RESTRICCIONES_TECNICAS.md §Frontend',
    'citas-web/src/styles/tokens.css',
  ]
tags: [frontend, diseno, decision]
---

# El sistema visual del frontend sale de Stitch, no del criterio del agente

## Decisión

El `DESIGN.md` que Stitch exportó junto a los mockups —**"Calm Clinical Clarity"**, archivado en
`citas-web/docs/diseno/stitch/`— es la **fuente de verdad visual** de `citas-web`. Los tokens de
`src/styles/tokens.css` lo traducen a CSS y ningún componente define color, radio, sombra ni
tipografía por su cuenta: si el diseño aprobado cambia un valor, se cambia en `tokens.css` y se
propaga solo.

Esto sustituye al diseño propio que el agente improvisó en S3, cuando aún no había mockups
(ver [[dec-004-decisiones-s3-reserva]] para el resto de decisiones de esa sesión).

**HECHO** (2026-09-23): el diseño está aplicado en código. Marco de autenticación a pantalla
partida, `AppShell` con tarjeta de identidad, inicio del paciente con hero claro, logo propio en
SVG y los tres niveles de elevación del diseño. Commits `08cbe02` (aplicación) y `07eaa05`
(corrección de paleta) en `citas-web`.

## Trampa: `DESIGN.md` lleva dos paletas y no coinciden

**HECHO verificado** contra el `code.html` de los mockups. El archivo tiene una paleta en la
prosa (`## Colors`) y otra en el frontmatter (`colors:`), y son distintas:

| | Primario | Acento | Fondo | Texto | Texto 2.º | Borde |
|---|---|---|---|---|---|---|
| Prosa | `#0B5C8C` | `#14B8A6` | `#F4F7FA` | `#14212B` | `#5B6B7B` | `#DCE4EB` |
| Frontmatter | `#00446A` | `#006B5F` | `#F6F9FF` | `#101D27` | `#41474F` | `#C1C7D0` |

**Manda el frontmatter.** Es el bloque que cada `code.html` carga como configuración de Tailwind,
o sea lo que los `screen.png` pintan de verdad. La prosa es narración desfasada de la misma
herramienta. `#0B5C8C` sigue vivo, pero como `primary-container` y como primera parada del
degradado del panel de marca (`from-[#0B5C8C] via-primary to-[#073A58]`).

El primer intento de aplicar el diseño tomó la paleta de la prosa y **el usuario detectó que los
colores no coincidían** con los mockups. Corregido en la misma sesión.

## Qué fija el diseño

| Eje | Valor |
|---|---|
| Primario | `#00446A` (`primary`); `#0B5C8C` queda como `primary-container` |
| Acento | `#006B5F` (`secondary`); `#71F8E4` (`secondary-fixed`) solo sobre el panel azul |
| Superficies | fondo `#F6F9FF`, tarjeta `#FFFFFF`, apagada `#EBF5FF`, bordes `#C1C7D0` / `#717880` |
| Tipografía | Plus Jakarta Sans en titulares, Inter en cuerpo, datos y tablas |
| Formas | 10 px controles, 12 px tarjetas, 16 px contenedores, pill solo en estados |
| Elevación | tres niveles: tarjeta en reposo, tarjeta interactiva, modal |
| Estados de cita | nunca solo color: color + icono + texto en español, y borde propio |

Las pills de estado no salen de los tokens: en el `code.html` son utilidades de Tailwind
(`bg-amber-100 text-amber-900` y equivalentes), que coinciden con las que ya usaba el frontend.

## Las fuentes van autoalojadas, no por CDN

**HECHO verificado.** Hasta el 2026-09-23 `tokens.css` declaraba `'Plus Jakarta Sans'` e
`'Inter'` pero nadie las cargaba, así que el navegador caía siempre en la pila del sistema y el
diseño no se veía como el mockup. Se resolvió con `@fontsource-variable/inter` y
`@fontsource/plus-jakarta-sans` (npm, en `src/styles/fonts.css`), **no** con Google Fonts: la app
tiene que arrancar en un laboratorio sin salida a internet, igual que el resto del stack.

## Accesibilidad: la paleta buena no obliga a desviarse

**HECHO.** El esquema real de Stitch es Material, con los pares ya calculados para contraste, así
que se implementa tal cual: `#717880` en bordes de control da ≈4.5:1 (WCAG 1.4.11 pide 3:1),
`#41474F` como texto secundario da 9.4:1 y `#00446A` como acción principal, 10.3:1.

**DECISIÓN** que sigue en pie: si un valor del diseño no alcanzara el contraste exigido, gana la
accesibilidad y la desviación se escribe. Con la paleta de la prosa hacían falta tres
desviaciones (borde de campo, color de foco y franja elegida); con la del frontmatter, ninguna.

## Todo lo que Stitch inventó se descarta

**HECHO.** Los mockups traían funcionalidad que el producto no tiene: historia
clínica, resultados y órdenes, chat de asesores, línea telefónica prioritaria, "SSL 256-bit",
acreditación JCI, confirmación por SMS/WhatsApp, un contador de pacientes y un toggle de
simulación. Nada de eso entró en el código, en coherencia con la regla de no inventar
requerimientos fuera del PRD ni de las HU aprobadas.

> **Matizado en el LINT del 2026-09-23.** Esta lista incluía también el **plan de EPS**. Ya no
> corresponde: ese mismo día se aprobó **HU-009** y el registro lleva un selector de plan
> opcional, alimentado por `GET /api/catalogs/insurance-plans`
> (`citas-web/src/pages/RegistroPage.tsx`, `src/api/catalogApi.ts`, prueba
> `src/registroAfiliacion.test.tsx`; contrato en [[contrato-rest-identidad]]). La diferencia con
> el mockup es la **procedencia**: el campo entró porque una HU aprobada lo pidió, no porque
> Stitch lo dibujara, y solo en el registro público de pacientes. El resto de la lista sigue
> descartado.

Stitch también nombró mal las sedes ("HIC Bucaramanga", "Instituto del Corazón de
Floridablanca"). Las del catálogo son **HIC Piedecuesta** e **ICV Floridablanca**; el frontend
usa lo que devuelve `GET /api/catalogs/sites` (ver [[contrato-rest-citas]]).

## Pregunta abierta

El mockup del panel del administrador incluye un gráfico "Citas por sede esta semana". La API no
expone esa serie: `GET /api/admin/summary` solo devuelve contadores. Queda sin implementar hasta
que exista una HU que la pida; si se aprueba, es un endpoint nuevo, no un cálculo en el
frontend. Ver [[sintesis-preguntas-abiertas]].

## Relacionado

- [[contrato-rest-citas]] — los endpoints que estas pantallas consumen, incluido `GET /api/catalogs/sites` y el `GET /api/admin/summary` del panel
- [[contrato-rest-identidad]] — el registro y el selector de plan de EPS que pinta la pantalla de alta
- [[dec-004-decisiones-s3-reserva]] — el resto de decisiones de la sesión a la que sustituye en lo visual
- [[riesgo-dos-copias-mismo-proyecto-docker]] — el `strictPort` del 5174 sin el cual estas pantallas no llegan a la API
- [[sintesis-preguntas-abiertas]]

## Historial

- 2026-09-23 (LINT) — la página era **huérfana** (solo la enlazaban `index` y `log`): añadidos
  `Relacionado` e `Historial` y enlaces entrantes desde los dos contratos y el riesgo de Docker.
  Matizado que el plan de EPS ya **no** está entre lo descartado, por HU-009.
- 2026-09-23 — corregida la paleta: manda el `colors:` del frontmatter de `DESIGN.md`, no la
  prosa; con ella desaparecen las tres desviaciones por contraste. Commit `07eaa05`.
- 2026-09-23 — página creada al aplicar el diseño de Stitch en código. Commit `08cbe02`.
