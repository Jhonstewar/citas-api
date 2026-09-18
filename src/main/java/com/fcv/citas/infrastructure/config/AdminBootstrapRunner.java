package com.fcv.citas.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.fcv.citas.application.user.BootstrapAdminUseCase;

/**
 * Decision D5: al arrancar, crea el primer ADMIN desde {@code ADMIN_BOOTSTRAP_EMAIL} y
 * {@code ADMIN_BOOTSTRAP_PASSWORD}. Nunca registra el email ni la contraseña, solo el id.
 */
@Component
class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final BootstrapAdminUseCase bootstrapAdmin;
    private final String email;
    private final String password;

    AdminBootstrapRunner(BootstrapAdminUseCase bootstrapAdmin,
            @Value("${app.admin-bootstrap.email:}") String email,
            @Value("${app.admin-bootstrap.password:}") String password) {
        this.bootstrapAdmin = bootstrapAdmin;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrapAdmin.bootstrap(email, password)
                .ifPresent(admin -> log.info("ADMIN inicial creado (id={})", admin.id()));
    }
}
