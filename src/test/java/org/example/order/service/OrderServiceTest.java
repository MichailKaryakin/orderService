package org.example.order.service;

import org.example.order.cache.OrderCacheService;
import org.example.order.client.CatalogClient;
import org.example.order.client.dto.StockResponse;
import org.example.order.dto.*;
import org.example.order.entity.Address;
import org.example.order.entity.Order;
import org.example.order.entity.OrderItem;
import org.example.order.enums.OrderStatus;
import org.example.order.exception.IllegalOrderStatusException;
import org.example.order.exception.InsufficientStockException;
import org.example.order.exception.OrderNotFoundException;
import org.example.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService unit tests")
class OrderServiceTest {

    @Mock
    OrderRepository orderRepository;
    @Mock
    ApplicationEventPublisher eventPublisher;
    @Mock
    OrderCacheService cacheService;
    @Mock
    CatalogClient catalogClient;

    @InjectMocks
    OrderService orderService;

    private UUID orderId;
    private UUID userId;
    private UUID productId;
    private Order order;
    private OrderItem orderItem;
    private AddressRequest address;
    private StockResponse stockResponse;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
        productId = UUID.randomUUID();

        address = new AddressRequest("Main St 1", "Berlin", "Berlin", "10115", "DE");

        orderItem = OrderItem.builder()
                .id(UUID.randomUUID())
                .productId(productId)
                .sku("ABC-123")
                .productName("Test Product")
                .quantity(2)
                .price(new BigDecimal("49.99"))
                .build();

        order = Order.builder()
                .id(orderId)
                .orderNumber("ORD-ABCD1234")
                .userId(userId)
                .status(OrderStatus.CREATED)
                .totalAmount(new BigDecimal("99.98"))
                .currency("EUR")
                .shippingAddress(Address.builder()
                        .street("Main St 1").city("Berlin")
                        .state("Berlin").zipCode("10115").country("DE")
                        .build())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        order.getItems().add(orderItem);
        orderItem.setOrder(order);

