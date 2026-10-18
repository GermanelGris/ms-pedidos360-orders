package cl.duoc.pedidos360.orders.messaging;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import tools.jackson.databind.ObjectMapper;

import cl.duoc.pedidos360.orders.config.RabbitMessagingProperties;
import cl.duoc.pedidos360.orders.config.RabbitMessagingProperties.Route;
import cl.duoc.pedidos360.orders.dto.OrderResponse;
import cl.duoc.pedidos360.orders.event.OrderChangedEvent;
import cl.duoc.pedidos360.orders.model.OrderStatus;

/**
 * Productor: traduce cada {@link OrderChangedEvent} en comandos de RabbitMQ, solo después de confirmar la transacción.
 * <ul>
 *   <li>email.send: en cada creación o cambio de estado (aviso al cliente).</li>
 *   <li>kitchen.ticket: cuando el pedido pasa a ACEPTADO.</li>
 *   <li>invoice.gen: cuando el pedido pasa a ENTREGADO.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "pedidos360.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class OrderCommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderCommandPublisher.class);

    private final RabbitTemplate rabbit;
    private final ObjectMapper mapper;
    private final RabbitMessagingProperties properties;

    public OrderCommandPublisher(RabbitTemplate rabbit, ObjectMapper mapper, RabbitMessagingProperties properties) {
        this.rabbit = rabbit;
        this.mapper = mapper;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderChanged(OrderChangedEvent event) {
        OrderResponse order = event.order();
        String traceId = UUID.randomUUID().toString();
        String correlationId = "order-" + order.id();
        try {
            send(properties.email(), "EmailNotification", emailPayload(order), traceId, correlationId);
            if (order.status() == OrderStatus.ACEPTADO) {
                send(properties.kitchen(), "KitchenTicket", kitchenPayload(order), traceId, correlationId);
            }
            if (order.status() == OrderStatus.ENTREGADO) {
                send(properties.invoice(), "InvoiceGeneration", invoicePayload(order), traceId, correlationId);
            }
        } catch (AmqpException ex) {
            // El pedido ya quedó guardado: un broker caído no debe revertirlo ni romper la respuesta HTTP.
            // Mejora futura: patrón Transactional Outbox para garantizar la entrega.
            log.error("No fue posible publicar los comandos del pedido {}: {}", order.id(), ex.getMessage());
        }
    }

    private void send(Route route, String type, Map<String, Object> payload, String traceId, String correlationId) {
        CommandEnvelope envelope = new CommandEnvelope(type, UUID.randomUUID().toString(), Instant.now(), traceId,
                correlationId, payload);
        Message message = MessageBuilder.withBody(mapper.writeValueAsBytes(envelope))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(envelope.eventId())
                .setCorrelationId(correlationId)
                .setHeader("x-trace-id", traceId)
                .build();
        rabbit.send(properties.exchanges().commands(), route.routingKey(), message,
                new CorrelationData(envelope.eventId()));
        log.info("Comando {} publicado ({} -> {}) para {}", type, properties.exchanges().commands(),
                route.routingKey(), correlationId);
    }

    static Map<String, Object> emailPayload(OrderResponse order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.id());
        payload.put("customerName", order.customerName());
        payload.put("status", order.status());
        payload.put("total", order.total());
        return payload;
    }

    static Map<String, Object> kitchenPayload(OrderResponse order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.id());
        payload.put("customerName", order.customerName());
        payload.put("items", order.items());
        return payload;
    }

    static Map<String, Object> invoicePayload(OrderResponse order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.id());
        payload.put("customerName", order.customerName());
        payload.put("total", order.total());
        payload.put("items", order.items());
        return payload;
    }
}
