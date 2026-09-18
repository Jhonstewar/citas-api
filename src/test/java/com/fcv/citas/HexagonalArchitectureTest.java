package com.fcv.citas;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;

/**
 * Reglas de la arquitectura hexagonal, comprobadas sobre el BYTECODE compilado.
 *
 * <p>La version anterior buscaba lineas {@code import} con una expresion regular. Eso dejaba
 * pasar, entre otras cosas, las referencias con nombre completamente cualificado
 * ({@code org.springframework.x.Y} escrito en linea), los tipos que llegan por herencia o por la
 * firma de un metodo, y las anotaciones. ArchUnit mira las dependencias reales de cada clase.</p>
 */
@AnalyzeClasses(packages = "com.fcv.citas", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    private static final String[] FRAMEWORK_PACKAGES = {
            "org.springframework..",
            "jakarta..",
            "javax.persistence..",
            "com.fasterxml..",
            "org.hibernate..",
            "org.flywaydb..",
            "com.nimbusds..",
    };

    /** El nucleo se escribe en Java puro: ningun framework puede filtrarse a domain ni application. */
    @ArchTest
    static final ArchRule elNucleoNoDependeDeFrameworks = noClasses()
            .that().resideInAnyPackage("com.fcv.citas.domain..", "com.fcv.citas.application..")
            .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORK_PACKAGES)
            .because("domain y application deben poder compilarse y probarse sin contenedor");

    /** La dependencia apunta hacia dentro: la infraestructura conoce el nucleo, nunca al reves. */
    @ArchTest
    static final ArchRule elNucleoNoDependeDeLaInfraestructura = noClasses()
            .that().resideInAnyPackage("com.fcv.citas.domain..", "com.fcv.citas.application..")
            .should().dependOnClassesThat().resideInAPackage("com.fcv.citas.infrastructure..")
            .because("los adaptadores se enchufan a los puertos, no al contrario");

    /** El dominio es la capa mas interna: tampoco depende de la capa de aplicacion. */
    @ArchTest
    static final ArchRule elDominioNoDependeDeLaAplicacion = noClasses()
            .that().resideInAPackage("com.fcv.citas.domain..")
            .should().dependOnClassesThat().resideInAPackage("com.fcv.citas.application..")
            .because("el dominio no conoce los casos de uso que lo orquestan");

    /** Nadie escribe a consola: las trazas pasan por el log, que si se configura (PRD seccion 8). */
    @ArchTest
    static final ArchRule nadieEscribeEnLaSalidaEstandar =
            GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
                    .because("un System.out puede filtrar tokens y no respeta el nivel de log");
}
