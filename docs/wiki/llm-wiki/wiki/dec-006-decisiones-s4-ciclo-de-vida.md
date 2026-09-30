---
titulo: "Decisión 006 — Decisiones de S4 para el ciclo de vida de la cita, la cuenta y la UI"
tipo: decision
estado: Provisional
actualizado: 2026-09-30
fuentes: ["PRD.md §4-§6 y §RF-19 (textual, para D40)", "PLAN_RETOMA_S4.md §2, §4 F5 y F10, ▶ Dónde retomar", "[[sintesis-preguntas-abiertas]]", "citas-web/docs/diseno/PANTALLAS_OBLIGATORIAS.md", "citas-api/docs/wiki/scrum/HU-*.md (frontmatter)", "citas-api/docs/wiki/scrum/epicas/EP-008-operacion-administrativa-de-solicitudes.md", "evidencias/s4/loops/LOOP-02/iter-1-verifier.json, iter-2-verifier.json", "suites ejecutadas el 2026-09-30: backend 484/484, frontend 218/218"]
tags: [decision, s4, ciclo-de-vida, reprogramacion, cuenta, aprobacion-delegada, estado-de-sesion]
---

# Decisión 006 — Decisiones de S4

El 2026-09-25 el usuario **delegó la aprobación de S4** (D15) y respondió directamente tres
puntos: D18, D19 y el reto del LOOP_03. El resto son propuestas del agente que quedan
**provisionales bajo aprobación delegada**, con el mismo estatus que D5–D14 de
[[dec-004-decisiones-s3-reserva]]: pasan a `Vigente` cuando el usuario las confirme, y si rechaza
alguna se anota aquí y se reabre la HU afectada.

La numeración empieza en **D15** porque D14 ya existe en [[dec-004-decisiones-s3-reserva]]
(lecturas por `JdbcTemplate`). El plan se escribió primero como D14–D29 y se renumeró el mismo día,
antes de que nada lo citara.

Origen: **U** = respondida por el usuario · **P** = provisional bajo delegación.

