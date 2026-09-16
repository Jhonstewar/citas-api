package com.fcv.citas.infrastructure.rest.me;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fcv.citas.application.auth.GetCurrentUserUseCase;
import com.fcv.citas.infrastructure.rest.auth.UserResponse;

/**
 * {@code GET /api/me}: datos basicos del usuario autenticado. Los roles se toman del contexto de
 * seguridad (authorities {@code ROLE_*} del access token) para evidenciar la conversion de claims.
 */
@RestController
class MeController {

    private static final String ROLE_PREFIX = "ROLE_";

    private final GetCurrentUserUseCase getCurrentUser;

    MeController(GetCurrentUserUseCase getCurrentUser) {
        this.getCurrentUser = getCurrentUser;
    }

    @GetMapping("/api/me")
    UserResponse me(JwtAuthenticationToken authentication) {
        long userId = Long.parseLong(authentication.getToken().getSubject());
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith(ROLE_PREFIX))
                .map(a -> a.substring(ROLE_PREFIX.length()))
                .toList();
        return UserResponse.from(getCurrentUser.get(userId), roles);
    }
}
