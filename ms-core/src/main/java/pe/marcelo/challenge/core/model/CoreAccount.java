package pe.marcelo.challenge.core.model;

import java.math.BigDecimal;

public record CoreAccount(
        String cuentaId,
        String productoId,
        String monedaId,
        String estado,
        BigDecimal saldoContable,
        BigDecimal saldoDisponible
) {}
