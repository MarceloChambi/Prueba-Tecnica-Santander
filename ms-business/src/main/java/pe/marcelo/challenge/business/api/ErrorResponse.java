package pe.marcelo.challenge.business.api;

public record ErrorResponse(String code, String message, String requestId) {}
