package com.aeroflow.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/** Consistent error contract returned by every AeroFlow service. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String traceId,
        List<FieldViolation> fieldErrors) {

    public record FieldViolation(String field, String message) {}
}
