package com.flashlock.strategy;

import com.flashlock.model.Order;
import com.flashlock.model.Product;
import com.flashlock.repository.OrderRepository;
import com.flashlock.repository.ProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class PessimisticStrategy implements ConcurrencyStrategy {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public PessimisticStrategy(ProductRepository productRepository, OrderRepository orderRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    public ConcurrencyStrategyType getType() {
        return ConcurrencyStrategyType.PESSIMISTIC;
    }

    @Override
    @Transactional
    public StrategyExecutionResult execute(Long requestId, Long productId, int quantity) {
        // 1. Acquire exclusive database row lock (SELECT ... FOR UPDATE)
        Optional<Product> lockedProductOpt = productRepository.findByIdForUpdate(productId);
        if (lockedProductOpt.isEmpty()) {
            return new StrategyExecutionResult(
                    false, "INVALID_PRODUCT", null, 0, 0, false,
                    "Purchase failed. Requested product does not exist."
            );
        }

        Product product = lockedProductOpt.get();

        // 2. Verify available stock while holding the row lock
        if (product.getStock() < quantity) {
            return new StrategyExecutionResult(
                    false,
                    "OUT_OF_STOCK",
                    null,
                    product.getStock(),
                    product.getVersion(),
                    false,
                    "Purchase failed. The requested stock is no longer available."
            );
        }

        // 3. Decrement stock and increment version within the locked transaction
        int newStock = product.getStock() - quantity;
        int newVersion = product.getVersion() + 1;
        product.setStock(newStock);
        product.setVersion(newVersion);
        productRepository.saveAndFlush(product);

        // 4. Persist order record; transaction commit releases the row lock
        Order order = new Order(requestId, productId, quantity, getType().name(), "SUCCESS");
        Order savedOrder = orderRepository.save(order);

        return new StrategyExecutionResult(
                true,
                "SUCCESS",
                savedOrder.getId(),
                newStock,
                newVersion,
                false,
                "Purchase successful. " + quantity + " unit(s) reserved under Pessimistic Row Lock (FOR UPDATE)."
        );
    }
}
