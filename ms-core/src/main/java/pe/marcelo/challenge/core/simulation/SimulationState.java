package pe.marcelo.challenge.core.simulation;

import org.springframework.stereotype.Component;

@Component
public class SimulationState {
    public enum Mode {
        normal, slow, unavailable, error
    }

    public record Settings(Mode mode, int delayMs) {
    }

    private volatile Settings settings = new Settings(Mode.normal, 3000);

    public Settings get() {
        return settings;
    }

    public void set(Mode mode, int delayMs) {
        settings = new Settings(mode, delayMs);
    }
}
