package com.fcv.citas.infrastructure.rest.appointment;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fcv.citas.application.appointment.AvailabilityQueries.Offer;
import com.fcv.citas.application.appointment.BookAppointmentUseCase;
import com.fcv.citas.application.appointment.BookAppointmentUseCase.BookingCommand;
import com.fcv.citas.application.appointment.SearchAvailabilityUseCase;
import com.fcv.citas.application.shared.Refs.PersonRef;
import com.fcv.citas.application.shared.Refs.SiteRef;
import com.fcv.citas.application.shared.Refs.SpecialtyRef;
import com.fcv.citas.infrastructure.rest.CurrentUser;
import com.fcv.citas.infrastructure.rest.appointment.AppointmentResponses.AppointmentResponse;

/**
 * HU-022 (disponibilidad), HU-023 (cita general) y HU-024 (cita especializada). Solo USER. El
 * titular es siempre el usuario del token: el cuerpo no lleva paciente ni duracion (RF-09).
 */
@RestController
@RequestMapping("/api/patient")
class PatientBookingController {

    record BookingRequest(
            @NotNull(message = "Seleccione el profesional") Long professionalId,
            @NotNull(message = "Seleccione la sede") Integer siteId,
            @NotNull(message = "Seleccione la especialidad") Integer specialtyId,
            @NotNull(message = "Seleccione la fecha") LocalDate date,
            @NotNull(message = "Seleccione la hora") LocalTime startTime) {

        BookingCommand toCommand() {
            return new BookingCommand(professionalId, siteId, specialtyId, date, startTime);
        }
    }

    record OfferResponse(PersonRef professional, SiteRef site, SpecialtyRef specialty, String date,
            String startTime, String endTime, int durationMinutes) {

        static OfferResponse from(Offer o) {
            return new OfferResponse(o.professional(), o.site(), o.specialty(), o.date().toString(),
                    AppointmentResponses.time(o.startTime()), AppointmentResponses.time(o.endTime()),
                    o.durationMinutes());
        }
    }

    record DayResponse(String date, int offers) {
    }

    private final SearchAvailabilityUseCase availability;
    private final BookAppointmentUseCase booking;

    PatientBookingController(SearchAvailabilityUseCase availability, BookAppointmentUseCase booking) {
        this.availability = availability;
        this.booking = booking;
    }

    @GetMapping("/availability")
    List<OfferResponse> offers(@RequestParam int specialtyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer siteId, @RequestParam(required = false) Long professionalId) {
        return availability.offers(specialtyId, date, siteId, professionalId).stream().map(OfferResponse::from)
                .toList();
    }

    @GetMapping("/availability/days")
    List<DayResponse> days(@RequestParam int specialtyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer siteId, @RequestParam(required = false) Long professionalId) {
        return availability.days(specialtyId, from, to, siteId, professionalId).stream()
                .map(d -> new DayResponse(d.date().toString(), d.offers())).toList();
    }

    @PostMapping("/appointments/general")
    @ResponseStatus(HttpStatus.CREATED)
    AppointmentResponse bookGeneral(JwtAuthenticationToken auth, @Valid @RequestBody BookingRequest request) {
        return AppointmentResponse.from(booking.bookGeneral(CurrentUser.id(auth), request.toCommand()));
    }

    @PostMapping("/appointments/specialized")
    @ResponseStatus(HttpStatus.CREATED)
    AppointmentResponse requestSpecialized(JwtAuthenticationToken auth, @Valid @RequestBody BookingRequest request) {
        return AppointmentResponse.from(booking.requestSpecialized(CurrentUser.id(auth), request.toCommand()));
    }
}
