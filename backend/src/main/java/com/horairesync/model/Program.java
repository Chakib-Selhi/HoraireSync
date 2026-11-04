package com.horairesync.model;

/**
 * A study program.
 *
 * @param code     program code (e.g. {@code 7416})
 * @param name     program title as published
 * @param type     program type as published (e.g. "Baccalauréat", "Certificat"), or {@code null}
 * @param schoolId school identifier
 */
public record Program(String code, String name, String type, String schoolId) {}
