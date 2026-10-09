package pe.marcelo.challenge.core.model;

import java.util.List;

public record CoreCustomer(String clienteId, List<CoreAccount> cuentas) {}
