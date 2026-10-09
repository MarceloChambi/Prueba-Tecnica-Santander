package pe.marcelo.challenge.business.api;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.marcelo.challenge.business.application.AccountsService;
import pe.marcelo.challenge.business.application.BusinessException;

@RestController
public class AccountsController {
    private final AccountsService service;
    private final MessageSource messages;

    public AccountsController(AccountsService service, MessageSource messages) {
        this.service = service;
        this.messages = messages;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/v1/customers/{customerId}/accounts")
    public ResponseEntity<?> accounts(@PathVariable String customerId,
                                      @RequestParam MultiValueMap<String, String> query,
                                      @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        String requestId = UUID.randomUUID().toString();
        Locale locale = language(acceptLanguage);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Request-Id", requestId);
        headers.setContentLanguage(locale);
        if (!customerId.matches("[0-9]{8}")) {
            return failure(new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CUSTOMER_ID"),
                    locale, requestId, headers);
        }
        List<String> currencies = query.get("currency");
        if (currencies != null && (currencies.size() != 1 || !List.of("PEN", "USD").contains(currencies.getFirst()))) {
            return failure(new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CURRENCY"),
                    locale, requestId, headers);
        }
        String currency = currencies == null ? null : currencies.getFirst();
        try {
            return ResponseEntity.ok().headers(headers).body(service.get(customerId, currency, requestId));
        } catch (BusinessException exception) {
            return failure(exception, locale, requestId, headers);
        }
    }

    private ResponseEntity<ErrorResponse> failure(BusinessException exception, Locale locale, String requestId, HttpHeaders headers) {
        String message = messages.getMessage(exception.code(), null, locale);
        return ResponseEntity.status(exception.status()).headers(headers)
                .body(new ErrorResponse(exception.code(), message, requestId));
    }

    private Locale language(String header) {
        if (header == null) {
            return Locale.ENGLISH;
        }
        try {
            for (Locale.LanguageRange range : Locale.LanguageRange.parse(header)) {
                if (range.getWeight() == 0) {
                    continue;
                }
                String primary = range.getRange().split("-")[0];
                if (primary.equals("es")) {
                    return Locale.of("es");
                }
                if (primary.equals("en")) {
                    return Locale.ENGLISH;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Use English when Accept-Language is malformed.
        }
        return Locale.ENGLISH;
    }
}
