package com.fcv.citas.domain.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * D39 — red de seguridad del invariante que sostiene {@link HistoryEvent#between}: de todas las filas
 * de historial que produce el dominio, la UNICA que repite el estado de la anterior es la
 * reprogramacion aprobada.
 *
 * <p>No comprueba una lista de nombres escrita a mano: recorre por reflexion todos los metodos de
 * {@link Appointment} que devuelven un {@link Appointment.Transition} —que es exactamente lo que
 * {@code AppointmentRepository.apply} convierte en fila de {@code appointment_status_history}— y los
 * invoca con todas las combinaciones de argumentos que sabe fabricar. Un metodo nuevo entra solo, asi
 * que anadir otra escritura de historial sobre una cita YA EXISTENTE que repita el estado hace caer
 * esta prueba en vez de rotular en silencio "Reprogramada" una fila que no lo es.</p>
 *
 * <p>Deja fuera los metodos privados a proposito: una {@code Transition} solo llega al caso de uso por
 * un metodo visible, y esos si se recorren ({@code close} se cubre por {@code complete} y
 * {@code noShow}). Que nadie construya {@code Transition} ni {@code StatusChange} fuera de
 * {@code Appointment} —lo que dejaria esta prueba ciega— lo fija
 * {@code HistoryWritersArchitectureTest}.</p>
 */
class HistoryRowInvariantTest {

    /** La unica productora que puede repetir el estado (HU-031 CA-01). */
    private static final String APPROVED_RESCHEDULE = "rescheduleTo";

    private static final LocalDate DAY = LocalDate.of(2030, 5, 6);
    private static final LocalTime START = LocalTime.of(8, 0);
    private static final LocalTime END = LocalTime.of(9, 0);
    private static final LocalDateTime AT_START = LocalDateTime.of(DAY, START);
    /** Dos instantes: antes de empezar (cancelar, aprobar, reprogramar) y ya empezada (cerrar). */
    private static final List<Object> INSTANTS = List.of(AT_START.minusHours(1), AT_START.plusMinutes(5));
    private static final TimeSlot OTHER_SLOT = new TimeSlot(DAY.plusDays(3), START, END, 9);
    private static final long ACTOR = 7L;
    private static final long EXISTING_ID = 42L;

    @Test
    void onlyAnApprovedRescheduleProducesARowThatRepeatsTheStatus() {
        List<Method> producers = historyRowProducers();
        assertThat(producers).as("Appointment debe seguir siendo quien produce las filas de historial")
                .isNotEmpty();
        for (Method producer : producers) {
            int rows = Modifier.isStatic(producer.getModifiers()) ? checkFactory(producer) : checkOnExisting(producer);
            assertThat(rows).as("ninguna combinacion de argumentos consiguio que %s produjera una fila:"
                    + " completa las candidatas de esta prueba", producer.getName()).isPositive();
        }
    }

    /**
     * Fabrica estatica: la cita nace sin id, asi que su fila es la PRIMERA del historial y
     * {@link HistoryEvent#between} la ve con {@code previous == null}. Por eso puede repetir el estado
     * (y de hecho lo escribe igual al de la cita) sin mentir.
     */
    private int checkFactory(Method factory) {
        int rows = 0;
        for (Object[] arguments : argumentCombinations(factory)) {
            Appointment.Transition produced = invoke(factory, null, arguments);
            if (produced == null) {
                continue;
            }
            rows++;
            assertRowMatchesColumn(factory, produced);
            assertThat(produced.appointment().id()).as("%s debe devolver una cita recien nacida (sin id):"
                    + " su fila de historial es la primera y no tiene anterior", factory.getName()).isNull();
        }
        return rows;
    }

    /** Metodo de instancia: escribe sobre una cita que ya existe, asi que su fila SI tiene anterior. */
    private int checkOnExisting(Method producer) {
        int rows = 0;
        for (AppointmentStatus status : AppointmentStatus.values()) {
            Appointment existing = new Appointment(EXISTING_ID, 10L, 1L, 1, 2, status, DAY, START, END);
            for (Object[] arguments : argumentCombinations(producer)) {
                Appointment.Transition produced = invoke(producer, existing, arguments);
                if (produced == null) {
                    continue;
                }
                rows++;
                assertRowMatchesColumn(producer, produced);
                if (APPROVED_RESCHEDULE.equals(producer.getName())) {
                    assertThat(produced.change().status()).as("rescheduleTo es la fila que repite el estado:"
                            + " si dejara de repetirlo, la reprogramacion ya no se veria en la linea de tiempo")
                            .isEqualTo(status);
                } else {
                    assertThat(produced.change().status()).as("%s escribe historial sobre una cita existente:"
                            + " si repite el estado, el lector rotula esa fila \"Reprogramada\" (D39). Pasala"
                            + " por transitionTo o revisa HistoryEvent.between", producer.getName())
                            .isNotEqualTo(status);
                }
            }
        }
        return rows;
    }

    /**
     * La fila y la columna nunca se separan: el adaptador guarda {@code appointment().status()} en
     * {@code appointments} y {@code change().status()} en el historial. Si difirieran, el estado de la
     * ultima fila dejaria de ser el de la cita y la derivacion compararia cosas distintas.
     */
    private static void assertRowMatchesColumn(Method producer, Appointment.Transition produced) {
        assertThat(produced.change().status()).as("%s: la fila de historial y el estado que queda en la cita"
                + " deben coincidir", producer.getName()).isEqualTo(produced.appointment().status());
    }

    private static List<Method> historyRowProducers() {
        return Arrays.stream(Appointment.class.getDeclaredMethods())
                .filter(method -> method.getReturnType() == Appointment.Transition.class)
                .filter(method -> !method.isSynthetic())
                .filter(method -> !Modifier.isPrivate(method.getModifiers()))
                .sorted(Comparator.comparing(Method::getName))
                .toList();
    }

    /** {@code null} si el dominio rechazo esa combinacion: entonces no hay fila que comprobar. */
    private static Appointment.Transition invoke(Method producer, Appointment target, Object[] arguments) {
        try {
            producer.setAccessible(true);
            return (Appointment.Transition) producer.invoke(target, arguments);
        } catch (InvocationTargetException rejectedByTheDomain) {
            return null;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("no se pudo invocar " + producer.getName(), e);
        }
    }

    /** Producto cartesiano de las candidatas de cada parametro: basta con que UNA combinacion pase. */
    private static List<Object[]> argumentCombinations(Method producer) {
        List<Object[]> combinations = new ArrayList<>();
        combinations.add(new Object[0]);
        for (Class<?> parameter : producer.getParameterTypes()) {
            List<Object> candidates = candidatesFor(parameter);
            if (candidates.isEmpty()) {
                fail("D39: esta prueba no sabe fabricar un " + parameter.getSimpleName() + " para "
                        + producer.getName() + ": anade la candidata y comprueba si esa forma nueva de"
                        + " escribir historial repite el estado de la fila anterior");
            }
            List<Object[]> extended = new ArrayList<>(combinations.size() * candidates.size());
            for (Object[] prefix : combinations) {
                for (Object candidate : candidates) {
                    Object[] next = Arrays.copyOf(prefix, prefix.length + 1);
                    next[prefix.length] = candidate;
                    extended.add(next);
                }
            }
            combinations = extended;
        }
        return combinations;
    }

    private static List<Object> candidatesFor(Class<?> parameter) {
        if (parameter == long.class || parameter == Long.class) {
            return List.of(ACTOR);
        }
        if (parameter == int.class || parameter == Integer.class) {
            return List.of(3);
        }
        if (parameter == boolean.class || parameter == Boolean.class) {
            return List.of(true, false);
        }
        if (parameter == String.class) {
            return List.of("motivo");
        }
        if (parameter == LocalDateTime.class) {
            return INSTANTS;
        }
        if (parameter == LocalDate.class) {
            return List.of(DAY.plusDays(3));
        }
        if (parameter == LocalTime.class) {
            // Dos horas: el constructor exige que la franja termine despues de empezar.
            return List.of(START, END);
        }
        if (parameter == TimeSlot.class) {
            return List.of(OTHER_SLOT);
        }
        if (parameter.isEnum()) {
            return Arrays.asList((Object[]) parameter.getEnumConstants());
        }
        return List.of();
    }
}
