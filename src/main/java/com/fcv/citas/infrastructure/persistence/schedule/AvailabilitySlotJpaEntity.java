package com.fcv.citas.infrastructure.persistence.schedule;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tabla {@code availability_slots}. {@code end_time} es columna generada (inicio + 30 min). */
@Entity
@Table(name = "availability_slots")
public class AvailabilitySlotJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "availability_block_id", nullable = false)
    private Long blockId;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    protected AvailabilitySlotJpaEntity() {
    }

    public AvailabilitySlotJpaEntity(Long blockId, LocalTime startTime) {
        this.blockId = blockId;
        this.startTime = startTime;
    }

    public Long getId() {
        return id;
    }
}
