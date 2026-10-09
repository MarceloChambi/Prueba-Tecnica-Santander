package pe.marcelo.challenge.business.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import pe.marcelo.challenge.business.api.AccountResponse;
import pe.marcelo.challenge.business.api.AccountsResponse;
import pe.marcelo.challenge.business.integration.core.CoreAccount;
import pe.marcelo.challenge.business.integration.core.CoreCustomer;

@Component
public class AccountMapper {
    public AccountsResponse map(CoreCustomer core, String requestedCustomerId, String currencyFilter, String requestId) {
        if (core == null || !requestedCustomerId.equals(core.clienteId()) || core.cuentas() == null)
            throw new IllegalArgumentException("Invalid core customer");

        List<AccountResponse> accounts = new ArrayList<>();
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        if (currencyFilter != null) {
            totals.put(currencyFilter, BigDecimal.ZERO.setScale(2));
        }
        for (CoreAccount raw : core.cuentas()) {
            if (raw == null || raw.cuentaId() == null || raw.cuentaId().isBlank())
                throw new IllegalArgumentException("Invalid core account");
            String product = switch (raw.productoId()) {
                case "AHO" -> "SAVINGS";
                case "CTE" -> "CHECKING";
                default -> throw new IllegalArgumentException("Unknown product");
            };
            String currency = switch (raw.monedaId()) {
                case "1" -> "PEN";
                case "2" -> "USD";
                default -> throw new IllegalArgumentException("Unknown currency");
            };
            String status = switch (raw.estado()) {
                case "A" -> "ACTIVE";
                case "B" -> "BLOCKED";
                default -> throw new IllegalArgumentException("Unknown status");
            };
            BigDecimal ledger = money(raw.saldoContable());
            BigDecimal available = money(raw.saldoDisponible());
            if (currencyFilter != null && !currencyFilter.equals(currency)) {
                continue;
            }
            accounts.add(new AccountResponse(raw.cuentaId(), product, currency, status,
                    ledger.toPlainString(), available.toPlainString()));
            totals.putIfAbsent(currency, BigDecimal.ZERO.setScale(2));
            if (status.equals("ACTIVE")) {
                totals.put(currency, totals.get(currency).add(available));
            }
        }
        Map<String, String> outputTotals = new LinkedHashMap<>();
        totals.forEach((currency, amount) -> outputTotals.put(currency, amount.toPlainString()));
        return new AccountsResponse(core.clienteId(), List.copyOf(accounts), outputTotals, requestId);
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("Missing balance");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }
}
