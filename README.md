# ms-pedidos360-orders

Microservicio de **Pedidos** de Pedidos360: CRUD de pedidos, máquina de estados y coordinación de stock con catalog. No es público: lo invoca el BFF, que propaga la identidad del usuario en los headers `X-User-Id`, `X-User-Name` y `X-User-Roles`.

## Reglas de negocio

```
CREADO -> ACEPTADO -> EN_PREPARACION -> DESPACHADO -> ENTREGADO
   \          \              \
    +----------+--------------+--> CANCELADO
```

- No se puede **despachar** sin haber **aceptado** → `409 Conflict`.
- Al pasar a **ACEPTADO** se descuenta el stock en `ms-pedidos360-catalog` (todo o nada).
- El **Cliente** solo ve sus propios pedidos; Admin y Operador ven todos.
- Al pasar a **ENTREGADO** se calcula el *lead time* (creación → entrega).

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/orders` | Listar pedidos |
| GET | `/api/orders/{id}` | Detalle |
| POST | `/api/orders` | Crear `{ "customerName": "...", "items": [{ "productId": 1, "quantity": 2 }] }` |
| PATCH | `/api/orders/{id}/status` | Cambiar estado `{ "status": "ACEPTADO" }` |

Swagger: `http://localhost:8081/swagger-ui.html`

## Base de datos

MySQL (Amazon RDS en AWS, Docker en local). Schema `pedidos360_orders`, tablas `orders` y `order_items` creadas por JPA.

| Variable | Por defecto |
|---|---|
| `DB_HOST` | `localhost` |
| `DB_USER` | `root` |
| `DB_PASSWORD` | `root` |
| `CATALOG_URL` | `http://localhost:8082` |

## Ejecutar

```bash
./mvnw test
./mvnw spring-boot:run
```
