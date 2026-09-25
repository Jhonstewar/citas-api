package com.fcv.citas.infrastructure.persistence.eps;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tabla {@code eps} (V2, V9). {@code created_at}/{@code updated_at} los rellena MySQL. */
@Entity
@Table(name = "eps")
public class EpsJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false)
    private boolean active;

    protected EpsJpaEntity() {
    }

    EpsJpaEntity(String code, String name, boolean active) {
        this.code = code;
        this.name = name;
        this.active = active;
    }

    void update(String name, boolean active) {
        this.name = name;
        this.active = active;
    }

    public Integer getId() {
        return id;
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
