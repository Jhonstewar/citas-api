package com.fcv.citas.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fcv.citas.domain.auth.InvalidResetTokenException;
import com.fcv.citas.domain.auth.PasswordResetToken;
import com.fcv.citas.domain.auth.RefreshToken;
import com.fcv.citas.domain.auth.RefreshTokenHasher;
import com.fcv.citas.domain.shared.InvalidRequestException;
import com.fcv.citas.domain.user.User;

/** HU-006 / HU-007 sin Spring: emision, un solo uso, caducidad, revocacion y D34. */
class PasswordRecoveryUseCasesTest {

    private static final Instant NOW = Instant.parse("2026-09-25T15:00:00Z");
    private static final String OLD_PASSWORD = "Clave-Vieja#1";
    private static final String NEW_PASSWORD = "Clave-Nueva#2";

    private final MutableClock clock = new MutableClock(NOW);
    private final Fakes.FakeHasher hasher = new Fakes.FakeHasher();
    private Fakes.InMemoryUsers users;
    private Fakes.InMemoryRefreshTokens refreshTokens;
    private Fakes.InMemoryPasswordResetTokens resetTokens;
    private Fakes.RecordingNotifier notifier;
    private LoginUseCase login;
    private User user;

    @BeforeEach
    void setUp() {
        users = new Fakes.InMemoryUsers();
        refreshTokens = new Fakes.InMemoryRefreshTokens();
        resetTokens = new Fakes.InMemoryPasswordResetTokens();
        notifier = new Fakes.RecordingNotifier();
        SessionIssuer issuer = new SessionIssuer(new Fakes.FakeAccessIssuer(), new Fakes.SequentialTokens(),
                refreshTokens, Duration.ofDays(7), clock);
        login = new LoginUseCase(users, hasher, issuer, Fakes.DIRECT_TX);
        user = new RegisterUserUseCase(users, Fakes.DOCUMENT_TYPES, hasher, Fakes.plans(),
                new Fakes.InMemoryAffiliations(), Fakes.DIRECT_TX, Fakes.FIXED_CLOCK)
                .register(new RegisterUserCommand("Ana", "Pérez", "CC", "1", "ana@example.com", "300", OLD_PASSWORD,
                        null));
    }

    private RequestPasswordRecoveryUseCase recovery(boolean expose) {
        return new RequestPasswordRecoveryUseCase(users, resetTokens, new Fakes.SequentialTokens(), notifier,
                Fakes.DIRECT_TX, clock, Duration.ofMinutes(30), expose);
    }

    private ResetPasswordUseCase reset() {
        return new ResetPasswordUseCase(resetTokens, users, refreshTokens, hasher, Fakes.DIRECT_TX, clock);
    }

    private String currentHash() {
        return users.findById(user.id()).orElseThrow().passwordHash();
    }

    // ------------------------------------------------------------------ HU-006

