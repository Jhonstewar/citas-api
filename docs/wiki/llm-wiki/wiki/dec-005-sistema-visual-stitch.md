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
SVG y los tres niveles de elevación del diseño. Commit `08cbe02` de `citas-web`.

## Qué fija el diseño

| Eje | Valor |
|---|---|
| Primario | `#0B5C8C` (hover `#094A71`, activo `#06334F`) |
| Acento aqua | `#14B8A6`, solo en progreso, selección y realces |
| Superficies | fondo `#F4F7FA`, tarjeta `#FFFFFF`, apagada `#EEF3F7`, borde `#DCE4EB` |
| Tipografía | Plus Jakarta Sans en titulares, Inter en cuerpo, datos y tablas |
| Formas | 10 px controles, 12 px tarjetas, 16 px contenedores, pill solo en estados |
| Elevación | tres niveles: tarjeta en reposo, tarjeta interactiva, modal |
| Estados de cita | nunca solo color: color + icono + texto en español, y borde propio |

## Las fuentes van autoalojadas, no por CDN

**HECHO verificado.** Hasta el 2026-09-23 `tokens.css` declaraba `'Plus Jakarta Sans'` e
`'Inter'` pero nadie las cargaba, así que el navegador caía siempre en la pila del sistema y el
diseño no se veía como el mockup. Se resolvió con `@fontsource-variable/inter` y
`@fontsource/plus-jakarta-sans` (npm, en `src/styles/fonts.css`), **no** con Google Fonts: la app
tiene que arrancar en un laboratorio sin salida a internet, igual que el resto del stack.

## Desviaciones deliberadas por accesibilidad

**DECISIÓN.** Donde el diseño de Stitch no alcanza el contraste que exige WCAG, gana la
accesibilidad y la desviación queda escrita. Las tres son de bordes y de foco, nunca de
identidad:

| `DESIGN.md` pide | Se implementó | Motivo |
|---|---|---|
| Borde de campo `#DCE4EB` | `#B7C4D0` | `#DCE4EB` sobre blanco da ~1.3:1; WCAG 1.4.11 exige 3:1 en bordes de control |
| Foco de botón en aqua `#14B8A6` | `#1D7FC0` | El aqua sobre blanco da 2.3:1 |
| Franja horaria elegida con fondo `#14B8A6` | `#0F766E` | Texto blanco sobre `#14B8A6` da 2.3:1; sobre `#0F766E`, 4.9:1 |

## Todo lo que Stitch inventó se descarta

**HECHO.** Los mockups traían funcionalidad que el producto no tiene: plan de EPS, historia
clínica, resultados y órdenes, chat de asesores, línea telefónica prioritaria, "SSL 256-bit",
acreditación JCI, confirmación por SMS/WhatsApp, un contador de pacientes y un toggle de
simulación. Nada de eso entró en el código, en coherencia con la regla de no inventar
requerimientos fuera del PRD ni de las HU aprobadas.

Stitch también nombró mal las sedes ("HIC Bucaramanga", "Instituto del Corazón de
Floridablanca"). Las del catálogo son **HIC Piedecuesta** e **ICV Floridablanca**; el frontend
usa lo que devuelve `GET /api/catalogs/sites` (ver [[contrato-rest-citas]]).

## Pregunta abierta

El mockup del panel del administrador incluye un gráfico "Citas por sede esta semana". La API no
expone esa serie: `GET /api/admin/summary` solo devuelve contadores. Queda sin implementar hasta
que exista una HU que la pida; si se aprueba, es un endpoint nuevo, no un cálculo en el
frontend. Ver [[sintesis-preguntas-abiertas]].
