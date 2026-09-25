package com.fcv.citas.infrastructure.persistence.user;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Tabla {@code users}. {@code created_at}/{@code updated_at} no se mapean: los rellena MySQL.
 */
@Entity
@Table(name = "users")
public class UserJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "document_type_id", nullable = false)
    private DocumentTypeJpaEntity documentType;

    @Column(name = "document_number", nullable = false, length = 20)
    private String documentNumber;

    @Column(name = "first_names", nullable = false, length = 100)
    private String firstNames;

    @Column(name = "last_names", nullable = false, length = 100)
    private String lastNames;

    @Column(name = "email", nullable = false, length = 160)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "active", nullable = false)
    private boolean active;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<RoleJpaEntity> roles = new HashSet<>();

    protected UserJpaEntity() {
    }

    public UserJpaEntity(DocumentTypeJpaEntity documentType, String documentNumber, String firstNames,
            String lastNames, String email, String phone, String passwordHash, boolean active,
            Set<RoleJpaEntity> roles) {
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.firstNames = firstNames;
        this.lastNames = lastNames;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.active = active;
        this.roles = new HashSet<>(roles);
    }

    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    public void updateContact(String newFirstNames, String newLastNames, String newPhone) {
        this.firstNames = newFirstNames;
        this.lastNames = newLastNames;
        this.phone = newPhone;
    }

    public Long getId() {
        return id;
    }

    public DocumentTypeJpaEntity getDocumentType() {
        return documentType;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public String getFirstNames() {
        return firstNames;
    }

    public String getLastNames() {
        return lastNames;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isActive() {
        return active;
    }

    public Set<RoleJpaEntity> getRoles() {
        return roles;
    }
}
