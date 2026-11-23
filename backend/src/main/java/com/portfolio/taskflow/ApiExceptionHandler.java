package com.portfolio.taskflow;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ApiError> handleTaskNotFound(TaskNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        return invalidTask(fieldMessages(exception.getBindingResult().getFieldErrors()));
    }

    @ExceptionHandler(InvalidAuthInput.class)
    public ResponseEntity<ApiError> handleAuthValidation(InvalidAuthInput exception) {
        return error(HttpStatus.BAD_REQUEST, "Les données d’authentification sont invalides.", exception.errors);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleStatus(ResponseStatusException exception) {
        return error(exception.getStatusCode(), exception.getReason(), Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException exception) {
        if (exception.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            String field = mismatch.getPath().getFirst().getFieldName();
            if (field != null) {
                return error(HttpStatus.BAD_REQUEST, "Les données de la requête sont invalides.",
                        Map.of(field, List.of("La valeur doit être une chaîne de caractères.")));
            }
        }
        return error(HttpStatus.BAD_REQUEST, "Le corps de la requête doit être un objet JSON valide.", Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleInvalidParameter(MethodArgumentTypeMismatchException exception) {
        return error(HttpStatus.BAD_REQUEST, "Le paramètre « " + exception.getName() + " » a un format invalide.",
                Map.of());
    }

    private ResponseEntity<ApiError> invalidTask(Map<String, List<String>> errors) {
        return error(HttpStatus.BAD_REQUEST, "Les données de la tâche sont invalides.", errors);
    }

    private ResponseEntity<ApiError> error(HttpStatusCode status, String message, Map<String, List<String>> errors) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message, errors));
    }

    private static Map<String, List<String>> fieldMessages(List<FieldError> fields) {
        return fields.stream().collect(groupingBy(FieldError::getField, mapping(FieldError::getDefaultMessage, toList())));
    }

    static class InvalidAuthInput extends RuntimeException {
        private final Map<String, List<String>> errors;

        InvalidAuthInput(List<FieldError> fields) {
            this.errors = fieldMessages(fields);
        }
    }

    public record ApiError(int status, String message, Map<String, List<String>> errors) {
    }
}
