package com.fcv.citas.application.appointment;

import java.util.ArrayList;
import java.util.List;

import com.fcv.citas.application.appointment.AppointmentQueries.HistoryView;
import com.fcv.citas.domain.appointment.AppointmentStatus;
import com.fcv.citas.domain.appointment.HistoryEvent;

/**
 * D39 — una fila del historial de la cita tal como la lee el cliente: la fila persistida
 * ({@link HistoryView}) mas el {@link HistoryEvent} que se DERIVA de ella y de la anterior.
 *
 * <p>Se separa de {@code HistoryView} a proposito: el evento no es un dato de
 * {@code appointment_status_history} (que solo guarda el estado nuevo, V3) sino una lectura del
 * historial completo, y no hay columna ni migracion nueva que lo respalde. Por eso lo deriva esta capa
 * con la regla del dominio ({@link HistoryEvent#between}) y no el adaptador de persistencia.</p>
 *
 * <p>{@code HistoryEntry} del contrato S4: {@code event} se omite cuando es {@code null}.</p>
 */
public record HistoryEntry(HistoryView change, HistoryEvent event) {

    /**
     * Deriva el evento de cada fila sobre el historial YA ORDENADO cronologicamente
     * ({@link AppointmentQueries#history}). El orden es parte de la regla: el evento depende de la fila
     * anterior, asi que una lista desordenada daria un resultado distinto.
     */
    public static List<HistoryEntry> derive(List<HistoryView> chronological) {
        List<HistoryEntry> entries = new ArrayList<>(chronological.size());
        AppointmentStatus previous = null;
        for (HistoryView row : chronological) {
            AppointmentStatus current = AppointmentStatus.valueOf(row.status());
            entries.add(new HistoryEntry(row, HistoryEvent.between(previous, current)));
            previous = current;
        }
        return List.copyOf(entries);
    }

    /** La misma entrada con la fila sustituida (para proyectarla, p. ej. ocultando el actor). */
    HistoryEntry withChange(HistoryView replacement) {
        return new HistoryEntry(replacement, event);
    }
}
