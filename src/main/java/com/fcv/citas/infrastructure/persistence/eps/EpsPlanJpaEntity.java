package com.fcv.citas.infrastructure.persistence.eps;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tabla {@code eps_plans} (V2, V9). Las FK a la EPS y al regimen se mapean como columnas planas: el
 * plan no navega, y el regimen se traduce por codigo en el adaptador.
 */
@Entity
@Table(name = "eps_plans")
public class EpsPlanJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "eps_id", nullable = false)
    private Integer epsId;

    @Column(name = "regime_id", nullable = false)
    private Short regimeId;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false)
    private boolean active;

    protected EpsPlanJpaEntity() {
    }

    EpsPlanJpaEntity(int epsId, short regimeId, String code, String name, boolean active) {
        this.epsId = epsId;
        this.regimeId = regimeId;
        this.code = code;
        this.name = name;
        this.active = active;
    }

    void update(short regimeId, String name, boolean active) {
        this.regimeId = regimeId;
        this.name = name;
        this.active = active;
    }

    public Integer getId() {
        return id;
    }

    public int getEpsId() {
        return epsId;
    }

    public short getRegimeId() {
        return regimeId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}
