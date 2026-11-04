package com.horairesync.web;

/**
 * Single error format of the API: {@code {"code": "SOURCE_UNAVAILABLE", "message": "..."}}.
 *
 * <p>{@code code} is stable and meant for the frontend (which shows its own translated text); {@code message} is a
 * readable explanation in French.
 */
public record ApiError(String code, String message) {}
