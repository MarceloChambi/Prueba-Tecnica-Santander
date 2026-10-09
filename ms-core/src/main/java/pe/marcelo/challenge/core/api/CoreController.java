package pe.marcelo.challenge.core.api;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import pe.marcelo.challenge.core.persistence.CustomerRepository;
import pe.marcelo.challenge.core.simulation.SimulationState;

@RestController
public class CoreController {
    private static final Logger log = LoggerFactory.getLogger(CoreController.class);
    private final CustomerRepository repository;
    private final SimulationState simulation;

    public CoreController(CustomerRepository repository, SimulationState simulation) {
        this.repository = repository;
        this.simulation = simulation;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("estado", "activo");
    }

    @GetMapping("/v1/clientes/{clienteId}/cuentas")
    public ResponseEntity<?> accounts(@PathVariable String clienteId,
                                      @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        if (!clienteId.matches("[0-9]{8}")) {
            return ResponseEntity.badRequest().body(new CoreError("CLIENTE_ID_INVALIDO", "El cliente debe tener 8 dígitos"));
        }

        var settings = simulation.get();
        if (settings.mode() == SimulationState.Mode.slow) {
            try {
                Thread.sleep(settings.delayMs());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return ResponseEntity.internalServerError().body(new CoreError("ERROR_INTERNO", "Error interno del core"));
            }
        }
        if (settings.mode() == SimulationState.Mode.unavailable) {
            return ResponseEntity.status(503).body(new CoreError("CORE_NO_DISPONIBLE", "El core no está disponible"));
        }
        if (settings.mode() == SimulationState.Mode.error) {
            return ResponseEntity.internalServerError().body(new CoreError("ERROR_INTERNO", "Error interno del core"));
        }

        try {
            return repository.find(clienteId)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(404)
                            .body(new CoreError("CLIENTE_NO_EXISTE", "El cliente no existe en el core")));
        } catch (RuntimeException exception) {
            log.error("Error al consultar DynamoDB, requestId={}", requestId, exception);
            return ResponseEntity.internalServerError().body(new CoreError("ERROR_INTERNO", "Error interno del core"));
        }
    }
}
