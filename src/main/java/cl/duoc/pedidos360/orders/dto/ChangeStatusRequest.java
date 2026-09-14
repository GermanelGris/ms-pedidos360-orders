package cl.duoc.pedidos360.orders.dto;

import jakarta.validation.constraints.NotNull;

import cl.duoc.pedidos360.orders.model.OrderStatus;

public record ChangeStatusRequest(@NotNull(message = "es obligatorio") OrderStatus status) {
}
