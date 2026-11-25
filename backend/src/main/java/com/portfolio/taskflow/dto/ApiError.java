package com.portfolio.taskflow.dto;

import java.util.List;
import java.util.Map;

public record ApiError(int status, String message, Map<String, List<String>> errors) {

    public ApiError(int status, String message) {
        this(status, message, Map.of());
    }
}