| # | Pregunta | Decisión | Alternativa descartada | Motivo |
|---|---|---|---|---|
| D15 U | Modo de aprobación de S4 | Delegada para el alcance del plan, registrada en el historial de cada HU | HU por HU | Elección del usuario |
| D16 P | INC-030 · ¿Se cancela una `REQUESTED`? | Sí, y libera su retención | Solo `APPROVED` | RF-14 dice "futura no terminal"; `AppointmentStatus` ya admite `REQUESTED → CANCELLED` |
| D17 P | INC-027 · Antelación mínima para cancelar | Ninguna: basta con que la cita no haya empezado | Un plazo de horas | El PRD solo exige "futura"; un plazo sería un requisito inventado |
| D18 U | N1 · Cancelar con reprogramación `PENDING` | En la misma transacción la solicitud pasa a `CANCELLED` y se liberan **las dos** franjas | Impedir cancelar hasta que el ADMIN decida | Una solicitud sobre una cita cancelada no tiene sentido y retendría una franja huérfana |
| D19 U | INC-018 · Desde cuándo se cierra como `COMPLETED`/`NO_SHOW` | Desde la **hora de inicio**, sin plazo máximo, aislado en un único punto de decisión (T-02 de HU-021) | Al terminar la franja o el día | Permite marcar la inasistencia sin esperar; el punto único permite cambiarlo sin tocar el caso de uso |
| D20 P | INC-028 / INC-029 · Número de solicitudes y retirada | Una sin decidir por cita; tras la decisión se puede pedir otra. El paciente no retira la solicitud en S4 | Retirada explícita | `uq_reschedule_requests_active` (`V3:162`) ya impone una activa; retirar añade una transición sin RF que la pida |
| D21 P | INC-031 · ¿Reprogramar a otra sede? | Sí, si el profesional atiende en ella | Misma sede obligatoria | RF-15 solo obliga a conservar profesional y especialidad |
| ~~D22 P~~ **SUPERADA por D39** | N3 · ¿La decisión sobre una reprogramación deja historial? | ~~Sí, las dos decisiones~~ → **solo la aprobación** escribe fila; el rechazo no toca la cita (ver D39) | Sin historial, porque el estado no cambia | Se justificó en «RF-19 pide trazar el cambio», pero eso venía de una **reformulación de RF-19 en EP-008**, no del PRD: RF-19 audita «todo cambio de estado de cita» y el rechazo no cambia ninguno. Corregido en **D40** |
| D23 P | INC-036 · Reprogramación cuya franja propuesta ya pasó | Aprobar responde 409; el ADMIN rechaza con motivo | Aprobarla igual | Igual que D12 y RN-06 |
| D24 P | N6 · Filtros de la bandeja sobre una reprogramación | Por la **franja propuesta** | Por la franja actual | Es lo que el ADMIN decide |
| D25 P | INC-006 · Campos editables del perfil | Nombres, apellidos y teléfono. Fijos: email y documento | Todo editable | El email es la credencial de login y el documento es identidad única (`users`, `V1`) |
| D26 P | INC-007 · Varias afiliaciones | Una vigente. Cambiar de plan cierra la anterior con `ended_on`; quitarla la cierra sin reemplazo | Varias simultáneas | `uq_affiliations_user_current` (`V2:158`) ya lo impone |
| D27 P | INC-002 / INC-005 · Token de recuperación | 30 min, configurable por entorno. Viaja en la respuesta solo si una variable de laboratorio lo activa; apagada por defecto. Nunca en logs | Solo en el log | RF-03 permite exponerlo en desarrollo; un log con tokens contradice PRD §8 |
| D28 P | INC-011 · Borrar EPS o plan no referenciado | Borrado físico si nada lo referencia; si no, 409 y se ofrece desactivar | Nunca borrar | Mismo comportamiento que especialidades (HU-011) |
| D29 P | INC-001 · Política de contraseña | Mín. 8 con letra y número también en el servidor. Solo al fijar una contraseña; las cuentas existentes siguen entrando | Mantener solo el máx. de 72 bytes | Cierra la divergencia A1 de [[sintesis-preguntas-abiertas]] |
| D30 P | Diseño de las pantallas nuevas | Sin mockups nuevos: se construyen con el sistema visual y los componentes existentes. Si la agenda del profesional no convence en el navegador, se hace un único mockup para esa pantalla | Pasar todo el lote por Stitch | Ver abajo |

### D31–D35 — divergencias que salieron al aprobar las HU (2026-09-25)

El `scrum-spec-writer` las encontró al contrastar criterios con el esquema real. Todas **P**.

| # | Divergencia | Decisión | Motivo |
|---|---|---|---|
| D31 P | HU-027 CA-03/CA-09 piden guardar la franja anterior, y `reschedule_requests` (V3) solo guarda la propuesta, sin sede | **`V10`** añade `previous_date`, `previous_start_time`, `previous_end_time`, `previous_site_id` y `proposed_site_id` | Tras aprobar, la cita se mueve y la franja anterior desaparece; y D21 permite cambiar de sede |
| D32 P | `uq_affiliations_user_plan UNIQUE(user_id, eps_plan_id)` (V2:157) impide volver a un plan ya usado, cosa que D26 hace posible | **`V9`** sustituye esa única por `(user_id, eps_plan_id, current_marker)`. La duplicidad que prohíbe HU-009 CA-03/04 (dos afiliaciones vigentes) ya la impide `uq_affiliations_user_current` | Conserva el historial sin reabrir filas cerradas |
| D33 P | La DoD de HU-012 pide nombre único de EPS y de plan; V2 solo tiene únicos por código | **`V9`** añade `UNIQUE(name)` en `eps` y `UNIQUE(eps_id, name)` en `eps_plans`, tras comprobar que la semilla no tiene duplicados | Misma solución que D-R1 para especialidades: la unicidad no vive solo en la aplicación |
| D34 P | Restablecer la contraseña revoca los refresh tokens (plan F7), y ninguna decisión lo registraba | Se mantiene: restablecer revoca **todas** las familias de refresh del usuario. No añade criterio a HU-007, pero sí prueba | Una contraseña robada no debe sobrevivir en sesiones abiertas (PRD §8, [[dec-002-rotacion-refresh-tokens]]) |
| D35 P | HU-020 no fija qué ve el profesional del paciente ni qué día empieza la semana | Nombre completo, tipo y número de documento; ni email ni teléfono. La semana empieza en **lunes** y la calcula el frontend; la API recibe `from`/`to` | Mínimo que identifica al paciente en la atención (RF-16) |

