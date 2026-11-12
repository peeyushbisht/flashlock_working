package com.flashlock.strategy;

import com.flashlock.model.Order;
import com.flashlock.model.Product;
import com.flashlock.repository.OrderRepository;
import com.flashlock.repository.ProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class AtomicStrategy implements ConcurrencyStrategy {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    public AtomicStrategy(ProductRepository productRepository, OrderRepository orderRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    public ConcurrencyStrategyType getType() {
        return ConcurrencyStrategyType.ATOMIC;
    }

    @Override
    @Transactional
    public StrategyExecutionResult execute(Long requestId, Long productId, int quantity) {
        int updatedRows = productRepository.decrementStockAtomic(productId, quantity);

        if (updatedRows == 1) {
            Order order = new Order(requestId, productId, quantity, getType().name(), "SUCCESS");
            Order savedOrder = orderRepository.save(order);

            Product updatedProduct = productRepository.findById(productId).orElseThrow();
            return new StrategyExecutionResult(
                    true,
                    "SUCCESS",
                    savedOrder.getId(),
                    updatedProduct.getStock(),
                    updatedProduct.getVersion(),
                    false,
                    "Purchase successful. " + quantity + " unit(s) reserved via Atomic Update."
            );
        }

        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isEmpty()) {
            return new StrategyExecutionResult(
                    false, "INVALID_PRODUCT", null, 0, 0, false,
                    "Purchase failed. Requested product does not exist."
            );
        }

        Product product = productOpt.get();
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
}
