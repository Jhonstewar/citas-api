package com.fcv.citas.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcv.citas.domain.shared.SystemZone;
import com.fcv.citas.domain.user.Role;
import com.fcv.citas.support.EpsTestData;
import com.fcv.citas.support.S3TestData;
import com.fcv.citas.support.TestTokens;

/**
 * HU-012: CRUD de EPS y planes para ADMIN (contrato S4, {@code /api/admin/eps} y
 * {@code /api/admin/eps-plans}), con borrado fisico solo de lo no referenciado (D28) y nombres
 * unicos (D33, V9).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EpsAdminIntegrationTest {

    private static final String EPS = "/api/admin/eps";
    private static final String PLANS = "/api/admin/eps-plans";
    private static final String CATALOG = "/api/catalogs/insurance-plans";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private S3TestData users;
    private EpsTestData eps;
    private String admin;
    private String patient;
    private long patientId;
    private String professional;

    @BeforeEach
    void setUp() {
        users = new S3TestData(jdbc, passwordEncoder.encode(S3TestData.PASSWORD));
        eps = new EpsTestData(jdbc);
        TestTokens tokens = new TestTokens(jwtEncoder);
        admin = tokens.bearer(users.user("admin", "ADMIN"), Role.ADMIN);
        patientId = users.user("patient", "USER");
        patient = tokens.bearer(patientId, Role.USER);
        professional = tokens.bearer(users.user("prof", "PROFESSIONAL"), Role.PROFESSIONAL);
    }

    @AfterEach
    void cleanUp() {
        users.cleanUp();
        eps.cleanUp();
    }

    // ------------------------------------------------------------------ ayudas

    private ResultActions call(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(request);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private JsonNode createEps(String code, String name) throws Exception {
        return body(call(post(EPS), admin, Map.of("code", code, "name", name)).andExpect(status().isCreated()));
    }

    private JsonNode createPlan(long epsId, String code, String name, String regime) throws Exception {
        return body(call(post(EPS + "/" + epsId + "/plans"), admin,
                Map.of("code", code, "name", name, "regimeCode", regime)).andExpect(status().isCreated()));
    }

    private List<Integer> catalogIds() throws Exception {
        List<Integer> ids = new ArrayList<>();
        json.readTree(mvc.perform(get(CATALOG)).andReturn().getResponse().getContentAsString())
                .forEach(p -> ids.add(p.get("id").asInt()));
        return ids;
    }

    private int planRows(int epsId) {
        return eps.count("SELECT COUNT(*) FROM eps_plans WHERE eps_id = ?", epsId);
    }

    // ------------------------------------------------------------------ CA-01

    /** CA-01: alta de EPS y de un plan con regimen del catalogo fijo; ambos activos y listados. */
    @Test
    void createsAnEpsAndAPlanAndListsThem() throws Exception {
        String code = EpsTestData.code();
        String name = EpsTestData.name("Alta");
        JsonNode created = createEps(code, name);
        long id = created.get("id").asLong();
        assertThat(created.get("code").asText()).isEqualTo(code);
        assertThat(created.get("name").asText()).isEqualTo(name);
        assertThat(created.get("active").asBoolean()).isTrue();
        assertThat(created.get("planCount").asInt()).isZero();

        String planCode = EpsTestData.code();
        JsonNode plan = createPlan(id, planCode, "Plan básico", "CONTRIBUTIVO");
        assertThat(plan.get("epsId").asLong()).isEqualTo(id);
        assertThat(plan.get("code").asText()).isEqualTo(planCode);
        assertThat(plan.get("name").asText()).isEqualTo("Plan básico");
        assertThat(plan.get("active").asBoolean()).isTrue();
        assertThat(plan.get("regime").get("code").asText()).isEqualTo("CONTRIBUTIVO");
        assertThat(plan.get("regime").get("name").asText()).isEqualTo("Régimen contributivo");
        assertThat(plan.get("regime").get("id").asInt()).isPositive();

        call(get(EPS + "/" + id + "/plans"), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(plan.get("id").asInt()));
        call(get(EPS + "/" + id), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.planCount").value(1))
                .andExpect(jsonPath("$.active").value(true));
        JsonNode all = body(call(get(EPS), admin, null).andExpect(status().isOk()));
        boolean listed = false;
        for (JsonNode e : all) {
            listed |= e.get("id").asLong() == id && e.get("planCount").asInt() == 1;
        }
        assertThat(listed).isTrue();
        assertThat(eps.count("SELECT COUNT(*) FROM eps WHERE id = ? AND active", id)).isOne();
        assertThat(eps.count("SELECT COUNT(*) FROM eps_plans WHERE id = ? AND active", plan.get("id").asInt()))
                .isOne();
        assertThat(catalogIds()).contains(plan.get("id").asInt());
    }

    /** Aclaracion 9: GET por id → 200 · 404; la lista trae activas e inactivas. */
    @Test
    void readsOneEpsAndListsInactiveOnesToo() throws Exception {
        int inactive = eps.eps("Inactiva", false);
        call(get(EPS + "/" + inactive), admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        call(get(EPS + "/" + Integer.MAX_VALUE), admin, null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        List<Long> ids = new ArrayList<>();
        body(call(get(EPS), admin, null)).forEach(e -> ids.add(e.get("id").asLong()));
        assertThat(ids).contains((long) inactive);
    }

    /** Editar: la EPS solo cambia de nombre; el plan cambia nombre y regimen. */
    @Test
    void updatesAnEpsAndAPlan() throws Exception {
        int epsId = eps.eps("Editar", true);
        int planId = eps.plan(epsId, "CONTRIBUTIVO", "Plan", true);
        String newName = EpsTestData.name("Editada");

        call(put(EPS + "/" + epsId), admin, Map.of("name", newName)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(newName))
                .andExpect(jsonPath("$.planCount").value(1));
        call(put(PLANS + "/" + planId), admin, Map.of("name", "Plan subsidiado", "regimeCode", "SUBSIDIADO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Plan subsidiado"))
                .andExpect(jsonPath("$.regime.code").value("SUBSIDIADO"))
                .andExpect(jsonPath("$.epsId").value(epsId));

        assertThat(jdbc.queryForObject("SELECT name FROM eps WHERE id = ?", String.class, epsId)).isEqualTo(newName);
        call(put(EPS + "/" + Integer.MAX_VALUE), admin, Map.of("name", "X")).andExpect(status().isNotFound());
        call(put(PLANS + "/" + Integer.MAX_VALUE), admin, Map.of("name", "X", "regimeCode", "SUBSIDIADO"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ unicidad (DoD, D33)

    /** DoD: codigo y nombre unicos de EPS (el nombre sin distinguir mayusculas ni tildes) → 409 DUPLICATE con field. */
    @Test
    void epsCodeAndNameAreUnique() throws Exception {
        String code = EpsTestData.code();
        String name = "IT_EPS Salud Única " + code;
        createEps(code, name);
        int before = eps.count("SELECT COUNT(*) FROM eps WHERE code LIKE 'IT_EPS_%'");

        call(post(EPS), admin, Map.of("code", code, "name", EpsTestData.name("Otra"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE")).andExpect(jsonPath("$.field").value("code"));
        call(post(EPS), admin, Map.of("code", EpsTestData.code(), "name", name.toUpperCase().replace('Ú', 'U')))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE")).andExpect(jsonPath("$.field").value("name"));
        int other = eps.eps("Otra", true);
        call(put(EPS + "/" + other), admin, Map.of("name", name)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.field").value("name"));

        assertThat(eps.count("SELECT COUNT(*) FROM eps WHERE code LIKE 'IT_EPS_%'")).isEqualTo(before + 1);
    }

    /** DoD: codigo y nombre de plan unicos DENTRO de su EPS; en otra EPS se pueden repetir. */
    @Test
    void planCodeAndNameAreUniqueWithinTheirEps() throws Exception {
        int epsA = eps.eps("A", true);
        int epsB = eps.eps("B", true);
        String code = EpsTestData.code();
        createPlan(epsA, code, "Plan Oro", "CONTRIBUTIVO");

        call(post(EPS + "/" + epsA + "/plans"), admin,
                Map.of("code", code, "name", "Plan Plata", "regimeCode", "CONTRIBUTIVO"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.field").value("code"));
        call(post(EPS + "/" + epsA + "/plans"), admin,
                Map.of("code", EpsTestData.code(), "name", "PLAN ORO", "regimeCode", "SUBSIDIADO"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE"))
                .andExpect(jsonPath("$.field").value("name"));
        int silver = eps.plan(epsA, "CONTRIBUTIVO", "Plata", true);
        call(put(PLANS + "/" + silver), admin, Map.of("name", "Plan Oro", "regimeCode", "CONTRIBUTIVO"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.field").value("name"));

        createPlan(epsB, code, "Plan Oro", "CONTRIBUTIVO");
        assertThat(planRows(epsA)).isEqualTo(2);
        assertThat(planRows(epsB)).isOne();
    }

    // ------------------------------------------------------------------ CA-02

    /** CA-02: sin regimen, regimen fuera del catalogo fijo, sin codigo o sin nombre → 400 con el campo; nada persiste. */
    @Test
    void aPlanNeedsAKnownRegimeAndItsFields() throws Exception {
        int epsId = eps.eps("Regimen", true);

        call(post(EPS + "/" + epsId + "/plans"), admin, Map.of("code", EpsTestData.code(), "name", "Plan"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.regimeCode").exists());
        call(post(EPS + "/" + epsId + "/plans"), admin,
                Map.of("code", EpsTestData.code(), "name", "Plan", "regimeCode", "INVENTADO"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.fieldErrors.regimeCode").exists());
        call(post(EPS + "/" + epsId + "/plans"), admin, Map.of("name", "Plan", "regimeCode", "CONTRIBUTIVO"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.code").exists());
        call(post(EPS + "/" + epsId + "/plans"), admin, Map.of("code", EpsTestData.code(), "regimeCode", "CONTRIBUTIVO"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.name").exists());
        int plan = eps.plan(epsId, "CONTRIBUTIVO", "Existente", true);
        call(put(PLANS + "/" + plan), admin, Map.of("name", "Plan", "regimeCode", "INVENTADO"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.regimeCode").exists());

        assertThat(planRows(epsId)).isOne();
    }

    /** CA-02: la EPS del plan es la de la ruta; si no existe → 404 y no se crea nada. */
    @Test
    void aPlanOfAMissingEpsIsNotFound() throws Exception {
        int before = eps.count("SELECT COUNT(*) FROM eps_plans");
        call(post(EPS + "/" + Integer.MAX_VALUE + "/plans"), admin,
                Map.of("code", EpsTestData.code(), "name", "Plan", "regimeCode", "CONTRIBUTIVO"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        call(get(EPS + "/" + Integer.MAX_VALUE + "/plans"), admin, null).andExpect(status().isNotFound());
        assertThat(eps.count("SELECT COUNT(*) FROM eps_plans")).isEqualTo(before);
    }

    @Test
    void anEpsNeedsCodeAndName() throws Exception {
        call(post(EPS), admin, Map.of("name", EpsTestData.name("Sin codigo"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.code").exists());
        call(post(EPS), admin, Map.of("code", EpsTestData.code(), "name", " ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
        call(post(EPS), admin, Map.of("code", "X".repeat(21), "name", "Larga")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.code").exists());
    }

    // ------------------------------------------------------------------ CA-03 y CA-09 (D28)

    /** CA-03: EPS con planes → 409 EPS_REFERENCED; plan con afiliaciones (incluso cerradas) → 409 PLAN_REFERENCED. */
    @Test
    void referencedEpsAndPlansAreNotDeleted() throws Exception {
        int epsId = eps.eps("Referenciada", true);
        int current = eps.plan(epsId, "CONTRIBUTIVO", "Vigente", true);
        int closed = eps.plan(epsId, "SUBSIDIADO", "Cerrada", true);
        LocalDate today = LocalDate.now(SystemZone.ZONE);
        eps.affiliation(patientId, closed, today.minusDays(10), today.minusDays(1));
        eps.affiliation(patientId, current, today, null);

        call(delete(PLANS + "/" + current), admin, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_REFERENCED"));
        call(delete(PLANS + "/" + closed), admin, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_REFERENCED"));
        call(delete(EPS + "/" + epsId), admin, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EPS_REFERENCED"));

        assertThat(planRows(epsId)).isEqualTo(2);
        assertThat(eps.count("SELECT COUNT(*) FROM eps WHERE id = ?", epsId)).isOne();
        // La via que queda es desactivar.
        call(patch(PLANS + "/" + current + "/status"), admin, Map.of("active", false)).andExpect(status().isOk());
        call(patch(EPS + "/" + epsId + "/status"), admin, Map.of("active", false)).andExpect(status().isOk());
    }

    /** CA-09: plan sin afiliaciones y EPS sin planes se borran fisicamente y desaparecen de los listados. */
    @Test
    void unreferencedPlansAndEpsAreDeletedPhysically() throws Exception {
        int epsId = eps.eps("Borrable", true);
        int plan = eps.plan(epsId, "CONTRIBUTIVO", "Borrable", true);

        call(delete(PLANS + "/" + plan), admin, null).andExpect(status().isNoContent());
        assertThat(eps.count("SELECT COUNT(*) FROM eps_plans WHERE id = ?", plan)).isZero();
        call(get(EPS + "/" + epsId + "/plans"), admin, null).andExpect(jsonPath("$.length()").value(0));

        call(delete(EPS + "/" + epsId), admin, null).andExpect(status().isNoContent());
        assertThat(eps.count("SELECT COUNT(*) FROM eps WHERE id = ?", epsId)).isZero();
        call(get(EPS + "/" + epsId), admin, null).andExpect(status().isNotFound());
        List<Long> ids = new ArrayList<>();
        body(call(get(EPS), admin, null)).forEach(e -> ids.add(e.get("id").asLong()));
        assertThat(ids).doesNotContain((long) epsId);

        call(delete(EPS + "/" + epsId), admin, null).andExpect(status().isNotFound());
        call(delete(PLANS + "/" + plan), admin, null).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ CA-04, CA-05, CA-06

    /** CA-04: un plan desactivado sale del catalogo y la API rechaza afiliarse a el. CA-06: al reactivarlo vuelve. */
    @Test
    void aDeactivatedPlanIsNoLongerOfferedUntilReactivated() throws Exception {
        int epsId = eps.eps("Oferta", true);
        int plan = eps.plan(epsId, "CONTRIBUTIVO", "Oferta", true);
        assertThat(catalogIds()).contains(plan);

        call(patch(PLANS + "/" + plan + "/status"), admin, Map.of("active", false)).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        assertThat(catalogIds()).doesNotContain(plan);
        call(put("/api/me/affiliation"), patient, Map.of("insurancePlanId", plan))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSURANCE_PLAN_UNAVAILABLE"));
        assertThat(eps.count("SELECT COUNT(*) FROM affiliations WHERE user_id = ?", patientId)).isZero();

        call(patch(PLANS + "/" + plan + "/status"), admin, Map.of("active", true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        assertThat(catalogIds()).contains(plan);
    }

    /**
     * CA-05: desactivar la EPS retira sus planes de la oferta sin borrar nada, y las afiliaciones ya
     * declaradas siguen mostrando su EPS y su plan. CA-06: reactivarla los devuelve a la oferta.
     */
    @Test
    void deactivatingAnEpsWithdrawsItsPlansButKeepsAffiliations() throws Exception {
        int epsId = eps.eps("Retirada", true);
        int planA = eps.plan(epsId, "CONTRIBUTIVO", "A", true);
        int planB = eps.plan(epsId, "SUBSIDIADO", "B", true);
        eps.affiliation(patientId, planA, LocalDate.now(SystemZone.ZONE), null);

        call(patch(EPS + "/" + epsId + "/status"), admin, Map.of("active", false)).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.planCount").value(2));

        assertThat(catalogIds()).doesNotContain(planA, planB);
        assertThat(planRows(epsId)).isEqualTo(2);
        // Los planes conservan su propio estado: desactivar la EPS no los toca.
        assertThat(eps.count("SELECT COUNT(*) FROM eps_plans WHERE eps_id = ? AND active", epsId)).isEqualTo(2);
        assertThat(eps.count("SELECT COUNT(*) FROM affiliations WHERE user_id = ? AND is_current", patientId)).isOne();
        call(get("/api/me"), patient, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.affiliation.plan.id").value(planA))
                .andExpect(jsonPath("$.affiliation.plan.eps.id").value(epsId))
                .andExpect(jsonPath("$.affiliation.plan.regime.code").value("CONTRIBUTIVO"));
        call(put("/api/me/affiliation"), patient, Map.of("insurancePlanId", planB))
                .andExpect(status().isUnprocessableEntity());

        call(patch(EPS + "/" + epsId + "/status"), admin, Map.of("active", true)).andExpect(status().isOk());
        assertThat(catalogIds()).contains(planA, planB);
    }

    @Test
    void theStatusBodyIsValidated() throws Exception {
        int epsId = eps.eps("Estado", true);
        call(patch(EPS + "/" + epsId + "/status"), admin, Map.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.active").exists());
        call(patch(EPS + "/" + Integer.MAX_VALUE + "/status"), admin, Map.of("active", false))
                .andExpect(status().isNotFound());
        call(patch(PLANS + "/" + Integer.MAX_VALUE + "/status"), admin, Map.of("active", false))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ CA-07

    /** CA-07: USER y PROFESSIONAL reciben 403 en todo el CRUD y nada cambia; ADMIN si opera. */
    @Test
    void onlyAdminsManageEpsAndPlans() throws Exception {
        int epsId = eps.eps("Roles", true);
        int plan = eps.plan(epsId, "CONTRIBUTIVO", "Roles", true);
        Map<String, Object> planBody = Map.of("code", EpsTestData.code(), "name", "P", "regimeCode", "CONTRIBUTIVO");

        for (String token : new String[] { patient, professional }) {
            call(get(EPS), token, null).andExpect(status().isForbidden());
            call(get(EPS + "/" + epsId), token, null).andExpect(status().isForbidden());
            call(post(EPS), token, Map.of("code", EpsTestData.code(), "name", "X")).andExpect(status().isForbidden());
            call(put(EPS + "/" + epsId), token, Map.of("name", "X")).andExpect(status().isForbidden());
            call(patch(EPS + "/" + epsId + "/status"), token, Map.of("active", false))
                    .andExpect(status().isForbidden());
            call(delete(EPS + "/" + epsId), token, null).andExpect(status().isForbidden());
            call(get(EPS + "/" + epsId + "/plans"), token, null).andExpect(status().isForbidden());
            call(post(EPS + "/" + epsId + "/plans"), token, planBody).andExpect(status().isForbidden());
            call(put(PLANS + "/" + plan), token, Map.of("name", "X", "regimeCode", "SUBSIDIADO"))
                    .andExpect(status().isForbidden());
            call(patch(PLANS + "/" + plan + "/status"), token, Map.of("active", false))
                    .andExpect(status().isForbidden());
            call(delete(PLANS + "/" + plan), token, null).andExpect(status().isForbidden());
        }
        mvc.perform(get(EPS)).andExpect(status().isUnauthorized());

        assertThat(eps.count("SELECT COUNT(*) FROM eps WHERE id = ? AND active", epsId)).isOne();
        assertThat(eps.count("SELECT COUNT(*) FROM eps_plans WHERE id = ? AND active", plan)).isOne();
        assertThat(planRows(epsId)).isOne();

        call(patch(EPS + "/" + epsId + "/status"), admin, Map.of("active", false)).andExpect(status().isOk());
        call(post(EPS + "/" + epsId + "/plans"), admin, planBody).andExpect(status().isCreated());
    }
}
