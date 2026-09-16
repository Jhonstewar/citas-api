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
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.fcv.citas.domain.auth.InvalidCredentialsException;
import com.fcv.citas.domain.auth.InvalidRefreshTokenException;
import com.fcv.citas.domain.user.DocumentAlreadyRegisteredException;
import com.fcv.citas.domain.user.EmailAlreadyRegisteredException;
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

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        // Solo el tipo: el mensaje de algunas excepciones puede arrastrar datos de la peticion.
        log.error("Error no controlado: {}", ex.getClass().getName());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "Ocurrió un error inesperado");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
