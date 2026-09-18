package com.fcv.citas.domain.catalog;

import java.util.Locale;
import java.util.Objects;

import com.fcv.citas.domain.shared.ConflictException;
import com.fcv.citas.domain.shared.InvalidRequestException;

/**
 * Especialidad medica con duracion fija de 30 o 60 minutos (RF-09). La duracion se traduce en
 * slots de 30 minutos: 30 → 1 slot, 60 → 2 slots consecutivos (RN-05).
 *
 * <p>{@code MEDICINA_GENERAL} (semilla V6, decision D7) es la especialidad de la cita general
 * (RF-11): no se puede desactivar ni cambiar de tipo.</p>
 */
public record Specialty(
        Integer id,
        String code,
        String name,
        AppointmentType appointmentType,
        int durationMinutes,
        boolean active) {

    public static final String GENERAL_MEDICINE_CODE = "MEDICINA_GENERAL";
    public static final int SLOT_MINUTES = 30;

    public Specialty {
        if (code == null || code.isBlank()) {
            throw InvalidRequestException.field("code", "El código es obligatorio");
        }
        if (name == null || name.isBlank()) {
            throw InvalidRequestException.field("name", "El nombre es obligatorio");
        }
        Objects.requireNonNull(appointmentType, "appointmentType");
        if (durationMinutes != 30 && durationMinutes != 60) {
            throw InvalidRequestException.field("durationMinutes", "Solo se admiten 30 o 60 minutos");
        }
        code = normalizeCode(code);
        name = name.trim();
    }

    public static Specialty create(String code, String name, AppointmentType type, int durationMinutes) {
        return new Specialty(null, code, name, type, durationMinutes, true);
    }

    public static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    /** Numero de slots de 30 minutos que ocupa una cita de esta especialidad. */
    public int slotsRequired() {
        return durationMinutes / SLOT_MINUTES;
    }

    public boolean isProtected() {
        return GENERAL_MEDICINE_CODE.equals(code);
    }

    public Specialty withDetails(String newName, AppointmentType newType, int newDuration) {
        if (isProtected() && newType != appointmentType) {
            throw new ConflictException("PROTECTED_SPECIALTY",
                    "Medicina General es la especialidad de la cita general y no puede cambiar de tipo");
        }
        return new Specialty(id, code, newName, newType, newDuration, active);
    }

    public Specialty withActive(boolean newActive) {
        if (isProtected() && !newActive) {
            throw new ConflictException("PROTECTED_SPECIALTY",
                    "Medicina General no se puede desactivar: sin ella no hay citas generales");
        }
        return new Specialty(id, code, name, appointmentType, durationMinutes, newActive);
    }
}
