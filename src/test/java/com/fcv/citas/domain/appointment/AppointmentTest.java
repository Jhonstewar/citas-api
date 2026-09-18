package com.fcv.citas.domain.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.fcv.citas.domain.shared.ConflictException;
import com.fcv.citas.domain.shared.InvalidRequestException;

/** RN-02, RN-03, RN-04, RN-11 y HU-032: transiciones explicitas e historial (dominio puro). */
class AppointmentTest {

    private static final LocalDate DAY = LocalDate.of(2030, 5, 6);
    private static final LocalDateTime BEFORE = LocalDateTime.of(DAY, LocalTime.of(7, 0));
    private static final long PATIENT = 10L;
    private static final long ADMIN = 99L;

    private static Appointment requested() {
        return Appointment.requestSpecialized(PATIENT, 1L, 1, 2, DAY, LocalTime.of(8, 0), LocalTime.of(9, 0))
                .appointment();
    }

    @Test
    void generalIsBornApprovedWithSystemHistoryWithoutActor() {
        Appointment.Transition t = Appointment.bookGeneral(PATIENT, 1L, 1, 2, DAY, LocalTime.of(8, 0),
                LocalTime.of(8, 30));
        assertThat(t.appointment().status()).isEqualTo(AppointmentStatus.APPROVED);
        assertThat(t.change().source()).isEqualTo(AuditSource.SYSTEM);
        assertThat(t.change().actorUserId()).isNull();
    }

    @Test
    void specializedIsBornRequestedWithThePatientAsActor() {
        Appointment.Transition t = Appointment.requestSpecialized(PATIENT, 1L, 1, 2, DAY, LocalTime.of(8, 0),
                LocalTime.of(9, 0));
        assertThat(t.appointment().status()).isEqualTo(AppointmentStatus.REQUESTED);
        assertThat(t.change().source()).isEqualTo(AuditSource.USER);
        assertThat(t.change().actorUserId()).isEqualTo(PATIENT);
    }

    @Test
    void adminApprovesARequestedAppointment() {
        Appointment.Transition t = requested().approve(ADMIN, BEFORE);
        assertThat(t.appointment().status()).isEqualTo(AppointmentStatus.APPROVED);
        assertThat(t.change().source()).isEqualTo(AuditSource.ADMIN);
        assertThat(t.change().actorUserId()).isEqualTo(ADMIN);
    }

    /** D12: aprobar una solicitud cuya franja ya empezo → 409. */
    @Test
    void cannotApproveAnExpiredRequest() {
        assertThatThrownBy(() -> requested().approve(ADMIN, LocalDateTime.of(DAY, LocalTime.of(8, 0))))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("APPOINTMENT_EXPIRED");
    }

    /** RN-04: rechazo con motivo; sin motivo → error de validacion. */
    @Test
    void rejectionRequiresAReasonAndReleasesSlots() {
        assertThatThrownBy(() -> requested().reject(ADMIN, "  ")).isInstanceOf(InvalidRequestException.class);
        Appointment.Transition t = requested().reject(ADMIN, " Agenda saturada ");
        assertThat(t.appointment().status()).isEqualTo(AppointmentStatus.REJECTED);
        assertThat(t.change().reason()).isEqualTo("Agenda saturada");
        assertThat(t.appointment().status().releasesSlots()).isTrue();
    }

    /** RN-11 / HU-030 CA-04: solo REQUESTED se decide; lo demas → 409 INVALID_TRANSITION. */
    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = "REQUESTED", mode = EnumSource.Mode.EXCLUDE)
    void onlyRequestedCanBeDecided(AppointmentStatus status) {
        Appointment a = new Appointment(1L, PATIENT, 1L, 1, 2, status, DAY, LocalTime.of(8, 0), LocalTime.of(9, 0));
        assertThatThrownBy(() -> a.approve(ADMIN, BEFORE)).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("INVALID_TRANSITION");
        assertThatThrownBy(() -> a.reject(ADMIN, "motivo")).isInstanceOf(ConflictException.class);
    }

    /** RN-11: los terminales no tienen salida ("una cita cancelada no se reactiva", RF-14). */
    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = { "REJECTED", "CANCELLED", "COMPLETED", "NO_SHOW" })
    void terminalStatesHaveNoWayOut(AppointmentStatus terminal) {
        assertThat(terminal.allowedNext()).isEmpty();
    }

    /** HU-032 CA-02: USER, ADMIN y PROFESSIONAL exigen actor. */
    @Test
    void nonSystemChangesRequireAnActor() {
        assertThatThrownBy(() -> new StatusChange(AppointmentStatus.APPROVED, null, AuditSource.ADMIN, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new StatusChange(AppointmentStatus.APPROVED, null, AuditSource.SYSTEM, null).actorUserId())
                .isNull();
    }
}
