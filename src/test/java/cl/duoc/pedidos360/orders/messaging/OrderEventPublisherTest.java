package cl.duoc.pedidos360.orders.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import tools.jackson.databind.json.JsonMapper;

import cl.duoc.pedidos360.orders.dto.OrderResponse;
import cl.duoc.pedidos360.orders.dto.RequestUser;
import cl.duoc.pedidos360.orders.event.OrderChangedEvent;
import cl.duoc.pedidos360.orders.model.Order;
import cl.duoc.pedidos360.orders.model.OrderItem;
import cl.duoc.pedidos360.orders.model.OrderStatus;

@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafka;

    @Mock
    private RabbitTemplate rabbit;

    private OrderEventPublisher publisher;

    private final RequestUser operador = new RequestUser("operador-1", "Operador Uno", null, "190.1.2.3",
            List.of("Operador"));

    @BeforeEach
    void setUp() {
        publisher = new OrderEventPublisher(kafka, rabbit, JsonMapper.builder().build());
    }

    @Test
    void aceptarPublicaEventoAuditoriaEmailYTicketDeCocina() {
        publisher.onOrderChanged(new OrderChangedEvent(pedido(OrderStatus.ACEPTADO), OrderStatus.CREADO, operador));

        verify(kafka).send(eq(MessagingTopics.ORDERS_EVENTS), eq("12"), contains("\"type\":\"OrderAccepted\""));
        verify(kafka).send(eq(MessagingTopics.AUDIT_TIMELINE), anyString(), contains("CREADO -> ACEPTADO por Operador Uno"));
        verify(rabbit).send(eq(RabbitTopology.DIRECT_EXCHANGE), eq(RabbitTopology.EMAIL_SEND), any(Message.class));
        verify(rabbit).send(eq(RabbitTopology.DIRECT_EXCHANGE), eq(RabbitTopology.KITCHEN_TICKET), any(Message.class));
        verify(rabbit, never()).send(eq(RabbitTopology.DIRECT_EXCHANGE), eq(RabbitTopology.INVOICE_GEN), any(Message.class));
    }

    @Test
    void entregarGeneraBoleta() {
        publisher.onOrderChanged(new OrderChangedEvent(pedido(OrderStatus.ENTREGADO), OrderStatus.DESPACHADO, operador));

        verify(rabbit).send(eq(RabbitTopology.DIRECT_EXCHANGE), eq(RabbitTopology.INVOICE_GEN), any(Message.class));
        verify(rabbit, never()).send(eq(RabbitTopology.DIRECT_EXCHANGE), eq(RabbitTopology.KITCHEN_TICKET), any(Message.class));
    }

    @Test
    void cadaEstadoTieneSuTipoDeEvento() {
        assertThat(OrderEventPublisher.eventType(OrderStatus.CREADO)).isEqualTo("OrderCreated");
        assertThat(OrderEventPublisher.eventType(OrderStatus.EN_PREPARACION)).isEqualTo("OrderPreparing");
        assertThat(OrderEventPublisher.eventType(OrderStatus.DESPACHADO)).isEqualTo("OrderDispatched");
        assertThat(OrderEventPublisher.eventType(OrderStatus.CANCELADO)).isEqualTo("OrderCancelled");
    }

    private static OrderResponse pedido(OrderStatus status) {
        Order order = new Order();
        order.setId(12L);
        order.setCustomerId("cliente-1");
        order.setCustomerName("Cliente Uno");
        order.setCustomerEmail("cliente1@pedidos360.onmicrosoft.com");
        order.setStatus(status);
        order.setTotal(new BigDecimal("17980"));
        order.addItem(new OrderItem(1L, "Pizza", 2, new BigDecimal("8990")));
        return OrderResponse.from(order);
    }
}
