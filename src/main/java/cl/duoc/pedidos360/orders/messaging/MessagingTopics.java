package cl.duoc.pedidos360.orders.messaging;

/** Tópicos de Kafka que publica ms-pedidos360-orders. */
public final class MessagingTopics {

    /** Flujo canónico de eventos de negocio del pedido (fuente de verdad para reportería). */
    public static final String ORDERS_EVENTS = "orders.events";

    /** Línea de tiempo legible por entidad/actor para auditoría. */
    public static final String AUDIT_TIMELINE = "audit.timeline";

    private MessagingTopics() {
    }
}
