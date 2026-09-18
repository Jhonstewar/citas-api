package com.fcv.citas.domain.schedule;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Puerto de salida de los bloques de disponibilidad y sus slots (RF-08). */
public interface BlockRepository {

    /**
     * Bloquea la fila del profesional hasta el fin de la transaccion. Serializa las escrituras de
     * agenda de un mismo profesional: dos altas simultaneas no pueden solaparse entre si (HU-017 CA-04),
     * porque la base solo impide dos bloques con el mismo inicio.
     */
    void lockProfessional(long professionalId);

    List<AvailabilityBlock> findByProfessionalAndDate(long professionalId, LocalDate date);

    Optional<AvailabilityBlock> findById(long blockId);

    /** Inserta el bloque y sus slots de 30 minutos. */
    AvailabilityBlock saveNew(AvailabilityBlock block);

    /** Cambia sede, fecha y horas y regenera los slots. Solo para bloques sin reservas. */
    AvailabilityBlock replace(AvailabilityBlock block);

    void delete(long blockId);

    /** Cierto si algun slot del bloque esta reservado o retenido (HU-018 CA-02). */
    boolean hasReservations(long blockId);

    /** Ids de los slots del bloque que empiezan en {@code starts}, en ese orden. */
    List<Long> slotIds(long blockId, List<java.time.LocalTime> starts);
}
