package com.fcv.citas.infrastructure.rest.error;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.fcv.citas.domain.auth.InvalidCredentialsException;
import com.fcv.citas.domain.shared.BusinessRuleException;
import com.fcv.citas.domain.shared.CodedException;
import com.fcv.citas.domain.shared.ConflictException;
import com.fcv.citas.domain.shared.DuplicateValueException;
import com.fcv.citas.domain.shared.InvalidRequestException;
import com.fcv.citas.domain.shared.NotFoundException;
import com.fcv.citas.domain.auth.InvalidRefreshTokenException;
import com.fcv.citas.domain.user.DocumentAlreadyRegisteredException;
import com.fcv.citas.domain.user.EmailAlreadyRegisteredException;
import com.fcv.citas.domain.user.FieldNotEditableException;
import com.fcv.citas.domain.user.UnknownDocumentTypeException;
import com.fcv.citas.domain.user.UserNotFoundException;

/**
 * Errores REST en formato {@link ProblemDetail} (RFC 9457). Los 400 de validacion agregan
 * {@code fieldErrors: {campo: mensaje}}; nunca se devuelve ni se registra el valor rechazado.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Datos inválidos",
                "La petición contiene campos inválidos");
        problem.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(problem);
    }

    /**
     * Cuerpo ausente, JSON mal formado o un valor de tipo incompatible (un objeto donde se espera
     * un texto). Sin esta redefinicion salia el ProblemDetail generico de Spring, en ingles
     * ({@code "Bad Request"} / {@code "Failed to read request"}), distinto del resto de errores.
     *
     * <p>El mensaje de la excepcion no se devuelve ni se registra: lo redacta Jackson, describe
     * el analizador y la posicion del fallo y puede citar el fragmento del cuerpo que no supo leer
     * —una contraseña mal entrecomillada, por ejemplo—. Sin {@code fieldErrors}: no hay un campo
     * concreto al que atribuir el error.</p>
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "Datos inválidos",
                "El cuerpo de la petición falta o no es un JSON válido"));
    }

    @ExceptionHandler(UnknownDocumentTypeException.class)
    ProblemDetail unknownDocumentType(UnknownDocumentTypeException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Datos inválidos", ex.getMessage());
        problem.setProperty("fieldErrors", Map.of("documentType", ex.getMessage()));
        return problem;
    }

    @ExceptionHandler({ EmailAlreadyRegisteredException.class, DocumentAlreadyRegisteredException.class })
    ProblemDetail conflict(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, "Conflicto", ex.getMessage());
    }

    @ExceptionHandler({ InvalidCredentialsException.class, InvalidRefreshTokenException.class })
    ProblemDetail unauthorized(RuntimeException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "No autenticado", ex.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail notFound(UserNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "No encontrado", ex.getMessage());
    }

    // ------------------------------------------------------------------ S3: excepciones con `code`

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalidRequest(InvalidRequestException ex) {
        ProblemDetail problem = coded(HttpStatus.BAD_REQUEST, "Datos inválidos", ex);
        if (ex.field() != null) {
            problem.setProperty("fieldErrors", Map.of(ex.field(), ex.getMessage()));
        }
        // HU-008 · D25: el cliente decide por `code` y `field`, como en DUPLICATE (contrato S4).
        if (ex instanceof FieldNotEditableException) {
            problem.setProperty("field", ex.field());
        }
        return problem;
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail codedNotFound(NotFoundException ex) {
        return coded(HttpStatus.NOT_FOUND, "No encontrado", ex);
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail codedConflict(ConflictException ex) {
        ProblemDetail problem = coded(HttpStatus.CONFLICT, "Conflicto", ex);
        if (ex instanceof DuplicateValueException duplicate) {
            problem.setProperty("field", duplicate.field());
        }
        return problem;
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail businessRule(BusinessRuleException ex) {
        return coded(HttpStatus.UNPROCESSABLE_ENTITY, "Regla de negocio", ex);
    }

    /** Parametro de consulta o de ruta con tipo incorrecto ({@code ?date=manana}): 400 en español. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail typeMismatch(MethodArgumentTypeMismatchException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Datos inválidos",
                "El parámetro «" + ex.getName() + "» tiene un formato inválido");
        problem.setProperty("code", "VALIDATION");
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Datos inválidos",
                "Falta el parámetro obligatorio «" + ex.getParameterName() + "»");
        problem.setProperty("code", "VALIDATION");
        return ResponseEntity.badRequest().body(problem);
    }

    /**
     * Ultima barrera: una restriccion de la base rechazo un cambio que las comprobaciones previas no
     * vieron (carrera entre transacciones). Es un conflicto, no un error interno. No se devuelve ni se
     * registra el mensaje: el motor lo redacta con los valores duplicados (p. ej. un email).
     */
    @ExceptionHandler({ org.springframework.dao.DataIntegrityViolationException.class,
            org.springframework.dao.ConcurrencyFailureException.class })
    ProblemDetail integrityConflict(org.springframework.dao.DataAccessException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "Conflicto",
                "Otro cambio simultáneo impidió completar la operación; vuelva a intentarlo");
        problem.setProperty("code", "CONCURRENT_CHANGE");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        // Solo el tipo: el mensaje de algunas excepciones puede arrastrar datos de la peticion.
        log.error("Error no controlado: {}", ex.getClass().getName());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "Ocurrió un error inesperado");
    }

    private static ProblemDetail coded(HttpStatus status, String title, CodedException ex) {
        ProblemDetail problem = problem(status, title, ex.getMessage());
        problem.setProperty("code", ex.code());
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
