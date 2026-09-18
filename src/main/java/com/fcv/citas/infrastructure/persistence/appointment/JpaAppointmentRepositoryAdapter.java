package com.fcv.citas.infrastructure.persistence.appointment;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.persistence.EntityManager;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;

import com.fcv.citas.domain.appointment.Appointment;
import com.fcv.citas.domain.appointment.AppointmentRepository;
import com.fcv.citas.domain.appointment.AppointmentStatus;
import com.fcv.citas.domain.appointment.StatusChange;
import com.fcv.citas.domain.shared.ConflictException;

/** Adaptador JPA de {@link AppointmentRepository}: cita, historial y libro de reservas. */
@Component
class JpaAppointmentRepositoryAdapter implements AppointmentRepository {

    private final SpringDataAppointmentRepository appointments;
    private final SpringDataStatusHistoryRepository history;
    private final SpringDataSlotReservationRepository reservations;
    private final EntityManager em;
    /** Catalogo fijo (V4): el id de cada estado no cambia mientras corre la aplicacion. */
    private final Map<AppointmentStatus, Short> statusIds = new ConcurrentHashMap<>();

    JpaAppointmentRepositoryAdapter(SpringDataAppointmentRepository appointments,
            SpringDataStatusHistoryRepository history, SpringDataSlotReservationRepository reservations,
            EntityManager em) {
        this.appointments = appointments;
        this.history = history;
        this.reservations = reservations;
        this.em = em;
    }

    @Override
    public Appointment create(Appointment a, StatusChange initial) {
        AppointmentJpaEntity saved = appointments.saveAndFlush(new AppointmentJpaEntity(a.patientUserId(),
                a.professionalId(), a.siteId(), a.specialtyId(), statusId(a.status()), a.date(), a.startTime(),
                a.endTime()));
        appendHistory(saved.getId(), initial);
        return toDomain(saved, a.status());
    }

    @Override
    public void reserveSlots(long appointmentId, List<Long> slotIds) {
        try {
            for (int i = 0; i < slotIds.size(); i++) {
                reservations.saveAndFlush(SlotReservationJpaEntity.forAppointment(slotIds.get(i), appointmentId, i + 1));
            }
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException e) {
            // PK de slot_reservations (o interbloqueo con otra reserva del mismo slot): otra reserva
            // gano la franja. La excepcion deshace toda la transaccion: cita, historial y reservas.
            throw new ConflictException("SLOT_TAKEN", "Esa franja ya no está disponible: otra persona la reservó");
        }
    }

    @Override
    public Optional<Appointment> lockById(long appointmentId) {
        return appointments.lockById(appointmentId).map(e -> toDomain(e, statusOf(e.getStatusId())));
    }

    @Override
    public void apply(Appointment.Transition transition) {
        Appointment a = transition.appointment();
        AppointmentJpaEntity entity = appointments.findById(a.id()).orElseThrow();
        entity.setStatusId(statusId(a.status()));
        appointments.saveAndFlush(entity);
        appendHistory(a.id(), transition.change());
    }

    @Override
    public void releaseSlots(long appointmentId) {
        reservations.deleteByAppointment(appointmentId);
    }

    private void appendHistory(long appointmentId, StatusChange change) {
        history.save(new StatusHistoryJpaEntity(appointmentId, statusId(change.status()), change.actorUserId(),
                change.source().name(), change.reason()));
        em.flush();
    }

    private short statusId(AppointmentStatus status) {
        return statusIds.computeIfAbsent(status, s -> ((Number) em
                .createNativeQuery("SELECT id FROM appointment_statuses WHERE code = ?")
                .setParameter(1, s.name()).getSingleResult()).shortValue());
    }

    private AppointmentStatus statusOf(short id) {
        String code = (String) em.createNativeQuery("SELECT code FROM appointment_statuses WHERE id = ?")
                .setParameter(1, id).getSingleResult();
        return AppointmentStatus.valueOf(code);
    }

    private static Appointment toDomain(AppointmentJpaEntity e, AppointmentStatus status) {
        return new Appointment(e.getId(), e.getPatientUserId(), e.getProfessionalId(), e.getSiteId(),
                e.getSpecialtyId(), status, e.getScheduledDate(), e.getStartTime(), e.getEndTime());
    }
}
