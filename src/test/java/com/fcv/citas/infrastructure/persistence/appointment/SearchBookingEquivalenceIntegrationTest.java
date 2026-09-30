package com.fcv.citas.infrastructure.persistence.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.fcv.citas.application.appointment.AvailabilityQueries;
import com.fcv.citas.application.appointment.BookAppointmentUseCase;
import com.fcv.citas.application.appointment.BookAppointmentUseCase.BookingCommand;
import com.fcv.citas.domain.schedule.AvailabilityBlock;
import com.fcv.citas.domain.shared.SystemZone;
import com.fcv.citas.support.S3TestData;

/**
 * LOOP_03 — equivalencia busqueda ↔ reserva. Sobre una matriz de bloques (30/60/90/120 min, inicio
 * alineado a :00 o :30), duraciones 30 y 60 y ocupacion parcial, lo que OFRECE la busqueda
 * ({@link JdbcAvailabilityQueries}, SQL) debe ser exactamente lo que ACEPTA el dominio
 * ({@link AvailabilityBlock#canHost} mas slots libres) y lo que la reserva real deja reservar.
 * Ata la copia SQL de la regla de consecutividad a la regla unica de {@code domain/schedule}.
 */
@SpringBootTest
@ActiveProfiles("test")
class SearchBookingEquivalenceIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AvailabilityQueries queries;
    @Autowired
    private BookAppointmentUseCase booking;

    private S3TestData data;
    private int hic;
    private long patient;
    private LocalDate day;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        data = new S3TestData(jdbc, passwordEncoder.encode(S3TestData.PASSWORD));
        hic = data.siteId("HIC");
        patient = data.user("patient", "USER");
        now = SystemZone.now(java.time.Clock.system(SystemZone.ZONE));
        day = now.toLocalDate().plusDays(5);
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    /** Ocupacion parcial: ninguna, solo el primer slot, solo el segundo, o todos. */
    private enum Occupancy { NONE, FIRST, SECOND, ALL }

    private record Scenario(int duration, String blockStart, int blockMinutes, Occupancy occupancy) {
    }

    private static List<Scenario> matrix() {
        List<Scenario> all = new ArrayList<>();
        for (int duration : new int[] { 30, 60 }) {
            for (String start : new String[] { "08:00", "08:30" }) {
                for (int minutes : new int[] { 30, 60, 90, 120 }) {
                    for (Occupancy o : Occupancy.values()) {
                        all.add(new Scenario(duration, start, minutes, o));
                    }
                }
            }
        }
        return all;
    }

    @Test
    void searchOffersExactlyWhatTheDomainAndTheBookingAccept() {
        int checked = 0;
        for (Scenario s : matrix()) {
            checked += verify(s);
        }
        assertThat(checked).isPositive();
    }

    private int verify(Scenario s) {
        String label = s.toString();
        int slotsRequired = s.duration() / AvailabilityBlock.SLOT_MINUTES;
        int specialty = data.specialty(s.duration() == 30 ? "GENERAL" : "SPECIALIZED", s.duration());
        S3TestData.Professional pro = data.professional("eq", new int[] { specialty }, hic);
        LocalTime blockStart = LocalTime.parse(s.blockStart());
        LocalTime blockEnd = blockStart.plusMinutes(s.blockMinutes());
        long blockId = data.block(pro.id(), hic, day, blockStart.toString(), blockEnd.toString());
        AvailabilityBlock block = new AvailabilityBlock(blockId, pro.id(), hic, day, blockStart, blockEnd);

        // Ocupacion: una cita sembrada que retiene el primer y/o el segundo slot del bloque.
        List<LocalTime> slotStarts = block.slotStarts();
        Set<LocalTime> occupied = new TreeSet<>();
        switch (s.occupancy()) {
            case FIRST -> occupied.add(slotStarts.get(0));
            case SECOND -> {
                if (slotStarts.size() > 1) {
                    occupied.add(slotStarts.get(1));
                }
            }
            case ALL -> occupied.addAll(slotStarts);
            default -> { }
        }
        int order = 1;
        for (LocalTime t : occupied) {
            long appt = data.appointment(patient, pro.id(), hic, specialty, day, t, t.plusMinutes(30), "APPROVED");
            data.reserve(data.slotId(blockId, t.toString()), appt, (order++ % 2) + 1);
        }

        // Lo que acepta el dominio: encaja en el bloque Y todos sus slots estan libres.
        // Se exploran inicios alineados y NO alineados dentro y fuera del bloque.
        Set<LocalTime> expected = new TreeSet<>();
        List<LocalTime> candidates = new ArrayList<>();
        for (LocalTime t = LocalTime.of(7, 0); t.isBefore(LocalTime.of(12, 0)); t = t.plusMinutes(15)) {
            candidates.add(t);
            if (block.canHost(t, slotsRequired) && freeRun(t, slotsRequired, occupied)) {
                expected.add(t);
            }
        }

        // Lo que ofrece la busqueda (SQL).
        List<AvailabilityQueries.Offer> offers = queries.offers(specialty, null, day, hic, pro.id(), now);
        Set<LocalTime> offered = new TreeSet<>();
        offers.forEach(o -> offered.add(o.startTime()));
        assertThat(offered).as("oferta vs dominio: " + label).isEqualTo(expected);
        assertThat(offers).as("duracion ofrecida: " + label)
                .allSatisfy(o -> assertThat(o.endTime()).isEqualTo(o.startTime().plusMinutes(s.duration())));

        // El conteo por dia usa la misma regla.
        int dayCount = queries.days(specialty, null, day, day, hic, pro.id(), now).stream()
                .mapToInt(AvailabilityQueries.DayCount::offers).sum();
        assertThat(dayCount).as("conteo por dia: " + label).isEqualTo(expected.size());

        // La reserva real rechaza todo lo que la busqueda no ofrece (no muta: falla antes o revierte).
        for (LocalTime t : candidates) {
            if (!expected.contains(t)) {
                assertThat(tryBook(pro.id(), specialty, t, s.duration()))
                        .as("la reserva debe rechazar " + t + " en " + label).isFalse();
            }
        }
        // ... y acepta lo que ofrece (solo la primera oferta: reservar ocupa slots y altera la oferta).
        if (!expected.isEmpty()) {
            LocalTime first = expected.iterator().next();
            assertThat(tryBook(pro.id(), specialty, first, s.duration()))
                    .as("la reserva debe aceptar " + first + " en " + label).isTrue();
        }
        return candidates.size();
    }

    private static boolean freeRun(LocalTime start, int slots, Set<LocalTime> occupied) {
        for (int i = 0; i < slots; i++) {
            if (occupied.contains(start.plusMinutes((long) AvailabilityBlock.SLOT_MINUTES * i))) {
                return false;
            }
        }
        return true;
    }

    private boolean tryBook(long professionalId, int specialty, LocalTime start, int duration) {
        BookingCommand command = new BookingCommand(professionalId, hic, specialty, day, start);
        try {
            if (duration == 30) {
                booking.bookGeneral(patient, command);
            } else {
                booking.requestSpecialized(patient, command);
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
