package cl.duoc.pedidos360.orders.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import cl.duoc.pedidos360.orders.config.RabbitMessagingProperties;
import cl.duoc.pedidos360.orders.config.RabbitMessagingProperties.Events;
import cl.duoc.pedidos360.orders.config.RabbitMessagingProperties.Exchanges;
import cl.duoc.pedidos360.orders.config.RabbitMessagingProperties.Route;
import cl.duoc.pedidos360.orders.dto.OrderResponse;
import cl.duoc.pedidos360.orders.event.OrderChangedEvent;
import cl.duoc.pedidos360.orders.model.OrderStatus;

class OrderCommandPublisherTest {

    static final RabbitMessagingProperties PROPERTIES = new RabbitMessagingProperties(
            new Exchanges("cmd.direct", "cmd.dead.dlx"),
            new Route("q.email", "q.email.dlq", "email.send"),
            new Route("q.kitchen", "q.kitchen.dlq", "kitchen.ticket"),
            new Route("q.invoice", "q.invoice.dlq", "invoice.gen"),
            new Events("orders.events", "order.created", "order.status-changed"));

    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final OrderCommandPublisher publisher = new OrderCommandPublisher(rabbit, mapper, PROPERTIES);

    static OrderResponse order(OrderStatus status) {
        return new OrderResponse(7L, "cliente-1", "Cliente Uno", status, Set.of(), new BigDecimal("17980"),
                Instant.now(), Instant.now(), null, null,
                List.of(new OrderResponse.Item(1L, "Pizza", 2, new BigDecimal("8990"), new BigDecimal("17980"))));
    }

    @Test
    void pedidoCreadoSoloNotificaAlCliente() {
        publisher.onOrderChanged(new OrderChangedEvent(order(OrderStatus.CREADO), null, null));

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(rabbit).send(eq("cmd.direct"), eq("email.send"), message.capture(), any(CorrelationData.class));
        verify(rabbit, times(1)).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        JsonNode body = mapper.readTree(message.getValue().getBody());
        assertThat(body.get("type").asString()).isEqualTo("EmailNotification");
        assertThat(body.get("eventId").asString()).isNotBlank();
        assertThat(body.get("correlationId").asString()).isEqualTo("order-7");
        assertThat(body.get("payload").get("status").asString()).isEqualTo("CREADO");
        assertThat(message.getValue().getMessageProperties().getMessageId()).isEqualTo(body.get("eventId").asString());
    }

    @Test
    void pedidoAceptadoGeneraTicketDeCocina() {
        publisher.onOrderChanged(new OrderChangedEvent(order(OrderStatus.ACEPTADO), OrderStatus.CREADO, null));

        verify(rabbit).send(eq("cmd.direct"), eq("email.send"), any(Message.class), any(CorrelationData.class));
        verify(rabbit).send(eq("cmd.direct"), eq("kitchen.ticket"), any(Message.class), any(CorrelationData.class));
        verify(rabbit, never()).send(anyString(), eq("invoice.gen"), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void pedidoEntregadoGeneraBoleta() {
        publisher.onOrderChanged(new OrderChangedEvent(order(OrderStatus.ENTREGADO), OrderStatus.DESPACHADO, null));

        verify(rabbit).send(eq("cmd.direct"), eq("invoice.gen"), any(Message.class), any(CorrelationData.class));
        verify(rabbit, never()).send(anyString(), eq("kitchen.ticket"), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void brokerCaidoNoRompeElFlujoDelPedido() {
        doThrow(new AmqpConnectException(new RuntimeException("Connection refused")))
                .when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        assertThatCode(() -> publisher.onOrderChanged(new OrderChangedEvent(order(OrderStatus.CREADO), null, null)))
                .doesNotThrowAnyException();
    }
}
