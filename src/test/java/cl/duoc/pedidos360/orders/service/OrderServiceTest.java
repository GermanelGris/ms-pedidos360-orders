package cl.duoc.pedidos360.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import cl.duoc.pedidos360.orders.client.CatalogClient;
import cl.duoc.pedidos360.orders.client.CatalogClient.ProductInfo;
import cl.duoc.pedidos360.orders.client.CatalogClient.StockItem;
import cl.duoc.pedidos360.orders.dto.CreateOrderRequest;
import cl.duoc.pedidos360.orders.dto.OrderResponse;
import cl.duoc.pedidos360.orders.dto.RequestUser;
import cl.duoc.pedidos360.orders.event.OrderChangedEvent;
import cl.duoc.pedidos360.orders.exception.ApiException;
import cl.duoc.pedidos360.orders.model.Order;
import cl.duoc.pedidos360.orders.model.OrderItem;
import cl.duoc.pedidos360.orders.model.OrderStatus;
import cl.duoc.pedidos360.orders.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository repository;

    @Mock
    private CatalogClient catalog;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private OrderService service;

    private final RequestUser cliente = new RequestUser("cliente-1", "Cliente Uno",
            "cliente1@pedidos360.onmicrosoft.com", "10.0.0.1", List.of("Cliente"));
    private final RequestUser operador = new RequestUser("operador-1", "Operador Uno", null, "10.0.0.2",
            List.of("Operador"));

    @Test
    void creaPedidoEnEstadoCreadoConTotalCalculadoYPublicaEvento() {
        given(catalog.getProduct(1L)).willReturn(new ProductInfo(1L, "Pizza", new BigDecimal("8990"), 10, true));
        given(repository.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = service.create(
                new CreateOrderRequest(null, List.of(new CreateOrderRequest.Item(1L, 2))), cliente);

        assertThat(response.status()).isEqualTo(OrderStatus.CREADO);
        assertThat(response.total()).isEqualByComparingTo("17980");
        assertThat(response.customerId()).isEqualTo("cliente-1");
        assertThat(response.customerName()).isEqualTo("Cliente Uno");
        assertThat(response.customerEmail()).isEqualTo("cliente1@pedidos360.onmicrosoft.com");

        ArgumentCaptor<OrderChangedEvent> event = ArgumentCaptor.forClass(OrderChangedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().previousStatus()).isNull();
        assertThat(event.getValue().actor()).isEqualTo(cliente);
    }

    @Test
    void aceptarPedidoDescuentaStockYPublicaEvento() {
        Order order = pedido("cliente-1", OrderStatus.CREADO);
        given(repository.findById(1L)).willReturn(Optional.of(order));
        given(repository.save(order)).willReturn(order);

        OrderResponse response = service.changeStatus(1L, OrderStatus.ACEPTADO, operador);

        assertThat(response.status()).isEqualTo(OrderStatus.ACEPTADO);
        verify(catalog).decreaseStock(List.of(new StockItem(1L, 2)));

        ArgumentCaptor<OrderChangedEvent> event = ArgumentCaptor.forClass(OrderChangedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().previousStatus()).isEqualTo(OrderStatus.CREADO);
        assertThat(event.getValue().order().status()).isEqualTo(OrderStatus.ACEPTADO);
    }

    @Test
    void despacharSinAceptarRespondeConflictoYNoPublicaEvento() {
        given(repository.findById(1L)).willReturn(Optional.of(pedido("cliente-1", OrderStatus.CREADO)));

        assertThatThrownBy(() -> service.changeStatus(1L, OrderStatus.DESPACHADO, operador))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
        verifyNoInteractions(catalog, events);
    }

    @Test
    void clienteNoPuedeVerPedidosDeOtroCliente() {
        given(repository.findById(1L)).willReturn(Optional.of(pedido("otro-cliente", OrderStatus.CREADO)));

        assertThatThrownBy(() -> service.get(1L, cliente))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static Order pedido(String customerId, OrderStatus status) {
        Order order = new Order();
        order.setId(1L);
        order.setCustomerId(customerId);
        order.setStatus(status);
        order.addItem(new OrderItem(1L, "Pizza", 2, new BigDecimal("8990")));
        return order;
    }
}
