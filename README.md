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

## Mensajería (RabbitMQ) — productor

Cuando se crea un pedido o cambia de estado, `OrderService` publica un evento de dominio (`OrderChangedEvent`) y no sabe nada de RabbitMQ. `messaging/OrderCommandPublisher` lo recibe **después del commit** (`@TransactionalEventListener(AFTER_COMMIT)`) y lo traduce a comandos:

| Cuándo | Routing key | Cola | Consumidor |
|---|---|---|---|
| Creación y cada cambio de estado | `email.send` | `q.cmd.email` | aviso al cliente |
| Pasa a `ACEPTADO` | `kitchen.ticket` | `q.cmd.kitchen` | ticket de cocina |
| Pasa a `ENTREGADO` | `invoice.gen` | `q.cmd.invoice` | boleta |

- **Nombres centralizados** en `application.yml` (`pedidos360.rabbitmq.*`) mediante `config/RabbitMessagingProperties`.
- **Beans por caso de uso** en `config/RabbitTopologyConfig`: exchanges `cmd.direct` y `cmd.dead.dlx`, y para cada caso su cola, su DLQ y sus bindings. Se declaran también en el productor para que los comandos no se pierdan si `notify` todavía no levanta.
- **Mensajes no entregados** (`config/RabbitTemplateConfig`): publisher confirms (`correlated`) y returns con `mandatory`. Si un comando no tiene cola de destino (`312 NO_ROUTE`), se registra con nivel `ERROR` y se reenvía al `cmd.dead.dlx`, con lo que queda en la DLQ de su flujo. Los NACK del broker también se registran.
- **Sin romper el flujo existente**: si RabbitMQ está caído, el pedido igual se guarda y la API responde normalmente; el error queda en el log. Con `MESSAGING_ENABLED=false` no se publica nada.
- Envelope común: `type`, `eventId` (también va como `messageId`), `timestamp`, `traceId`, `correlationId` (`order-{id}`) y `payload`.

### Eventos de dominio (publish/subscribe)

Además de los comandos, `messaging/OrderEventPublisher` publica cada cambio **una sola vez** en el exchange topic `orders.events`. El productor no conoce a los suscriptores: cada uno declara y enlaza su propia cola (con su retry y su DLQ).

| Cuándo | Routing key | Tipo | Suscriptores |
|---|---|---|---|
| Creación | `order.created` | `OrderCreated` | `q.report.orders` (KPIs), `q.audit.orders` (timeline) |
| Cambio de estado | `order.status-changed` | `OrderStatusChanged` | `q.report.orders`, `q.audit.orders` |

El evento lleva `eventId`, `occurredAt`, `traceId`, `correlationId`, `source`, el `actor` que hizo el cambio (id, nombre y roles, desde los headers `X-User-*` del BFF), `previousStatus` y la foto completa del pedido. Un evento sin colas enlazadas solo se registra en el log (no se reenvía a la DLQ de comandos).

## Base de datos

MySQL (Amazon RDS en AWS, Docker en local). Schema `pedidos360_orders`, tablas `orders` y `order_items` creadas por JPA.

| Variable | Por defecto |
|---|---|
| `DB_HOST` | `localhost` |
| `DB_USER` | `root` |
| `DB_PASSWORD` | `root` |
| `CATALOG_URL` | `http://localhost:8082` |

## Variables de entorno de mensajería

| Variable | Por defecto |
|---|---|
| `MESSAGING_ENABLED` | `true` |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | `pedidos360` / `pedidos360` |

## Ejecutar

```bash
./mvnw test
./mvnw spring-boot:run
```

## CI/CD

`.github/workflows/ci-cd.yml` corre las pruebas en cada push. En `main`, además, despliega en la EC2 con `docker compose up -d --build orders`.

## Autores

Germán Marabolí, Sebastián Velez & Camila Vera

Proyecto Pedidos360 · DSY1107 Desarrollo Cloud Native I · Duoc UC
