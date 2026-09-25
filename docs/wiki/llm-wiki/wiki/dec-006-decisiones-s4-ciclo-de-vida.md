---
titulo: "Decisión 006 — Decisiones de S4 para el ciclo de vida de la cita, la cuenta y la UI"
tipo: decision
estado: Provisional
actualizado: 2026-09-25
fuentes: ["PRD.md §4-§6", "PLAN_RETOMA_S4.md §2", "[[sintesis-preguntas-abiertas]]", "citas-web/docs/diseno/PANTALLAS_OBLIGATORIAS.md"]
tags: [decision, s4, ciclo-de-vida, reprogramacion, cuenta, aprobacion-delegada]
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
| D22 P | N3 · ¿La decisión sobre una reprogramación deja historial? | Sí: fila con estado `APPROVED`, origen `ADMIN` y motivo que nombra franja anterior y nueva | Sin historial, porque el estado no cambia | RF-19 pide trazar el cambio. Sin migración: `appointment_status_history` guarda solo el estado nuevo (`V3:115`) |
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

- 2026-09-25 — creada con D15–D30 al resolver el plan de S4.
