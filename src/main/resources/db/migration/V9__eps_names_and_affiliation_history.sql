-- =====================================================================================
-- V9 — Nombres unicos de EPS y de plan, e historial de afiliaciones (S4, F6)
--
-- Por que V9 y no V10 (el plan reservaba V9 para la reprogramacion, D31):
-- Flyway 11.7.2 corre con outOfOrder = false (valor por defecto; application.yml no lo cambia) y
-- validate-on-migrate = true. Las bases de desarrollo y de pruebas son PERSISTENTES: si esta
-- migracion fuera V10 y se aplicara antes que la V9 de la reprogramacion, esa V9 quedaria como
-- "ignored" (mas antigua que la ultima aplicada) y el arranque fallaria con "Detected resolved
-- migration not applied to database: 9". Como esta fase se aplica primero, toma V9 y la
-- reprogramacion (D31) pasa a V10.
--
-- 1. D33 (HU-012 DoD): el nombre de la EPS es unico, y el del plan es unico dentro de su EPS.
--    Hasta ahora V2 solo tenia unicos por CODIGO (uq_eps_code, uq_eps_plans_eps_code). Misma
--    solucion que V8 para especialidades: la garantia la da el motor, no solo el caso de uso. La
--    collation utf8mb4_0900_ai_ci hace que "Salud Demo" y "SALUD DEMÓ" choquen, igual que la
--    comprobacion del caso de uso (que consulta con la misma collation).
--
-- 2. D32 (HU-009 CA-09, D26): uq_affiliations_user_plan UNIQUE (user_id, eps_plan_id) impedia
--    volver a un plan ya usado, porque la fila cerrada (is_current = false) chocaba con la nueva.
--    Se sustituye por UNIQUE (user_id, eps_plan_id, current_marker): current_marker es 1 en la
--    vigente y NULL en las cerradas, y MySQL admite varios NULL en un indice unico. Asi:
--      - dos afiliaciones VIGENTES al mismo plan siguen prohibidas (HU-009 CA-03/CA-04), y ya lo
--        estaban tambien por uq_affiliations_user_current (una sola vigente por usuario);
--      - el historial de afiliaciones cerradas se conserva sin reabrir filas.
--    La nueva unica se crea ANTES de borrar la antigua, en la misma sentencia: la FK
--    fk_affiliations_user siempre tiene un indice que empieza por user_id.
--
-- Comprobacion previa (2026-09-25): ni citas_fcv_training (4 EPS, 9 planes) ni
-- citas_fcv_training_test tienen nombres repetidos. Si una base los tuviera, esta migracion
-- fallaria con "Duplicate entry" y no se aplicaria; detectarlos con:
--   SELECT name, COUNT(*) FROM eps GROUP BY name HAVING COUNT(*) > 1;
--   SELECT eps_id, name, COUNT(*) FROM eps_plans GROUP BY eps_id, name HAVING COUNT(*) > 1;
-- =====================================================================================

ALTER TABLE eps
    MODIFY name VARCHAR(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    ADD CONSTRAINT uq_eps_name UNIQUE (name);

ALTER TABLE eps_plans
    MODIFY name VARCHAR(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    ADD CONSTRAINT uq_eps_plans_eps_name UNIQUE (eps_id, name);

ALTER TABLE affiliations
    ADD CONSTRAINT uq_affiliations_user_plan_current UNIQUE (user_id, eps_plan_id, current_marker),
    DROP INDEX uq_affiliations_user_plan;
