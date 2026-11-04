package com.horairesync.error;

import org.springframework.http.HttpStatus;

/** Error codes returned in the body of every failed API call, each with its HTTP status. */
public enum ErrorCode {
    SCHOOL_NOT_SUPPORTED(HttpStatus.NOT_FOUND),
    PROGRAM_NOT_FOUND(HttpStatus.NOT_FOUND),
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND),
    SOURCE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    PARSING_FAILED(HttpStatus.BAD_GATEWAY),
    INVALID_SCHEDULE(HttpStatus.BAD_REQUEST),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
