package pe.marcelo.challenge.core.simulation;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pe.marcelo.challenge.core.api.CoreError;

@RestController
@Profile("local")
public class SimulationController {
    public record SimulationRequest(String mode, Integer delayMs) {
    }

    private final SimulationState state;

    public SimulationController(SimulationState state) {
        this.state = state;
    }

    @PostMapping("/__admin/simulation")
    public ResponseEntity<?> configure(@RequestBody SimulationRequest request) {
        try {
            var mode = SimulationState.Mode.valueOf(request.mode());
            int delay = request.delayMs() == null ? 3000 : request.delayMs();
            if (delay < 0 || delay > 30000) {
                throw new IllegalArgumentException();
            }
            state.set(mode, delay);
            return ResponseEntity.ok(Map.of("modo", mode.name(), "retrasoMs", delay));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return ResponseEntity.badRequest().body(new CoreError("SIMULACION_INVALIDA", "Modo o retraso no válido"));
        }
    }
}
