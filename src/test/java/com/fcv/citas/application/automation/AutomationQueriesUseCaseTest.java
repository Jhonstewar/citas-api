package com.fcv.citas.application.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
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

    @ParameterizedTest
    @ValueSource(ints = { 0, 73, -1, -24, Integer.MIN_VALUE, Integer.MAX_VALUE })
    void rechazaHorasFueraDeRangoAtribuidoAHours(int hours) {
        InvalidRequestException e = assertThrows(InvalidRequestException.class, () -> useCase.upcoming(hours));

        assertEquals("hours", e.field());
        assertEquals("VALIDATION", e.code());
        assertTrue(calls.isEmpty(), "no debe consultar el puerto con una ventana invalida");
    }
}
