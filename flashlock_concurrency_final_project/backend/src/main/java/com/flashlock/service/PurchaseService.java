package com.flashlock.service;

import com.flashlock.ipc.EngineIpcClient;
import com.flashlock.model.Product;
import com.flashlock.model.PurchaseResponse;
import com.flashlock.repository.OrderRepository;
import com.flashlock.repository.ProductRepository;
import com.flashlock.strategy.ConcurrencyStrategy;
import com.flashlock.strategy.ConcurrencyStrategyType;
import com.flashlock.strategy.StrategyExecutionResult;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PurchaseService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final EngineIpcClient ipcClient;
    private final Map<ConcurrencyStrategyType, ConcurrencyStrategy> strategyMap = new EnumMap<>(ConcurrencyStrategyType.class);
    private final AtomicLong requestIdGenerator = new AtomicLong(1000);
    private static final long ENGINE_WAIT_TIMEOUT_MS = 30000;

    public PurchaseService(ProductRepository productRepository,
                           OrderRepository orderRepository,
                           EngineIpcClient ipcClient,
                           List<ConcurrencyStrategy> strategies) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.ipcClient = ipcClient;
        for (ConcurrencyStrategy strategy : strategies) {
            strategyMap.put(strategy.getType(), strategy);
        }
    }

    public Long generateNextRequestId() {
        return requestIdGenerator.incrementAndGet();
    }

    public PurchaseResponse executePurchaseTransaction(Long requestId, Long productId, int quantity, int priority, String strategyName) {
        if (productId == null || productId <= 0) {
            return new PurchaseResponse(requestId, "INVALID_REQUEST", "ATOMIC", null, 0, 0, false, "Invalid product ID specified.");
        }
        if (quantity <= 0 || quantity > 100) {
            return new PurchaseResponse(requestId, "INVALID_REQUEST", "ATOMIC", null, 0, 0, false, "Quantity must be between 1 and 100.");
        }

        ConcurrencyStrategyType strategyType = ConcurrencyStrategyType.fromString(strategyName);
        ConcurrencyStrategy strategy = strategyMap.get(strategyType);

        // Queue the request in the C engine; its scheduler (FIFO / Priority / Priority + Aging)
        // decides when this request may touch the database. Null = engine offline, run directly.
        EngineIpcClient.Execution execution =
                ipcClient.submitAndAwait(requestId, productId, quantity, priority, "WEB", ENGINE_WAIT_TIMEOUT_MS);

        if (execution != null && execution.isRejected()) {
            return new PurchaseResponse(requestId, "QUEUE_FULL", strategyType.name(), null, 0, 0, false,
                    "Scheduler queue is full. Please retry.")
                    .withScheduling(true, null, execution.getPolicy());
        }

        try {
            StrategyExecutionResult result = strategy.execute(requestId, productId, quantity);
            return withScheduling(new PurchaseResponse(
                    requestId,
                    result.getStatus(),
                    strategyType.name(),
                    result.getOrderId(),
                    result.getRemainingStock(),
                    result.getVersion(),
                    result.isConflictDetected(),
                    result.getMessage()
            ), execution);
        } catch (DataAccessException ex) {
            Product current = productRepository.findById(productId).orElse(null);
            int currentStock = current != null ? current.getStock() : 0;
            int currentVersion = current != null ? current.getVersion() : 0;
            return withScheduling(new PurchaseResponse(
                    requestId,
                    "CONFLICT",
                    strategyType.name(),
                    null,
                    currentStock,
                    currentVersion,
                    true,
                    "Database transaction contention detected. Please retry."
            ), execution);
        } catch (Exception ex) {
            return withScheduling(new PurchaseResponse(
                    requestId,
                    "ERROR",
                    strategyType.name(),
                    null,
                    0,
                    0,
                    false,
                    "Unable to process purchase request due to an internal database error."
            ), execution);
        } finally {
            if (execution != null) {
                ipcClient.markDone(requestId); // frees the engine worker for the next scheduled request
            }
        }
    }

    private PurchaseResponse withScheduling(PurchaseResponse response, EngineIpcClient.Execution execution) {
        if (execution == null) {
            return response.withScheduling(false, null, null);
        }
        return response.withScheduling(true, execution.getWaitMs(), execution.getPolicy());
    }

    @Transactional
    public Product resetProductStock(Long productId, int targetStock) {
        int safeStock = Math.max(0, Math.min(targetStock, 100000));
        orderRepository.deleteByProductId(productId);
        productRepository.resetProductStock(productId, safeStock);
        return productRepository.findById(productId).orElseThrow();
    }

    @Transactional
    public List<Product> resetAllProducts() {
        orderRepository.deleteAllInBatch();
        productRepository.resetProductStock(1L, 10);
        productRepository.resetProductStock(2L, 50);
        productRepository.resetProductStock(3L, 30);
        productRepository.resetProductStock(4L, 100);
        return productRepository.findAllByOrderByIdAsc();
    }
}
