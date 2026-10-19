package cl.duoc.pedidos360.orders.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import cl.duoc.pedidos360.orders.dto.RequestUser;
import cl.duoc.pedidos360.orders.event.OrderChangedEvent;
import cl.duoc.pedidos360.orders.model.OrderStatus;

class OrderEventPublisherTest {

    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final OrderEventPublisher publisher = new OrderEventPublisher(rabbit, mapper,
            OrderCommandPublisherTest.PROPERTIES, "ms-pedidos360-orders");

    private final RequestUser operador = new RequestUser("op-1", "Operador Uno", List.of("Operador"));

    @Test
    void pedidoCreadoSePublicaConRoutingKeyCreated() {
        publisher.onOrderChanged(new OrderChangedEvent(OrderCommandPublisherTest.order(OrderStatus.CREADO), null,
                operador));

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(rabbit).send(eq("orders.events"), eq("order.created"), message.capture(), any(CorrelationData.class));

        JsonNode body = mapper.readTree(message.getValue().getBody());
        assertThat(body.get("type").asString()).isEqualTo("OrderCreated");
        assertThat(body.get("source").asString()).isEqualTo("ms-pedidos360-orders");
        assertThat(body.get("actor").get("name").asString()).isEqualTo("Operador Uno");
        assertThat(body.get("order").get("id").asLong()).isEqualTo(7L);
        assertThat(body.get("order").get("items")).hasSize(1);
        assertThat(message.getValue().getMessageProperties().getMessageId()).isEqualTo(body.get("eventId").asString());
    }

    @Test
    void cambioDeEstadoIncluyeEstadoAnterior() {
        publisher.onOrderChanged(new OrderChangedEvent(OrderCommandPublisherTest.order(OrderStatus.ACEPTADO),
                OrderStatus.CREADO, operador));

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(rabbit).send(eq("orders.events"), eq("order.status-changed"), message.capture(),
                any(CorrelationData.class));
        JsonNode body = mapper.readTree(message.getValue().getBody());
        assertThat(body.get("type").asString()).isEqualTo("OrderStatusChanged");
        assertThat(body.get("previousStatus").asString()).isEqualTo("CREADO");
        assertThat(body.get("order").get("status").asString()).isEqualTo("ACEPTADO");
    }

    @Test
    void brokerCaidoNoRompeElFlujoDelPedido() {
        doThrow(new AmqpConnectException(new RuntimeException("Connection refused")))
                .when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        assertThatCode(() -> publisher.onOrderChanged(
                new OrderChangedEvent(OrderCommandPublisherTest.order(OrderStatus.CREADO), null, operador)))
                .doesNotThrowAnyException();
    }
}
