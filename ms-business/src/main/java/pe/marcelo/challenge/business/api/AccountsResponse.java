package pe.marcelo.challenge.business.api;

import java.util.List;
import java.util.Map;

public record AccountsResponse(String customerId, List<AccountResponse> accounts,
                               Map<String, String> totalAvailableByCurrency, String requestId) {}
