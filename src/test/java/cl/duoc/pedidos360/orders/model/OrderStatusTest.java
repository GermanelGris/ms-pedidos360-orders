package cl.duoc.pedidos360.orders.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderStatusTest {

    @Test
    void noSePuedeDespacharSinAceptar() {
        assertThat(OrderStatus.CREADO.canTransitionTo(OrderStatus.DESPACHADO)).isFalse();
    }

    @Test
    void flujoCompletoEsValido() {
        assertThat(OrderStatus.CREADO.canTransitionTo(OrderStatus.ACEPTADO)).isTrue();
        assertThat(OrderStatus.ACEPTADO.canTransitionTo(OrderStatus.EN_PREPARACION)).isTrue();
        assertThat(OrderStatus.EN_PREPARACION.canTransitionTo(OrderStatus.DESPACHADO)).isTrue();
        assertThat(OrderStatus.DESPACHADO.canTransitionTo(OrderStatus.ENTREGADO)).isTrue();
    }

    @Test
    void pedidoDespachadoNoSePuedeCancelar() {
        assertThat(OrderStatus.DESPACHADO.canTransitionTo(OrderStatus.CANCELADO)).isFalse();
    }

    @Test
    void estadosFinalesNoTienenSiguiente() {
        assertThat(OrderStatus.ENTREGADO.nextStatuses()).isEmpty();
        assertThat(OrderStatus.CANCELADO.nextStatuses()).isEmpty();
    }
}
