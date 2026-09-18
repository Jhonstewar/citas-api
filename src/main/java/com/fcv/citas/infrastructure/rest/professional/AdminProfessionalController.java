package com.fcv.citas.infrastructure.rest.professional;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fcv.citas.application.professional.CreateProfessionalCommand;
import com.fcv.citas.application.professional.ManageProfessionalsUseCase;
import com.fcv.citas.application.professional.ProfessionalQueries.Filter;
import com.fcv.citas.infrastructure.rest.validation.BcryptPasswordLength;

/** HU-013 a HU-016: gestion de profesionales. Solo ADMIN (prefijo {@code /api/admin}). */
@RestController
@RequestMapping("/api/admin/professionals")
class AdminProfessionalController {

    record CreateProfessionalRequest(
            @NotBlank @Size(max = 100) String firstNames,
            @NotBlank @Size(max = 100) String lastNames,
            @NotBlank @Size(max = 10) String documentType,
            @NotBlank @Size(max = 20) String documentNumber,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(max = 30) String phone,
            @NotBlank @BcryptPasswordLength String password,
            @NotBlank @Size(max = 30) String professionalCode,
            @NotBlank @Size(max = 40) String licenseNumber,
            @NotNull(message = "Asigne al menos una especialidad") List<Integer> specialtyIds,
            @NotNull(message = "Marque una especialidad como primaria") Integer primarySpecialtyId,
            @NotNull(message = "Asigne al menos una sede") List<Integer> siteIds) {

        @Override
        public String toString() {
            return "CreateProfessionalRequest[professionalCode=%s, password=***]".formatted(professionalCode);
        }
    }

    record UpdateContactRequest(
            @NotBlank @Size(max = 100) String firstNames,
            @NotBlank @Size(max = 100) String lastNames,
            @Size(max = 30) String phone) {
    }

    record SpecialtiesRequest(
            @NotNull(message = "Asigne al menos una especialidad") List<Integer> specialtyIds,
            @NotNull(message = "Marque una especialidad como primaria") Integer primarySpecialtyId) {
    }

    record SitesRequest(@NotNull(message = "Asigne al menos una sede") List<Integer> siteIds) {
    }

    record ActiveRequest(@NotNull(message = "Indique si queda activo o inactivo") Boolean active) {
    }

    private final ManageProfessionalsUseCase professionals;

    AdminProfessionalController(ManageProfessionalsUseCase professionals) {
        this.professionals = professionals;
    }

    @GetMapping
    List<ProfessionalResponse> list(@RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer specialtyId, @RequestParam(required = false) Integer siteId) {
        return professionals.list(new Filter(active, specialtyId, siteId)).stream().map(ProfessionalResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    ProfessionalResponse get(@PathVariable long id) {
        return ProfessionalResponse.from(professionals.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ProfessionalResponse create(@Valid @RequestBody CreateProfessionalRequest r) {
        return ProfessionalResponse.from(professionals.create(new CreateProfessionalCommand(r.firstNames(),
                r.lastNames(), r.documentType(), r.documentNumber(), r.email(), r.phone(), r.password(),
                r.professionalCode(), r.licenseNumber(), r.specialtyIds(), r.primarySpecialtyId(), r.siteIds())));
    }

    @PutMapping("/{id}")
    ProfessionalResponse updateContact(@PathVariable long id, @Valid @RequestBody UpdateContactRequest r) {
        return ProfessionalResponse.from(professionals.updateContact(id, r.firstNames(), r.lastNames(), r.phone()));
    }

    @PutMapping("/{id}/specialties")
    ProfessionalResponse assignSpecialties(@PathVariable long id, @Valid @RequestBody SpecialtiesRequest r) {
        return ProfessionalResponse.from(professionals.assignSpecialties(id, r.specialtyIds(),
                r.primarySpecialtyId()));
    }

    @PutMapping("/{id}/sites")
    ProfessionalResponse assignSites(@PathVariable long id, @Valid @RequestBody SitesRequest r) {
        return ProfessionalResponse.from(professionals.assignSites(id, r.siteIds()));
    }

    @PatchMapping("/{id}/status")
    ProfessionalResponse setStatus(@PathVariable long id, @Valid @RequestBody ActiveRequest r) {
        return ProfessionalResponse.from(professionals.setActive(id, r.active()));
    }
}
