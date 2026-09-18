package com.fcv.citas.application.schedule;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.fcv.citas.application.TransactionRunner;
import com.fcv.citas.application.schedule.ScheduleQueries.BlockView;
import com.fcv.citas.domain.professional.Professional;
import com.fcv.citas.domain.professional.ProfessionalRepository;
import com.fcv.citas.domain.schedule.AvailabilityBlock;
import com.fcv.citas.domain.schedule.BlockRepository;
import com.fcv.citas.domain.shared.BusinessRuleException;
import com.fcv.citas.domain.shared.ConflictException;
import com.fcv.citas.domain.shared.InvalidRequestException;
import com.fcv.citas.domain.shared.NotFoundException;
import com.fcv.citas.domain.shared.SystemZone;

/**
 * HU-017 a HU-019: el profesional publica, edita, elimina y consulta sus bloques. El titular sale
 * siempre del token (userId); un bloque ajeno responde como inexistente (HU-018 CA-06).
 */
public class ManageScheduleUseCase {

    public record BlockCommand(Integer siteId, LocalDate date, LocalTime startTime, LocalTime endTime) {
    }

    static final int MAX_RANGE_DAYS = 62;

    private final ProfessionalRepository professionals;
    private final BlockRepository blocks;
    private final ScheduleQueries queries;
    private final TransactionRunner tx;
    private final Clock clock;

    public ManageScheduleUseCase(ProfessionalRepository professionals, BlockRepository blocks,
            ScheduleQueries queries, TransactionRunner tx, Clock clock) {
        this.professionals = professionals;
        this.blocks = blocks;
        this.queries = queries;
        this.tx = tx;
        this.clock = clock;
    }

    public List<BlockView> calendar(long userId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw InvalidRequestException.field("to", "Indique un rango de fechas válido (desde ≤ hasta)");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw InvalidRequestException.field("to", "El rango máximo es de " + MAX_RANGE_DAYS + " días");
        }
        return queries.calendar(professionalOf(userId).id(), from, to);
    }

    public BlockView create(long userId, BlockCommand command) {
        long blockId = tx.inTransaction(() -> {
            Professional professional = professionalOf(userId);
            AvailabilityBlock block = validated(professional, null, command);
            return blocks.saveNew(block).id();
        });
        return view(blockId);
    }

    public BlockView update(long userId, long blockId, BlockCommand command) {
        tx.inTransaction(() -> {
            Professional professional = professionalOf(userId);
            requireModifiable(ownBlock(professional, blockId));
            blocks.replace(validated(professional, blockId, command));
            return null;
        });
        return view(blockId);
    }

    public void delete(long userId, long blockId) {
        tx.inTransaction(() -> {
            Professional professional = professionalOf(userId);
            requireModifiable(ownBlock(professional, blockId));
            blocks.delete(blockId);
            return null;
        });
    }

    /** Valida el bloque nuevo o editado: rejilla, pasado, sede asignada y solape (HU-017, HU-018). */
    private AvailabilityBlock validated(Professional professional, Long blockId, BlockCommand c) {
        if (c.siteId() == null) {
            throw InvalidRequestException.field("siteId", "Seleccione la sede");
        }
        if (c.date() == null) {
            throw InvalidRequestException.field("date", "Seleccione la fecha");
        }
        AvailabilityBlock block = new AvailabilityBlock(blockId, professional.id(), c.siteId(), c.date(),
                c.startTime(), c.endTime());
        if (block.hasStartedAt(now())) {
            throw new InvalidRequestException("PAST_TIME", "startTime",
                    "No se pueden publicar bloques en el pasado");
        }
        if (!professional.worksAt(block.siteId())) {
            throw new BusinessRuleException("SITE_NOT_ASSIGNED", "No está habilitado para atender en esa sede");
        }
        blocks.lockProfessional(professional.id());
        for (AvailabilityBlock existing : blocks.findByProfessionalAndDate(professional.id(), block.date())) {
            if (!existing.id().equals(blockId) && existing.overlaps(block)) {
                throw new ConflictException("BLOCK_OVERLAP", "Se cruza con otro bloque de "
                        + existing.startTime() + " a " + existing.endTime());
            }
        }
        return block;
    }

    private void requireModifiable(AvailabilityBlock block) {
        if (block.hasStartedAt(now())) {
            throw new InvalidRequestException("PAST_TIME", null, "Un bloque pasado no se puede modificar");
        }
        if (blocks.hasReservations(block.id())) {
            throw new ConflictException("BLOCK_HAS_APPOINTMENTS",
                    "El bloque tiene citas comprometidas y no se puede modificar ni eliminar");
        }
    }

    private AvailabilityBlock ownBlock(Professional professional, long blockId) {
        return blocks.findById(blockId).filter(b -> b.professionalId() == professional.id())
                .orElseThrow(() -> new NotFoundException("El bloque no existe"));
    }

    private Professional professionalOf(long userId) {
        return professionals.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("No hay un perfil profesional para esta cuenta"));
    }

    private BlockView view(long blockId) {
        return queries.findBlock(blockId).orElseThrow(() -> new NotFoundException("El bloque no existe"));
    }

    private LocalDateTime now() {
        return SystemZone.now(clock);
    }
}
