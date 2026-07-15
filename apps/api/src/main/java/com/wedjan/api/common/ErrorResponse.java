package com.wedjan.api.common;

import java.util.List;

/** Standard error envelope: {error:{code,message,fieldErrors[],traceId}}. */
public record ErrorResponse(ErrorBody error) {

    public record ErrorBody(String code, String message, List<FieldError> fieldErrors, String traceId) {}

    public record FieldError(String field, String message) {}

    public static ErrorResponse of(String code, String message, List<FieldError> fieldErrors, String traceId) {
        return new ErrorResponse(new ErrorBody(code, message, fieldErrors == null ? List.of() : fieldErrors, traceId));
    }
}
