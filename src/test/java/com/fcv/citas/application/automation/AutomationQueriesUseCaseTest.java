package com.fcv.citas.application.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fcv.citas.application.appointment.AppointmentQueries;
import com.fcv.citas.application.appointment.AppointmentQueries.UpcomingAppointmentView;
import com.fcv.citas.domain.shared.InvalidRequestException;
import com.fcv.citas.domain.shared.SystemZone;

/** HU-034: ventana de recordatorios. Reloj fijo y doble del puerto, sin Spring ni base de datos. */
class AutomationQueriesUseCaseTest {

    /** 2026-03-10T15:00:00Z = 10:00 en America/Bogota (UTC-5, sin DST). */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-10T15:00:00Z"), SystemZone.ZONE);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 10, 0);

    private final List<LocalDateTime[]> calls = new ArrayList<>();
    private final List<UpcomingAppointmentView> result = List.of();

    private final AppointmentQueries port = (AppointmentQueries) Proxy.newProxyInstance(
            AppointmentQueries.class.getClassLoader(), new Class<?>[] { AppointmentQueries.class },
            (proxy, method, args) -> {
                if (method.getName().equals("findApprovedStartingBetween")) {
                    calls.add(new LocalDateTime[] { (LocalDateTime) args[0], (LocalDateTime) args[1] });
                    return result;
                }
                throw new UnsupportedOperationException(method.getName());
            });

    private final AutomationQueriesUseCase useCase = new AutomationQueriesUseCase(port, CLOCK);

    @Test
    void ventanaExactaConLimiteInferiorUnaHora() {
        List<UpcomingAppointmentView> out = useCase.upcoming(1);

        assertSame(result, out);
        assertEquals(1, calls.size());
        assertEquals(NOW, calls.get(0)[0]);
        assertEquals(NOW.plusHours(1), calls.get(0)[1]);
    }

    @Test
    void ventanaExactaConLimiteSuperiorSetentaYDosHoras() {
        useCase.upcoming(72);

        assertEquals(NOW, calls.get(0)[0]);
        assertEquals(LocalDateTime.of(2026, 3, 13, 10, 0), calls.get(0)[1]);
    }

    @Test
    void ventanaDe24HorasUsaLaHoraDeBogota() {
        useCase.upcoming(24);

        assertEquals(SystemZone.now(CLOCK), calls.get(0)[0]);
        assertEquals(LocalDateTime.of(2026, 3, 11, 10, 0), calls.get(0)[1]);
    }

    @Test
    void dailyUsaHoyDeBogotaSiNoHayFechaYSacaPendientesDeSummary() {
        // 2026-03-11T03:00Z = 2026-03-10 22:00 en Bogota: hoy es el 10, no el 11 de UTC.
        Clock late = Clock.fixed(Instant.parse("2026-03-11T03:00:00Z"), SystemZone.ZONE);
        List<LocalDate> rowCalls = new ArrayList<>();
        List<LocalDate> summaryCalls = new ArrayList<>();
        AppointmentQueries fake = (AppointmentQueries) Proxy.newProxyInstance(
                AppointmentQueries.class.getClassLoader(), new Class<?>[] { AppointmentQueries.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "findDailyRows" -> {
                        rowCalls.add((LocalDate) args[0]);
                        yield List.of(new AppointmentQueries.DailyRowView("HIC", "APPROVED", "General",
                                LocalTime.of(8, 0)));
                    }
                    case "summary" -> {
                        summaryCalls.add((LocalDate) args[0]);
                        yield new AppointmentQueries.Summary(4, 9, 9, 9, 2);
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });

        AutomationQueriesUseCase.DailySummary out = new AutomationQueriesUseCase(fake, late).daily(null);

        assertEquals(LocalDate.of(2026, 3, 10), out.date());
        assertEquals(List.of(LocalDate.of(2026, 3, 10)), rowCalls);
        assertEquals(List.of(LocalDate.of(2026, 3, 10)), summaryCalls);
        assertEquals(1, out.rows().size());
        assertEquals(4, out.pendingRequests());
        assertEquals(2, out.pendingReschedules());
    }

    @Test
    void dailyConFechaExplicitaPideFilasDeEsaFechaYSummaryConHoyDeBogota() {
        List<LocalDate> rowCalls = new ArrayList<>();
        List<LocalDate> summaryCalls = new ArrayList<>();
        AppointmentQueries fake = (AppointmentQueries) Proxy.newProxyInstance(
                AppointmentQueries.class.getClassLoader(), new Class<?>[] { AppointmentQueries.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "findDailyRows" -> {
                        rowCalls.add((LocalDate) args[0]);
                        yield List.<AppointmentQueries.DailyRowView>of();
                    }
                    case "summary" -> {
                        summaryCalls.add((LocalDate) args[0]);
                        yield new AppointmentQueries.Summary(1, 0, 0, 0, 5);
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        LocalDate explicit = LocalDate.of(2026, 4, 1);

        AutomationQueriesUseCase.DailySummary out = new AutomationQueriesUseCase(fake, CLOCK).daily(explicit);

        assertEquals(explicit, out.date());
        assertEquals(List.of(explicit), rowCalls);
        assertEquals(List.of(LocalDate.of(2026, 3, 10)), summaryCalls);
        assertEquals(5, out.pendingReschedules());
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 73, -1, -24, Integer.MIN_VALUE, Integer.MAX_VALUE })
    void rechazaHorasFueraDeRangoAtribuidoAHours(int hours) {
        InvalidRequestException e = assertThrows(InvalidRequestException.class, () -> useCase.upcoming(hours));

        assertEquals("hours", e.field());
        assertEquals("VALIDATION", e.code());
        assertTrue(calls.isEmpty(), "no debe consultar el puerto con una ventana invalida");
    }
}
