package com.fcv.citas.infrastructure.persistence.appointment;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.persistence.EntityManager;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.fcv.citas.domain.appointment.RescheduleRequest;
import com.fcv.citas.domain.appointment.RescheduleRequestRepository;
import com.fcv.citas.domain.appointment.RescheduleStatus;
import com.fcv.citas.domain.appointment.TimeSlot;
import com.fcv.citas.domain.shared.ConflictException;

/** Adaptador JPA de {@link RescheduleRequestRepository} ({@code reschedule_requests}, V3 + V10). */
@Component
class JpaRescheduleRequestRepositoryAdapter implements RescheduleRequestRepository {

    private final SpringDataRescheduleRequestRepository requests;
    private final EntityManager em;
    /** Catalogo fijo (V4): los ids de estado no cambian mientras corre la aplicacion. */
    private final Map<RescheduleStatus, Short> statusIds = new ConcurrentHashMap<>();

    JpaRescheduleRequestRepositoryAdapter(SpringDataRescheduleRequestRepository requests, EntityManager em) {
        this.requests = requests;
        this.em = em;
    }

    @Override
    public RescheduleRequest create(RescheduleRequest r) {
        if (!r.isPending()) {
            throw new IllegalArgumentException("Solo se crean solicitudes PENDING");
        }
        TimeSlot previous = r.previous();
        TimeSlot proposed = r.proposed();
        try {
            RescheduleRequestJpaEntity saved = requests.saveAndFlush(new RescheduleRequestJpaEntity(r.appointmentId(),
                    statusId(RescheduleStatus.PENDING), r.requestedByUserId(), previous.date(), previous.startTime(),
                    previous.endTime(), previous.siteId(), proposed.date(), proposed.startTime(), proposed.endTime(),
                    proposed.siteId(), r.requestReason()));
            return toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            // uq_reschedule_requests_active (V3): otra solicitud sin decidir gano la carrera (D20).
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("uq_reschedule_requests_active")) {
                throw new ConflictException("RESCHEDULE_PENDING",
                        "Esta cita ya tiene una reprogramación pendiente de decisión");
            }
            throw e;
        }
    }

    @Override
    public Optional<RescheduleRequest> findById(long requestId) {
        return requests.findById(requestId).map(this::toDomain);
    }

    @Override
    public Optional<RescheduleRequest> lockById(long requestId) {
        return requests.lockById(requestId).map(e -> {
            // Si la entidad ya estaba en el contexto, Hibernate la devuelve con su estado viejo. Se relee
            // con lectura bloqueante: en REPEATABLE READ un SELECT normal veria la instantanea antigua.
            em.refresh(e, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            return toDomain(e);
        });
    }

    @Override
    public Optional<RescheduleRequest> lockPendingByAppointment(long appointmentId) {
        return requests.lockPendingByAppointment(appointmentId).map(this::toDomain);
    }

    @Override
    public void saveDecision(RescheduleRequest decided) {
        if (decided.isPending() || decided.id() == null) {
            throw new IllegalArgumentException("Solo se guarda la decision de una solicitud existente");
        }
        em.flush();
        int updated = em.createNativeQuery("""
                UPDATE reschedule_requests
                SET status_id = ?1, decided_by_user_id = ?2, decision_reason = ?3, decided_at = CURRENT_TIMESTAMP(6)
                WHERE id = ?4 AND decided_at IS NULL
                """).setParameter(1, statusId(decided.status())).setParameter(2, decided.decidedByUserId())
                .setParameter(3, decided.decisionReason()).setParameter(4, decided.id()).executeUpdate();
        if (updated != 1) {
            // Imposible bajo el bloqueo de la fila; si ocurre, otra transaccion decidio primero.
            throw new ConflictException("CONCURRENT_CHANGE",
                    "La solicitud cambió mientras se procesaba: vuelva a consultarla");
        }
    }

    private RescheduleRequest toDomain(RescheduleRequestJpaEntity e) {
        return new RescheduleRequest(e.getId(), e.getAppointmentId(), statusOf(e.getStatusId()),
                e.getRequestedByUserId(),
                new TimeSlot(e.getPreviousDate(), e.getPreviousStartTime(), e.getPreviousEndTime(), e.getPreviousSiteId()),
                new TimeSlot(e.getProposedDate(), e.getProposedStartTime(), e.getProposedEndTime(), e.getProposedSiteId()),
                e.getRequestReason(), e.getDecidedByUserId(), e.getDecisionReason());
    }

    private short statusId(RescheduleStatus status) {
        return statusIds.computeIfAbsent(status, s -> ((Number) em
                .createNativeQuery("SELECT id FROM reschedule_statuses WHERE code = ?")
                .setParameter(1, s.name()).getSingleResult()).shortValue());
    }

    private RescheduleStatus statusOf(short id) {
        String code = (String) em.createNativeQuery("SELECT code FROM reschedule_statuses WHERE id = ?")
                .setParameter(1, id).getSingleResult();
        return RescheduleStatus.valueOf(code);
    }
}
