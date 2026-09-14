package cl.duoc.pedidos360.orders.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Máquina de estados del pedido.
 * CREADO -> ACEPTADO -> EN_PREPARACION -> DESPACHADO -> ENTREGADO, con CANCELADO antes del despacho.
 * Regla clave: no se puede despachar un pedido que no fue aceptado.
 */
public enum OrderStatus {
    CREADO,
    ACEPTADO,
    EN_PREPARACION,
    DESPACHADO,
    ENTREGADO,
    CANCELADO;

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            CREADO, EnumSet.of(ACEPTADO, CANCELADO),
            ACEPTADO, EnumSet.of(EN_PREPARACION, CANCELADO),
            EN_PREPARACION, EnumSet.of(DESPACHADO, CANCELADO),
            DESPACHADO, EnumSet.of(ENTREGADO),
            ENTREGADO, EnumSet.noneOf(OrderStatus.class),
            CANCELADO, EnumSet.noneOf(OrderStatus.class));

    public boolean canTransitionTo(OrderStatus next) {
        return TRANSITIONS.get(this).contains(next);
    }

    public Set<OrderStatus> nextStatuses() {
        return TRANSITIONS.get(this);
    }
}
