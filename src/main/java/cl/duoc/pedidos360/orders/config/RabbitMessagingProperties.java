package cl.duoc.pedidos360.orders.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Nombres de exchanges, colas y routing keys, leídos de {@code pedidos360.rabbitmq} en application.yml.
 * Ninguna otra clase escribe nombres de RabbitMQ en el código.
 */
@Validated
@ConfigurationProperties("pedidos360.rabbitmq")
public record RabbitMessagingProperties(
        @Valid @NotNull Exchanges exchanges,
        @Valid @NotNull Route email,
        @Valid @NotNull Route kitchen,
        @Valid @NotNull Route invoice,
        @Valid @NotNull Events events) {

    public record Exchanges(@NotBlank String commands, @NotBlank String deadLetter) {
    }

    /** Exchange topic de eventos de dominio y las routing keys de cada tipo de evento. */
    public record Events(@NotBlank String exchange, @NotBlank String createdKey, @NotBlank String statusChangedKey) {
    }

    /** Un flujo de trabajo: cola principal, su DLQ y la routing key con la que se publica. */
    public record Route(@NotBlank String queue, @NotBlank String dlq, @NotBlank String routingKey) {
    }
}
