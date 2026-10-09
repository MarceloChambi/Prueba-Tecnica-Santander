package pe.marcelo.challenge.business.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.util.LinkedMultiValueMap;
import pe.marcelo.challenge.business.application.AccountsService;
import pe.marcelo.challenge.business.application.BusinessException;
import pe.marcelo.challenge.business.config.LanguageConfiguration;

class AccountsControllerTest {
    private final AccountsService service = new AccountsService(null, null) {
        @Override
        public AccountsResponse get(String customerId, String currency, String requestId) {
            if (customerId.equals("99999999")) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND");
            }
            throw new AssertionError("The controller should reject invalid currency before calling the service");
        }
    };
    private final AccountsController controller = new AccountsController(
            service, new LanguageConfiguration().messageSource());

    @Test
    void returnsSpanishMessageForMissingCustomer() {
        var response = controller.accounts("99999999", new LinkedMultiValueMap<>(), "es-PE,es;q=0.9");
        var error = assertInstanceOf(ErrorResponse.class, response.getBody());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("CUSTOMER_NOT_FOUND", error.code());
        assertEquals("Cliente no encontrado", error.message());
        assertEquals("es", response.getHeaders().getContentLanguage().getLanguage());
        assertEquals(response.getHeaders().getFirst("X-Request-Id"), error.requestId());
    }

    @Test
    void defaultsToEnglish() {
        var response = controller.accounts("99999999", new LinkedMultiValueMap<>(), null);
        var error = assertInstanceOf(ErrorResponse.class, response.getBody());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Customer not found", error.message());
        assertEquals("en", response.getHeaders().getContentLanguage().getLanguage());
    }

    @Test
    void rejectsInvalidCurrencyBeforeCallingCore() {
        var query = new LinkedMultiValueMap<String, String>();
        query.add("currency", "EUR");

        var response = controller.accounts("12345678", query, null);
        var error = assertInstanceOf(ErrorResponse.class, response.getBody());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INVALID_CURRENCY", error.code());
        assertEquals("Currency must be PEN or USD", error.message());
    }
}