### D37 P — motivo de una reprogramación cancelada con su cita

Cuando D18 cancela una reprogramación `PENDING` porque el paciente cancela la cita, el CHECK
`ck_reschedule_requests_decision` (V3) obliga a registrar un decisor. Queda como decisor el propio
paciente, con `decision_reason = "Cita cancelada por el paciente"` automático, para que la
solicitud no quede sin explicación en la bandeja ni en el detalle.

### D38 P — cerrar la atención con una reprogramación pendiente

El Builder del LOOP_02 detectó que si el profesional cierra como `COMPLETED`/`NO_SHOW` una cita con
una reprogramación `PENDING`, la franja propuesta queda **retenida sin salida**: el ADMIN no puede
rechazarla, porque HU-031 CA-05 exige una cita `APPROVED`. **Decisión:** igual que D18, cerrar
la atención cancela en la misma transacción la solicitud `PENDING` (decisor = el profesional,
motivo "Cita cerrada por el profesional") y libera su retención.

### D39 P — historial de la reprogramación (refina D22)

El Verifier de frontend del LOOP_02 (iteración 1) vio que, con D22 tal cual, **rechazar** una
reprogramación escribe una fila `APPROVED`/`ADMIN` con el motivo del rechazo, y la línea de tiempo
del paciente muestra "Aprobada · Motivo: <motivo del rechazo>". **Decisión:**
- solo la **aprobación** escribe fila de historial, porque es la única que cambia la cita (fecha,
  hora y sede). El motivo nombra la franja anterior y la nueva;
- el **rechazo** no toca la cita: queda en la solicitud (`decisionReason`), que el detalle ya
  muestra en el aviso de HU-028. La cancelación por D18/D38 tampoco escribe fila extra;
- `HistoryEntry` gana `event?: 'RESCHEDULED'`, que el backend deriva sin esquema nuevo (una fila
  cuyo estado es igual al de la fila anterior es una reprogramación aprobada). La UI la rotula
  "Reprogramada".

HU-031 CA-06 se ajusta a D39.

### D36 U — el F5 no cierra la sesión (2026-09-25)

**PREFERENCIA del usuario:** recargar la página no debe cerrar la sesión. Hasta ahora sí la cerraba,
y no por un fallo de S4: era la consecuencia directa de guardar el refresh token solo en la memoria
de JavaScript ([[dec-002-rotacion-refresh-tokens]], S2).

**DECISIÓN:** el refresh token pasa a una cookie `HttpOnly; Secure; SameSite=Strict;
Path=/api/auth`, sin copia en JavaScript. Al arrancar, el frontend llama una vez a `refresh`. Se
descarta `localStorage` porque un XSS podría leerlo. Contrato en [[contrato-rest-identidad]] §S4
"refresh token en cookie". Afecta a HU-002, HU-003 y HU-004, ya `Completada`: su matriz se revisa
en la verificación final. Cierra la pregunta S4 de [[sintesis-preguntas-abiertas]].

Además, D29 **no** afecta a HU-008: el perfil no cambia contraseñas. HU-001 (`Completada`, por
D29) y HU-032 (por D22) deben revisar su matriz en la verificación final de S4.

## D30 — por qué las pantallas de S4 no pasan por Stitch

- **HECHO:** 9 de las 13 pantallas protegidas se construyeron en S3 sin mockup y quedaron
  coherentes con las 4 que sí lo tuvieron, porque todas se componen de los mismos componentes.
  El rediseño de [[dec-005-sistema-visual-stitch]] solo tocó tokens, CSS, `AuthLayout`, `AppShell`
  y el inicio del paciente.
- **HECHO:** `app.css` no tiene colores hex ni `rgba`: todo pasa por `var(--…)`. Los TSX no
  llevan colores. Hay modo oscuro, `prefers-reduced-motion` y una `DataTable` que en móvil pasa
  a tarjetas.
