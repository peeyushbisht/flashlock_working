package com.flashlock.strategy;

import com.flashlock.model.Order;
import com.flashlock.model.Product;
import com.flashlock.repository.OrderRepository;
import com.flashlock.repository.ProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class OptimisticStrategy implements ConcurrencyStrategy {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public OptimisticStrategy(ProductRepository productRepository, OrderRepository orderRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    public ConcurrencyStrategyType getType() {
        return ConcurrencyStrategyType.OPTIMISTIC;
    }

    @Override
    @Transactional
    public StrategyExecutionResult execute(Long requestId, Long productId, int quantity) {
        // 1. Read current product version without acquiring a row lock
        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isEmpty()) {
            return new StrategyExecutionResult(
                    false, "INVALID_PRODUCT", null, 0, 0, false,
                    "Purchase failed. Requested product does not exist."
            );
        }

        Product snapshot = productOpt.get();
        if (snapshot.getStock() < quantity) {
            return new StrategyExecutionResult(
                    false,
                    "OUT_OF_STOCK",
                    null,
                    snapshot.getStock(),
                    snapshot.getVersion(),
                    false,
                    "Purchase failed. The requested stock is no longer available."
            );
        }

        int expectedVersion = snapshot.getVersion();

        // 2. Conditional version-checked update (UPDATE ... WHERE id = ? AND version = ? AND stock >= ?)
        int updatedRows = productRepository.decrementStockOptimistic(productId, quantity, expectedVersion);

        if (updatedRows == 1) {
            Order order = new Order(requestId, productId, quantity, getType().name(), "SUCCESS");
            Order savedOrder = orderRepository.save(order);

            int remainingStock = snapshot.getStock() - quantity;
            int newVersion = expectedVersion + 1;
            return new StrategyExecutionResult(
                    true,
                    "SUCCESS",
                    savedOrder.getId(),
                    remainingStock,
                    newVersion,
                    false,
                    "Purchase successful. " + quantity + " unit(s) reserved via Optimistic Locking (v" + expectedVersion + " -> v" + newVersion + ")."
            );
        }

        // 3. Determine whether zero rows were affected due to version conflict or depleted stock
        Product latest = productRepository.findById(productId).orElse(snapshot);
        if (latest.getStock() >= quantity) {
            return new StrategyExecutionResult(
                    false,
                    "CONFLICT",
                    null,
                    latest.getStock(),
                    latest.getVersion(),
                    true,
                    "Purchase conflict. Product version was modified by another concurrent request (expected v" + expectedVersion + ", found v" + latest.getVersion() + "). Please retry."
            );
        }

        return new StrategyExecutionResult(
                false,
                "OUT_OF_STOCK",
                null,
                latest.getStock(),
                latest.getVersion(),
                false,
                "Purchase failed. The requested stock is no longer available."
        );
    }
}
