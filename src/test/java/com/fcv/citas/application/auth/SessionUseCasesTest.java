package com.fcv.citas.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fcv.citas.domain.auth.InvalidCredentialsException;
import com.fcv.citas.domain.auth.InvalidRefreshTokenException;
import com.fcv.citas.domain.auth.RefreshToken;
import com.fcv.citas.domain.auth.RefreshTokenHasher;
import com.fcv.citas.domain.user.User;

/** Login, rotacion, deteccion de reuso y logout sin contexto Spring. */
class SessionUseCasesTest {

    private static final Instant NOW = Instant.parse("2026-09-16T12:00:00Z");

    private final MutableClock clock = new MutableClock(NOW);
    private Fakes.InMemoryUsers users;
    private Fakes.InMemoryRefreshTokens refreshTokens;
    private LoginUseCase login;
    private RefreshSessionUseCase refresh;
    private LogoutUseCase logout;
    private User user;

    @BeforeEach
    void setUp() {
        users = new Fakes.InMemoryUsers();
        refreshTokens = new Fakes.InMemoryRefreshTokens();
        Fakes.FakeHasher hasher = new Fakes.FakeHasher();
        SessionIssuer issuer = new SessionIssuer(new Fakes.FakeAccessIssuer(), new Fakes.SequentialTokens(),
                refreshTokens, Duration.ofDays(7), clock);
        login = new LoginUseCase(users, hasher, issuer, Fakes.DIRECT_TX);
        refresh = new RefreshSessionUseCase(refreshTokens, users, issuer, Fakes.DIRECT_TX, clock);
        logout = new LogoutUseCase(refreshTokens, Fakes.DIRECT_TX, clock);
        new RegisterUserUseCase(users, Fakes.DOCUMENT_TYPES, hasher, Fakes.DIRECT_TX)
                .register(new RegisterUserCommand("Ana", "Pérez", "CC", "1", "ana@example.com", "300", "Clave#1"));
        user = users.findByEmail("ana@example.com").orElseThrow();
    }

    @Test
    void loginIssuesDistinctTokensAndStoresOnlyRefreshHash() {
        AuthSession session = login.login("ANA@example.com", "Clave#1");

        assertThat(session.accessToken()).isNotBlank().isNotEqualTo(session.refreshToken());
        assertThat(session.expiresInSeconds()).isEqualTo(900);
        assertThat(refreshTokens.tokens).singleElement().satisfies(t -> {
            assertThat(t.tokenHash()).isNotEqualTo(session.refreshToken())
                    .isEqualTo(RefreshTokenHasher.sha256Hex(session.refreshToken()));
            assertThat(t.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        });
    }

    @Test
    void loginFailuresAreIndistinguishable() {
        assertThatThrownBy(() -> login.login("nadie@example.com", "Clave#1"))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Credenciales inválidas");
        assertThatThrownBy(() -> login.login("ana@example.com", "incorrecta"))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Credenciales inválidas");
        assertThat(refreshTokens.tokens).isEmpty();
    }

    @Test
    void inactiveUserCannotLogin() {
        users.replace(new User(user.id(), user.documentTypeCode(), user.documentNumber(), user.firstNames(),
                user.lastNames(), user.email(), user.phone(), user.passwordHash(), false, user.roles()));

        assertThatThrownBy(() -> login.login("ana@example.com", "Clave#1"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThat(refreshTokens.tokens).isEmpty();
    }

    @Test
    void refreshRotatesTokenWithinSameFamily() {
        AuthSession first = login.login("ana@example.com", "Clave#1");
        clock.advance(Duration.ofMinutes(5));

        AuthSession second = refresh.refresh(first.refreshToken());

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(second.accessToken()).isNotEqualTo(first.accessToken());
        RefreshToken old = refreshTokens.byId(1);
        RefreshToken replacement = refreshTokens.byId(2);
        assertThat(old.usedAt()).isEqualTo(clock.instant());
        assertThat(old.replacedById()).isEqualTo(replacement.id());
        assertThat(replacement.familyId()).isEqualTo(old.familyId());
        assertThat(replacement.isUsed()).isFalse();
    }

    @Test
    void reusingConsumedTokenRevokesWholeFamily() {
        AuthSession first = login.login("ana@example.com", "Clave#1");
        AuthSession second = refresh.refresh(first.refreshToken());

        assertThatThrownBy(() -> refresh.refresh(first.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(refreshTokens.tokens).hasSize(2).allSatisfy(t -> {
            assertThat(t.isRevoked()).isTrue();
            assertThat(t.revokedReason()).isEqualTo(RefreshToken.REASON_REUSE_DETECTED);
        });
        // El token legitimo mas reciente tambien queda inutilizable.
        assertThatThrownBy(() -> refresh.refresh(second.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void loginsOpenIndependentFamilies() {
        AuthSession a = login.login("ana@example.com", "Clave#1");
        AuthSession b = login.login("ana@example.com", "Clave#1");
        logout.logout(a.refreshToken());

        assertThat(refresh.refresh(b.refreshToken())).isNotNull();
    }

    @Test
    void unknownExpiredOrRevokedRefreshIsRejected() {
        assertThatThrownBy(() -> refresh.refresh("no-existe")).isInstanceOf(InvalidRefreshTokenException.class);

        AuthSession session = login.login("ana@example.com", "Clave#1");
        clock.advance(Duration.ofDays(7));
        assertThatThrownBy(() -> refresh.refresh(session.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);

        AuthSession other = login.login("ana@example.com", "Clave#1");
        logout.logout(other.refreshToken());
        assertThatThrownBy(() -> refresh.refresh(other.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void inactiveUserCannotRefresh() {
        AuthSession session = login.login("ana@example.com", "Clave#1");
        users.replace(new User(user.id(), user.documentTypeCode(), user.documentNumber(), user.firstNames(),
                user.lastNames(), user.email(), user.phone(), user.passwordHash(), false, user.roles()));

        assertThatThrownBy(() -> refresh.refresh(session.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logoutRevokesFamilyAndIsIdempotent() {
        AuthSession first = login.login("ana@example.com", "Clave#1");
        AuthSession second = refresh.refresh(first.refreshToken());

        logout.logout(second.refreshToken());
        Instant revokedAt = refreshTokens.byId(2).revokedAt();
        clock.advance(Duration.ofMinutes(1));
        logout.logout(second.refreshToken());
        logout.logout("token-inexistente");

        assertThat(refreshTokens.tokens).allSatisfy(t -> assertThat(t.isRevoked()).isTrue());
        assertThat(refreshTokens.byId(2).revokedAt()).isEqualTo(revokedAt);
        assertThat(refreshTokens.byId(2).revokedReason()).isEqualTo(RefreshToken.REASON_LOGOUT);
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
