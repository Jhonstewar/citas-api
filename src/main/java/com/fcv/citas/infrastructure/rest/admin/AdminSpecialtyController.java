package com.fcv.citas.infrastructure.rest.admin;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

import com.fcv.citas.application.catalog.ManageSpecialtiesUseCase;
import com.fcv.citas.domain.catalog.AppointmentType;
import com.fcv.citas.infrastructure.rest.catalog.SpecialtyResponse;

/** HU-011: CRUD de especialidades. Solo ADMIN (regla de prefijo en {@code SecurityConfig}). */
@RestController
@RequestMapping("/api/admin/specialties")
class AdminSpecialtyController {

    record CreateSpecialtyRequest(
            @NotBlank(message = "El código es obligatorio") @Size(max = 40, message = "máximo 40 caracteres") String code,
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 120, message = "máximo 120 caracteres") String name,
            @NotNull(message = "El tipo de cita es obligatorio") AppointmentType appointmentType,
            @NotNull(message = "La duración es obligatoria") Integer durationMinutes) {
    }

    record UpdateSpecialtyRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 120, message = "máximo 120 caracteres") String name,
            @NotNull(message = "El tipo de cita es obligatorio") AppointmentType appointmentType,
            @NotNull(message = "La duración es obligatoria") Integer durationMinutes) {
    }

    private final ManageSpecialtiesUseCase specialties;

    AdminSpecialtyController(ManageSpecialtiesUseCase specialties) {
        this.specialties = specialties;
    }

    @GetMapping
    List<SpecialtyResponse> list() {
        return specialties.listAll().stream().map(SpecialtyResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SpecialtyResponse create(@Valid @RequestBody CreateSpecialtyRequest request) {
        return SpecialtyResponse.from(specialties.create(request.code(), request.name(), request.appointmentType(),
                request.durationMinutes()));
    }

    @PutMapping("/{id}")
    SpecialtyResponse update(@PathVariable int id, @Valid @RequestBody UpdateSpecialtyRequest request) {
        return SpecialtyResponse.from(specialties.update(id, request.name(), request.appointmentType(),
                request.durationMinutes()));
    }

    @PatchMapping("/{id}/status")
    SpecialtyResponse setStatus(@PathVariable int id, @Valid @RequestBody ActiveRequest request) {
        return SpecialtyResponse.from(specialties.setActive(id, request.active()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable int id) {
        specialties.delete(id);
    }
}
