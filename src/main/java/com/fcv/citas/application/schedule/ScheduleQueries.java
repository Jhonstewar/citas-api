package com.fcv.citas.application.schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fcv.citas.application.shared.Refs.SiteRef;

/** Puerto de lectura del calendario del profesional (HU-019). */
public interface ScheduleQueries {

    record SlotView(long id, LocalTime startTime, LocalTime endTime, boolean available) {
    }

    /** {@code reserved}: algun slot tiene reserva o retencion. */
    record BlockView(long id, LocalDate date, LocalTime startTime, LocalTime endTime, SiteRef site,
            boolean reserved, List<SlotView> slots) {
    }

    /** Bloques del profesional entre {@code from} y {@code to} (inclusive), por fecha y hora. */
    List<BlockView> calendar(long professionalId, LocalDate from, LocalDate to);

    java.util.Optional<BlockView> findBlock(long blockId);
}
