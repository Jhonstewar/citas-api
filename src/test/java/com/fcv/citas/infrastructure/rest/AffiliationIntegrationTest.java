package com.fcv.citas.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcv.citas.domain.affiliation.AffiliationRepository;
import com.fcv.citas.domain.shared.SystemZone;
import com.fcv.citas.domain.user.Role;
import com.fcv.citas.support.EpsTestData;
import com.fcv.citas.support.S3TestData;
import com.fcv.citas.support.TestTokens;

/**
 * HU-009, segundo corte: afiliacion desde el perfil ({@code PUT/DELETE /api/me/affiliation}), con
 * una sola vigente y el historial de las cerradas (D26, D32).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AffiliationIntegrationTest {

    private static final String AFFILIATION = "/api/me/affiliation";

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
    /** Espia sobre el adaptador real: solo se altera en la prueba de atomicidad. */
    @MockitoSpyBean
    private AffiliationRepository affiliationRepository;

    private S3TestData users;
    private EpsTestData eps;
    private TestTokens tokens;
    private long userId;
    private String user;
    private long otherId;
    private String other;
    private int epsId;
    private int planA;
    private int planB;
    private int inactivePlan;
    private int planOfInactiveEps;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        users = new S3TestData(jdbc, passwordEncoder.encode(S3TestData.PASSWORD));
        eps = new EpsTestData(jdbc);
        tokens = new TestTokens(jwtEncoder);
        userId = users.user("ana", "USER");
        user = tokens.bearer(userId, Role.USER);
        otherId = users.user("otro", "USER");
        other = tokens.bearer(otherId, Role.USER);
        epsId = eps.eps("Afiliacion", true);
        planA = eps.plan(epsId, "CONTRIBUTIVO", "A", true);
        planB = eps.plan(epsId, "SUBSIDIADO", "B", true);
        inactivePlan = eps.plan(epsId, "CONTRIBUTIVO", "Inactivo", false);
        planOfInactiveEps = eps.plan(eps.eps("Inactiva", false), "CONTRIBUTIVO", "De EPS inactiva", true);
        today = LocalDate.now(SystemZone.ZONE);
    }

    @AfterEach
    void cleanUp() {
        users.cleanUp();
        eps.cleanUp();
    }

    // ------------------------------------------------------------------ ayudas

    private ResultActions set(String token, Object planId) throws Exception {
        return mvc.perform(put(AFFILIATION).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(planId == null ? Map.of() : Map.of("insurancePlanId", planId))));
    }

    private ResultActions remove(String token) throws Exception {
        return mvc.perform(delete(AFFILIATION).header(HttpHeaders.AUTHORIZATION, token));
    }

    private List<Map<String, Object>> rows(long owner) {
        return jdbc.queryForList("SELECT id, eps_plan_id, is_current, started_on, ended_on FROM affiliations"
                + " WHERE user_id = ? ORDER BY id", owner);
    }

    private int current(long owner) {
        return eps.count("SELECT COUNT(*) FROM affiliations WHERE user_id = ? AND is_current", owner);
    }

    private static LocalDate date(Object value) {
        return value == null ? null : ((Date) value).toLocalDate();
    }

    // ------------------------------------------------------------------ CA-01, CA-02

    /** CA-01 y CA-02: sin afiliacion, se registra; la respuesta trae plan, EPS y regimen derivados. */
    @Test
    void registersTheFirstAffiliation() throws Exception {
        long id = json.readTree(set(user, planA).andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.id").value(planA))
                .andExpect(jsonPath("$.plan.code").exists())
                .andExpect(jsonPath("$.plan.eps.id").value(epsId))
                .andExpect(jsonPath("$.plan.regime.code").value("CONTRIBUTIVO"))
                .andExpect(jsonPath("$.startedOn").value(today.toString()))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();

        List<Map<String, Object>> rows = rows(userId);
        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.get(0).get("id")).longValue()).isEqualTo(id);
        assertThat(rows.get(0).get("eps_plan_id")).isEqualTo(planA);
        assertThat(date(rows.get(0).get("started_on"))).isEqualTo(today);
        assertThat(rows.get(0).get("ended_on")).isNull();
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(jsonPath("$.affiliation.id").value(id))
                .andExpect(jsonPath("$.affiliation.plan.eps.id").value(epsId))
                .andExpect(jsonPath("$.affiliation.plan.regime.code").value("CONTRIBUTIVO"));
    }

    /** CA-02: la tabla solo referencia el plan; la EPS y el regimen no se copian (3FN). */
    @Test
    void theAffiliationStoresOnlyThePlan() {
        List<String> columns = jdbc.queryForList("SELECT COLUMN_NAME FROM information_schema.COLUMNS"
                + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'affiliations'", String.class);
        assertThat(columns).contains("eps_plan_id").doesNotContain("eps_id", "regime_id");
    }

    // ------------------------------------------------------------------ CA-03, CA-04

    /**
     * CA-03 con el contrato S4: repetir el plan vigente responde 200 con la misma afiliacion y NO crea
     * un segundo registro (aclaracion del contrato de identidad: "mismo plan = sin cambios").
     */
    @Test
    void theSamePlanAgainChangesNothing() throws Exception {
        long id = json.readTree(set(user, planA).andReturn().getResponse().getContentAsString()).get("id").asLong();

        set(user, planA).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.plan.id").value(planA));

        assertThat(rows(userId)).hasSize(1);
        assertThat(current(userId)).isOne();
    }

    /** CA-04 (V9): la base rechaza dos afiliaciones vigentes del mismo usuario al mismo plan. */
    @Test
    void theDatabaseRejectsTwoCurrentRowsForTheSamePlan() {
        eps.affiliation(userId, planA, today, null);

        assertThatThrownBy(() -> eps.affiliation(userId, planA, today, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Tampoco dos vigentes a planes distintos (uq_affiliations_user_current).
        assertThatThrownBy(() -> eps.affiliation(userId, planB, today, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Una cerrada y una vigente del mismo plan si conviven (D32).
        eps.affiliation(userId, planB, today.minusDays(5), today.minusDays(1));
        eps.affiliation(otherId, planB, today, null);
        assertThat(rows(userId)).hasSize(2);
    }

    // ------------------------------------------------------------------ CA-05, CA-06

    /** CA-06: plan desactivado, plan de EPS inactiva o inexistente → 422 INSURANCE_PLAN_UNAVAILABLE; nada se crea. */
    @Test
    void unavailablePlansAreRejected() throws Exception {
        for (int plan : new int[] { inactivePlan, planOfInactiveEps, Integer.MAX_VALUE }) {
            set(user, plan).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("INSURANCE_PLAN_UNAVAILABLE"));
        }
        set(user, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.insurancePlanId").exists());
        assertThat(rows(userId)).isEmpty();
    }

    /** CA-05: el catalogo que alimenta la seleccion contiene el activo y no el desactivado. */
    @Test
    void theCatalogOffersOnlyActivePlans() throws Exception {
        String raw = mvc.perform(get("/api/catalogs/insurance-plans")).andReturn().getResponse().getContentAsString();
        List<Integer> ids = new java.util.ArrayList<>();
        json.readTree(raw).forEach(p -> ids.add(p.get("id").asInt()));
        assertThat(ids).contains(planA, planB).doesNotContain(inactivePlan, planOfInactiveEps);
    }

    /** Cambiar a un plan no ofrecible deja intacta la afiliacion vigente. */
    @Test
    void aRejectedChangeKeepsTheCurrentAffiliation() throws Exception {
        set(user, planA).andExpect(status().isOk());
        set(user, inactivePlan).andExpect(status().isUnprocessableEntity());
        assertThat(rows(userId)).hasSize(1);
        assertThat(rows(userId).get(0).get("eps_plan_id")).isEqualTo(planA);
        assertThat(current(userId)).isOne();
    }

    // ------------------------------------------------------------------ CA-07

    /** CA-07: no hay ruta a la afiliacion de otro; lo que hace un usuario nunca toca la del otro. */
    @Test
    void aUserNeverTouchesAnotherUsersAffiliation() throws Exception {
        set(other, planB).andExpect(status().isOk());
        List<Map<String, Object>> before = rows(otherId);

        mvc.perform(get("/api/users/" + otherId + "/affiliation").header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/users/" + otherId + "/affiliation").header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isForbidden());
        mvc.perform(put(AFFILIATION).header(HttpHeaders.AUTHORIZATION, user).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("insurancePlanId", planA, "userId", otherId))))
                .andExpect(status().isOk());
        remove(user).andExpect(status().isNoContent());

        assertThat(rows(otherId)).isEqualTo(before);
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(jsonPath("$.affiliation.plan.id").value(planB));
    }

    /** Solo USER se afilia (contrato S4); PROFESSIONAL y ADMIN → 403, sin token → 401. */
    @Test
    void onlyUsersManageTheirAffiliation() throws Exception {
        String professional = tokens.bearer(users.user("prof", "PROFESSIONAL"), Role.PROFESSIONAL);
        String admin = tokens.bearer(users.user("admin", "ADMIN"), Role.ADMIN);
        for (String token : new String[] { professional, admin }) {
            set(token, planA).andExpect(status().isForbidden());
            remove(token).andExpect(status().isForbidden());
        }
        mvc.perform(put(AFFILIATION).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("insurancePlanId", planA))))
                .andExpect(status().isUnauthorized());
        assertThat(eps.count("SELECT COUNT(*) FROM affiliations WHERE eps_plan_id = ?", planA)).isZero();
    }

    // ------------------------------------------------------------------ CA-09, CA-10, D32

    /** CA-09: cambiar de A a B cierra A con ended_on = hoy y deja B como unica vigente. */
    @Test
    void changingThePlanClosesTheCurrentOne() throws Exception {
        long old = eps.affiliation(userId, planA, today.minusDays(30), null);

        set(user, planB).andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.id").value(planB))
                .andExpect(jsonPath("$.plan.regime.code").value("SUBSIDIADO"))
                .andExpect(jsonPath("$.startedOn").value(today.toString()));

        List<Map<String, Object>> rows = rows(userId);
        assertThat(rows).hasSize(2);
        Map<String, Object> closed = rows.get(0);
        assertThat(((Number) closed.get("id")).longValue()).isEqualTo(old);
        assertThat(closed.get("is_current")).isEqualTo(false);
        assertThat(date(closed.get("ended_on"))).isEqualTo(today);
        assertThat(date(closed.get("started_on"))).isEqualTo(today.minusDays(30));
        assertThat(current(userId)).isOne();
        assertThat(rows.get(1).get("eps_plan_id")).isEqualTo(planB);
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(jsonPath("$.affiliation.plan.id").value(planB))
                .andExpect(jsonPath("$.affiliation.plan.eps.id").value(epsId))
                .andExpect(jsonPath("$.affiliation.plan.regime.code").value("SUBSIDIADO"));
    }

    /** D32: volver a un plan ya usado funciona y conserva el historial (A → B → A, el mismo dia). */
    @Test
    void returningToAPreviouslyUsedPlanWorks() throws Exception {
        set(user, planA).andExpect(status().isOk());
        set(user, planB).andExpect(status().isOk());
        set(user, planA).andExpect(status().isOk()).andExpect(jsonPath("$.plan.id").value(planA));

        List<Map<String, Object>> rows = rows(userId);
        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(r -> r.get("eps_plan_id")).containsExactly(planA, planB, planA);
        assertThat(current(userId)).isOne();
        assertThat(rows.get(2).get("is_current")).isEqualTo(true);
    }

    /** CA-10: quitarla la cierra sin reemplazo; el registro sigue. Sin vigente, DELETE tambien es 204. */
    @Test
    void removingClosesItWithoutReplacement() throws Exception {
        set(user, planA).andExpect(status().isOk());

        remove(user).andExpect(status().isNoContent());

        List<Map<String, Object>> rows = rows(userId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("is_current")).isEqualTo(false);
        assertThat(date(rows.get(0).get("ended_on"))).isEqualTo(today);
        assertThat(current(userId)).isZero();
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.affiliation").doesNotExist());

        remove(user).andExpect(status().isNoContent());
        assertThat(rows(userId)).hasSize(1);
    }

    /** Cerrar la vigente y abrir la nueva es atomico: si el alta falla, la anterior sigue vigente. */
    @Test
    void aFailureOpeningTheNewAffiliationKeepsTheOldOne() throws Exception {
        set(user, planA).andExpect(status().isOk());
        doThrow(new IllegalStateException("fallo simulado de persistencia"))
                .when(affiliationRepository).saveNew(any());

        set(user, planB).andExpect(status().isInternalServerError());

        List<Map<String, Object>> rows = rows(userId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("eps_plan_id")).isEqualTo(planA);
        assertThat(rows.get(0).get("is_current")).isEqualTo(true);
        assertThat(rows.get(0).get("ended_on")).isNull();
    }
}
