package org.example.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.order.client.CatalogClient;
import org.example.order.client.dto.StockResponse;
import org.example.order.dto.*;
import org.example.order.enums.OrderStatus;
import org.example.order.kafka.StockReserveResultConsumer;
import org.example.order.kafka.dto.StockReserveItemResult;
import org.example.order.kafka.dto.StockReserveResultEvent;
import org.example.order.repository.OrderRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.concurrent.TimeUnit;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Order integration tests")
class OrderIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("order_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0")
    );

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    StockReserveResultConsumer stockReserveResultConsumer;
    @MockitoBean
    CatalogClient catalogClient;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private UUID productId;
    private StockResponse stockResponse;
    private AddressRequest address;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        productId = UUID.randomUUID();
        stockResponse = new StockResponse(UUID.randomUUID(), productId, 100, 10, "A1");
        address = new AddressRequest("Main St 1", "Berlin", "Berlin", "10115", "DE");
    }

    @Test
    @WithMockUser
    @DisplayName("POST /orders — creates order in DB with CREATED status")
    void createOrder_persistsToDb() throws Exception {
        when(catalogClient.getStock(productId)).thenReturn(stockResponse);

        CreateOrderRequest request = buildCreateRequest(productId, 2);

        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andReturn();

        OrderResponse response = mapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class);

        assertThat(orderRepository.findById(response.id())).isPresent();
        assertThat(response.totalAmount()).isEqualByComparingTo("99.98");
    }

    @Test
    @WithMockUser
    @DisplayName("POST /orders — not enough stock — returns 422")
    void createOrder_notEnoughStock_returns422() throws Exception {
        StockResponse lowStock = new StockResponse(UUID.randomUUID(), productId, 5, 4, "A1");
        when(catalogClient.getStock(productId)).thenReturn(lowStock);

        CreateOrderRequest request = buildCreateRequest(productId, 2);

        mockMvc.perform(post("/api/v1/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /orders/{id} — existing — returns order")
    void getById_existing_returnsOrder() throws Exception {
        when(catalogClient.getStock(productId)).thenReturn(stockResponse);
        UUID orderId = createOrderViaApi(productId, 1);

        mockMvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /orders/{id} — missing — returns 404")
    void getById_missing_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /orders?userId= — returns user's orders")
    void getByUser_returnsUserOrders() throws Exception {
        UUID userId = UUID.randomUUID();
        when(catalogClient.getStock(any())).thenReturn(stockResponse);

        createOrderViaApi(productId, 1, userId);
        createOrderViaApi(productId, 1, userId);

        mockMvc.perform(get("/api/v1/orders")
                        .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /orders?userId=&status= — filters by status")
    void getByUser_withStatusFilter_returnsFiltered() throws Exception {
        UUID userId = UUID.randomUUID();
        when(catalogClient.getStock(any())).thenReturn(stockResponse);
        createOrderViaApi(productId, 1, userId);

        mockMvc.perform(get("/api/v1/orders")
                        .param("userId", userId.toString())
                        .param("status", "CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("CREATED"));
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /orders/{id}/cancel — CREATED order — cancels")
    void cancelOrder_created_cancels() throws Exception {
        when(catalogClient.getStock(productId)).thenReturn(stockResponse);
        UUID orderId = createOrderViaApi(productId, 1);

        mockMvc.perform(put("/api/v1/orders/{id}/cancel", orderId).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(orderRepository.findById(orderId))
                .isPresent()
                .get()
                .extracting(o -> o.getStatus().name())
                .isEqualTo("CANCELLED");
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /orders/{id}/cancel — not found — returns 404")
    void cancelOrder_notFound_returns404() throws Exception {
        mockMvc.perform(put("/api/v1/orders/{id}/cancel", UUID.randomUUID()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /orders/{id}/confirm-payment — RESERVED — moves to PAID")
    void confirmPayment_reserved_movesPaid() throws Exception {
        when(catalogClient.getStock(productId)).thenReturn(stockResponse);
        UUID orderId = createOrderViaApi(productId, 1);

        // manually move to RESERVED
        orderRepository.findById(orderId).ifPresent(o -> {
            o.setStatus(OrderStatus.RESERVED);
            orderRepository.save(o);
        });

        mockMvc.perform(post("/api/v1/orders/{id}/confirm-payment", orderId).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /orders/{id}/confirm-payment — CREATED (not RESERVED) — returns 422")
    void confirmPayment_notReserved_returns422() throws Exception {
        when(catalogClient.getStock(productId)).thenReturn(stockResponse);
        UUID orderId = createOrderViaApi(productId, 1);

        mockMvc.perform(post("/api/v1/orders/{id}/confirm-payment", orderId).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser
    @DisplayName("Kafka: stock.reserve.result success — order moves to RESERVED")
    void kafkaConsumer_allSuccess_marksReserved() throws Exception {
        when(catalogClient.getStock(productId)).thenReturn(stockResponse);
        UUID orderId = createOrderViaApi(productId, 1);

        StockReserveResultEvent event = new StockReserveResultEvent(
                "STOCK_RESERVE_RESULT",
                orderId,
                List.of(new StockReserveItemResult(productId, "ABC-123", 1, 1, true, null)),
                Instant.now()
        );

        stockReserveResultConsumer.handle(event);

        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(orderRepository.findById(orderId))
                        .isPresent()
                        .get()
                        .extracting(o -> o.getStatus().name())
                        .isEqualTo("RESERVED")
        );
    }

    @Test
    @WithMockUser
    @DisplayName("Kafka: stock.reserve.result failure — order moves to FAILED")
    void kafkaConsumer_anyFailure_marksFailed() throws Exception {
        when(catalogClient.getStock(productId)).thenReturn(stockResponse);
        UUID orderId = createOrderViaApi(productId, 1);

        StockReserveResultEvent event = new StockReserveResultEvent(
                "STOCK_RESERVE_RESULT",
                orderId,
                List.of(new StockReserveItemResult(productId, "ABC-123", 2, 0, false, "Not enough stock")),
                Instant.now()
        );

        stockReserveResultConsumer.handle(event);

        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(orderRepository.findById(orderId))
                        .isPresent()
                        .get()
                        .extracting(o -> o.getStatus().name())
                        .isEqualTo("FAILED")
        );
    }

    private UUID createOrderViaApi(UUID productId, int quantity) throws Exception {
        return createOrderViaApi(productId, quantity, UUID.randomUUID());
    }

    private UUID createOrderViaApi(UUID productId, int quantity, UUID userId) throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                userId,
                List.of(new OrderItemRequest(productId, "ABC-123", "Test Product",
                        quantity, new BigDecimal("49.99"))),
                address,
                "EUR"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/orders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        OrderResponse response = mapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class);
        return response.id();
    }

    private CreateOrderRequest buildCreateRequest(UUID productId, int quantity) {
        return new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(new OrderItemRequest(productId, "ABC-123", "Test Product",
                        quantity, new BigDecimal("49.99"))),
                address,
                "EUR"
        );
    }
}
