package com.cubetech.eventpass.common;

import java.time.Instant;
import java.util.Map;

/**
 * Uniform error body for every failed API call.
 *
 * @param code        stable machine-readable code the frontend can branch on
 * @param message     safe, human-readable message
 * @param fieldErrors per-field validation messages (field name to message), when relevant
 */
public record ApiError(int status, String code, String message, Map<String, String> fieldErrors, Instant timestamp) {

    public static ApiError of(int status, String code, String message) {
        return new ApiError(status, code, message, null, Instant.now());
    }
}
