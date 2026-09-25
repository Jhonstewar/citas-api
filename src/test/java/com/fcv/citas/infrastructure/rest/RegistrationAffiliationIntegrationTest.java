package com.fcv.citas.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcv.citas.domain.shared.SystemZone;

/**
 * HU-009 acotada a la ruta de registro: afiliacion OPCIONAL en {@code POST /api/auth/register} y
 * catalogo publico {@code GET /api/catalogs/insurance-plans}.
 *
 * <p>Siembra su propia EPS y sus planes con el prefijo {@code IT_HU009_} y usa correos del dominio
 * {@code hu009.fcv.test}; {@link #cleanUp()} borra ambas cosas.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationAffiliationIntegrationTest {

    private static final String DOMAIN = "@hu009.fcv.test";
    private static final String PREFIX = "IT_HU009_";
    private static final String PASSWORD = "Clave-Secreta#2026";
    private static final String PLANS_PATH = "/api/catalogs/insurance-plans";
    private static final String UNAVAILABLE = "INSURANCE_PLAN_UNAVAILABLE";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    private int activePlanA;
    private int activePlanB;
    private int inactivePlan;
    private int planOfInactiveEps;

    @BeforeEach
    void seedCatalog() {
        cleanUp();
        int activeEps = eps("A", "IT_HU009 Alfa EPS", true);
        int inactiveEps = eps("Z", "IT_HU009 Zeta EPS", false);
        // Se insertan en orden inverso al esperado para que la prueba del orden signifique algo.
        activePlanB = plan(activeEps, "SUBSIDIADO", "P_B", "IT_HU009 Plan B", true);
        activePlanA = plan(activeEps, "CONTRIBUTIVO", "P_A", "IT_HU009 Plan A", true);
        inactivePlan = plan(activeEps, "CONTRIBUTIVO", "P_OFF", "IT_HU009 Plan desactivado", false);
        planOfInactiveEps = plan(inactiveEps, "CONTRIBUTIVO", "P_ZZ", "IT_HU009 Plan de EPS inactiva", true);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM affiliations WHERE eps_plan_id IN (SELECT id FROM eps_plans WHERE code LIKE ?)",
                PREFIX + "%");
        // Las afiliaciones de estos usuarios caen por ON DELETE CASCADE.
        jdbc.update("DELETE FROM users WHERE email LIKE ?", "%" + DOMAIN);
        jdbc.update("DELETE FROM eps_plans WHERE code LIKE ?", PREFIX + "%");
        jdbc.update("DELETE FROM eps WHERE code LIKE ?", PREFIX + "%");
    }

    // ------------------------------------------------------------------ registro sin plan

    @Test
    void registerWithoutPlanKeepsWorkingAndCreatesNoAffiliation() throws Exception {
        long absent = registeredId(body(null, false));
        Map<String, Object> explicitNull = body(null, true);
        long withNull = registeredId(explicitNull);

        assertThat(explicitNull).containsKey("insurancePlanId");
        assertThat(affiliationCount(absent)).isZero();
        assertThat(affiliationCount(withNull)).isZero();
    }

    @Test
    void registerWithActivePlanCreatesCurrentAffiliationStartingToday() throws Exception {
        long userId = registeredId(body(activePlanA, true));

        assertThat(jdbc.queryForObject("SELECT eps_plan_id FROM affiliations WHERE user_id = ?", Integer.class,
                userId)).isEqualTo(activePlanA);
        // Se comprueba el valor almacenado (1), no la interpretacion del driver.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM affiliations WHERE user_id = ? AND is_current = 1",
                Integer.class, userId)).isOne();
        Date startedOn = jdbc.queryForObject("SELECT started_on FROM affiliations WHERE user_id = ?", Date.class,
                userId);
        assertThat(startedOn.toLocalDate()).isEqualTo(LocalDate.now(SystemZone.ZONE));
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT ended_on, membership_number FROM affiliations WHERE user_id = ?", userId);
        assertThat(row.get("ended_on")).isNull();
        assertThat(row.get("membership_number")).isNull();
    }

    /** La forma de la respuesta de registro no cambia por llevar plan (o no llevarlo). */
    @Test
    void registerResponseShapeDoesNotChange() throws Exception {
        JsonNode withPlan = registered(body(activePlanA, true));
        JsonNode withoutPlan = registered(body(null, false));

        assertThat(fieldNames(withPlan)).isEqualTo(fieldNames(withoutPlan));
        assertThat(fieldNames(withPlan)).doesNotContain("insurancePlanId", "affiliation");
    }

    // ------------------------------------------------------------------ plan no seleccionable

    @Test
    void registerWithUnknownPlanIsRejectedAndCreatesNoUser() throws Exception {
        assertRejected(999_999);
    }

    @Test
    void registerWithInactivePlanIsRejectedAndCreatesNoUser() throws Exception {
        assertRejected(inactivePlan);
    }

    @Test
    void registerWithPlanOfInactiveEpsIsRejectedAndCreatesNoUser() throws Exception {
        assertRejected(planOfInactiveEps);
    }

    /** Los tres casos responden lo mismo: la API no revela si el plan existe. */
    @Test
    void allUnavailablePlansShareTheSameAnswer() throws Exception {
        String unknown = detailOfRejection(999_999);
        String inactive = detailOfRejection(inactivePlan);
        String inactiveEps = detailOfRejection(planOfInactiveEps);

        assertThat(inactive).isEqualTo(unknown);
        assertThat(inactiveEps).isEqualTo(unknown);
    }

    // ------------------------------------------------------------------ catalogo publico

    @Test
    void insurancePlansIsPublicAndListsOnlyActivePlansOfActiveEps() throws Exception {
        String response = mvc.perform(get(PLANS_PATH))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        List<Integer> ids = new ArrayList<>();
        for (JsonNode plan : json.readTree(response)) {
            if (plan.get("code").asText().startsWith(PREFIX)) {
                ids.add(plan.get("id").asInt());
            }
        }
        assertThat(ids).containsExactly(activePlanA, activePlanB)
                .doesNotContain(inactivePlan, planOfInactiveEps);
    }

    @Test
    void insurancePlanExposesItsEpsAndRegimeDerivedFromThePlan() throws Exception {
        JsonNode plan = planFromCatalog(activePlanA);

        assertThat(plan.get("code").asText()).isEqualTo(PREFIX + "P_A");
        assertThat(plan.get("name").asText()).isEqualTo("IT_HU009 Plan A");
        assertThat(plan.get("eps").get("code").asText()).isEqualTo(PREFIX + "A");
        assertThat(plan.get("eps").get("name").asText()).isEqualTo("IT_HU009 Alfa EPS");
        assertThat(plan.get("eps").get("id").asInt()).isPositive();
        assertThat(plan.get("regime").get("code").asText()).isEqualTo("CONTRIBUTIVO");
        assertThat(plan.get("regime").get("name").asText()).isEqualTo("Régimen contributivo");
        assertThat(plan.get("regime").get("id").asInt()).isPositive();
    }

    /** La ruta es publica para TODO metodo: una escritura da 405, no 401 (HU-010 CA-06). */
    @Test
    void writeMethodsOnInsurancePlansAnswer405WithoutToken() throws Exception {
        mvc.perform(post(PLANS_PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(put(PLANS_PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete(PLANS_PATH)).andExpect(status().isMethodNotAllowed());
    }

    /** El resto de catalogos sigue exigiendo token: la excepcion es solo esta ruta. */
    @Test
    void otherCatalogsStillRequireAToken() throws Exception {
        mvc.perform(get("/api/catalogs/regimes")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ helpers

    private void assertRejected(int planId) throws Exception {
        Map<String, Object> body = body(planId, true);

        register(body)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(UNAVAILABLE));

        assertThat(userCount((String) body.get("email"))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM affiliations WHERE eps_plan_id = ?", Integer.class,
                planId)).isZero();
    }

    private String detailOfRejection(int planId) throws Exception {
        String response = register(body(planId, true)).andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();
        JsonNode problem = json.readTree(response);
        assertThat(problem.get("code").asText()).isEqualTo(UNAVAILABLE);
        return problem.get("detail").asText();
    }

    private JsonNode planFromCatalog(int planId) throws Exception {
        String response = mvc.perform(get(PLANS_PATH)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode plan : json.readTree(response)) {
            if (plan.get("id").asInt() == planId) {
                return plan;
            }
        }
        throw new AssertionError("El plan sembrado no aparece en el catalogo publico");
    }

    private List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private ResultActions register(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private JsonNode registered(Map<String, Object> body) throws Exception {
        String response = register(body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private long registeredId(Map<String, Object> body) throws Exception {
        return registered(body).get("id").asLong();
    }

    private Map<String, Object> body(Integer insurancePlanId, boolean includePlanKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstNames", "Ana María");
        body.put("lastNames", "Pérez Gómez");
        body.put("documentType", "CC");
        body.put("documentNumber", "HU9" + ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        body.put("email", "it-" + UUID.randomUUID().toString().substring(0, 8) + DOMAIN);
        body.put("phone", "3001234567");
        body.put("password", PASSWORD);
        if (includePlanKey) {
            body.put("insurancePlanId", insurancePlanId);
        }
        return body;
    }

    private int affiliationCount(long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM affiliations WHERE user_id = ?", Integer.class, userId);
    }

    private int userCount(String email) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
    }

    private int eps(String suffix, String name, boolean active) {
        jdbc.update("INSERT INTO eps (code, name, active) VALUES (?, ?, ?)", PREFIX + suffix, name, active);
        return jdbc.queryForObject("SELECT id FROM eps WHERE code = ?", Integer.class, PREFIX + suffix);
    }

    private int plan(int epsId, String regime, String suffix, String name, boolean active) {
        jdbc.update("""
                INSERT INTO eps_plans (eps_id, regime_id, code, name, active)
                VALUES (?, (SELECT id FROM regimes WHERE code = ?), ?, ?, ?)
                """, epsId, regime, PREFIX + suffix, name, active);
        return jdbc.queryForObject("SELECT id FROM eps_plans WHERE eps_id = ? AND code = ?", Integer.class, epsId,
                PREFIX + suffix);
    }
}
