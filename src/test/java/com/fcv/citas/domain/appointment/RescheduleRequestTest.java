package com.fcv.citas.domain.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.fcv.citas.domain.shared.BusinessRuleException;
import com.fcv.citas.domain.shared.CodedException;
import com.fcv.citas.domain.shared.ConflictException;
import com.fcv.citas.domain.shared.InvalidRequestException;

/** Invariantes del agregado de reprogramacion (HU-027 T-01, HU-031 T-01), sin framework. */
class RescheduleRequestTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 10);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 12, 0);
    private static final int HIC = 1;
    private static final int ICV = 2;
    private static final long PATIENT = 7L;
    private static final long ADMIN = 99L;

    private static Appointment appointment(AppointmentStatus status, LocalDate date, String start, String end) {
        return new Appointment(10L, PATIENT, 3L, HIC, 5, status, date, LocalTime.parse(start), LocalTime.parse(end));
    }

    private static Appointment approved() {
        return appointment(AppointmentStatus.APPROVED, DAY, "08:00", "09:00");
    }

    private static TimeSlot slot(LocalDate date, String start, String end, int site) {
        return new TimeSlot(date, LocalTime.parse(start), LocalTime.parse(end), site);
    }

    private static RescheduleRequest pending() {
        return withId(RescheduleRequest.request(approved(), PATIENT, slot(DAY, "10:00", "11:00", HIC), null, NOW,
                false), 55L);
    }

    /** La solicitud tal como la devuelve el repositorio tras insertarla. */
    private static RescheduleRequest withId(RescheduleRequest r, long id) {
        return new RescheduleRequest(id, r.appointmentId(), r.status(), r.requestedByUserId(), r.previous(),
                r.proposed(), r.requestReason(), r.decidedByUserId(), r.decisionReason());
    }

    private static String code(Runnable action) {
        try {
            action.run();
        } catch (CodedException e) {
            return e.getClass().getSimpleName() + ":" + e.code();
        }
        return "none";
    }

    // ------------------------------------------------------------------ pedir

    @Test
    void aValidRequestIsPendingWithThePreviousAndProposedSlotsAndKeepsCare() {
        RescheduleRequest r = RescheduleRequest.request(approved(), PATIENT, slot(DAY.plusDays(1), "09:00", "10:00", ICV),
                "  Viaje  ", NOW, false);
        assertThat(r.status()).isEqualTo(RescheduleStatus.PENDING);
        assertThat(r.appointmentId()).isEqualTo(10L);
        assertThat(r.previous()).isEqualTo(slot(DAY, "08:00", "09:00", HIC));
        assertThat(r.proposed().siteId()).isEqualTo(ICV);
        assertThat(r.requestReason()).isEqualTo("Viaje");
        assertThat(r.decidedByUserId()).isNull();
    }

    @Test
    void onlyAnApprovedFutureAppointmentWithoutPendingAdmitsARequest() {
        TimeSlot target = slot(DAY, "10:00", "11:00", HIC);
        for (AppointmentStatus s : new AppointmentStatus[] { AppointmentStatus.REQUESTED, AppointmentStatus.CANCELLED,
                AppointmentStatus.REJECTED, AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW }) {
            assertThat(code(() -> RescheduleRequest.request(appointment(s, DAY, "08:00", "09:00"), PATIENT, target,
                    null, NOW, false))).isEqualTo("ConflictException:INVALID_TRANSITION");
        }
        Appointment started = appointment(AppointmentStatus.APPROVED, NOW.toLocalDate(), "12:00", "13:00");
        assertThat(code(() -> RescheduleRequest.request(started, PATIENT, target, null, NOW, false)))
                .isEqualTo("ConflictException:APPOINTMENT_EXPIRED");
        assertThat(code(() -> RescheduleRequest.request(approved(), PATIENT, target, null, NOW, true)))
                .isEqualTo("ConflictException:RESCHEDULE_PENDING");
    }

    @Test
    void theProposalMustBeFutureDifferentAndNotOverlapTheCurrentSlot() {
        assertThat(code(() -> RescheduleRequest.request(approved(), PATIENT, slot(DAY, "08:00", "09:00", HIC), null,
                NOW, false))).isEqualTo("BusinessRuleException:SAME_SLOT");
        assertThat(code(() -> RescheduleRequest.request(approved(), PATIENT, slot(DAY, "08:30", "09:30", HIC), null,
                NOW, false))).isEqualTo("BusinessRuleException:SLOT_NOT_AVAILABLE");
        assertThat(code(() -> RescheduleRequest.request(approved(), PATIENT,
                slot(NOW.toLocalDate(), "12:00", "13:00", HIC), null, NOW, false)))
                .isEqualTo("BusinessRuleException:PAST_TIME");
        // Contigua (09:00, justo al terminar la actual) no se cruza.
        assertThat(RescheduleRequest.request(approved(), PATIENT, slot(DAY, "09:00", "10:00", HIC), null, NOW, false)
                .isPending()).isTrue();
    }

    @Test
    void changingProfessionalOrSpecialtyIsANewAppointment() {
        assertThatThrownBy(() -> RescheduleRequest.requireSameCare(approved(), 4L, null))
                .isInstanceOf(BusinessRuleException.class).hasFieldOrPropertyWithValue("code", "WRONG_FLOW");
        assertThatThrownBy(() -> RescheduleRequest.requireSameCare(approved(), null, 6))
                .isInstanceOf(BusinessRuleException.class);
        RescheduleRequest.requireSameCare(approved(), 3L, 5);
        RescheduleRequest.requireSameCare(approved(), null, null);
    }

    @Test
    void aTooLongReasonIsAValidationError() {
        assertThatThrownBy(() -> RescheduleRequest.request(approved(), PATIENT, slot(DAY, "10:00", "11:00", HIC),
                "x".repeat(501), NOW, false)).isInstanceOf(InvalidRequestException.class);
    }

    // ------------------------------------------------------------------ decidir

    @Test
    void approvingMovesTheSameAppointmentAndTracesBothSlots() {
        RescheduleRequest.Approval approval = pending().approve(ADMIN, approved(), NOW, id -> id == HIC ? "HIC" : "ICV");
        assertThat(approval.request().status()).isEqualTo(RescheduleStatus.APPROVED);
        assertThat(approval.request().decidedByUserId()).isEqualTo(ADMIN);
        Appointment moved = approval.appointment().appointment();
        assertThat(moved.id()).isEqualTo(10L);
        assertThat(moved.status()).isEqualTo(AppointmentStatus.APPROVED);
        assertThat(moved.professionalId()).isEqualTo(3L);
        assertThat(moved.specialtyId()).isEqualTo(5);
        assertThat(moved.startTime()).isEqualTo(LocalTime.of(10, 0));
        StatusChange change = approval.appointment().change();
        assertThat(change.status()).isEqualTo(AppointmentStatus.APPROVED);
        assertThat(change.source()).isEqualTo(AuditSource.ADMIN);
        assertThat(change.actorUserId()).isEqualTo(ADMIN);
        assertThat(change.reason()).contains("08:00").contains("10:00").contains("HIC").contains(DAY.toString());
    }

    @Test
    void anExpiredProposalCannotBeApprovedButCanBeRejected() {
        RescheduleRequest expired = withId(RescheduleRequest.request(approved(), PATIENT,
                slot(DAY, "10:00", "11:00", HIC), null, NOW, false), 56L);
        LocalDateTime later = LocalDateTime.of(DAY, LocalTime.of(10, 0));
        Appointment stillApproved = appointment(AppointmentStatus.APPROVED, DAY.plusDays(1), "08:00", "09:00");
        assertThat(code(() -> expired.approve(ADMIN, stillApproved, later, String::valueOf)))
                .isEqualTo("ConflictException:APPOINTMENT_EXPIRED");
        assertThat(expired.reject(ADMIN, stillApproved, "Ya pasó").request().status())
                .isEqualTo(RescheduleStatus.REJECTED);
    }

    @Test
    void rejectingNeedsAReasonAndKeepsTheAppointment() {
        assertThat(code(() -> pending().reject(ADMIN, approved(), "  "))).isEqualTo("InvalidRequestException:VALIDATION");
        assertThat(code(() -> pending().reject(ADMIN, approved(), null))).isEqualTo("InvalidRequestException:VALIDATION");
        RescheduleRequest.Rejection rejection = pending().reject(ADMIN, approved(), " Sin cupo ");
        assertThat(rejection.request().status()).isEqualTo(RescheduleStatus.REJECTED);
        assertThat(rejection.request().decisionReason()).isEqualTo("Sin cupo");
        assertThat(rejection.appointment().appointment()).isEqualTo(approved());
        assertThat(rejection.appointment().change().reason()).isEqualTo("Sin cupo");
        assertThat(rejection.appointment().change().source()).isEqualTo(AuditSource.ADMIN);
    }

    @Test
    void onlyAPendingRequestOnAnApprovedAppointmentIsDecided() {
        RescheduleRequest decided = pending().reject(ADMIN, approved(), "No").request();
        assertThat(code(() -> decided.approve(ADMIN, approved(), NOW, String::valueOf)))
                .isEqualTo("ConflictException:INVALID_TRANSITION");
        // Estado antes que motivo: sobre una ya decidida el problema es la transicion.
        assertThat(code(() -> decided.reject(ADMIN, approved(), null))).isEqualTo("ConflictException:INVALID_TRANSITION");
        Appointment completed = appointment(AppointmentStatus.COMPLETED, DAY, "08:00", "09:00");
        assertThat(code(() -> pending().approve(ADMIN, completed, NOW, String::valueOf)))
                .isEqualTo("ConflictException:INVALID_TRANSITION");
        assertThat(code(() -> pending().reject(ADMIN, completed, "x"))).isEqualTo("ConflictException:INVALID_TRANSITION");
    }

    @Test
    void cancellingWithTheAppointmentRecordsThePatientAndTheAutomaticReason() {
        RescheduleRequest cancelled = pending().cancelWithAppointment(PATIENT);
        assertThat(cancelled.status()).isEqualTo(RescheduleStatus.CANCELLED);
        assertThat(cancelled.decidedByUserId()).isEqualTo(PATIENT);
        assertThat(cancelled.decisionReason()).isEqualTo("Cita cancelada por el paciente");
        assertThatThrownBy(() -> cancelled.cancelWithAppointment(PATIENT)).isInstanceOf(ConflictException.class);
    }
}
