package cl.duoc.pedidos360.orders.event;

import cl.duoc.pedidos360.orders.dto.OrderResponse;
import cl.duoc.pedidos360.orders.model.OrderStatus;

/**
 * Evento de dominio: un pedido se creó o cambió de estado. El servicio de negocio solo publica este evento;
 * no sabe si alguien lo traduce a mensajes de RabbitMQ. {@code previousStatus} es null al crear el pedido.
 */
public record OrderChangedEvent(OrderResponse order, OrderStatus previousStatus) {
}
