package com.fcv.citas.infrastructure.persistence.schedule;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;

import com.fcv.citas.domain.schedule.AvailabilityBlock;
import com.fcv.citas.domain.schedule.BlockRepository;
import com.fcv.citas.domain.shared.NotFoundException;

/** Adaptador JPA de {@link BlockRepository}: bloque + slots en la misma transaccion. */
@Component
class JpaBlockRepositoryAdapter implements BlockRepository {

    private final SpringDataBlockRepository blocks;
    private final SpringDataSlotRepository slots;
    private final EntityManager em;

    JpaBlockRepositoryAdapter(SpringDataBlockRepository blocks, SpringDataSlotRepository slots, EntityManager em) {
        this.blocks = blocks;
        this.slots = slots;
        this.em = em;
    }

    @Override
    public void lockProfessional(long professionalId) {
        em.createNativeQuery("SELECT id FROM professionals WHERE id = ? FOR UPDATE")
                .setParameter(1, professionalId).getResultList();
    }

    @Override
    public List<AvailabilityBlock> findByProfessionalAndDate(long professionalId, LocalDate date) {
        return blocks.findByProfessionalIdAndBlockDate(professionalId, date).stream()
                .map(JpaBlockRepositoryAdapter::toDomain).toList();
    }

    @Override
    public Optional<AvailabilityBlock> findById(long blockId) {
        return blocks.findById(blockId).map(JpaBlockRepositoryAdapter::toDomain);
    }

    @Override
    public AvailabilityBlock saveNew(AvailabilityBlock b) {
        AvailabilityBlockJpaEntity saved = blocks.saveAndFlush(new AvailabilityBlockJpaEntity(b.professionalId(),
                b.siteId(), b.date(), b.startTime(), b.endTime()));
        insertSlots(saved.getId(), b);
        return toDomain(saved);
    }

    @Override
    public AvailabilityBlock replace(AvailabilityBlock b) {
        slots.deleteAllOfBlock(b.id());
        AvailabilityBlockJpaEntity entity = blocks.findById(b.id())
                .orElseThrow(() -> new NotFoundException("El bloque no existe"));
        entity.reschedule(b.siteId(), b.date(), b.startTime(), b.endTime());
        AvailabilityBlockJpaEntity saved = blocks.saveAndFlush(entity);
        insertSlots(saved.getId(), b);
        return toDomain(saved);
    }

    @Override
    public void delete(long blockId) {
        // Los slots caen por ON DELETE CASCADE; el caso de uso ya comprobo que no hay reservas.
        blocks.deleteById(blockId);
        blocks.flush();
    }

    @Override
    public boolean hasReservations(long blockId) {
        Number count = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM slot_reservations r
                JOIN availability_slots s ON s.id = r.slot_id
                WHERE s.availability_block_id = ?
                """).setParameter(1, blockId).getSingleResult();
        return count.longValue() > 0;
    }

    @Override
    public List<Long> slotIds(long blockId, List<java.time.LocalTime> starts) {
        List<Long> ids = new java.util.ArrayList<>();
        for (java.time.LocalTime start : starts) {
            List<?> found = em.createNativeQuery(
                    "SELECT id FROM availability_slots WHERE availability_block_id = ? AND start_time = ?")
                    .setParameter(1, blockId).setParameter(2, start).getResultList();
            if (found.isEmpty()) {
                return List.of();
            }
            ids.add(((Number) found.get(0)).longValue());
        }
        return ids;
    }

    private void insertSlots(long blockId, AvailabilityBlock b) {
        slots.saveAllAndFlush(b.slotStarts().stream().map(t -> new AvailabilitySlotJpaEntity(blockId, t)).toList());
    }

    private static AvailabilityBlock toDomain(AvailabilityBlockJpaEntity e) {
        return new AvailabilityBlock(e.getId(), e.getProfessionalId(), e.getSiteId(), e.getBlockDate(),
                e.getStartTime(), e.getEndTime());
    }
}
