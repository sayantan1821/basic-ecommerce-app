package com.basic_ecommerce.app.service;

import com.basic_ecommerce.app.dto.CheckoutItemRequest;
import com.basic_ecommerce.app.dto.CheckoutRequest;
import com.basic_ecommerce.app.dto.CheckoutResponse;
import com.basic_ecommerce.app.model.CheckoutIdempotency;
import com.basic_ecommerce.app.model.OrderItem;
import com.basic_ecommerce.app.model.Payment;
import com.basic_ecommerce.app.model.Product;
import com.basic_ecommerce.app.model.PurchaseOrder;
import com.basic_ecommerce.app.repository.CheckoutIdempotencyRepository;
import com.basic_ecommerce.app.repository.OrderItemRepository;
import com.basic_ecommerce.app.repository.OrderProductStockRepository;
import com.basic_ecommerce.app.repository.PaymentRepository;
import com.basic_ecommerce.app.repository.ProductRepository;
import com.basic_ecommerce.app.repository.PurchaseOrderRepository;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutService {
    private final JdbcTemplate jdbcTemplate;
    private final ProductRepository products;
    private final OrderProductStockRepository stock;
    private final PurchaseOrderRepository orders;
    private final OrderItemRepository orderItems;
    private final PaymentRepository payments;
    private final CheckoutIdempotencyRepository idempotencies;

    public CheckoutService(JdbcTemplate jdbcTemplate, ProductRepository products,
            OrderProductStockRepository stock, PurchaseOrderRepository orders,
            OrderItemRepository orderItems, PaymentRepository payments,
            CheckoutIdempotencyRepository idempotencies) {
        this.jdbcTemplate = jdbcTemplate;
        this.products = products;
        this.stock = stock;
        this.orders = orders;
        this.orderItems = orderItems;
        this.payments = payments;
        this.idempotencies = idempotencies;
    }

    @Transactional
    public CheckoutResponse checkout(String idempotencyKey, CheckoutRequest request) {
        validateKey(idempotencyKey);
        TreeMap<UUID, Integer> quantities = normalize(request);
        String requestHash = hashRequest(quantities);

        // Serialize the same key across requests, including concurrent requests on multiple instances.
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, idempotencyKey);
                statement.execute();
            }
            return null;
        });

        var previous = idempotencies.findById(idempotencyKey);
        if (previous.isPresent()) {
            if (!previous.get().getRequestHash().equals(requestHash)) {
                throw new CheckoutException("IDEMPOTENCY_KEY_REUSED",
                        "This idempotency key was already used for a different cart.", HttpStatus.CONFLICT);
            }
            return responseFor(previous.get().getOrderId(), true);
        }

        List<Product> found = products.findAllById(quantities.keySet());
        Map<UUID, Product> byId = found.stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        for (UUID productId : quantities.keySet()) {
            Product product = byId.get(productId);
            if (product == null || !product.isActive()) {
                throw new CheckoutException("PRODUCT_UNAVAILABLE", "One or more products are unavailable.", HttpStatus.CONFLICT);
            }
        }

        long totalPaise = calculateTotal(quantities, byId);
        // Stable lock/update order reduces deadlock risk when carts contain overlapping products.
        for (UUID productId : quantities.keySet().stream().sorted(Comparator.comparing(UUID::toString)).toList()) {
            if (stock.reserveStock(productId, quantities.get(productId)) != 1) {
                throw new CheckoutException("OUT_OF_STOCK", "One or more items no longer have enough stock.", HttpStatus.CONFLICT);
            }
        }

        Instant now = Instant.now();
        UUID orderId = UUID.randomUUID();
        String referenceId = UUID.randomUUID().toString();
        PurchaseOrder order = orders.saveAndFlush(new PurchaseOrder(orderId, referenceId, totalPaise, now));

        List<OrderItem> snapshots = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : quantities.entrySet()) {
            Product product = byId.get(entry.getKey());
            long lineTotal = Math.multiplyExact(product.getPricePaise(), entry.getValue());
            snapshots.add(new OrderItem(UUID.randomUUID(), orderId, product, entry.getValue(), lineTotal));
        }
        orderItems.saveAll(snapshots);

        String providerReference = "mock_" + UUID.randomUUID();
        String mockClientSecret = "mock_secret_" + UUID.randomUUID();
        Payment payment = payments.save(new Payment(UUID.randomUUID(), orderId, providerReference, totalPaise, mockClientSecret, now));
        idempotencies.save(new CheckoutIdempotency(idempotencyKey, requestHash, orderId, now));
        return toResponse(order, payment, false);
    }

    private static TreeMap<UUID, Integer> normalize(CheckoutRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new CheckoutException("INVALID_CART", "Add at least one product before checkout.", HttpStatus.BAD_REQUEST);
        }
        TreeMap<UUID, Integer> normalized = new TreeMap<>(Comparator.comparing(UUID::toString));
        HashSet<UUID> seen = new HashSet<>();
        for (CheckoutItemRequest item : request.items()) {
            if (item == null || item.productId() == null || item.quantity() < 1 || item.quantity() > 10) {
                throw new CheckoutException("INVALID_CART_ITEM", "Each item needs a product ID and quantity from 1 to 10.", HttpStatus.BAD_REQUEST);
            }
            if (!seen.add(item.productId())) {
                throw new CheckoutException("DUPLICATE_PRODUCT", "Send each product only once with its combined quantity.", HttpStatus.BAD_REQUEST);
            }
            normalized.put(item.productId(), item.quantity());
        }
        return normalized;
    }

    private static long calculateTotal(Map<UUID, Integer> quantities, Map<UUID, Product> byId) {
        try {
            long total = 0;
            for (Map.Entry<UUID, Integer> item : quantities.entrySet()) {
                total = Math.addExact(total, Math.multiplyExact(byId.get(item.getKey()).getPricePaise(), item.getValue()));
            }
            return total;
        } catch (ArithmeticException overflow) {
            throw new CheckoutException("TOTAL_TOO_LARGE", "The checkout total is too large.", HttpStatus.BAD_REQUEST);
        }
    }

    private CheckoutResponse responseFor(UUID orderId, boolean replayed) {
        PurchaseOrder order = orders.findById(orderId).orElseThrow(() -> new IllegalStateException("Idempotency mapping has no order"));
        Payment payment = payments.findByOrderId(orderId).orElseThrow(() -> new IllegalStateException("Order has no payment"));
        return toResponse(order, payment, replayed);
    }

    private static CheckoutResponse toResponse(PurchaseOrder order, Payment payment, boolean replayed) {
        return new CheckoutResponse(
                new CheckoutResponse.OrderSummary(order.getReferenceId(), order.getStatus(), "INR", order.getTotalPaise()),
                new CheckoutResponse.PaymentSummary(payment.getId(), payment.getStatus(), payment.getMockClientSecret()),
                replayed);
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new CheckoutException("INVALID_IDEMPOTENCY_KEY", "Provide an Idempotency-Key between 1 and 128 characters.", HttpStatus.BAD_REQUEST);
        }
    }

    private static String hashRequest(Map<UUID, Integer> quantities) {
        String canonical = quantities.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining("\n"));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) hex.append(String.format("%02x", value));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
