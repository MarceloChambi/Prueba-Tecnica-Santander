package pe.marcelo.challenge.business.application;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pe.marcelo.challenge.business.integration.core.CoreAccount;
import pe.marcelo.challenge.business.integration.core.CoreCustomer;

class AccountMapperTest {
    private final AccountMapper mapper = new AccountMapper();
    private final CoreCustomer customer = new CoreCustomer("12345678", List.of(
            new CoreAccount("0011000001", "AHO", "1", "A", new BigDecimal("1500.50"), new BigDecimal("1450.00")),
            new CoreAccount("0011000002", "CTE", "2", "A", new BigDecimal("320.00"), new BigDecimal("300.00")),
            new CoreAccount("0011000003", "AHO", "1", "B", new BigDecimal("50.00"), new BigDecimal("0.00"))
    ));

    @Test
    void mapsAccountsAndTotalsOnlyActiveBalances() {
        var result = mapper.map(customer, "12345678", null, "test-request");
        assertEquals(3, result.accounts().size());
        assertEquals(Map.of("PEN", "1450.00", "USD", "300.00"), result.totalAvailableByCurrency());
        assertEquals("BLOCKED", result.accounts().get(2).status());
        assertEquals("SAVINGS", result.accounts().getFirst().product());
    }

    @Test
    void filtersCurrencyAndHandlesEmptyCustomer() {
        var usd = mapper.map(customer, "12345678", "USD", "test-request");
        assertEquals(1, usd.accounts().size());
        assertEquals(Map.of("USD", "300.00"), usd.totalAvailableByCurrency());
        var empty = mapper.map(new CoreCustomer("87654321", List.of()), "87654321", "PEN", "test-request");
        assertEquals(List.of(), empty.accounts());
        assertEquals(Map.of("PEN", "0.00"), empty.totalAvailableByCurrency());
    }

    @Test
    void rejectsUnknownCodesAndExcessPrecision() {
        assertThrows(IllegalArgumentException.class, () -> mapper.map(
                new CoreCustomer("12345678", List.of(new CoreAccount("x", "AHO", "1", "X", BigDecimal.ZERO, BigDecimal.ZERO))),
                "12345678", null, "test-request"));
        assertThrows(ArithmeticException.class, () -> mapper.map(
                new CoreCustomer("12345678", List.of(new CoreAccount("x", "AHO", "1", "A", BigDecimal.ZERO, new BigDecimal("1.234")))),
                "12345678", null, "test-request"));
    }
}
