package com.fcv.citas.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
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
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcv.citas.domain.appointment.AppointmentStatus;
import com.fcv.citas.domain.catalog.AppointmentType;
import com.fcv.citas.domain.catalog.Specialty;
import com.fcv.citas.domain.catalog.SpecialtyRepository;
import com.fcv.citas.domain.shared.DuplicateValueException;
import com.fcv.citas.domain.shared.SystemZone;
import com.fcv.citas.domain.user.Role;
import com.fcv.citas.support.S3TestData;
import com.fcv.citas.support.TestTokens;

/**
 * Deuda de S3 cerrada en S4 (F2): nombre de especialidad unico en la BD (R1, HU-011), activar y
 * desactivar profesional sin tocar sus citas (R2, HU-016) y una sola verdad sobre que estados
 * liberan slots (R5).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class S3DebtIntegrationTest {

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
    @Autowired
    private SpecialtyRepository specialties;

    private S3TestData data;
    private TestTokens tokens;

    @BeforeEach
    void setUp() {
        data = new S3TestData(jdbc, passwordEncoder.encode(S3TestData.PASSWORD));
        tokens = new TestTokens(jwtEncoder);
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    // ------------------------------------------------------------------ R1 · HU-011

    private void insertSpecialty(String name) {
        jdbc.update("""
                INSERT INTO specialties (appointment_type_id, code, name, duration_minutes, active)
                VALUES ((SELECT id FROM appointment_types WHERE code = 'SPECIALIZED'), ?, ?, 30, TRUE)
                """, S3TestData.uniqueCode(S3TestData.SPECIALTY_PREFIX), name);
    }

    /**
     * HU-011 DoD (V8): la BD impide dos especialidades con el mismo nombre aunque se salte el caso de
     * uso. La collation {@code utf8mb4_0900_ai_ci} hace la comparacion insensible a mayusculas y tildes.
     */
    @Test
    void theDatabaseRejectsADuplicateSpecialtyNameEvenBypassingTheUseCase() {
        String suffix = S3TestData.uniqueCode("");
        insertSpecialty("Cardiología " + suffix);

        assertThatThrownBy(() -> insertSpecialty("Cardiología " + suffix))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertSpecialty("CARDIOLOGIA " + suffix.toLowerCase()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(data.count("SELECT COUNT(*) FROM specialties WHERE name = ?", "Cardiología " + suffix))
                .isEqualTo(1);
    }

    /** La violacion de la unica se traduce a 409 DUPLICATE con field=name (carrera entre dos altas). */
    @Test
    void theAdapterTranslatesTheNameConstraintToDuplicateName() {
        String name = "Neumología " + S3TestData.uniqueCode("");
        insertSpecialty(name);

        Specialty sameName = Specialty.create(S3TestData.uniqueCode(S3TestData.SPECIALTY_PREFIX),
                name.toUpperCase(), AppointmentType.SPECIALIZED, 30);
        assertThatThrownBy(() -> specialties.save(sameName))
                .isInstanceOfSatisfying(DuplicateValueException.class, e -> {
                    assertThat(e.code()).isEqualTo("DUPLICATE");
                    assertThat(e.field()).isEqualTo("name");
                });
    }

    /** El codigo duplicado sigue traduciendose a field=code. */
    @Test
    void theAdapterStillTranslatesADuplicateCode() {
        int existing = data.specialty("SPECIALIZED", 30);
        String code = jdbc.queryForObject("SELECT code FROM specialties WHERE id = ?", String.class, existing);

        Specialty sameCode = Specialty.create(code, "Otra " + S3TestData.uniqueCode(""),
                AppointmentType.SPECIALIZED, 30);
        assertThatThrownBy(() -> specialties.save(sameCode))
                .isInstanceOfSatisfying(DuplicateValueException.class, e -> assertThat(e.field()).isEqualTo("code"));
    }

    // ------------------------------------------------------------------ R2 · HU-016

    /**
     * HU-016 CA-05: desactivar (y reactivar) al profesional no cambia el estado de ninguna cita, no
     * libera reservas y no borra historial.
     */
    @Test
    void deactivatingAProfessionalKeepsItsAppointmentsReservationsAndHistory() throws Exception {
        int hic = data.siteId("HIC");
        int specialty = data.specialty("SPECIALIZED", 30);
        S3TestData.Professional pro = data.professional("keep", new int[] { specialty }, hic);
        long patient = data.user("patient", "USER");
        LocalDate day = LocalDate.now(SystemZone.ZONE).plusDays(3);
        long block = data.block(pro.id(), hic, day, "08:00", "09:00");

        long approved = data.appointment(patient, pro.id(), hic, specialty, day, LocalTime.of(8, 0),
                LocalTime.of(8, 30), "APPROVED");
        long requested = data.appointment(patient, pro.id(), hic, specialty, day, LocalTime.of(8, 30),
                LocalTime.of(9, 0), "REQUESTED");
        data.reserve(data.slotId(block, "08:00"), approved, 1);
        data.reserve(data.slotId(block, "08:30"), requested, 1);
        jdbc.update("INSERT INTO appointment_status_history (appointment_id, status_id, actor_user_id, source)"
                + " SELECT ?, id, NULL, 'SYSTEM' FROM appointment_statuses WHERE code = 'APPROVED'", approved);
        jdbc.update("INSERT INTO appointment_status_history (appointment_id, status_id, actor_user_id, source)"
                + " SELECT ?, id, ?, 'USER' FROM appointment_statuses WHERE code = 'REQUESTED'", requested, patient);

        String admin = tokens.bearer(data.user("admin", "ADMIN"), Role.ADMIN);
        for (boolean active : List.of(false, true)) {
            mvc.perform(patch("/api/admin/professionals/" + pro.id() + "/status")
                    .header(HttpHeaders.AUTHORIZATION, admin).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("active", active))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(active));

            assertThat(statusOf(approved)).isEqualTo("APPROVED");
            assertThat(statusOf(requested)).isEqualTo("REQUESTED");
            assertThat(data.count("SELECT COUNT(*) FROM slot_reservations WHERE appointment_id IN (?, ?)",
                    approved, requested)).isEqualTo(2);
            assertThat(data.count("SELECT COUNT(*) FROM appointment_status_history WHERE appointment_id IN (?, ?)",
                    approved, requested)).isEqualTo(2);
        }

        mvc.perform(get("/api/patient/appointments/" + approved)
                .header(HttpHeaders.AUTHORIZATION, tokens.bearer(patient, Role.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.professional.id").value(pro.id()));
    }

    private String statusOf(long appointmentId) {
        return jdbc.queryForObject("SELECT st.code FROM appointments a JOIN appointment_statuses st"
                + " ON st.id = a.status_id WHERE a.id = ?", String.class, appointmentId);
    }

    // ------------------------------------------------------------------ R5

    /**
     * R5: "que estados liberan slots" y "que estados son terminales" viven en el enum (dominio) y en
     * el catalogo sembrado por V4 ({@code releases_slots}, {@code is_terminal}). Esta prueba impide
     * que diverjan: si se cambia uno, falla hasta que se cambie el otro con una migracion.
     */
    @Test
    void theStatusEnumAndTheSeededCatalogAgree() {
        Map<AppointmentStatus, Boolean> releases = new EnumMap<>(AppointmentStatus.class);
        Map<AppointmentStatus, Boolean> terminal = new EnumMap<>(AppointmentStatus.class);
        jdbc.query("SELECT code, releases_slots, is_terminal FROM appointment_statuses", rs -> {
            AppointmentStatus status = AppointmentStatus.valueOf(rs.getString("code"));
            releases.put(status, rs.getBoolean("releases_slots"));
            terminal.put(status, rs.getBoolean("is_terminal"));
        });

        assertThat(releases.keySet()).containsExactlyInAnyOrder(AppointmentStatus.values());
        for (AppointmentStatus status : AppointmentStatus.values()) {
            assertThat(releases.get(status)).as("releases_slots de %s", status).isEqualTo(status.releasesSlots());
            assertThat(terminal.get(status)).as("is_terminal de %s", status).isEqualTo(status.isTerminal());
        }
    }
}