- **HECHO:** `PROMPT_STITCH_S4_REDISENO.md` no pide EPS, perfil, cierre de atención ni la bandeja
  de reprogramaciones. `PANTALLAS_OBLIGATORIAS.md` sí especifica campos, estados y acciones de
  todas ellas.
- El único patrón visual realmente nuevo es la **agenda con citas y cierre de atención**. Se
  resuelve con `SegmentedControl` "Bloques / Citas" y una tarjeta de cita del profesional.

**HECHO medido:** `#717880` (`--color-border-strong`) da **4,47:1** sobre blanco. El comentario de
`tokens.css:27` dice 4,1:1 y está mal. Como borde cumple de sobra (WCAG 1.4.11 exige 3:1) y el
texto deshabilitado está exento, así que solo hay que corregir el comentario.

## Estado real de S4 al pausarse (2026-09-25) y al retomarse (2026-09-30)

Esta sección existe porque el plan **no** se puede leer literalmente: sus casillas dicen más de lo
que se hizo. Todo lo de abajo se comprobó el 2026-09-30 contra los ficheros reales.

### S4 cerró cero HU

**HECHO**, leído del frontmatter de los 33 `HU-*.md` de `citas-api/docs/wiki/scrum/`:

| Estado | Nº | Cuáles |
|---|---|---|
| `Completada` | **16** | HU-001, 002, 003, 004, 010, 013, 014, 015, 017, 018, 019, 023, 024, 025, 030, 032 |
| `En validación` | 5 | HU-005, 011, 016, 022, 029 — deuda heredada de S3 |
| `Aprobada`, con código escrito y **sin verificación independiente** | 11 | HU-006, 007, 008, 009, 012, 020, 021, 026, 027, 028, 031 |
| `En desarrollo` | 1 | HU-033, viva por diseño (artefacto vivo) |

Esas 16 son **exactamente** las mismas que ya estaban cerradas al empezar S4 (§1 de
`PLAN_RETOMA_S4.md`). Es decir: S4 escribió mucho código y no cerró ninguna historia. Las 11
`Aprobada` tienen implementación y suites en verde, pero nadie las contrastó HU por HU contra sus
criterios ni llenó su matriz de evidencia; eso es justamente F10. **`Aprobada` con código no es
`Completada`.**

### DEFECTO DE DOCUMENTO — las casillas de F10 estaban `[x]` y F10 no se ejecutó (saneado el 2026-09-30)

Las siete casillas de la fase F10 de `PLAN_RETOMA_S4.md` aparecían marcadas. No lo estaban
porque se hiciera el trabajo: **todo el archivo se escribió en un único commit (`bc13adc`)**, que
incluye a la vez las casillas marcadas y la sección "▶ Dónde retomar" que lista F10 como
pendiente. Pruebas de que F10 no corrió:

- ninguna HU pasó a `Completada` en S4 (tabla de arriba), así que la verificación HU por HU no ocurrió;
- `EVIDENCIAS_S4.md` solo contiene la §1 de LOOP_01: no hay guía de prueba manual en navegador con
  los tres roles;
- [[log]] no tiene ninguna entrada `lint` posterior al 2026-09-23 (la última es la del cierre de F11 de S3);
- [[datos-modelo-3fn]] (líneas 17-20) sigue afirmando que la comparación contra `database/reference/`
  **sigue pendiente**;
- la **única** casilla de F10 que sí se cumplió es la última: el push y el merge a `main`.

El error iba **en los dos sentidos**: F4, F6 y F7 estaban **sin marcar** aunque su código está
escrito, como decía el Registro de avance. Verificado endpoint por endpoint y ruta por ruta:
`ProfessionalAppointmentController` (agenda, `complete`, `no-show`), `AdminEpsController`,
`MeController` (`PUT /api/me`, afiliación), `PasswordRecoveryController`, `ResetPasswordUseCase:70`
(revoca los refresh), `PasswordPolicyCompliant` (D29), `AuthFlowIntegrationTest` con
`OutputCaptureExtension` (CA-09) y las rutas `perfil`, `/admin/eps` y `/restablecer-password`.

