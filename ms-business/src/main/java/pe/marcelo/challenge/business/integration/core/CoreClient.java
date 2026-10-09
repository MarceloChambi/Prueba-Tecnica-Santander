package pe.marcelo.challenge.business.integration.core;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CoreClient {
    private final RestClient restClient;

    public CoreClient(RestClient coreRestClient) {
        this.restClient = coreRestClient;
    }

    public CoreCustomer getCustomer(String customerId, String requestId) {
        return restClient.get()
                .uri("/v1/clientes/{clienteId}/cuentas", customerId)
                .header("X-Request-Id", requestId)
                .retrieve()
                .body(CoreCustomer.class);
    }
}
