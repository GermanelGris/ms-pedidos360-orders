package cl.duoc.pedidos360.orders.client;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import cl.duoc.pedidos360.orders.exception.ApiException;

/** Coordinación con ms-pedidos360-catalog (red interna). */
@Component
public class CatalogClient {

    public record ProductInfo(Long id, String name, BigDecimal price, int stock, boolean active) {
    }

    public record StockItem(Long productId, int quantity) {
    }

    private final RestClient rest;

    public CatalogClient(@Value("${services.catalog-url}") String catalogUrl) {
        this.rest = RestClient.create(catalogUrl);
    }

    public ProductInfo getProduct(Long productId) {
        return rest.get()
                .uri("/api/catalog/products/{id}", productId)
                .retrieve()
                .onStatus(status -> status.value() == 404, (request, response) -> {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "El producto " + productId + " no existe");
                })
                .body(ProductInfo.class);
    }

    /** Descuenta stock de todos los productos del pedido en una sola operación (todo o nada). */
    public void decreaseStock(List<StockItem> items) {
        rest.post()
                .uri("/api/catalog/products/decrease-stock")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("items", items))
                .retrieve()
                .onStatus(status -> status.value() == 409, (request, response) -> {
                    throw new ApiException(HttpStatus.CONFLICT, "Stock insuficiente para aceptar el pedido");
                })
                .toBodilessEntity();
    }
}
