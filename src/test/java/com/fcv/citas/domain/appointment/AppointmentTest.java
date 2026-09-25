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

    // ------------------------------------------------------------------ S4: cancelar y cerrar (HU-026, HU-021)

    private static final LocalTime START = LocalTime.of(8, 0);
    private static final LocalDateTime AT_START = LocalDateTime.of(DAY, START);
    private static final long PROFESSIONAL_USER = 55L;

    private static Appointment in(AppointmentStatus status) {
        return new Appointment(1L, PATIENT, 1L, 1, 2, status, DAY, START, LocalTime.of(9, 0));
    }

    /** HU-026 CA-01 y D16: APPROVED y REQUESTED futuras se cancelan; historial USER con el paciente. */
    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = { "REQUESTED", "APPROVED" })
    void patientCancelsAFutureNonTerminalAppointment(AppointmentStatus status) {
        Appointment a = in(status);
        assertThat(a.isCancellableAt(BEFORE)).isTrue();
        Appointment.Transition t = a.cancel(PATIENT, BEFORE, " Ya no la necesito ");
        assertThat(t.appointment().status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(t.change().status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(t.change().source()).isEqualTo(AuditSource.USER);
        assertThat(t.change().actorUserId()).isEqualTo(PATIENT);
        assertThat(t.change().reason()).isEqualTo("Ya no la necesito");
        assertThat(t.appointment().status().releasesSlots()).isTrue();
    }

    /** D17: sin antelacion minima; un minuto antes de empezar aun se cancela. Sin motivo tambien. */
    @Test
    void cancellationNeedsNoMinimumNoticeNorReason() {
        Appointment.Transition t = in(AppointmentStatus.APPROVED).cancel(PATIENT, AT_START.minusMinutes(1), "");
        assertThat(t.appointment().status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(t.change().reason()).isNull();
    }

    /** HU-026 CA-04: a la hora de inicio o despues → 409 APPOINTMENT_EXPIRED. */
    @Test
    void cannotCancelOnceItHasStarted() {
        Appointment a = in(AppointmentStatus.APPROVED);
        assertThat(a.isCancellableAt(AT_START)).isFalse();
        assertThatThrownBy(() -> a.cancel(PATIENT, AT_START, null)).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("APPOINTMENT_EXPIRED");
        assertThatThrownBy(() -> a.cancel(PATIENT, AT_START.plusDays(1), null))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("APPOINTMENT_EXPIRED");
    }

    /** HU-026 CA-03 y CA-05: un terminal no se cancela (ni se reactiva); el estado manda sobre la hora. */
    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = { "REJECTED", "CANCELLED", "COMPLETED", "NO_SHOW" })
    void terminalAppointmentsCannotBeCancelled(AppointmentStatus terminal) {
        Appointment a = in(terminal);
        assertThat(terminal.isTerminal()).isTrue();
        assertThat(a.isCancellableAt(BEFORE)).isFalse();
        assertThatThrownBy(() -> a.cancel(PATIENT, BEFORE, null)).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("INVALID_TRANSITION");
        assertThatThrownBy(() -> a.cancel(PATIENT, AT_START.plusHours(3), null))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("INVALID_TRANSITION");
    }

    @Test
    void cancellationReasonIsLimitedTo500Characters() {
        assertThatThrownBy(() -> in(AppointmentStatus.APPROVED).cancel(PATIENT, BEFORE, "x".repeat(501)))
                .isInstanceOf(InvalidRequestException.class);
    }

    /** D20 (HU-027): solo APPROVED, futura y sin otra solicitud pendiente. */
    @Test
    void reschedulableOnlyWhenApprovedFutureAndWithoutPendingRequest() {
        assertThat(in(AppointmentStatus.APPROVED).isReschedulableAt(BEFORE, false)).isTrue();
        assertThat(in(AppointmentStatus.APPROVED).isReschedulableAt(BEFORE, true)).isFalse();
        assertThat(in(AppointmentStatus.APPROVED).isReschedulableAt(AT_START, false)).isFalse();
        assertThat(in(AppointmentStatus.REQUESTED).isReschedulableAt(BEFORE, false)).isFalse();
    }

    /** D19 / HU-021: se cierra desde la hora de inicio, sin plazo maximo; historial PROFESSIONAL. */
    @Test
    void professionalClosesAnApprovedAppointmentFromItsStartTime() {
        Appointment a = in(AppointmentStatus.APPROVED);
        assertThat(a.isClosableAt(AT_START.minusMinutes(1))).isFalse();
        assertThat(a.isClosableAt(AT_START)).isTrue();
        assertThat(a.isClosableAt(AT_START.plusDays(30))).isTrue();

        Appointment.Transition done = a.complete(PROFESSIONAL_USER, AT_START);
        assertThat(done.appointment().status()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(done.change().source()).isEqualTo(AuditSource.PROFESSIONAL);
        assertThat(done.change().actorUserId()).isEqualTo(PROFESSIONAL_USER);
        assertThat(done.appointment().status().releasesSlots()).isFalse();

        Appointment.Transition absent = a.noShow(PROFESSIONAL_USER, AT_START.plusMinutes(5));
        assertThat(absent.appointment().status()).isEqualTo(AppointmentStatus.NO_SHOW);
        assertThat(absent.change().source()).isEqualTo(AuditSource.PROFESSIONAL);
        assertThat(absent.appointment().status().releasesSlots()).isFalse();
    }

    /** D19: antes de la hora de inicio → 409 APPOINTMENT_NOT_STARTED. */
    @Test
    void cannotCloseBeforeItStarts() {
        Appointment a = in(AppointmentStatus.APPROVED);
        assertThatThrownBy(() -> a.complete(PROFESSIONAL_USER, BEFORE)).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("APPOINTMENT_NOT_STARTED");
        assertThatThrownBy(() -> a.noShow(PROFESSIONAL_USER, BEFORE)).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("APPOINTMENT_NOT_STARTED");
    }

    /** HU-021: solo una APPROVED se cierra; el resto → 409 INVALID_TRANSITION, haya empezado o no. */
    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    void onlyApprovedCanBeClosed(AppointmentStatus status) {
        Appointment a = in(status);
        assertThat(a.isClosableAt(AT_START)).isFalse();
        assertThatThrownBy(() -> a.complete(PROFESSIONAL_USER, AT_START)).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("INVALID_TRANSITION");
        assertThatThrownBy(() -> a.noShow(PROFESSIONAL_USER, BEFORE)).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("INVALID_TRANSITION");
    }

    /** Los estados terminales son justamente los que no tienen salida (una sola definicion). */
    @ParameterizedTest
    @EnumSource(AppointmentStatus.class)
    void terminalMeansNoWayOut(AppointmentStatus status) {
        assertThat(status.isTerminal()).isEqualTo(status.allowedNext().isEmpty());
    }
}
