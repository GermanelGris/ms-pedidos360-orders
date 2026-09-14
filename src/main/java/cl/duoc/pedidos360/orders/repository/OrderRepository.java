package cl.duoc.pedidos360.orders.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.duoc.pedidos360.orders.model.Order;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findAllByOrderByCreatedAtDesc();

    List<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId);
}
