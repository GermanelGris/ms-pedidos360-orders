package cl.duoc.pedidos360.orders.messaging;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

/**
 * Topología de RabbitMQ del caso: 3 flujos de trabajo (email, cocina, boleta),
 * cada uno con su cola principal y su DLQ, sobre los exchanges cmd.direct, cmd.topic y cmd.dead.dlx.
 * La misma definición existe en ms-pedidos360-notify; declararla dos veces es idempotente.
 */
public final class RabbitTopology {

    public static final String DIRECT_EXCHANGE = "cmd.direct";
    public static final String TOPIC_EXCHANGE = "cmd.topic";
    public static final String DEAD_LETTER_EXCHANGE = "cmd.dead.dlx";

    public static final String EMAIL_QUEUE = "q.cmd.email";
    public static final String KITCHEN_QUEUE = "q.cmd.kitchen";
    public static final String INVOICE_QUEUE = "q.cmd.invoice";

    public static final String EMAIL_SEND = "email.send";
    public static final String KITCHEN_TICKET = "kitchen.ticket";
    public static final String INVOICE_GEN = "invoice.gen";

    private RabbitTopology() {
    }

    public static Declarables declarables() {
        DirectExchange direct = ExchangeBuilder.directExchange(DIRECT_EXCHANGE).durable(true).build();
        TopicExchange topic = ExchangeBuilder.topicExchange(TOPIC_EXCHANGE).durable(true).build();
        DirectExchange deadLetter = ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();

        List<Declarable> declarables = new ArrayList<>(List.of(direct, topic, deadLetter));
        addWorkflow(declarables, direct, topic, deadLetter, EMAIL_QUEUE, EMAIL_SEND, "email.*");
        addWorkflow(declarables, direct, topic, deadLetter, KITCHEN_QUEUE, KITCHEN_TICKET, "kitchen.#");
        addWorkflow(declarables, direct, topic, deadLetter, INVOICE_QUEUE, INVOICE_GEN, "invoice.*");
        return new Declarables(declarables);
    }

    private static void addWorkflow(List<Declarable> declarables, DirectExchange direct, TopicExchange topic,
                                    DirectExchange deadLetter, String queueName, String routingKey, String pattern) {
        Queue queue = QueueBuilder.durable(queueName)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(routingKey)
                .build();
        Queue dlq = QueueBuilder.durable(queueName + ".dlq").build();

        declarables.add(queue);
        declarables.add(dlq);
        declarables.add(BindingBuilder.bind(queue).to(direct).with(routingKey));
        declarables.add(BindingBuilder.bind(queue).to(topic).with(pattern));
        declarables.add(BindingBuilder.bind(dlq).to(deadLetter).with(routingKey));
    }
}
