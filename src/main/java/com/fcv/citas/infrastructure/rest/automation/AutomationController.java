package com.fcv.citas.infrastructure.rest.automation;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fcv.citas.application.automation.AutomationQueriesUseCase;
import com.fcv.citas.domain.shared.InvalidRequestException;

/**
 * HU-034: lecturas para n8n. Se protege con la cadena de clave de API de {@code AutomationSecurityConfig},
 * solo GET. El controlador traduce HTTP a caso de uso: la validacion de rango de {@code hours} y la
 * seleccion de citas viven en {@link AutomationQueriesUseCase}.
 */
@RestController
@RequestMapping("/api/automation")
class AutomationController {

    static final int DEFAULT_HOURS = 24;

    private final AutomationQueriesUseCase automation;

    AutomationController(AutomationQueriesUseCase automation) {
        this.automation = automation;
    }

    /** 200 · 400 VALIDATION ({@code fieldErrors.hours}) si {@code hours} no es entero o esta fuera de 1..72. */
    @GetMapping("/appointments/upcoming")
    List<UpcomingAppointmentResponse> upcoming(@RequestParam(required = false) String hours) {
        return automation.upcoming(parseHours(hours)).stream().map(UpcomingAppointmentResponse::from).toList();
    }

    /**
     * HU-034 CA-11 (WF-003): resumen operativo del dia, sin PII. {@code date} ausente = hoy en Bogota; presente
     * pero vacio o no ISO ({@code yyyy-MM-dd}) = 400 VALIDATION con {@code fieldErrors.date}.
     */
    @GetMapping("/appointments/daily")
    DailySummaryResponse daily(@RequestParam(required = false) String date) {
        return DailySummaryResponse.from(automation.daily(parseDate(date)));
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw InvalidRequestException.field("date", "La fecha debe tener el formato yyyy-MM-dd");
        }
    }

    /** Ausente: 24. Presente pero vacio o no numerico: mismo 400 que un valor fuera de rango. */
    private static int parseHours(String raw) {
        if (raw == null) {
            return DEFAULT_HOURS;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw InvalidRequestException.field("hours", "Las horas deben ser un entero entre 1 y 72");
        }
    }
}
