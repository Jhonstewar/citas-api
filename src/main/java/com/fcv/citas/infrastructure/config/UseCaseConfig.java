package com.fcv.citas.infrastructure.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fcv.citas.application.TransactionRunner;
import com.fcv.citas.application.auth.GetCurrentUserUseCase;
import com.fcv.citas.application.auth.LoginUseCase;
import com.fcv.citas.application.auth.LogoutUseCase;
import com.fcv.citas.application.auth.RefreshSessionUseCase;
import com.fcv.citas.application.auth.RegisterUserUseCase;
import com.fcv.citas.application.auth.SessionIssuer;
import com.fcv.citas.application.appointment.AppointmentQueries;
import com.fcv.citas.application.appointment.AvailabilityQueries;
import com.fcv.citas.application.appointment.BookAppointmentUseCase;
import com.fcv.citas.application.appointment.SearchAvailabilityUseCase;
import com.fcv.citas.domain.appointment.AppointmentRepository;
import com.fcv.citas.application.appointment.PatientAppointmentsUseCase;
import com.fcv.citas.application.catalog.ManageSpecialtiesUseCase;
import com.fcv.citas.application.professional.ManageProfessionalsUseCase;
import com.fcv.citas.application.professional.ProfessionalQueries;
import com.fcv.citas.application.schedule.ManageScheduleUseCase;
import com.fcv.citas.application.schedule.ScheduleQueries;
import com.fcv.citas.domain.catalog.SiteCatalog;
import com.fcv.citas.domain.schedule.BlockRepository;
import com.fcv.citas.domain.professional.ProfessionalRepository;
import com.fcv.citas.application.user.BootstrapAdminUseCase;
import com.fcv.citas.domain.catalog.SpecialtyRepository;
import com.fcv.citas.domain.auth.AccessTokenIssuer;
import com.fcv.citas.domain.auth.PasswordHasher;
import com.fcv.citas.domain.auth.RefreshTokenRepository;
import com.fcv.citas.domain.auth.SecureTokenGenerator;
import com.fcv.citas.domain.user.DocumentTypeCatalog;
import com.fcv.citas.domain.user.UserRepository;
import com.fcv.citas.infrastructure.security.JwtProperties;

/** Ensambla los casos de uso (clases Java puras) con los adaptadores de infraestructura. */
@Configuration
public class UseCaseConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RegisterUserUseCase registerUserUseCase(UserRepository users, DocumentTypeCatalog documentTypes,
            PasswordHasher passwordHasher, TransactionRunner tx) {
        return new RegisterUserUseCase(users, documentTypes, passwordHasher, tx);
    }

    @Bean
    SessionIssuer sessionIssuer(AccessTokenIssuer accessTokenIssuer, SecureTokenGenerator tokenGenerator,
            RefreshTokenRepository refreshTokens, JwtProperties jwtProperties, Clock clock) {
        return new SessionIssuer(accessTokenIssuer, tokenGenerator, refreshTokens, jwtProperties.refreshTtl(),
                clock);
    }

    @Bean
    LoginUseCase loginUseCase(UserRepository users, PasswordHasher passwordHasher, SessionIssuer sessionIssuer,
            TransactionRunner tx) {
        return new LoginUseCase(users, passwordHasher, sessionIssuer, tx);
    }

    @Bean
    RefreshSessionUseCase refreshSessionUseCase(RefreshTokenRepository refreshTokens, UserRepository users,
            SessionIssuer sessionIssuer, TransactionRunner tx, Clock clock) {
        return new RefreshSessionUseCase(refreshTokens, users, sessionIssuer, tx, clock);
    }

    @Bean
    LogoutUseCase logoutUseCase(RefreshTokenRepository refreshTokens, TransactionRunner tx, Clock clock) {
        return new LogoutUseCase(refreshTokens, tx, clock);
    }

    @Bean
    GetCurrentUserUseCase getCurrentUserUseCase(UserRepository users) {
        return new GetCurrentUserUseCase(users);
    }

    // ------------------------------------------------------------------ S3

    @Bean
    ManageSpecialtiesUseCase manageSpecialtiesUseCase(SpecialtyRepository specialties, TransactionRunner tx) {
        return new ManageSpecialtiesUseCase(specialties, tx);
    }

    @Bean
    ManageProfessionalsUseCase manageProfessionalsUseCase(UserRepository users,
            ProfessionalRepository professionals, ProfessionalQueries queries, SpecialtyRepository specialties,
            SiteCatalog sites, DocumentTypeCatalog documentTypes, PasswordHasher passwordHasher,
            TransactionRunner tx) {
        return new ManageProfessionalsUseCase(users, professionals, queries, specialties, sites, documentTypes,
                passwordHasher, tx);
    }

    @Bean
    ManageScheduleUseCase manageScheduleUseCase(ProfessionalRepository professionals, BlockRepository blocks,
            ScheduleQueries queries, TransactionRunner tx, Clock clock) {
        return new ManageScheduleUseCase(professionals, blocks, queries, tx, clock);
    }

    @Bean
    BookAppointmentUseCase bookAppointmentUseCase(SpecialtyRepository specialties,
            ProfessionalRepository professionals, BlockRepository blocks, AppointmentRepository appointments,
            AppointmentQueries queries, TransactionRunner tx, Clock clock) {
        return new BookAppointmentUseCase(specialties, professionals, blocks, appointments, queries, tx, clock);
    }

    @Bean
    SearchAvailabilityUseCase searchAvailabilityUseCase(AvailabilityQueries queries, Clock clock) {
        return new SearchAvailabilityUseCase(queries, clock);
    }

    @Bean
    PatientAppointmentsUseCase patientAppointmentsUseCase(AppointmentQueries queries) {
        return new PatientAppointmentsUseCase(queries);
    }

    @Bean
    BootstrapAdminUseCase bootstrapAdminUseCase(UserRepository users, PasswordHasher passwordHasher,
            TransactionRunner tx) {
        return new BootstrapAdminUseCase(users, passwordHasher, tx);
    }
}
