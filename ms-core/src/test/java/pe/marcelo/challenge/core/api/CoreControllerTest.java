package pe.marcelo.challenge.core.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pe.marcelo.challenge.core.model.CoreCustomer;
import pe.marcelo.challenge.core.persistence.CustomerRepository;
import pe.marcelo.challenge.core.simulation.SimulationState;

class CoreControllerTest {
    private final SimulationState simulation = new SimulationState();
    private final CoreController controller = new CoreController(new CustomerRepository(null, "test") {
        @Override public Optional<CoreCustomer> find(String id) {
            return id.equals("12345678") ? Optional.of(new CoreCustomer(id, List.of())) : Optional.empty();
        }
    }, simulation);

    @Test
    void returnsSpanishErrorForMissingCustomer() {
        var response = controller.accounts("99999999", "request-id");
        assertEquals(404, response.getStatusCode().value());
        assertEquals("CLIENTE_NO_EXISTE", ((CoreError) response.getBody()).codigo());
    }

    @Test
    void reproducesUnavailableAndErrorModes() {
        simulation.set(SimulationState.Mode.unavailable, 0);
        assertEquals(503, controller.accounts("12345678", "request-id").getStatusCode().value());
        simulation.set(SimulationState.Mode.error, 0);
        assertEquals(500, controller.accounts("12345678", "request-id").getStatusCode().value());
    }
}
