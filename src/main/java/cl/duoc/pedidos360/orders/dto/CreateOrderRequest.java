package cl.duoc.pedidos360.orders.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @Size(max = 150) String customerName,
        @NotEmpty(message = "el pedido debe tener al menos un producto") List<@Valid Item> items) {

    public record Item(
            @NotNull(message = "es obligatorio") Long productId,
            @Positive(message = "debe ser mayor a 0") int quantity) {
    }
}
