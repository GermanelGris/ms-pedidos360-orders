package cl.duoc.pedidos360.orders.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.duoc.pedidos360.orders.client.CatalogClient;
import cl.duoc.pedidos360.orders.client.CatalogClient.ProductInfo;
import cl.duoc.pedidos360.orders.client.CatalogClient.StockItem;
import cl.duoc.pedidos360.orders.dto.CreateOrderRequest;
import cl.duoc.pedidos360.orders.dto.OrderResponse;
import cl.duoc.pedidos360.orders.dto.RequestUser;
import cl.duoc.pedidos360.orders.exception.ApiException;
import cl.duoc.pedidos360.orders.model.Order;
import cl.duoc.pedidos360.orders.model.OrderItem;
import cl.duoc.pedidos360.orders.model.OrderStatus;
import cl.duoc.pedidos360.orders.repository.OrderRepository;

@Service
public class OrderService {

    private final OrderRepository repository;
    private final CatalogClient catalog;

    public OrderService(OrderRepository repository, CatalogClient catalog) {
        this.repository = repository;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(RequestUser user) {
        List<Order> orders = user.isStaff()
                ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findByCustomerIdOrderByCreatedAtDesc(user.id());
        return orders.stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id, RequestUser user) {
        Order order = repository.findById(id).orElseThrow(() -> notFound(id));
        if (!user.isStaff() && !order.getCustomerId().equals(user.id())) {
            throw notFound(id);
        }
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request, RequestUser user) {
        Order order = new Order();
        order.setCustomerId(user.id());
        order.setCustomerName(request.customerName() != null && !request.customerName().isBlank()
                ? request.customerName()
                : user.name());
        order.setStatus(OrderStatus.CREADO);

        BigDecimal total = BigDecimal.ZERO;
        for (CreateOrderRequest.Item line : request.items()) {
            ProductInfo product = catalog.getProduct(line.productId());
            if (!product.active()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "El producto " + product.name() + " no está disponible");
            }
            OrderItem item = new OrderItem(product.id(), product.name(), line.quantity(), product.price());
            order.addItem(item);
            total = total.add(item.subtotal());
        }
        order.setTotal(total);
        return OrderResponse.from(repository.save(order));
    }

    @Transactional
    public OrderResponse changeStatus(Long id, OrderStatus next) {
        Order order = repository.findById(id).orElseThrow(() -> notFound(id));
        OrderStatus current = order.getStatus();

        if (!current.canTransitionTo(next)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Transición inválida %s -> %s. Desde %s solo se permite: %s"
                            .formatted(current, next, current, current.nextStatuses()));
        }
        if (next == OrderStatus.ACEPTADO) {
            catalog.decreaseStock(order.getItems().stream()
                    .map(item -> new StockItem(item.getProductId(), item.getQuantity()))
                    .toList());
        }
        if (next == OrderStatus.ENTREGADO) {
            order.setDeliveredAt(Instant.now());
        }
        order.setStatus(next);
        return OrderResponse.from(repository.save(order));
    }

    private static ApiException notFound(Long id) {
        return new ApiException(HttpStatus.NOT_FOUND, "El pedido " + id + " no existe");
    }
}
