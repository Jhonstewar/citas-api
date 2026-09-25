package com.fcv.citas.infrastructure.rest.admin;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fcv.citas.application.eps.EpsQueries.EpsPlanView;
import com.fcv.citas.application.eps.EpsQueries.EpsView;
import com.fcv.citas.application.eps.ManageEpsUseCase;

/**
 * HU-012: CRUD de EPS ({@code /api/admin/eps}) y de sus planes ({@code /api/admin/eps/{id}/plans},
 * {@code /api/admin/eps-plans/{id}}). Solo ADMIN (regla de prefijo en {@code SecurityConfig}). Los
 * cuerpos de respuesta son {@code Eps} y {@code EpsPlan} del contrato S4.
 */
@RestController
@RequestMapping("/api/admin")
class AdminEpsController {

    record CreateEpsRequest(
            @NotBlank(message = "El código es obligatorio") @Size(max = 20, message = "máximo 20 caracteres") String code,
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 160, message = "máximo 160 caracteres") String name) {
    }

    record UpdateEpsRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 160, message = "máximo 160 caracteres") String name) {
    }

    record CreatePlanRequest(
            @NotBlank(message = "El código es obligatorio") @Size(max = 30, message = "máximo 30 caracteres") String code,
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 160, message = "máximo 160 caracteres") String name,
            @NotBlank(message = "Seleccione el régimen") String regimeCode) {
    }

    record UpdatePlanRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 160, message = "máximo 160 caracteres") String name,
            @NotBlank(message = "Seleccione el régimen") String regimeCode) {
    }

    private final ManageEpsUseCase eps;

    AdminEpsController(ManageEpsUseCase eps) {
        this.eps = eps;
    }

    // ------------------------------------------------------------------ EPS

    @GetMapping("/eps")
    List<EpsView> list() {
        return eps.list();
    }

    /** Aclaracion 9 del contrato S4: 200 {@code Eps} · 404. */
    @GetMapping("/eps/{id}")
    EpsView get(@PathVariable int id) {
        return eps.get(id);
    }

    @PostMapping("/eps")
    @ResponseStatus(HttpStatus.CREATED)
    EpsView create(@Valid @RequestBody CreateEpsRequest request) {
        return eps.create(request.code(), request.name());
    }

    @PutMapping("/eps/{id}")
    EpsView rename(@PathVariable int id, @Valid @RequestBody UpdateEpsRequest request) {
        return eps.rename(id, request.name());
    }

    @PatchMapping("/eps/{id}/status")
    EpsView setStatus(@PathVariable int id, @Valid @RequestBody ActiveRequest request) {
        return eps.setActive(id, request.active());
    }

    /** 204 · 404 · 409 EPS_REFERENCED (D28). */
    @DeleteMapping("/eps/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable int id) {
        eps.delete(id);
    }

    // ------------------------------------------------------------------ planes

    @GetMapping("/eps/{id}/plans")
    List<EpsPlanView> plans(@PathVariable int id) {
        return eps.plans(id);
    }

    @PostMapping("/eps/{id}/plans")
    @ResponseStatus(HttpStatus.CREATED)
    EpsPlanView createPlan(@PathVariable int id, @Valid @RequestBody CreatePlanRequest request) {
        return eps.createPlan(id, request.code(), request.name(), request.regimeCode());
    }

    @PutMapping("/eps-plans/{id}")
    EpsPlanView updatePlan(@PathVariable int id, @Valid @RequestBody UpdatePlanRequest request) {
        return eps.updatePlan(id, request.name(), request.regimeCode());
    }

    @PatchMapping("/eps-plans/{id}/status")
    EpsPlanView setPlanStatus(@PathVariable int id, @Valid @RequestBody ActiveRequest request) {
        return eps.setPlanActive(id, request.active());
    }

    /** 204 · 404 · 409 PLAN_REFERENCED (D28). */
    @DeleteMapping("/eps-plans/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deletePlan(@PathVariable int id) {
        eps.deletePlan(id);
    }
}
