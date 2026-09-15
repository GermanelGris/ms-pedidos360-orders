package cl.duoc.pedidos360.orders.controller;

import java.util.List;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.pedidos360.orders.dto.ChangeStatusRequest;
import cl.duoc.pedidos360.orders.dto.CreateOrderRequest;
import cl.duoc.pedidos360.orders.dto.OrderResponse;
import cl.duoc.pedidos360.orders.dto.RequestUser;
import cl.duoc.pedidos360.orders.service.OrderService;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Pedidos")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Listar pedidos (Admin/Operador ven todos, Cliente solo los suyos)")
    public List<OrderResponse> list(@Parameter(hidden = true) RequestUser user) {
        return service.list(user);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un pedido")
    public OrderResponse get(@PathVariable Long id, @Parameter(hidden = true) RequestUser user) {
        return service.get(id, user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear pedido en estado CREADO")
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request,
                                @Parameter(hidden = true) RequestUser user) {
        return service.create(request, user);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Cambiar estado (valida la máquina de estados y descuenta stock al aceptar)")
    public OrderResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeStatusRequest request,
                                      @Parameter(hidden = true) RequestUser user) {
        return service.changeStatus(id, request.status(), user);
    }
}
