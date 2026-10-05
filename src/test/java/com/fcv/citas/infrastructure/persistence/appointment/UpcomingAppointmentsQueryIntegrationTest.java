package com.fcv.citas.infrastructure.persistence.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.fcv.citas.application.appointment.AppointmentQueries;
import com.fcv.citas.application.appointment.AppointmentQueries.UpcomingAppointmentView;
import com.fcv.citas.support.S3TestData;

/**
 * HU-034 (paso 2.1): {@code findApprovedStartingBetween} devuelve solo citas APPROVED cuyo inicio cae
 * en el intervalo (from, to], ordenadas, y con una vista minima sin documento ni telefono.
 * La ventana es fija y lejana en el futuro: no depende del reloj ni de otros datos de la base.
 */
@SpringBootTest
@ActiveProfiles("test")
class UpcomingAppointmentsQueryIntegrationTest {

    private static final LocalDateTime FROM = LocalDateTime.of(2031, 3, 10, 10, 0);
    private static final LocalDateTime TO = FROM.plusHours(24);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AppointmentQueries queries;

    private S3TestData data;
    private int hic;
    private int specialty;
    private long patient;
    private S3TestData.Professional pro;

    @BeforeEach
    void setUp() {
        data = new S3TestData(jdbc, passwordEncoder.encode(S3TestData.PASSWORD));
        hic = data.siteId("HIC");
        specialty = data.specialty("GENERAL", 30);
        patient = data.user("Ana", "USER");
        pro = data.professional("upc", new int[] { specialty }, hic);
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    private long seed(LocalDateTime start, String status) {
        return data.appointment(patient, pro.id(), hic, specialty, start.toLocalDate(), start.toLocalTime(),
                start.toLocalTime().plusMinutes(30), status);
    }

    /** Solo las citas sembradas por esta prueba (la base de pruebas es compartida). */
    private List<UpcomingAppointmentView> mine() {
        return queries.findApprovedStartingBetween(FROM, TO).stream()
                .filter(v -> jdbc.queryForObject("SELECT patient_user_id FROM appointments WHERE id = ?", Long.class,
                        v.appointmentId()) == patient)
                .toList();
    }

    @Test
    void onlyApprovedAppointmentsAreReturned() {
        long approved = seed(FROM.plusHours(2), "APPROVED");
        int minute = 3;
        for (String status : List.of("REQUESTED", "REJECTED", "CANCELLED", "COMPLETED", "NO_SHOW")) {
            seed(FROM.plusHours(minute++), status);
        }
        assertThat(mine()).extracting(UpcomingAppointmentView::appointmentId).containsExactly(approved);
    }

    @Test
    void pastAndBeyondWindowAreExcluded() {
        long inside = seed(FROM.plusHours(5), "APPROVED");
        seed(FROM.minusHours(3), "APPROVED");
        seed(TO.plusHours(1), "APPROVED");
        assertThat(mine()).extracting(UpcomingAppointmentView::appointmentId).containsExactly(inside);
    }

    @Test
    void exactUpperBoundIsIncludedAndExactLowerBoundIsExcluded() {
        seed(FROM, "APPROVED");
        long atTo = seed(TO, "APPROVED");
        assertThat(mine()).extracting(UpcomingAppointmentView::appointmentId).containsExactly(atTo);
    }

    @Test
    void resultsAreOrderedByDateAndStartTime() {
        long third = seed(TO, "APPROVED");
        long first = seed(FROM.plusHours(1), "APPROVED");
        long second = seed(FROM.plusHours(20), "APPROVED");
        assertThat(mine()).extracting(UpcomingAppointmentView::appointmentId).containsExactly(first, second, third);
    }

    @Test
    void viewCarriesTheContractFieldsAndNoDocumentOrPhone() {
        long id = seed(FROM.plusHours(1), "APPROVED");
        UpcomingAppointmentView v = mine().get(0);
        assertThat(v.appointmentId()).isEqualTo(id);
        assertThat(v.patientFirstName()).isEqualTo("Ana");
        assertThat(v.patientEmail()).endsWith(S3TestData.DOMAIN);
        assertThat(v.date()).isEqualTo(LocalDate.of(2031, 3, 10));
        assertThat(v.startTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(v.endTime()).isEqualTo(LocalTime.of(11, 30));
        assertThat(v.site().code()).isEqualTo("HIC");
        assertThat(v.site().name()).isNotBlank();
        assertThat(v.site().address()).isNotBlank();
        assertThat(v.professional()).isNotBlank();
        assertThat(v.specialty()).isNotBlank();

        List<String> names = Arrays.stream(UpcomingAppointmentView.class.getRecordComponents())
                .map(RecordComponent::getName).toList();
        assertThat(names).noneMatch(n -> n.toLowerCase().contains("document") || n.toLowerCase().contains("phone"));
        assertThat(Arrays.stream(UpcomingAppointmentView.SiteInfo.class.getRecordComponents())
                .map(RecordComponent::getName)).containsExactly("code", "name", "address");
    }
}
