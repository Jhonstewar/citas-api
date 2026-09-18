package com.fcv.citas.infrastructure.rest.professional;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fcv.citas.application.professional.ManageProfessionalsUseCase;
import com.fcv.citas.infrastructure.rest.CurrentUser;

/** Perfil propio del profesional autenticado: sus sedes y especialidades (para su agenda). */
@RestController
class ProfessionalMeController {

    private final ManageProfessionalsUseCase professionals;

    ProfessionalMeController(ManageProfessionalsUseCase professionals) {
        this.professionals = professionals;
    }

    @GetMapping("/api/professional/me")
    ProfessionalResponse me(JwtAuthenticationToken auth) {
        return ProfessionalResponse.from(professionals.me(CurrentUser.id(auth)));
    }
}
