package cl.duoc.pedidos360.orders.messaging;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import tools.jackson.databind.ObjectMapper;

import cl.duoc.pedidos360.orders.dto.OrderResponse;
import cl.duoc.pedidos360.orders.dto.RequestUser;
import cl.duoc.pedidos360.orders.event.OrderChangedEvent;
import cl.duoc.pedidos360.orders.messaging.Messages.Actor;
import cl.duoc.pedidos360.orders.messaging.Messages.AuditTimelineEntry;
import cl.duoc.pedidos360.orders.messaging.Messages.CommandEnvelope;
import cl.duoc.pedidos360.orders.messaging.Messages.OrderEvent;
import cl.duoc.pedidos360.orders.model.OrderStatus;

/**
 * Publica, después de confirmar la transacción:
 * <ul>
 *   <li>Kafka orders.events: evento de negocio (OrderCreated, OrderAccepted, ...) con el pedido completo.</li>
 *   <li>Kafka audit.timeline: quién / qué / cuándo / desde dónde.</li>
 *   <li>RabbitMQ: comandos email.send (siempre), kitchen.ticket (al aceptar) e invoice.gen (al entregar).</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "pedidos360.messaging.enabled", havingValue = "true")
public class OrderEventPublisher {

    static final String SOURCE = "ms-pedidos360-orders";

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final KafkaTemplate<String, String> kafka;
    private final RabbitTemplate rabbit;
    private final ObjectMapper mapper;

    public OrderEventPublisher(KafkaTemplate<String, String> kafka, RabbitTemplate rabbit, ObjectMapper mapper) {
        this.kafka = kafka;
        this.rabbit = rabbit;
        this.mapper = mapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderChanged(OrderChangedEvent event) {
        OrderResponse order = event.order();
        RequestUser user = event.actor();
        String type = eventType(order.status());
        String traceId = UUID.randomUUID().toString();
        String correlationId = "order-" + order.id();
        Instant now = Instant.now();
        Actor actor = new Actor(user.id(), user.name(), user.email(), user.roles(), user.ip());

        try {
            OrderEvent orderEvent = new OrderEvent(UUID.randomUUID().toString(), type, now, traceId, correlationId,
                    SOURCE, actor, event.previousStatus(), order);
            kafka.send(MessagingTopics.ORDERS_EVENTS, String.valueOf(order.id()), mapper.writeValueAsString(orderEvent));

            AuditTimelineEntry audit = new AuditTimelineEntry(UUID.randomUUID().toString(), now, actor, type,
                    summary(event), "ORDER", String.valueOf(order.id()), SOURCE, traceId, correlationId);
            kafka.send(MessagingTopics.AUDIT_TIMELINE, audit.eventId(), mapper.writeValueAsString(audit));

            sendCommand(RabbitTopology.EMAIL_SEND, "EmailNotification", emailPayload(order), traceId, correlationId);
            if (order.status() == OrderStatus.ACEPTADO) {
                sendCommand(RabbitTopology.KITCHEN_TICKET, "KitchenTicket", kitchenPayload(order), traceId, correlationId);
            }
            if (order.status() == OrderStatus.ENTREGADO) {
                sendCommand(RabbitTopology.INVOICE_GEN, "InvoiceGeneration", invoicePayload(order), traceId, correlationId);
            }
        } catch (RuntimeException ex) {
            // El pedido ya quedó confirmado en la base de datos; un broker caído no debe revertirlo.
            // Mejora futura: patrón Transactional Outbox para garantizar la entrega.
            log.error("No fue posible publicar los mensajes del pedido {}: {}", order.id(), ex.getMessage(), ex);
        }
    }

    static String eventType(OrderStatus status) {
        return switch (status) {
            case CREADO -> "OrderCreated";
            case ACEPTADO -> "OrderAccepted";
            case EN_PREPARACION -> "OrderPreparing";
            case DESPACHADO -> "OrderDispatched";
            case ENTREGADO -> "OrderDelivered";
            case CANCELADO -> "OrderCancelled";
        };
    }

    static String summary(OrderChangedEvent event) {
        String who = event.actor().name() != null ? event.actor().name() : event.actor().id();
        if (event.previousStatus() == null) {
            return "Pedido #%d creado por %s".formatted(event.order().id(), who);
        }
        return "Pedido #%d: %s -> %s por %s".formatted(event.order().id(), event.previousStatus(),
                event.order().status(), who);
    }

    private void sendCommand(String routingKey, String type, Map<String, Object> payload, String traceId,
                             String correlationId) {
        CommandEnvelope envelope = new CommandEnvelope(type, UUID.randomUUID().toString(), Instant.now(), traceId,
                correlationId, payload);
        Message message = MessageBuilder.withBody(mapper.writeValueAsBytes(envelope))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(envelope.eventId())
                .setCorrelationId(correlationId)
                .setHeader("x-trace-id", traceId)
                .build();
        rabbit.send(RabbitTopology.DIRECT_EXCHANGE, routingKey, message);
    }

    private static Map<String, Object> emailPayload(OrderResponse order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.id());
        payload.put("to", order.customerEmail());
        payload.put("customerName", order.customerName());
        payload.put("status", order.status());
        payload.put("total", order.total());
        return payload;
    }

    private static Map<String, Object> kitchenPayload(OrderResponse order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.id());
        payload.put("customerName", order.customerName());
        payload.put("items", order.items());
        return payload;
    }

    private static Map<String, Object> invoicePayload(OrderResponse order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.id());
        payload.put("customerName", order.customerName());
        payload.put("to", order.customerEmail());
        payload.put("total", order.total());
        payload.put("items", order.items());
        return payload;
    }
}
