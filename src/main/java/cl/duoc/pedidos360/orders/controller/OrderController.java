package cl.duoc.pedidos360.orders.controller;

import java.util.List;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    public List<OrderResponse> list(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                    @RequestHeader(value = "X-User-Name", required = false) String userName,
                                    @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return service.list(RequestUser.fromHeaders(userId, userName, roles));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un pedido")
    public OrderResponse get(@PathVariable Long id,
                             @RequestHeader(value = "X-User-Id", required = false) String userId,
                             @RequestHeader(value = "X-User-Name", required = false) String userName,
                             @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return service.get(id, RequestUser.fromHeaders(userId, userName, roles));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear pedido en estado CREADO")
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request,
                                @RequestHeader(value = "X-User-Id", required = false) String userId,
                                @RequestHeader(value = "X-User-Name", required = false) String userName,
                                @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        return service.create(request, RequestUser.fromHeaders(userId, userName, roles));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Cambiar estado (valida la máquina de estados y descuenta stock al aceptar)")
    public OrderResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeStatusRequest request) {
        return service.changeStatus(id, request.status());
    }
}
