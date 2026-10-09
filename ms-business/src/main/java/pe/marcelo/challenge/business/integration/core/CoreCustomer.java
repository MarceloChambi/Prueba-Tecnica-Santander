package pe.marcelo.challenge.business.integration.core;

import java.util.List;

public record CoreCustomer(String clienteId, List<CoreAccount> cuentas) {}
