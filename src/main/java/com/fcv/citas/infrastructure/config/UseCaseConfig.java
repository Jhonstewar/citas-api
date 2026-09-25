package com.fcv.citas.infrastructure.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fcv.citas.application.TransactionRunner;
import com.fcv.citas.domain.affiliation.AffiliationRepository;
import com.fcv.citas.domain.affiliation.InsurancePlanCatalog;
import com.fcv.citas.application.auth.GetCurrentUserUseCase;
import com.fcv.citas.application.auth.LoginUseCase;
import com.fcv.citas.application.auth.LogoutUseCase;
import com.fcv.citas.application.auth.RefreshSessionUseCase;
import com.fcv.citas.application.auth.RegisterUserUseCase;
import com.fcv.citas.application.auth.SessionIssuer;
import com.fcv.citas.application.appointment.AdminAppointmentsUseCase;
import com.fcv.citas.application.appointment.AppointmentQueries;
import com.fcv.citas.application.appointment.AvailabilityQueries;
import com.fcv.citas.application.appointment.BookAppointmentUseCase;
import com.fcv.citas.application.appointment.CancelAppointmentUseCase;
import com.fcv.citas.application.appointment.SearchAvailabilityUseCase;
import com.fcv.citas.domain.appointment.AppointmentRepository;
import com.fcv.citas.application.appointment.PatientAppointmentsUseCase;
import com.fcv.citas.application.appointment.RescheduleAppointmentUseCase;
import com.fcv.citas.domain.appointment.RescheduleRequestRepository;
import com.fcv.citas.application.appointment.ProfessionalAppointmentsUseCase;
import com.fcv.citas.application.affiliation.AffiliationQueries;
import com.fcv.citas.application.affiliation.ManageAffiliationUseCase;
import com.fcv.citas.application.eps.EpsQueries;
import com.fcv.citas.application.eps.ManageEpsUseCase;
import com.fcv.citas.application.user.ProfileUseCase;
import com.fcv.citas.domain.eps.EpsPlanRepository;
import com.fcv.citas.domain.eps.EpsRepository;
import com.fcv.citas.domain.eps.RegimeCatalog;
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
import com.fcv.citas.application.auth.RequestPasswordRecoveryUseCase;
import com.fcv.citas.application.auth.ResetPasswordUseCase;
import com.fcv.citas.domain.auth.PasswordResetNotifier;
import com.fcv.citas.domain.auth.PasswordResetTokenRepository;
import com.fcv.citas.infrastructure.security.JwtProperties;
import com.fcv.citas.infrastructure.security.PasswordResetProperties;

/** Ensambla los casos de uso (clases Java puras) con los adaptadores de infraestructura. */
@Configuration
public class UseCaseConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RegisterUserUseCase registerUserUseCase(UserRepository users, DocumentTypeCatalog documentTypes,
            PasswordHasher passwordHasher, InsurancePlanCatalog insurancePlans,
            AffiliationRepository affiliations, TransactionRunner tx, Clock clock) {
        return new RegisterUserUseCase(users, documentTypes, passwordHasher, insurancePlans, affiliations, tx,
                clock);
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
    AdminAppointmentsUseCase adminAppointmentsUseCase(AppointmentRepository appointments,
            AppointmentQueries queries, TransactionRunner tx, Clock clock) {
        return new AdminAppointmentsUseCase(appointments, queries, tx, clock);
    }

    @Bean
    SearchAvailabilityUseCase searchAvailabilityUseCase(AvailabilityQueries queries, Clock clock) {
        return new SearchAvailabilityUseCase(queries, clock);
    }

    @Bean
    PatientAppointmentsUseCase patientAppointmentsUseCase(AppointmentQueries queries, Clock clock) {
        return new PatientAppointmentsUseCase(queries, clock);
    }

    // ------------------------------------------------------------------ S4

    @Bean
    CancelAppointmentUseCase cancelAppointmentUseCase(AppointmentRepository appointments,
            RescheduleRequestRepository reschedules, PatientAppointmentsUseCase patientAppointments,
            TransactionRunner tx, Clock clock) {
        return new CancelAppointmentUseCase(appointments, reschedules, patientAppointments, tx, clock);
    }

    /** HU-027 y HU-031: pedir, aprobar y rechazar una reprogramacion. */
    @Bean
    RescheduleAppointmentUseCase rescheduleAppointmentUseCase(SpecialtyRepository specialties,
            ProfessionalRepository professionals, BlockRepository blocks, AppointmentRepository appointments,
            RescheduleRequestRepository reschedules, AppointmentQueries queries, AdminAppointmentsUseCase admin,
            TransactionRunner tx, Clock clock) {
        return new RescheduleAppointmentUseCase(specialties, professionals, blocks, appointments, reschedules,
                queries, admin, tx, clock);
    }

    /** HU-020 y HU-021: agenda de citas aprobadas y cierre de atencion del profesional. */
    @Bean
    ProfessionalAppointmentsUseCase professionalAppointmentsUseCase(ProfessionalRepository professionals,
            AppointmentRepository appointments, AppointmentQueries queries, TransactionRunner tx, Clock clock) {
        return new ProfessionalAppointmentsUseCase(professionals, appointments, queries, tx, clock);
    }

    /** HU-012: CRUD de EPS y planes (D28, D33). */
    @Bean
    ManageEpsUseCase manageEpsUseCase(EpsRepository eps, EpsPlanRepository plans, RegimeCatalog regimes,
            EpsQueries queries, TransactionRunner tx) {
        return new ManageEpsUseCase(eps, plans, regimes, queries, tx);
    }

    /** HU-009 segundo corte (D26): afiliacion desde el perfil. */
    @Bean
    ManageAffiliationUseCase manageAffiliationUseCase(AffiliationRepository affiliations,
            InsurancePlanCatalog insurancePlans, AffiliationQueries queries, TransactionRunner tx, Clock clock) {
        return new ManageAffiliationUseCase(affiliations, insurancePlans, queries, tx, clock);
    }

    /** HU-008 (D25): perfil propio con la afiliacion vigente. */
    @Bean
    ProfileUseCase profileUseCase(UserRepository users, AffiliationQueries affiliations, TransactionRunner tx) {
        return new ProfileUseCase(users, affiliations, tx);
    }

    /** HU-006 · D27: vigencia y exposicion de laboratorio salen de PasswordResetProperties. */
    @Bean
    RequestPasswordRecoveryUseCase requestPasswordRecoveryUseCase(UserRepository users,
            PasswordResetTokenRepository resetTokens, SecureTokenGenerator tokenGenerator,
            PasswordResetNotifier notifier, TransactionRunner tx, Clock clock, PasswordResetProperties properties) {
        return new RequestPasswordRecoveryUseCase(users, resetTokens, tokenGenerator, notifier, tx, clock,
                properties.ttl(), properties.exposeToken());
    }

    /** HU-007 · D34: restablecer revoca todas las familias de refresh del usuario. */
    @Bean
    ResetPasswordUseCase resetPasswordUseCase(PasswordResetTokenRepository resetTokens, UserRepository users,
            RefreshTokenRepository refreshTokens, PasswordHasher passwordHasher, TransactionRunner tx, Clock clock) {
        return new ResetPasswordUseCase(resetTokens, users, refreshTokens, passwordHasher, tx, clock);
    }

    @Bean
    BootstrapAdminUseCase bootstrapAdminUseCase(UserRepository users, PasswordHasher passwordHasher,
            TransactionRunner tx) {
        return new BootstrapAdminUseCase(users, passwordHasher, tx);
    }
}
