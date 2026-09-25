package com.fcv.citas.application.appointment;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.IntStream;

import com.fcv.citas.domain.catalog.AppointmentType;
import com.fcv.citas.domain.catalog.Specialty;
import com.fcv.citas.domain.catalog.SpecialtyRepository;
import com.fcv.citas.domain.professional.Professional;
import com.fcv.citas.domain.professional.ProfessionalRepository;
import com.fcv.citas.domain.schedule.AvailabilityBlock;
import com.fcv.citas.domain.schedule.BlockRepository;
import com.fcv.citas.domain.shared.BusinessRuleException;
import com.fcv.citas.domain.shared.NotFoundException;
import com.fcv.citas.domain.shared.SystemZone;

/**
 * El UNICO camino para elegir los slots de una franja: lo usan la reserva (HU-023, HU-024) y la
 * reprogramacion (HU-027), para que "franja valida" signifique lo mismo en las dos (DoD de HU-022 y
 * HU-027). Valida especialidad, profesional, sede (RN-07, RN-08) y pasado (RN-06), serializa con la
 * agenda del profesional y resuelve los slots con la regla de dominio {@link AvailabilityBlock#canHost}
 * (RN-05, D9). No reserva nada: ocupar los slots lo hace el libro unico con su barrera de PK (dec-003).
 */
final class SlotAllocator {

    /** Franja resuelta: especialidad y profesional validados, slots en orden y hora de fin. */
    record Allocation(Specialty specialty, Professional professional, List<Long> slotIds, LocalTime end) {
    }

    private final SpecialtyRepository specialties;
    private final ProfessionalRepository professionals;
    private final BlockRepository blocks;
    private final Clock clock;

    SlotAllocator(SpecialtyRepository specialties, ProfessionalRepository professionals, BlockRepository blocks,
            Clock clock) {
        this.specialties = specialties;
        this.professionals = professionals;
        this.blocks = blocks;
        this.clock = clock;
    }

    /**
     * Debe llamarse dentro de la transaccion que despues reserva o retiene los slots.
     *
     * @param expectedFlow tipo de cita que exige la ruta de reserva ({@code WRONG_FLOW} si no coincide);
     *                     {@code null} en la reprogramacion, que conserva la especialidad de la cita
     */
    Allocation allocate(long professionalId, int siteId, int specialtyId, LocalDate date, LocalTime start,
            AppointmentType expectedFlow) {
        Specialty specialty = specialties.findById(specialtyId).filter(Specialty::active)
                .orElseThrow(() -> new BusinessRuleException("SPECIALTY_INACTIVE",
                        "La especialidad no existe o no está activa"));
        if (expectedFlow != null && specialty.appointmentType() != expectedFlow) {
            throw wrongFlow(expectedFlow);
        }
        Professional professional = professionals.findById(professionalId)
                .orElseThrow(() -> new NotFoundException("El profesional no existe"));
        if (!professional.active()) {
            throw new BusinessRuleException("PROFESSIONAL_INACTIVE", "El profesional no está disponible");
        }
        if (!professional.offers(specialty.id())) {
            throw new BusinessRuleException("SPECIALTY_NOT_ASSIGNED",
                    "El profesional no atiende la especialidad seleccionada");
        }
        if (!professional.worksAt(siteId)) {
            throw new BusinessRuleException("SITE_NOT_ASSIGNED", "El profesional no atiende en esa sede");
        }
        if (!LocalDateTime.of(date, start).isAfter(SystemZone.now(clock))) {
            throw new BusinessRuleException("PAST_TIME", "No se pueden reservar franjas en el pasado");
        }
        // Serializa la reserva con la edicion/borrado de la agenda del mismo profesional: sin esto,
        // borrar un bloque mientras se reserva uno de sus slots acababa en un 500 (verificacion S3, F7).
        // La doble reserva entre pacientes la sigue impidiendo la PK de slot_reservations.
        blocks.lockProfessional(professional.id());
        int slots = specialty.slotsRequired();
        AvailabilityBlock block = blocks.findByProfessionalAndDate(professional.id(), date).stream()
                .filter(b -> b.siteId() == siteId && b.canHost(start, slots))
                .findFirst()
                .orElseThrow(SlotAllocator::notAvailable);
        List<LocalTime> starts = IntStream.range(0, slots)
                .mapToObj(i -> start.plusMinutes((long) AvailabilityBlock.SLOT_MINUTES * i)).toList();
        List<Long> slotIds = blocks.slotIds(block.id(), starts);
        if (slotIds.size() != slots) {
            throw notAvailable();
        }
        return new Allocation(specialty, professional, slotIds, start.plusMinutes(specialty.durationMinutes()));
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
