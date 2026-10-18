package cl.duoc.pedidos360.orders.messaging;

import java.time.Instant;
import java.util.Map;

/** Envelope común de los comandos enviados a RabbitMQ: type, eventId, timestamp, traceId, correlationId + payload. */
public record CommandEnvelope(String type, String eventId, Instant timestamp, String traceId, String correlationId,
                              Map<String, Object> payload) {
}
