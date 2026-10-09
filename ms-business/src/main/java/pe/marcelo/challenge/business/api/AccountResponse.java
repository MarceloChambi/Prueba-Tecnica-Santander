package pe.marcelo.challenge.business.api;

public record AccountResponse(String accountId, String product, String currency, String status,
                              String ledgerBalance, String availableBalance) {}
