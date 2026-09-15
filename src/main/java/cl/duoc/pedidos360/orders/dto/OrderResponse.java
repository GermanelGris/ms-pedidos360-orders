package cl.duoc.pedidos360.orders.dto;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import cl.duoc.pedidos360.orders.model.Order;
import cl.duoc.pedidos360.orders.model.OrderStatus;

public record OrderResponse(
        Long id,
        String customerId,
        String customerName,
        OrderStatus status,
        Set<OrderStatus> nextStatuses,
        BigDecimal total,
        Instant createdAt,
        Instant updatedAt,
        Instant deliveredAt,
        Long leadTimeMinutes,
        List<Item> items) {

    public record Item(Long productId, String productName, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {
    }

    public static OrderResponse from(Order order) {
        Long leadTime = order.getCreatedAt() != null && order.getDeliveredAt() != null
                ? Duration.between(order.getCreatedAt(), order.getDeliveredAt()).toMinutes()
                : null;
        List<Item> items = order.getItems().stream()
                .map(i -> new Item(i.getProductId(), i.getProductName(), i.getQuantity(), i.getUnitPrice(), i.subtotal()))
                .toList();
        return new OrderResponse(order.getId(), order.getCustomerId(), order.getCustomerName(), order.getStatus(),
                order.getStatus().nextStatuses(), order.getTotal(), order.getCreatedAt(), order.getUpdatedAt(),
                order.getDeliveredAt(), leadTime, items);
    }
}