**Saneado el 2026-09-30:** F10 desmarcada salvo el push/merge, cada casilla con la razón de por qué
no está hecha; F4, F6 y F7 marcadas con la referencia que las prueba; nota de precedencia añadida al
encabezado del §4 del plan.

> **REGLA, para quien retome:** una casilla `[x]` en `PLAN_RETOMA_S4.md` significa "el código existe
> y su suite estaba en verde", **no** "HU verificada ni cerrada" — eso es F10, que no ha corrido, y
> por eso las 11 HU de S4 siguen en `Aprobada`. Si el plan se contradice consigo mismo, manda la
> sección **"▶ Dónde retomar"**.

### LOOP_02 quedó a medias

`evidencias/s4/loops/LOOP-02/iter-1-verifier.json`: en la iteración 1 el Builder terminó, el
`frontend-verifier` dio **PASS** (typecheck 0, lint 0, 212/212, build OK) y el `backend-verifier`
quedó **INTERRUMPIDO** por la pausa del usuario — hay que ejecutarlo **entero** al retomar, no
reanudarlo. El resultado registrado es `ITERATION_2_REQUIRED`, con el feedback ya convertido en
decisión aquí mismo: **D38**, **D39** y tres correcciones de frontend (texto del aviso de rechazo
sin botón de cancelar; el tipo `AdminAppointment` exige `cancellable`/`reschedulable` que el backend
no emite).

### `develop` se mergeó a `main` con F5, F8, F9 y F10 sin terminar

Merge commits del 2026-09-25 `0ae5184` (raíz) y `016baae` (citas-api), ambos "Merge pull request #3
from Jhonstewar/develop". Hoy `git rev-list --count origin/main..origin/develop` = **0** en los tres
repos: están idénticos.

**CONTRADICCIÓN con la convención del workspace.** `AGENTS.md:102` dice que "`main` solo recibe
incrementos que el usuario declara estables", y `AGENTS.md:41` que `main` = estable. Hoy `main`
contiene trabajo de S4 a medio verificar. Se deja anotado, no resuelto: qué hacer con `main` es una
decisión del usuario, registrada como pregunta abierta en [[sintesis-preguntas-abiertas]].

### Entorno al retomar

Al abrir la sesión, Docker Desktop **no** estaba arrancado (`docker ps` fallaba con
`open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified`), así que ninguna
cifra de backend se podía reverificar. **Se levantó ese mismo día** y a partir de ahí la suite corrió
varias veces de verdad: 471 → 480 → **484**, siempre `BUILD SUCCESS`.

> **Este párrafo estuvo obsoleto unas horas y causó un error real.** Mientras decía «Docker no está
> arrancado», un agente lo leyó aquí y en `index.md` y lo propagó de buena fe a
> [[contrato-rest-citas]] como reserva de «pruebas leídas, no ejecutadas», cuando ya se habían
> ejecutado. **Un dato de entorno desactualizado en la wiki no se queda quieto: se copia.** Al cambiar
> el entorno, actualiza estas páginas en el momento, no al cerrar el turno.

Sigue pendiente, y es del usuario: el **`.env` de la raíz quedó atrás de `dafb0fa`**. No viaja en git,
así que `COMPOSE_PROJECT_NAME` sigue valiendo `fcv-citas-training` y MySQL se publica en **3307**, no
3308. Los volúmenes se crean como `fcv-citas-training_*` y se comparten con cualquier otra copia del
laboratorio, que es el riesgo de [[riesgo-dos-copias-mismo-proyecto-docker]] — latente hoy porque solo
hay una copia. Comprobable sin abrir el fichero, con `docker compose ls` y `docker port`.

### Cierre del LOOP_02 (iteración 3) y la deriva respecto del PRD

**El loop cerró en 3 iteraciones de 4.** La iteración 2 implementó D38 y D39; el `backend-verifier`
dio **PASS al comportamiento** (43 criterios con evidencia, las cinco reglas innegociables cumplidas,
y descartados **con prueba** los dos riesgos del §5 del plan) y **FAIL a la Definition of Done**,
porque los documentos afirmaban lo contrario del código. La iteración 3 cerró ese FAIL.

