package com.fcv.citas.application.appointment;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fcv.citas.application.TransactionRunner;
import com.fcv.citas.application.appointment.AppointmentQueries.AppointmentView;
import com.fcv.citas.domain.appointment.Appointment;
import com.fcv.citas.domain.appointment.AppointmentRepository;
import com.fcv.citas.domain.catalog.AppointmentType;
import com.fcv.citas.domain.catalog.Specialty;
import com.fcv.citas.domain.catalog.SpecialtyRepository;
import com.fcv.citas.domain.professional.Professional;
import com.fcv.citas.domain.professional.ProfessionalRepository;
import com.fcv.citas.domain.schedule.AvailabilityBlock;
import com.fcv.citas.domain.schedule.BlockRepository;
import com.fcv.citas.domain.shared.BusinessRuleException;
import com.fcv.citas.domain.shared.InvalidRequestException;
import com.fcv.citas.domain.shared.NotFoundException;
import com.fcv.citas.domain.shared.SystemZone;

/**
 * HU-023 (cita general, APPROVED al instante) y HU-024 (cita especializada, REQUESTED con la
 * franja retenida). Son dos flujos distintos: usar el equivocado responde {@code WRONG_FLOW}.
 *
 * <p>La doble reserva no la impide una comprobacion previa sino la clave primaria de
 * {@code slot_reservations} (dec-003): si otra reserva gana la carrera, el INSERT falla, la
 * transaccion se deshace entera (cita, historial y reservas) y el cliente recibe 409.</p>
 */
public class BookAppointmentUseCase {

    public record BookingCommand(Long professionalId, Integer siteId, Integer specialtyId, LocalDate date,
            LocalTime startTime) {
    }

    private final SpecialtyRepository specialties;
    private final ProfessionalRepository professionals;
    private final BlockRepository blocks;
    private final AppointmentRepository appointments;
    private final AppointmentQueries queries;
    private final TransactionRunner tx;
    private final Clock clock;

    public BookAppointmentUseCase(SpecialtyRepository specialties, ProfessionalRepository professionals,
            BlockRepository blocks, AppointmentRepository appointments, AppointmentQueries queries,
            TransactionRunner tx, Clock clock) {
        this.specialties = specialties;
        this.professionals = professionals;
        this.blocks = blocks;
        this.appointments = appointments;
        this.queries = queries;
        this.tx = tx;
        this.clock = clock;
    }

    /** HU-023: cita de Medicina General (u otra especialidad de tipo GENERAL). */
    public AppointmentView bookGeneral(long patientUserId, BookingCommand command) {
        return book(patientUserId, command, AppointmentType.GENERAL);
    }

    /** HU-024: solicitud de cita especializada. */
    public AppointmentView requestSpecialized(long patientUserId, BookingCommand command) {
        return book(patientUserId, command, AppointmentType.SPECIALIZED);
    }

    private AppointmentView book(long patientUserId, BookingCommand c, AppointmentType flow) {
        requireFields(c);
        long id = tx.inTransaction(() -> {
            Specialty specialty = specialties.findById(c.specialtyId()).filter(Specialty::active)
                    .orElseThrow(() -> new BusinessRuleException("SPECIALTY_INACTIVE",
                            "La especialidad no existe o no está activa"));
            if (specialty.appointmentType() != flow) {
                throw wrongFlow(flow);
            }
            Professional professional = professionals.findById(c.professionalId())
                    .orElseThrow(() -> new NotFoundException("El profesional no existe"));
            if (!professional.active()) {
                throw new BusinessRuleException("PROFESSIONAL_INACTIVE", "El profesional no está disponible");
            }
            if (!professional.offers(specialty.id())) {
                throw new BusinessRuleException("SPECIALTY_NOT_ASSIGNED",
                        "El profesional no atiende la especialidad seleccionada");
            }
            if (!professional.worksAt(c.siteId())) {
                throw new BusinessRuleException("SITE_NOT_ASSIGNED", "El profesional no atiende en esa sede");
            }
            if (!java.time.LocalDateTime.of(c.date(), c.startTime()).isAfter(SystemZone.now(clock))) {
                throw new BusinessRuleException("PAST_TIME", "No se pueden reservar franjas en el pasado");
            }
            int slots = specialty.slotsRequired();
            AvailabilityBlock block = blocks.findByProfessionalAndDate(professional.id(), c.date()).stream()
                    .filter(b -> b.siteId() == c.siteId() && b.canHost(c.startTime(), slots))
                    .findFirst()
                    .orElseThrow(BookAppointmentUseCase::notAvailable);
            List<LocalTime> starts = java.util.stream.IntStream.range(0, slots)
                    .mapToObj(i -> c.startTime().plusMinutes((long) AvailabilityBlock.SLOT_MINUTES * i)).toList();
            List<Long> slotIds = blocks.slotIds(block.id(), starts);
            if (slotIds.size() != slots) {
                throw notAvailable();
            }
            LocalTime end = c.startTime().plusMinutes(specialty.durationMinutes());
            Appointment.Transition created = flow == AppointmentType.GENERAL
                    ? Appointment.bookGeneral(patientUserId, professional.id(), c.siteId(), specialty.id(), c.date(),
                            c.startTime(), end)
                    : Appointment.requestSpecialized(patientUserId, professional.id(), c.siteId(), specialty.id(),
                            c.date(), c.startTime(), end);
            Appointment saved = appointments.create(created.appointment(), created.change());
            appointments.reserveSlots(saved.id(), slotIds);
            return saved.id();
        });
        return queries.findById(id).orElseThrow();
    }

    private static void requireFields(BookingCommand c) {
        if (c.professionalId() == null) {
            throw InvalidRequestException.field("professionalId", "Seleccione el profesional");
        }
        if (c.siteId() == null) {
            throw InvalidRequestException.field("siteId", "Seleccione la sede");
        }
        if (c.specialtyId() == null) {
            throw InvalidRequestException.field("specialtyId", "Seleccione la especialidad");
        }
        if (c.date() == null) {
            throw InvalidRequestException.field("date", "Seleccione la fecha");
        }
        if (c.startTime() == null) {
            throw InvalidRequestException.field("startTime", "Seleccione la hora");
        }
    }

    private static BusinessRuleException wrongFlow(AppointmentType flow) {
        return flow == AppointmentType.GENERAL
                ? new BusinessRuleException("WRONG_FLOW",
                        "Esta especialidad requiere aprobación: solicítela como cita especializada")
                : new BusinessRuleException("WRONG_FLOW",
                        "Esta especialidad es de cita general: agéndela por el flujo de cita general");
    }

    private static BusinessRuleException notAvailable() {
        return new BusinessRuleException("SLOT_NOT_AVAILABLE",
                "Esa franja no está publicada o no alcanza para la duración de la especialidad");
    }
}
