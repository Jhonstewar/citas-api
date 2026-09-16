package com.fcv.citas.application.auth;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.fcv.citas.application.TransactionRunner;
import com.fcv.citas.domain.auth.AccessTokenIssuer;
import com.fcv.citas.domain.auth.IssuedAccessToken;
import com.fcv.citas.domain.auth.PasswordHasher;
import com.fcv.citas.domain.auth.RefreshToken;
import com.fcv.citas.domain.auth.RefreshTokenRepository;
import com.fcv.citas.domain.auth.SecureTokenGenerator;
import com.fcv.citas.domain.user.DocumentAlreadyRegisteredException;
import com.fcv.citas.domain.user.DocumentTypeCatalog;
import com.fcv.citas.domain.user.EmailAlreadyRegisteredException;
import com.fcv.citas.domain.user.User;
import com.fcv.citas.domain.user.UserRepository;

/** Dobles de prueba en memoria: sin Spring, sin base de datos. */
final class Fakes {

    private Fakes() {
    }

    static final TransactionRunner DIRECT_TX = new TransactionRunner() {
        @Override
        public <T> T inTransaction(Supplier<T> work) {
            return work.get();
        }
    };

    static final DocumentTypeCatalog DOCUMENT_TYPES = code -> List.of("CC", "CE", "TI", "PA", "RC").contains(code);

    static final class FakeHasher implements PasswordHasher {
        @Override
        public String hash(String rawPassword) {
            return "{fake}" + new StringBuilder(rawPassword).reverse();
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            return hash(rawPassword).equals(passwordHash);
        }
    }

    static final class InMemoryUsers implements UserRepository {
        final List<User> users = new ArrayList<>();

        @Override
        public boolean existsByEmail(String normalizedEmail) {
            return users.stream().anyMatch(u -> u.email().equals(normalizedEmail));
        }

        @Override
        public boolean existsByDocumentNumber(String documentNumber) {
            return users.stream().anyMatch(u -> u.documentNumber().equals(documentNumber));
        }

        @Override
        public User saveNew(User user) {
            if (existsByEmail(user.email())) {
                throw new EmailAlreadyRegisteredException();
            }
            if (existsByDocumentNumber(user.documentNumber())) {
                throw new DocumentAlreadyRegisteredException();
            }
            User saved = user.withId((long) users.size() + 1);
            users.add(saved);
            return saved;
        }

        @Override
        public Optional<User> findByEmail(String normalizedEmail) {
            return users.stream().filter(u -> u.email().equals(normalizedEmail)).findFirst();
        }

        @Override
        public Optional<User> findById(long id) {
            return users.stream().filter(u -> u.id() == id).findFirst();
        }

        void replace(User user) {
            users.replaceAll(u -> u.id().equals(user.id()) ? user : u);
        }
    }

    static final class InMemoryRefreshTokens implements RefreshTokenRepository {
        final List<RefreshToken> tokens = new ArrayList<>();

        @Override
        public RefreshToken save(RefreshToken token) {
            if (token.id() == null) {
                RefreshToken saved = token.withId((long) tokens.size() + 1);
                tokens.add(saved);
                return saved;
            }
            tokens.replaceAll(t -> t.id().equals(token.id()) ? token : t);
            return token;
        }

        @Override
        public Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash) {
            return tokens.stream().filter(t -> t.tokenHash().equals(tokenHash)).findFirst();
        }

        @Override
        public void revokeFamily(String familyId, Instant now, String reason) {
            tokens.replaceAll(t -> t.familyId().equals(familyId) ? t.revoke(now, reason) : t);
        }

        RefreshToken byId(long id) {
            return tokens.stream().filter(t -> t.id() == id).findFirst().orElseThrow();
        }
    }

    static final class SequentialTokens implements SecureTokenGenerator {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public String generate() {
            return "opaque-token-" + counter.incrementAndGet();
        }
    }

    static final class FakeAccessIssuer implements AccessTokenIssuer {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public IssuedAccessToken issue(User user, Instant issuedAt) {
            return new IssuedAccessToken("access-" + user.id() + "-" + counter.incrementAndGet(),
                    issuedAt.plusSeconds(900));
        }
    }
}
