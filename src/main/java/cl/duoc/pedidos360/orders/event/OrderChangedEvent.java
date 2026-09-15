package cl.duoc.pedidos360.orders.event;

import cl.duoc.pedidos360.orders.dto.OrderResponse;
import cl.duoc.pedidos360.orders.dto.RequestUser;
import cl.duoc.pedidos360.orders.model.OrderStatus;

/**
 * Evento de dominio: un pedido se creó o cambió de estado.
 * {@code previousStatus} es null cuando el pedido recién se crea.
 */
public record OrderChangedEvent(OrderResponse order, OrderStatus previousStatus, RequestUser actor) {
}
