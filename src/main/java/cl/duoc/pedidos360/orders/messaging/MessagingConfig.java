package cl.duoc.pedidos360.orders.messaging;

import java.time.Duration;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.amqp.core.Declarables;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Solo se activa con pedidos360.messaging.enabled=true (variable MESSAGING_ENABLED). */
@Configuration
@ConditionalOnProperty(name = "pedidos360.messaging.enabled", havingValue = "true")
public class MessagingConfig {

    @Bean
    NewTopic ordersEventsTopic() {
        return TopicBuilder.name(MessagingTopics.ORDERS_EVENTS)
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(Duration.ofDays(7).toMillis()))
                .build();
    }

    @Bean
    NewTopic auditTimelineTopic() {
        return TopicBuilder.name(MessagingTopics.AUDIT_TIMELINE)
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, "compact,delete")
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(Duration.ofDays(30).toMillis()))
                .build();
    }

    @Bean
    Declarables commandTopology() {
        return RabbitTopology.declarables();
    }
}
