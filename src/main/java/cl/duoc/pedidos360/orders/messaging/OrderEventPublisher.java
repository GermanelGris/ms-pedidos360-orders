package cl.duoc.pedidos360.orders.messaging;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import tools.jackson.databind.ObjectMapper;

import cl.duoc.pedidos360.orders.config.RabbitMessagingProperties;
import cl.duoc.pedidos360.orders.dto.RequestUser;
import cl.duoc.pedidos360.orders.event.OrderChangedEvent;

/**
 * Productor de eventos de dominio (publish/subscribe): cada {@link OrderChangedEvent} confirmado se publica una vez
 * en el exchange topic {@code orders.events}, con routing key {@code order.created} u {@code order.status-changed}.
 * Cada suscriptor recibe su propia copia en su cola.
 */
@Component
@ConditionalOnProperty(name = "pedidos360.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class OrderEventPublisher {

    static final String ORDER_CREATED = "OrderCreated";
    static final String ORDER_STATUS_CHANGED = "OrderStatusChanged";

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final RabbitTemplate rabbit;
    private final ObjectMapper mapper;
    private final RabbitMessagingProperties.Events events;
    private final String source;

    public OrderEventPublisher(RabbitTemplate rabbit, ObjectMapper mapper, RabbitMessagingProperties properties,
                               @Value("${spring.application.name}") String source) {
        this.rabbit = rabbit;
        this.mapper = mapper;
        this.events = properties.events();
        this.source = source;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderChanged(OrderChangedEvent event) {
        boolean created = event.previousStatus() == null;
        String routingKey = created ? events.createdKey() : events.statusChangedKey();
        OrderEventMessage message = new OrderEventMessage(UUID.randomUUID().toString(),
                created ? ORDER_CREATED : ORDER_STATUS_CHANGED, Instant.now(), UUID.randomUUID().toString(),
                "order-" + event.order().id(), source, actor(event.actor()), event.previousStatus(), event.order());
        try {
            rabbit.send(events.exchange(), routingKey, toAmqp(message), new CorrelationData(message.eventId()));
            log.info("Evento {} publicado ({} -> {}) para {}", message.type(), events.exchange(), routingKey,
                    message.correlationId());
        } catch (AmqpException ex) {
            // Igual que los comandos: el pedido ya está guardado y la respuesta HTTP no debe fallar
            log.error("No fue posible publicar el evento {} del pedido {}: {}", message.type(), event.order().id(),
                    ex.getMessage());
        }
    }

    private Message toAmqp(OrderEventMessage event) {
        return MessageBuilder.withBody(mapper.writeValueAsBytes(event))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(event.eventId())
                .setCorrelationId(event.correlationId())
                .setType(event.type())
                .setHeader("x-trace-id", event.traceId())
                .build();
    }

    private static OrderEventMessage.Actor actor(RequestUser user) {
        return user == null ? null : new OrderEventMessage.Actor(user.id(), user.name(), user.roles());
    }
}