**Lección del loop, que vale más que las cifras.** El Verifier no se limitó a comprobar que la
derivación de D39 funciona: verificó **la razón escrita** en el javadoc, y era **falsa**. Decía que la
reprogramación aprobada es «la única escritura de historial que no pasa por `transitionTo`», y
`bookGeneral` y `requestSpecialized` también construyen su `StatusChange` directamente. El invariante
real: el historial se escribe solo por `AppointmentRepository.create` y `apply`; la fila de `create` es
por contrato **la primera** de la cita, donde no hay anterior con la que comparar; y toda fila
posterior viene de un `Transition`, que solo `Appointment` construye. Al simular la violación,
`HistoryEventTest` y `HexagonalArchitectureTest` **seguían en verde**: la suite entera pasaba con la
mentira dentro. **Una prueba verde no dice que el razonamiento sea correcto; solo dice que ese camino
no se rompió.** Cerrado con `HistoryRowInvariantTest` (reflexión, sin lista que mantener) y
`HistoryWritersArchitectureTest` (bytecode: quién puede componer una fila de historial).

**D40 U — corregida una deriva respecto del PRD en EP-008.** Aprobación **directa del usuario**, no
delegada. [[EP-008]] reformulaba RF-19 como «cada **decisión** se registra en el historial con actor y
origen `ADMIN`», y `PRD.md` §RF-19 dice «**todo cambio de estado de cita** guarda: cita; estado nuevo;
actor cuando existe; fuente `SYSTEM`, `USER` o `ADMIN`; fecha/hora; motivo opcional». Rechazar una
reprogramación **no cambia el estado de la cita**, que sigue `APPROVED`, así que el PRD **nunca pidió**
esa fila: la exigencia la había añadido la épica, y **fue la que justificó D22**. Se alinearon cinco
textos —la regla y el criterio de completitud de EP-008, y el contexto, el alcance y la tarea T-03 de
HU-031— citando RF-19 textualmente. **No se relajó ningún requisito: se volvió al PRD.** HU-030 no
cambia, porque allí la decisión sí es un cambio de estado de la cita.

**Estado de las HU tras el cierre:** HU-027, HU-028 y HU-031 pasan de `Aprobada` a `En validación` con
matriz de evidencia rellenada. **Ninguna a `Completada`**: les faltan los criterios de frontend
verificados uno a uno y la prueba manual en navegador de F10.

**Queda abierto para F10:** la carrera «cerrar la atención» contra «decidir la reprogramación» que D38
abre no tiene prueba concurrente; el aviso `CANCELLED` del frontend descarta `decisionReason` y deja al
paciente sin explicación; HU-021 no menciona D38 en ninguna parte; y las matrices de HU-021, HU-026 y
HU-029 siguen sin rellenar.

## Consecuencias

- La reprogramación y la cancelación comparten un único camino de liberación de slots (D18).
- `SecurityConfig` gana rutas públicas para restablecer contraseña (D27); el resto de S4 cae en
  los prefijos por rol o en `/api/me`.
- La política de contraseña (D29) toca registro, restablecimiento y perfil a la vez, y HU-001
  (`Completada`) debe revisar su matriz. Es un endurecimiento del contrato, no una ampliación.
- Antes de F3, en el frontend: extraer `Timeline` y `DetailItem` a `src/components` y unificar los
  cinco botones de envío hechos a mano con `SubmitButton`.

## Relacionado

- [[dec-004-decisiones-s3-reserva]] — D5–D14, las provisionales de S3
- [[dec-003-libro-unico-slot-reservations]] — dónde conviven retención de reprogramación y ocupación
- [[dec-005-sistema-visual-stitch]] — el sistema visual que D30 reutiliza
- [[sintesis-preguntas-abiertas]]
- [[contrato-rest-citas]] — donde se publicarán los endpoints de S4

## Historial

- 2026-09-30 — añadida la sección "Estado real de S4": cero HU cerradas, las 11 `Aprobada` sin
  verificar, el defecto de las casillas de F10, LOOP_02 a medias y el merge a `main` con S4 a medio
  verificar. Ninguna decisión D15–D39 cambia.
- 2026-09-25 — creada con D15–D30 al resolver el plan de S4.
