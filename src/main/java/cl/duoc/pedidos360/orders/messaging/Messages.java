package cl.duoc.pedidos360.orders.messaging;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import cl.duoc.pedidos360.orders.dto.OrderResponse;
import cl.duoc.pedidos360.orders.model.OrderStatus;

/** Contratos de los mensajes que publica ms-pedidos360-orders. */
public final class Messages {

    private Messages() {
    }

    /** Quién realizó la acción y desde dónde. */
    public record Actor(String id, String name, String email, List<String> roles, String ip) {
    }

    /** Evento de negocio publicado en el tópico orders.events. */
    public record OrderEvent(String eventId, String type, Instant occurredAt, String traceId, String correlationId,
                             String source, Actor actor, OrderStatus previousStatus, OrderResponse order) {
    }

    /** Registro de auditoría publicado en audit.timeline: quién / qué / cuándo / desde dónde. */
    public record AuditTimelineEntry(String eventId, Instant occurredAt, Actor actor, String action, String summary,
                                     String entityType, String entityId, String source, String traceId,
                                     String correlationId) {
    }

    /** Envelope común de los comandos enviados a RabbitMQ. */
    public record CommandEnvelope(String type, String eventId, Instant timestamp, String traceId,
                                  String correlationId, Map<String, Object> payload) {
    }
}
