package com.fcv.citas.infrastructure.persistence.affiliation;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tabla {@code affiliations} (V2). Solo referencia al plan: ni la EPS ni el regimen se copian
 * aqui (3FN, HU-009). {@code membership_number} y {@code ended_on} quedan nulos en el registro y
 * no se mapean; {@code created_at}/{@code updated_at} los rellena MySQL, y {@code current_marker}
 * es una columna generada que sostiene la unicidad de la afiliacion vigente.
 *
 * <p>Las claves foraneas se mapean como columnas planas: la afiliacion no navega al usuario ni al
 * plan, asi que una asociacion solo traeria cargas innecesarias.</p>
 */
@Entity
@Table(name = "affiliations")
public class AffiliationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "eps_plan_id", nullable = false)
    private Integer epsPlanId;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Column(name = "started_on", nullable = false)
    private LocalDate startedOn;

    protected AffiliationJpaEntity() {
    }

    public AffiliationJpaEntity(long userId, int epsPlanId, boolean current, LocalDate startedOn) {
        this.userId = userId;
        this.epsPlanId = epsPlanId;
        this.current = current;
        this.startedOn = startedOn;
    }

    public Long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public int getEpsPlanId() {
        return epsPlanId;
    }

    public boolean isCurrent() {
        return current;
    }

    public LocalDate getStartedOn() {
        return startedOn;
    }
}
