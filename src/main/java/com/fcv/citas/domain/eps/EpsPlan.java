package com.fcv.citas.domain.eps;

/**
 * Plan de una EPS (RF-04, HU-012). El regimen se declara por plan, no por EPS (V2), y debe
 * pertenecer al catalogo fijo de regimenes: eso lo comprueba el caso de uso con
 * {@link RegimeCatalog}. La EPS no cambia: un plan pertenece siempre a la EPS en que se creo.
 *
 * <p>El codigo y el nombre son unicos dentro de su EPS (V2 y V9).</p>
 */
public record EpsPlan(Integer id, int epsId, String code, String name, String regimeCode, boolean active) {

    /** Limites de las columnas {@code eps_plans.code} y {@code eps_plans.name} (V2). */
    public static final int MAX_CODE = 30;
    public static final int MAX_NAME = 160;
    private static final int MAX_REGIME_CODE = 20;

    public EpsPlan {
        if (epsId <= 0) {
            throw new IllegalArgumentException("epsId invalido");
        }
        code = CatalogText.code(code, "code", "El código", MAX_CODE);
        name = CatalogText.required(name, "name", "El nombre", MAX_NAME);
        regimeCode = CatalogText.code(regimeCode, "regimeCode", "El régimen", MAX_REGIME_CODE);
    }

    /** Alta dentro de una EPS: nace activo. */
    public static EpsPlan create(int epsId, String code, String name, String regimeCode) {
        return new EpsPlan(null, epsId, code, name, regimeCode, true);
    }

    /** Edicion (contrato S4): nombre y regimen; el codigo y la EPS no cambian. */
    public EpsPlan withDetails(String newName, String newRegimeCode) {
        return new EpsPlan(id, epsId, code, newName, newRegimeCode, active);
    }

    public EpsPlan activate() {
        return new EpsPlan(id, epsId, code, name, regimeCode, true);
    }

    public EpsPlan deactivate() {
        return new EpsPlan(id, epsId, code, name, regimeCode, false);
    }
}
