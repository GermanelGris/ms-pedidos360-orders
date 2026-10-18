package cl.duoc.pedidos360.orders.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología que necesita el productor. Se declaran también las colas (no solo el exchange) para que los comandos
 * no se pierdan si ms-pedidos360-notify todavía no ha levantado: quedan esperando en su cola.
 *
 * <pre>
 * Caso de uso        routing key      cola principal   DLQ (vía cmd.dead.dlx)
 * Notificar cliente  email.send       q.cmd.email      q.cmd.email.dlq     cada cambio de estado
 * Ticket de cocina   kitchen.ticket   q.cmd.kitchen    q.cmd.kitchen.dlq   al pasar a ACEPTADO
 * Boleta             invoice.gen      q.cmd.invoice    q.cmd.invoice.dlq   al pasar a ENTREGADO
 * </pre>
 */
@Configuration
@ConditionalOnProperty(name = "pedidos360.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitTopologyConfig {

    private final RabbitMessagingProperties properties;

    public RabbitTopologyConfig(RabbitMessagingProperties properties) {
        this.properties = properties;
    }

    // ---- Exchanges ----

    @Bean
    DirectExchange commandsExchange() {
        return ExchangeBuilder.directExchange(properties.exchanges().commands()).durable(true).build();
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(properties.exchanges().deadLetter()).durable(true).build();
    }

    // ---- Caso de uso: notificar al cliente ----

    @Bean
    Queue emailQueue() {
        return mainQueue(properties.email());
    }

    @Bean
    Queue emailDeadLetterQueue() {
        return QueueBuilder.durable(properties.email().dlq()).build();
    }

    @Bean
    Binding emailBinding(@Qualifier("commandsExchange") DirectExchange commands) {
        return BindingBuilder.bind(emailQueue()).to(commands).with(properties.email().routingKey());
    }

    @Bean
    Binding emailDeadLetterBinding(@Qualifier("deadLetterExchange") DirectExchange deadLetter) {
        return BindingBuilder.bind(emailDeadLetterQueue()).to(deadLetter).with(properties.email().routingKey());
    }

    // ---- Caso de uso: ticket de cocina ----

    @Bean
    Queue kitchenQueue() {
        return mainQueue(properties.kitchen());
    }

    @Bean
    Queue kitchenDeadLetterQueue() {
        return QueueBuilder.durable(properties.kitchen().dlq()).build();
    }

    @Bean
    Binding kitchenBinding(@Qualifier("commandsExchange") DirectExchange commands) {
        return BindingBuilder.bind(kitchenQueue()).to(commands).with(properties.kitchen().routingKey());
    }

    @Bean
    Binding kitchenDeadLetterBinding(@Qualifier("deadLetterExchange") DirectExchange deadLetter) {
        return BindingBuilder.bind(kitchenDeadLetterQueue()).to(deadLetter).with(properties.kitchen().routingKey());
    }

    // ---- Caso de uso: boleta ----

    @Bean
    Queue invoiceQueue() {
        return mainQueue(properties.invoice());
    }

    @Bean
    Queue invoiceDeadLetterQueue() {
        return QueueBuilder.durable(properties.invoice().dlq()).build();
    }

    @Bean
    Binding invoiceBinding(@Qualifier("commandsExchange") DirectExchange commands) {
        return BindingBuilder.bind(invoiceQueue()).to(commands).with(properties.invoice().routingKey());
    }

    @Bean
    Binding invoiceDeadLetterBinding(@Qualifier("deadLetterExchange") DirectExchange deadLetter) {
        return BindingBuilder.bind(invoiceDeadLetterQueue()).to(deadLetter).with(properties.invoice().routingKey());
    }

    /** Mismos argumentos que declara ms-pedidos360-notify: los rechazos (NACK) van al dead-letter exchange. */
    private Queue mainQueue(RabbitMessagingProperties.Route route) {
        return QueueBuilder.durable(route.queue())
                .deadLetterExchange(properties.exchanges().deadLetter())
                .deadLetterRoutingKey(route.routingKey())
                .build();
    }
}
