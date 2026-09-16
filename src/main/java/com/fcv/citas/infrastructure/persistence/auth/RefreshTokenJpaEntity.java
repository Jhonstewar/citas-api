package com.fcv.citas.infrastructure.persistence.auth;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tabla {@code refresh_tokens} (V1). Solo guarda el SHA-256 del token. */
@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, columnDefinition = "char(64)")
    private String tokenHash;

    @Column(name = "family_id", nullable = false, columnDefinition = "char(36)")
    private String familyId;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_reason", length = 100)
    private String revokedReason;

    @Column(name = "replaced_by_token_id")
    private Long replacedByTokenId;

    protected RefreshTokenJpaEntity() {
    }

    public RefreshTokenJpaEntity(Long id, Long userId, String tokenHash, String familyId, LocalDateTime issuedAt,
            LocalDateTime expiresAt, LocalDateTime usedAt, LocalDateTime revokedAt, String revokedReason,
            Long replacedByTokenId) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.usedAt = usedAt;
        this.revokedAt = revokedAt;
        this.revokedReason = revokedReason;
        this.replacedByTokenId = replacedByTokenId;
    }

    void updateLifecycle(LocalDateTime usedAt, LocalDateTime revokedAt, String revokedReason,
            Long replacedByTokenId) {
        this.usedAt = usedAt;
        this.revokedAt = revokedAt;
        this.revokedReason = revokedReason;
        this.replacedByTokenId = replacedByTokenId;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getFamilyId() {
        return familyId;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public String getRevokedReason() {
        return revokedReason;
    }

    public Long getReplacedByTokenId() {
        return replacedByTokenId;
    }
}
