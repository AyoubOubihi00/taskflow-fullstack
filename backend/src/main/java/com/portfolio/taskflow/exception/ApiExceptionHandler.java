package com.portfolio.taskflow.exception;

import com.portfolio.taskflow.dto.ApiError;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ApiError> handleTaskNotFound(TaskNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        return error(HttpStatus.BAD_REQUEST, "Les données de la tâche sont invalides.",
                RequestValidationException.fieldMessages(exception.getBindingResult().getFieldErrors()));
    }

    @ExceptionHandler(RequestValidationException.class)
    public ResponseEntity<ApiError> handleRequestValidation(RequestValidationException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), exception.errors());
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

    private ResponseEntity<ApiError> error(HttpStatusCode status, String message, Map<String, List<String>> errors) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message, errors));
    }

}
