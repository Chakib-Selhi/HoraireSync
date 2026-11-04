package com.horairesync.web;

import com.horairesync.error.ErrorCode;
import com.horairesync.error.HoraireSyncException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Turns every exception into the {@link ApiError} format with the status of its {@link ErrorCode}. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(HoraireSyncException.class)
    public ResponseEntity<ApiError> handleBusiness(HoraireSyncException e) {
        if (e.code() == ErrorCode.PARSING_FAILED || e.code() == ErrorCode.SOURCE_UNAVAILABLE) {
            log.warn("{}: {}", e.code(), e.getMessage());
        }
        return body(e.code(), e.getMessage());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception e) {
        return body(ErrorCode.INVALID_REQUEST, "Requête invalide : " + e.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(new ApiError(ErrorCode.INVALID_REQUEST.name(), "Méthode non permise : " + e.getMethod()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException e) {
        return ResponseEntity.status(404)
                .body(new ApiError(ErrorCode.INVALID_REQUEST.name(), "Route inconnue : /" + e.getResourcePath()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return body(ErrorCode.INTERNAL_ERROR, "Erreur interne inattendue.");
    }

    private static ResponseEntity<ApiError> body(ErrorCode code, String message) {
        return ResponseEntity.status(code.status()).body(new ApiError(code.name(), message));
    }
}
