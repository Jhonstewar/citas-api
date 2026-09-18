package com.fcv.citas.infrastructure.persistence.schedule;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tabla {@code availability_blocks}. Marcas de tiempo las rellena MySQL. */
@Entity
@Table(name = "availability_blocks")
public class AvailabilityBlockJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "professional_id", nullable = false)
    private Long professionalId;

    @Column(name = "site_id", nullable = false)
    private Short siteId;

    @Column(name = "block_date", nullable = false)
    private LocalDate blockDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected AvailabilityBlockJpaEntity() {
    }

    public AvailabilityBlockJpaEntity(Long professionalId, int siteId, LocalDate blockDate, LocalTime startTime,
            LocalTime endTime) {
        this.professionalId = professionalId;
        this.siteId = (short) siteId;
        this.blockDate = blockDate;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void reschedule(int newSiteId, LocalDate newDate, LocalTime newStart, LocalTime newEnd) {
        this.siteId = (short) newSiteId;
        this.blockDate = newDate;
        this.startTime = newStart;
        this.endTime = newEnd;
    }

    public Long getId() {
        return id;
    }

    public Long getProfessionalId() {
        return professionalId;
    }

    public Short getSiteId() {
        return siteId;
    }

    public LocalDate getBlockDate() {
        return blockDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }
}
