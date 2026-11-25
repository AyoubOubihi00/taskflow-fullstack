package com.portfolio.taskflow.exception;

import org.springframework.validation.FieldError;

import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

public class RequestValidationException extends RuntimeException {

    private final Map<String, List<String>> errors;

    public RequestValidationException(String message, List<FieldError> fields) {
        super(message);
        this.errors = fieldMessages(fields);
    }

    Map<String, List<String>> errors() {
        return errors;
    }

    static Map<String, List<String>> fieldMessages(List<FieldError> fields) {
        return fields.stream().collect(groupingBy(FieldError::getField, mapping(FieldError::getDefaultMessage, toList())));
    }
}
