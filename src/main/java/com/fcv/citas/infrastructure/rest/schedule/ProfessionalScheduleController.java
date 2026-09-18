package com.fcv.citas.infrastructure.rest.schedule;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fcv.citas.application.schedule.ManageScheduleUseCase;
import com.fcv.citas.application.schedule.ManageScheduleUseCase.BlockCommand;
import com.fcv.citas.application.schedule.ScheduleQueries.BlockView;
import com.fcv.citas.application.shared.Refs.SiteRef;
import com.fcv.citas.domain.shared.SystemZone;
import com.fcv.citas.infrastructure.rest.CurrentUser;
import com.fcv.citas.infrastructure.rest.appointment.AppointmentResponses;

/** HU-017 a HU-019: agenda del profesional autenticado. Solo PROFESSIONAL. */
@RestController
@RequestMapping("/api/professional/blocks")
class ProfessionalScheduleController {

    record BlockRequest(
            @NotNull(message = "Seleccione la sede") Integer siteId,
            @NotNull(message = "Seleccione la fecha") LocalDate date,
            @NotNull(message = "La hora de inicio es obligatoria") LocalTime startTime,
            @NotNull(message = "La hora de fin es obligatoria") LocalTime endTime) {

        BlockCommand toCommand() {
            return new BlockCommand(siteId, date, startTime, endTime);
        }
    }

    record SlotResponse(long id, String startTime, String endTime, boolean available) {
    }

    record BlockResponse(long id, String date, String startTime, String endTime, SiteRef site, boolean editable,
            List<SlotResponse> slots) {
    }

    private final ManageScheduleUseCase schedule;
    private final Clock clock;

    ProfessionalScheduleController(ManageScheduleUseCase schedule, Clock clock) {
        this.schedule = schedule;
        this.clock = clock;
    }

    @GetMapping
    List<BlockResponse> calendar(JwtAuthenticationToken auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDateTime now = SystemZone.now(clock);
        return schedule.calendar(CurrentUser.id(auth), from, to).stream().map(b -> response(b, now)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    BlockResponse create(JwtAuthenticationToken auth, @Valid @RequestBody BlockRequest request) {
        return response(schedule.create(CurrentUser.id(auth), request.toCommand()), SystemZone.now(clock));
    }

    @PutMapping("/{id}")
    BlockResponse update(JwtAuthenticationToken auth, @PathVariable long id,
            @Valid @RequestBody BlockRequest request) {
        return response(schedule.update(CurrentUser.id(auth), id, request.toCommand()), SystemZone.now(clock));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(JwtAuthenticationToken auth, @PathVariable long id) {
        schedule.delete(CurrentUser.id(auth), id);
    }

    /** {@code editable} = futuro y sin reservas: lo mismo que exige el caso de uso para editar. */
    private static BlockResponse response(BlockView b, LocalDateTime now) {
        boolean future = LocalDateTime.of(b.date(), b.startTime()).isAfter(now);
        return new BlockResponse(b.id(), b.date().toString(), AppointmentResponses.time(b.startTime()),
                AppointmentResponses.time(b.endTime()), b.site(), future && !b.reserved(),
                b.slots().stream().map(s -> new SlotResponse(s.id(), AppointmentResponses.time(s.startTime()),
                        AppointmentResponses.time(s.endTime()), s.available())).toList());
    }
}
