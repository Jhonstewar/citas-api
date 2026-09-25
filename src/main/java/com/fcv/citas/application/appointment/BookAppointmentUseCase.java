package com.fcv.citas.application.appointment;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;

import com.fcv.citas.application.TransactionRunner;
import com.fcv.citas.application.appointment.AppointmentQueries.AppointmentView;
import com.fcv.citas.domain.appointment.Appointment;
import com.fcv.citas.domain.appointment.AppointmentRepository;
import com.fcv.citas.domain.catalog.AppointmentType;
import com.fcv.citas.domain.catalog.SpecialtyRepository;
import com.fcv.citas.domain.professional.ProfessionalRepository;
import com.fcv.citas.domain.schedule.BlockRepository;
import com.fcv.citas.domain.shared.InvalidRequestException;

/**
 * HU-023 (cita general, APPROVED al instante) y HU-024 (cita especializada, REQUESTED con la
 * franja retenida). Son dos flujos distintos: usar el equivocado responde {@code WRONG_FLOW}.
 *
 * <p>La doble reserva no la impide una comprobacion previa sino la clave primaria de
 * {@code slot_reservations} (dec-003): si otra reserva gana la carrera, el INSERT falla, la
 * transaccion se deshace entera (cita, historial y reservas) y el cliente recibe 409. La eleccion de
 * slots es la de {@link SlotAllocator}, compartida con la reprogramacion (HU-027).</p>
 */
public class BookAppointmentUseCase {

    public record BookingCommand(Long professionalId, Integer siteId, Integer specialtyId, LocalDate date,
            LocalTime startTime) {
    }

    private final SlotAllocator allocator;
    private final AppointmentRepository appointments;
    private final AppointmentQueries queries;
    private final TransactionRunner tx;

    public BookAppointmentUseCase(SpecialtyRepository specialties, ProfessionalRepository professionals,
            BlockRepository blocks, AppointmentRepository appointments, AppointmentQueries queries,
            TransactionRunner tx, Clock clock) {
        this.allocator = new SlotAllocator(specialties, professionals, blocks, clock);
        this.appointments = appointments;
        this.queries = queries;
        this.tx = tx;
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
            SlotAllocator.Allocation slot = allocator.allocate(c.professionalId(), c.siteId(), c.specialtyId(),
                    c.date(), c.startTime(), flow);
            long professionalId = slot.professional().id();
            int specialtyId = slot.specialty().id();
            Appointment.Transition created = flow == AppointmentType.GENERAL
                    ? Appointment.bookGeneral(patientUserId, professionalId, c.siteId(), specialtyId, c.date(),
                            c.startTime(), slot.end())
                    : Appointment.requestSpecialized(patientUserId, professionalId, c.siteId(), specialtyId,
                            c.date(), c.startTime(), slot.end());
            Appointment saved = appointments.create(created.appointment(), created.change());
            appointments.reserveSlots(saved.id(), slot.slotIds());
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
}
