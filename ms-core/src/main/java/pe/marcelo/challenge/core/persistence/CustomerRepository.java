package pe.marcelo.challenge.core.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Repository;
import pe.marcelo.challenge.core.model.CoreAccount;
import pe.marcelo.challenge.core.model.CoreCustomer;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.core.exception.SdkClientException;

@Repository
public class CustomerRepository {
    private final DynamoDbClient db;
    private final String table;

    public CustomerRepository(DynamoDbClient db, @Value("${app.dynamodb.table}") String table) {
        this.db = db;
        this.table = table;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        for (int attempt = 1; ; attempt++) {
            try {
                initializeWhenAvailable();
                return;
            } catch (SdkClientException exception) {
                if (attempt >= 20) {
                    throw exception;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrumpido durante el arranque de DynamoDB", interrupted);
                }
            }
        }
    }

    private void initializeWhenAvailable() {
        try {
            db.describeTable(request -> request.tableName(table));
        } catch (ResourceNotFoundException ignored) {
            db.createTable(CreateTableRequest.builder()
                    .tableName(table)
                    .attributeDefinitions(AttributeDefinition.builder().attributeName("clienteId").attributeType(ScalarAttributeType.S).build())
                    .keySchema(KeySchemaElement.builder().attributeName("clienteId").keyType(KeyType.HASH).build())
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build());
        }
        try (var waiter = db.waiter()) {
            waiter.waitUntilTableExists(request -> request.tableName(table));
        }
        seed(new CoreCustomer("12345678", List.of(
                new CoreAccount("0011000001", "AHO", "1", "A", new BigDecimal("1500.50"), new BigDecimal("1450.00")),
                new CoreAccount("0011000002", "CTE", "2", "A", new BigDecimal("320.00"), new BigDecimal("300.00")),
                new CoreAccount("0011000003", "AHO", "1", "B", new BigDecimal("50.00"), new BigDecimal("0.00"))
        )));
        seed(new CoreCustomer("87654321", List.of()));
    }

    private void seed(CoreCustomer customer) {
        try {
            db.putItem(PutItemRequest.builder()
                    .tableName(table)
                    .item(Map.of(
                            "clienteId", AttributeValue.fromS(customer.clienteId()),
                            "cuentas", AttributeValue.fromL(customer.cuentas().stream().map(this::toAttribute).toList())
                    ))
                    .conditionExpression("attribute_not_exists(clienteId)")
                    .build());
        } catch (ConditionalCheckFailedException ignored) {
            // A restart must not overwrite data already present in the simulator.
        }
    }

    public Optional<CoreCustomer> find(String customerId) {
        var item = db.getItem(GetItemRequest.builder()
                .tableName(table)
                .key(Map.of("clienteId", AttributeValue.fromS(customerId)))
                .consistentRead(true)
                .build()).item();
        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        var accounts = item.get("cuentas").l().stream().map(this::fromAttribute).toList();
        return Optional.of(new CoreCustomer(item.get("clienteId").s(), accounts));
    }

    private AttributeValue toAttribute(CoreAccount account) {
        return AttributeValue.fromM(Map.of(
                "cuentaId", AttributeValue.fromS(account.cuentaId()),
                "productoId", AttributeValue.fromS(account.productoId()),
                "monedaId", AttributeValue.fromS(account.monedaId()),
                "estado", AttributeValue.fromS(account.estado()),
                "saldoContable", AttributeValue.fromN(account.saldoContable().toPlainString()),
                "saldoDisponible", AttributeValue.fromN(account.saldoDisponible().toPlainString())
        ));
    }

    private CoreAccount fromAttribute(AttributeValue value) {
        var fields = value.m();
        return new CoreAccount(
                fields.get("cuentaId").s(), fields.get("productoId").s(), fields.get("monedaId").s(),
                fields.get("estado").s(), new BigDecimal(fields.get("saldoContable").n()),
                new BigDecimal(fields.get("saldoDisponible").n())
        );
    }
}
