package pe.marcelo.challenge.business.application;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import pe.marcelo.challenge.business.integration.core.CoreClient;

class AccountsServiceTest {
    private static HttpServer server;
    private static ExecutorService executor;
    private static AccountsService service;
    private static final AtomicReference<String> mode = new AtomicReference<>("normal");

    @BeforeAll
    static void startCoreStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/v1/clientes/12345678/cuentas", exchange -> {
            int status = switch (mode.get()) {
                case "missing" -> 404;
                case "unavailable" -> 503;
                case "error" -> 500;
                default -> 200;
            };
            if (mode.get().equals("slow")) {
                try { Thread.sleep(400); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
            byte[] body = (status == 200 ? "{\"clienteId\":\"12345678\",\"cuentas\":[]}" : "{\"codigo\":\"ERROR\",\"mensaje\":\"Falla del core\"}").getBytes();
            try {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
            } finally { exchange.close(); }
        });
        server.start();
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofMillis(100)).build());
        factory.setReadTimeout(Duration.ofMillis(100));
        var client = RestClient.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .requestFactory(factory).build();
        service = new AccountsService(new CoreClient(client), new AccountMapper());
    }

    @AfterAll
    static void stopCoreStub() { server.stop(0); executor.shutdownNow(); }

    @Test
    void mapsCoreHttpFailures() {
        for (var caseData : new String[][] {
                { "missing", "CUSTOMER_NOT_FOUND" },
                { "unavailable", "CORE_UNAVAILABLE" },
                { "error", "CORE_ERROR" }
        }) {
            mode.set(caseData[0]);
            var exception = assertThrows(BusinessException.class, () -> service.get("12345678", null, "request-id"));
            assertEquals(caseData[1], exception.code());
        }
        mode.set("normal");
        assertEquals("12345678", service.get("12345678", null, "request-id").customerId());
    }

    @Test
    void timesOutSlowCore() {
        mode.set("slow");
        var exception = assertThrows(BusinessException.class, () -> service.get("12345678", null, "request-id"));
        assertEquals("CORE_TIMEOUT", exception.code());
        mode.set("normal");
    }
}
