package cl.duoc.pedidos360.orders.config;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Entrega confiable del lado del productor (publisher-confirm-type: correlated, publisher-returns + mandatory):
 * <ul>
 *   <li><b>Confirm:</b> si el broker no confirma (NACK) la publicación, se registra con nivel ERROR.</li>
 *   <li><b>Return:</b> si un comando no tenía cola de destino (por ejemplo, la cola fue eliminada desde el
 *       administrador), se registra y se reenvía al dead-letter exchange para que quede en la DLQ del flujo.
 *       Un evento de dominio sin suscriptores solo se registra.</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(name = "pedidos360.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitTemplateConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitTemplateConfig.class);

    // Los callbacks corren en el hilo de la conexión: el reenvío a la DLQ se hace en otro hilo
    private final Executor deadLetterForwarder = Executors.newSingleThreadExecutor();

    @Bean
    RabbitTemplateCustomizer reliablePublishing(RabbitMessagingProperties properties) {
        String commandsExchange = properties.exchanges().commands();
        String deadLetterExchange = properties.exchanges().deadLetter();
        return template -> {
            template.setConfirmCallback((correlation, ack, cause) -> {
                if (!ack) {
                    log.error("RabbitMQ rechazó la publicación {}: {}",
                            correlation == null ? "-" : correlation.getId(), cause);
                }
            });
            template.setReturnsCallback(returned -> onReturned(template, returned, commandsExchange, deadLetterExchange));
        };
    }

    private void onReturned(RabbitTemplate template, ReturnedMessage returned, String commandsExchange,
                            String deadLetterExchange) {
        if (!commandsExchange.equals(returned.getExchange())) {
            // Eventos de dominio sin suscriptores (o la DLQ tampoco existe): solo se registra
            log.error("Mensaje no entregado ({} {}): exchange={} routingKey={} no tiene colas enlazadas",
                    returned.getReplyCode(), returned.getReplyText(), returned.getExchange(),
                    returned.getRoutingKey());
            return;
        }
        log.error("Mensaje no entregado ({} {}): exchange={} routingKey={}. Se envía a la DLQ",
                returned.getReplyCode(), returned.getReplyText(), returned.getExchange(), returned.getRoutingKey());
        deadLetterForwarder.execute(() -> {
            try {
                template.send(deadLetterExchange, returned.getRoutingKey(), returned.getMessage());
            } catch (AmqpException ex) {
                log.error("No se pudo mover a la DLQ el mensaje devuelto: {}", ex.getMessage());
            }
        });
    }
}
