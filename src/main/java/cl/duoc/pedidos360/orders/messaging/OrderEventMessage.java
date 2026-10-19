package cl.duoc.pedidos360.orders.messaging;

import java.time.Instant;
import java.util.List;

import cl.duoc.pedidos360.orders.dto.OrderResponse;
import cl.duoc.pedidos360.orders.model.OrderStatus;

/**
 * Evento de dominio publicado en {@code orders.events}: qué pasó (type), cuándo, quién lo hizo y la foto completa
 * del pedido después del cambio. Lo consumen ms-pedidos360-report (KPIs) y ms-pedidos360-audit (timeline).
 */
public record OrderEventMessage(String eventId, String type, Instant occurredAt, String traceId,
                                String correlationId, String source, Actor actor, OrderStatus previousStatus,
                                OrderResponse order) {

    public record Actor(String id, String name, List<String> roles) {
    }
}
