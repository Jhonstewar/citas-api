package com.fcv.citas.application.appointment;

import java.time.LocalDate;
import java.util.List;

import com.fcv.citas.application.appointment.AppointmentQueries.AppointmentView;
import com.fcv.citas.application.appointment.AppointmentQueries.HistoryView;
import com.fcv.citas.domain.shared.NotFoundException;

/**
 * HU-025: el paciente consulta sus citas y el detalle. El titular sale siempre del token; una cita
 * ajena responde igual que una inexistente (CA-07).
 */
public class PatientAppointmentsUseCase {

    public record Detail(AppointmentView appointment, List<HistoryView> history) {
    }

    private final AppointmentQueries queries;

    public PatientAppointmentsUseCase(AppointmentQueries queries) {
        this.queries = queries;
    }

    public List<AppointmentView> list(long patientUserId, String status, LocalDate date) {
        return queries.findByPatient(patientUserId, status, date);
    }

    public Detail detail(long patientUserId, long appointmentId) {
        AppointmentView view = queries.findById(appointmentId)
                .filter(a -> a.patient().id() == patientUserId)
                .orElseThrow(() -> new NotFoundException("La cita no existe"));
        return new Detail(view, queries.history(appointmentId));
    }
}