    @Test
    void registeredEmailGetsAHashedTokenValidFor30MinutesAndDeliveredThroughThePort() {
        RequestPasswordRecoveryUseCase.Result result = recovery(false).request("  ANA@example.com ");

        assertThat(result.devToken()).isEmpty();
        String delivered = notifier.delivered.getFirst();
        assertThat(resetTokens.tokens).singleElement().satisfies(t -> {
            assertThat(t.userId()).isEqualTo(user.id());
            assertThat(t.tokenHash()).isNotEqualTo(delivered).isEqualTo(RefreshTokenHasher.sha256Hex(delivered));
            assertThat(t.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
            assertThat(t.usedAt()).isNull();
            assertThat(t.revokedAt()).isNull();
        });
    }

    @Test
    void unknownOrInactiveEmailCreatesNothingAndReturnsTheSameResult() {
        RequestPasswordRecoveryUseCase.Result unknown = recovery(false).request("nadie@example.com");
        users.replace(new User(user.id(), user.documentTypeCode(), user.documentNumber(), user.firstNames(),
                user.lastNames(), user.email(), user.phone(), user.passwordHash(), false, user.roles()));
        RequestPasswordRecoveryUseCase.Result inactive = recovery(false).request("ana@example.com");

        assertThat(unknown).isEqualTo(inactive);
        assertThat(unknown.devToken()).isEmpty();
        assertThat(resetTokens.tokens).isEmpty();
        assertThat(notifier.delivered).isEmpty();
    }

    @Test
    void exposureOnlyReturnsTheTokenForAnExistingEmail() {
        assertThat(recovery(true).request("nadie@example.com").devToken()).isEmpty();

        RequestPasswordRecoveryUseCase.Result result = recovery(true).request("ana@example.com");
        assertThat(result.devToken()).contains(notifier.delivered.getFirst());
        assertThat(result.toString()).doesNotContain(notifier.delivered.getFirst());
    }

    @Test
    void requestingANewTokenRevokesThePreviousOnes() {
        RequestPasswordRecoveryUseCase useCase = recovery(true);
        String first = useCase.request("ana@example.com").devToken().orElseThrow();
        String second = useCase.request("ana@example.com").devToken().orElseThrow();

        assertThat(resetTokens.tokens).hasSize(2);
        assertThat(resetTokens.tokens.get(0).revokedAt()).isEqualTo(NOW);
        assertThat(resetTokens.tokens.get(0).revokedReason()).isEqualTo(PasswordResetToken.REASON_SUPERSEDED);
        assertThat(resetTokens.tokens.get(1).revokedAt()).isNull();

        assertThatThrownBy(() -> reset().reset(first, NEW_PASSWORD)).isInstanceOf(InvalidResetTokenException.class);
        reset().reset(second, NEW_PASSWORD);
    }

    // ------------------------------------------------------------------ HU-007

    @Test
    void resetChangesThePasswordConsumesTheTokenAndRevokesEverySession() {
        AuthSession phone = login.login("ana@example.com", OLD_PASSWORD);
        AuthSession laptop = login.login("ana@example.com", OLD_PASSWORD);
        String token = recovery(true).request("ana@example.com").devToken().orElseThrow();
        String before = currentHash();

        reset().reset(token, NEW_PASSWORD);

        assertThat(currentHash()).isNotEqualTo(before).isEqualTo(hasher.hash(NEW_PASSWORD));
        assertThat(resetTokens.tokens.getFirst().usedAt()).isEqualTo(NOW);
        // D34: las dos familias (dos dispositivos) quedan revocadas.
        assertThat(refreshTokens.tokens).hasSize(2).allSatisfy(t -> {
            assertThat(t.revokedAt()).isEqualTo(NOW);
            assertThat(t.revokedReason()).isEqualTo(RefreshToken.REASON_PASSWORD_RESET);
        });
        assertThat(phone.refreshToken()).isNotEqualTo(laptop.refreshToken());
        assertThat(login.login("ana@example.com", NEW_PASSWORD).accessToken()).isNotBlank();
    }

    @Test
    void aTokenServesOnlyOnce() {
        String token = recovery(true).request("ana@example.com").devToken().orElseThrow();
        reset().reset(token, NEW_PASSWORD);
        String afterFirst = currentHash();

        assertThatThrownBy(() -> reset().reset(token, "Otra-Clave#3")).isInstanceOf(InvalidResetTokenException.class);
        assertThat(currentHash()).isEqualTo(afterFirst);
    }

    @Test
    void expiredUnknownAndBlankTokensAreRejectedTheSameWay() {
        String token = recovery(true).request("ana@example.com").devToken().orElseThrow();
        String before = currentHash();
        clock.set(NOW.plus(Duration.ofMinutes(30)));

        assertThatThrownBy(() -> reset().reset(token, NEW_PASSWORD))
                .isInstanceOfSatisfying(InvalidResetTokenException.class,
                        e -> assertThat(e.code()).isEqualTo("RESET_TOKEN_INVALID"));
        assertThatThrownBy(() -> reset().reset("inventado", NEW_PASSWORD))
                .isInstanceOf(InvalidResetTokenException.class);
        assertThatThrownBy(() -> reset().reset(" ", NEW_PASSWORD)).isInstanceOf(InvalidResetTokenException.class);
        assertThat(currentHash()).isEqualTo(before);
    }

    @Test
    void aPasswordOutsideThePolicyDoesNotConsumeTheToken() {
        String token = recovery(true).request("ana@example.com").devToken().orElseThrow();

        for (String weak : new String[] { "abc123", "abcdefgh", "12345678", "", " " }) {
            assertThatThrownBy(() -> reset().reset(token, weak))
                    .isInstanceOfSatisfying(InvalidRequestException.class,
                            e -> assertThat(e.field()).isEqualTo("newPassword"));
        }
        assertThat(resetTokens.tokens.getFirst().usedAt()).isNull();
        reset().reset(token, NEW_PASSWORD);
    }

    @Test
    void anInactiveAccountCannotReset() {
        String token = recovery(true).request("ana@example.com").devToken().orElseThrow();
        users.replace(new User(user.id(), user.documentTypeCode(), user.documentNumber(), user.firstNames(),
                user.lastNames(), user.email(), user.phone(), user.passwordHash(), false, user.roles()));

        assertThatThrownBy(() -> reset().reset(token, NEW_PASSWORD)).isInstanceOf(InvalidResetTokenException.class);
        assertThat(resetTokens.tokens.getFirst().usedAt()).isNull();
    }

    /** Reloj que la prueba mueve a mano. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
