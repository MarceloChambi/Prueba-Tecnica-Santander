package pe.marcelo.challenge.business.application;

import java.net.http.HttpTimeoutException;
import java.net.SocketTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import pe.marcelo.challenge.business.api.AccountsResponse;
import pe.marcelo.challenge.business.integration.core.CoreClient;
import pe.marcelo.challenge.business.integration.core.CoreCustomer;

@Service
public class AccountsService {
    private static final Logger log = LoggerFactory.getLogger(AccountsService.class);
    private final CoreClient coreClient;
    private final AccountMapper mapper;

    public AccountsService(CoreClient coreClient, AccountMapper mapper) {
        this.coreClient = coreClient;
        this.mapper = mapper;
    }

    public AccountsResponse get(String customerId, String currency, String requestId) {
        CoreCustomer customer;
        try {
            customer = coreClient.getCustomer(customerId, requestId);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND");
        } catch (HttpServerErrorException.ServiceUnavailable exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "CORE_UNAVAILABLE");
        } catch (RestClientResponseException exception) {
            log.warn("Core returned status {}, requestId={}", exception.getStatusCode(), requestId);
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "CORE_ERROR");
        } catch (ResourceAccessException exception) {
            log.warn("Core request failed, requestId={}, cause={}", requestId, exception.getMessage());
            if (isTimeout(exception)) {
                throw new BusinessException(HttpStatus.GATEWAY_TIMEOUT, "CORE_TIMEOUT");
            }
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "CORE_UNAVAILABLE");
        } catch (RestClientException exception) {
            log.warn("Core response could not be read, requestId={}", requestId, exception);
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "INVALID_CORE_RESPONSE");
        }
        try {
            return mapper.map(customer, customerId, currency, requestId);
        } catch (RuntimeException exception) {
            log.warn("Invalid core data, requestId={}", requestId, exception);
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "INVALID_CORE_RESPONSE");
        }
    }

    private boolean isTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
