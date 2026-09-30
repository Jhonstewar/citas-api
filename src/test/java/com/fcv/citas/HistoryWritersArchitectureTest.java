package com.fcv.citas;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import com.fcv.citas.domain.appointment.Appointment;
import com.fcv.citas.domain.appointment.StatusChange;
import com.fcv.citas.infrastructure.persistence.appointment.StatusHistoryJpaEntity;

/**
 * D39 — quien puede escribir una fila del historial de una cita, comprobado sobre el BYTECODE.
 *
 * <p>La derivacion de {@code HistoryEvent.between} ("una fila que repite el estado de la anterior es una
 * reprogramacion aprobada") solo es cierta mientras el historial tenga un unico autor: el dominio
 * construye la fila y el adaptador del puerto la inserta. Si un caso de uso o un controlador se fabrica
 * su propio {@link StatusChange} para una cita ya existente, la etiqueta "Reprogramada" empieza a mentir
 * sin que caiga ninguna prueba de comportamiento, porque el estado repetido es indistinguible.</p>
 *
 * <p>Estas reglas cierran esa puerta por fuera de {@code Appointment};
 * {@code HistoryRowInvariantTest} la cierra por dentro (ningun metodo de {@code Appointment} repite el
 * estado salvo {@code rescheduleTo}). Queda un hueco declarado: un INSERT nativo contra
 * {@code appointment_status_history} escrito a mano no lo ve ArchUnit.</p>
 */
@AnalyzeClasses(packages = "com.fcv.citas", importOptions = ImportOption.DoNotIncludeTests.class)
class HistoryWritersArchitectureTest {

    /** Package-private, asi que no se puede referenciar con {@code .class} desde aqui. */
    private static final String JPA_ADAPTER =
            "com.fcv.citas.infrastructure.persistence.appointment.JpaAppointmentRepositoryAdapter";

    /** La fila del historial es una decision del dominio: la cita es la unica que puede componerla. */
    @ArchTest
    static final ArchRule soloLaCitaComponeFilasDeHistorial = classes()
            .that().doNotHaveFullyQualifiedName(Appointment.class.getName())
            .should(notConstruct(StatusChange.class))
            .because("un StatusChange nacido fuera de Appointment puede repetir el estado de la fila anterior"
                    + " y volver falsa la etiqueta RESCHEDULED (D39)");

    /** Y es tambien la unica que decide que cita queda escrita junto a esa fila. */
    @ArchTest
    static final ArchRule soloLaCitaComponeTransiciones = classes()
            .that().doNotHaveFullyQualifiedName(Appointment.class.getName())
            .should(notConstruct(Appointment.Transition.class))
            .because("AppointmentRepository.apply escribe lo que trae la Transition: si otra capa la arma,"
                    + " el estado de la cita y el de su historial pueden separarse (D39)");

    /**
     * Un tercer camino de escritura (otro adaptador, otro repositorio Spring Data) romperia el hecho de
     * que el historial solo se escribe por {@code create} y {@code apply}.
     */
    @ArchTest
    static final ArchRule soloElAdaptadorDelPuertoInsertaHistorial = classes()
            .that().doNotHaveFullyQualifiedName(JPA_ADAPTER)
            .should(notConstruct(StatusHistoryJpaEntity.class))
            .because("el historial se escribe solo por AppointmentRepository.create y apply (D39)");

    private static ArchCondition<JavaClass> notConstruct(Class<?> forbidden) {
        String owner = forbidden.getName();
        // Descripcion en ingles: se concatena con la frase que arma ArchUnit ("classes ... should ...").
        return new ArchCondition<JavaClass>("not call the constructor of " + forbidden.getSimpleName()) {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaConstructorCall call : item.getConstructorCallsFromSelf()) {
                    if (call.getTarget().getOwner().getFullName().equals(owner)) {
                        events.add(SimpleConditionEvent.violated(call, call.getDescription()));
                    }
                }
            }
        };
    }
}