        stockResponse = new StockResponse(UUID.randomUUID(), productId, 100, 10, "A1");
    }

    @Test
    @DisplayName("createOrder: enough stock — saves order, publishes event, caches")
    void createOrder_enoughStock_savesAndPublishes() {
        OrderItemRequest itemReq = new OrderItemRequest(productId, "ABC-123", "Test Product", 2, new BigDecimal("49.99"));
        CreateOrderRequest request = new CreateOrderRequest(userId, List.of(itemReq), address, "EUR");

        when(catalogClient.getStock(productId)).thenReturn(stockResponse);
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        OrderResponse result = orderService.createOrder(request);

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.status()).isEqualTo(OrderStatus.CREATED);
        verify(orderRepository).save(any(Order.class));
        verify(eventPublisher).publishEvent(any(Object.class));
        verify(cacheService).putOrder(any(OrderResponse.class));
    }

    @Test
    @DisplayName("createOrder: not enough stock — throws InsufficientStockException, no save")
    void createOrder_notEnoughStock_throws() {
        StockResponse lowStock = new StockResponse(UUID.randomUUID(), productId, 5, 4, "A1");
        OrderItemRequest itemReq = new OrderItemRequest(productId, "ABC-123", "Test Product", 2, new BigDecimal("49.99"));
        CreateOrderRequest request = new CreateOrderRequest(userId, List.of(itemReq), address, "EUR");

        when(catalogClient.getStock(productId)).thenReturn(lowStock);

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("ABC-123");

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("createOrder: no currency — defaults to EUR")
    void createOrder_noCurrency_defaultsToEur() {
        OrderItemRequest itemReq = new OrderItemRequest(productId, "ABC-123", "Test Product", 1, new BigDecimal("49.99"));
        CreateOrderRequest request = new CreateOrderRequest(userId, List.of(itemReq), address, null);

        Order savedOrder = Order.builder()
                .id(orderId).orderNumber("ORD-TEST").userId(userId)
                .status(OrderStatus.CREATED).totalAmount(new BigDecimal("49.99"))
                .currency("EUR")
                .shippingAddress(Address.builder()
                        .street("Main St 1").city("Berlin")
                        .state("Berlin").zipCode("10115").country("DE").build())
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();

        when(catalogClient.getStock(productId)).thenReturn(stockResponse);
        when(orderRepository.save(any())).thenReturn(savedOrder);

        OrderResponse result = orderService.createOrder(request);

        assertThat(result.currency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("createOrder: total calculated correctly from items")
    void createOrder_totalCalculatedCorrectly() {
        UUID p2 = UUID.randomUUID();
        StockResponse stock2 = new StockResponse(UUID.randomUUID(), p2, 50, 0, "A2");

        OrderItemRequest item1 = new OrderItemRequest(productId, "ABC-123", "Product A", 2, new BigDecimal("10.00"));
        OrderItemRequest item2 = new OrderItemRequest(p2, "XYZ-999", "Product B", 3, new BigDecimal("5.00"));
        CreateOrderRequest request = new CreateOrderRequest(userId, List.of(item1, item2), address, "EUR");

        Order savedOrder = Order.builder()
                .id(orderId).orderNumber("ORD-CALC").userId(userId)
                .status(OrderStatus.CREATED).totalAmount(new BigDecimal("35.00"))
                .currency("EUR")
                .shippingAddress(Address.builder()
                        .street("Main St 1").city("Berlin")
                        .state("Berlin").zipCode("10115").country("DE").build())
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();

        when(catalogClient.getStock(productId)).thenReturn(stockResponse);
        when(catalogClient.getStock(p2)).thenReturn(stock2);
        when(orderRepository.save(any())).thenReturn(savedOrder);

        OrderResponse result = orderService.createOrder(request);

        assertThat(result.totalAmount()).isEqualByComparingTo("35.00");
    }

    @Test
    @DisplayName("getById: cache hit — returns cached, no DB call")
    void getById_cacheHit_returnsCached() {
        OrderResponse cached = buildOrderResponse(OrderStatus.CREATED);
        when(cacheService.getOrder(orderId)).thenReturn(Optional.of(cached));

        OrderResponse result = orderService.getById(orderId);

        assertThat(result.id()).isEqualTo(orderId);
        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("getById: cache miss — loads from DB, caches result")
    void getById_cacheMiss_loadsFromDb() {
        when(cacheService.getOrder(orderId)).thenReturn(Optional.empty());
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        OrderResponse result = orderService.getById(orderId);

        assertThat(result.id()).isEqualTo(orderId);
        verify(cacheService).putOrder(any());
    }

    @Test
    @DisplayName("getById: not found — throws OrderNotFoundException")
    void getById_notFound_throws() {
        when(cacheService.getOrder(orderId)).thenReturn(Optional.empty());
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getById(orderId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(orderId.toString());
    }

    @Test
    @DisplayName("getByUser: no status filter — returns all user orders")
    void getByUser_noStatus_returnsAll() {
        Page<Order> page = new PageImpl<>(List.of(order));
        when(orderRepository.findAllByUserId(eq(userId), any())).thenReturn(page);

        Page<OrderResponse> result = orderService.getByUser(userId, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("getByUser: with status filter — queries with status")
    void getByUser_withStatus_queriesWithStatus() {
        Page<Order> page = new PageImpl<>(List.of(order));
        when(orderRepository.findAllByUserIdAndStatus(eq(userId), eq(OrderStatus.CREATED), any())).thenReturn(page);

        Page<OrderResponse> result = orderService.getByUser(userId, OrderStatus.CREATED, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        verify(orderRepository).findAllByUserIdAndStatus(eq(userId), eq(OrderStatus.CREATED), any());
    }

    @Test
    @DisplayName("cancelOrder: CREATED — cancels successfully")
    void cancelOrder_created_cancels() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        OrderResponse result = orderService.cancelOrder(orderId);

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(cacheService).evictOrder(orderId);
    }

    @Test
    @DisplayName("cancelOrder: RESERVED — cancels successfully")
    void cancelOrder_reserved_cancels() {
        order.setStatus(OrderStatus.RESERVED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        OrderResponse result = orderService.cancelOrder(orderId);

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancelOrder: PAID — throws IllegalOrderStatusException")
    void cancelOrder_paid_throws() {
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(orderId))
                .isInstanceOf(IllegalOrderStatusException.class)
                .hasMessageContaining("PAID");
    }

    @Test
    @DisplayName("cancelOrder: SHIPPED — throws IllegalOrderStatusException")
    void cancelOrder_shipped_throws() {
        order.setStatus(OrderStatus.SHIPPED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(orderId))
                .isInstanceOf(IllegalOrderStatusException.class)
                .hasMessageContaining("SHIPPED");
    }

    @Test
    @DisplayName("cancelOrder: not found — throws OrderNotFoundException")
    void cancelOrder_notFound_throws() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(orderId))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("confirmPayment: RESERVED — moves to PAID")
    void confirmPayment_reserved_movesToPaid() {
        order.setStatus(OrderStatus.RESERVED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        OrderResponse result = orderService.confirmPayment(orderId);

        assertThat(result.status()).isEqualTo(OrderStatus.PAID);
        verify(cacheService).evictOrder(orderId);
    }

    @Test
    @DisplayName("confirmPayment: CREATED (not RESERVED) — throws IllegalOrderStatusException")
    void confirmPayment_notReserved_throws() {
        order.setStatus(OrderStatus.CREATED);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirmPayment(orderId))
                .isInstanceOf(IllegalOrderStatusException.class)
                .hasMessageContaining("CREATED");
    }

    @Test
    @DisplayName("confirmPayment: not found — throws OrderNotFoundException")
    void confirmPayment_notFound_throws() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.confirmPayment(orderId))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("markAsReserved: updates status to RESERVED, updates cache")
    void markAsReserved_updatesStatus() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        orderService.markAsReserved(orderId);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.RESERVED);
        verify(cacheService).evictOrder(orderId);
        verify(cacheService).putOrder(any());
    }

    @Test
    @DisplayName("markAsFailed: updates status to FAILED, updates cache")
    void markAsFailed_updatesStatus() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenReturn(order);

        orderService.markAsFailed(orderId);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        verify(cacheService).evictOrder(orderId);
        verify(cacheService).putOrder(any());
    }

    @Test
    @DisplayName("markAsReserved: not found — throws OrderNotFoundException")
    void markAsReserved_notFound_throws() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.markAsReserved(orderId))
                .isInstanceOf(OrderNotFoundException.class);
    }

    private OrderResponse buildOrderResponse(OrderStatus status) {
        return new OrderResponse(
                orderId, "ORD-ABCD1234", userId,
                List.of(new OrderItemResponse(UUID.randomUUID(), productId, "ABC-123",
                        "Test Product", 2, new BigDecimal("49.99"))),
                new BigDecimal("99.98"), "EUR", status, address,
                Instant.now(), Instant.now()
        );
    }
}
