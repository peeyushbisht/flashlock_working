package com.flashlock.service;

import com.flashlock.ipc.EngineIpcClient;
import com.flashlock.model.Order;
import com.flashlock.model.Product;
import com.flashlock.model.PurchaseResponse;
import com.flashlock.repository.OrderRepository;
import com.flashlock.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class PurchaseService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final EngineIpcClient ipcClient;
    private final AtomicLong requestIdGenerator = new AtomicLong(1000);

    public PurchaseService(ProductRepository productRepository, OrderRepository orderRepository, EngineIpcClient ipcClient) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.ipcClient = ipcClient;
    }

    public Long generateNextRequestId() {
        return requestIdGenerator.incrementAndGet();
    }

    @Transactional
    public PurchaseResponse executePurchaseTransaction(Long requestId, Long productId, int quantity, int priority) {
        // 1. Submit request to C Engine bounded queue via Unix Domain Socket
        ipcClient.sendRequestToEngine(requestId, productId, quantity, priority, "WEB");

        // 2. Zero Overselling Guardrail: Atomic Conditional SQL Decrement in PostgreSQL
        int updatedRows = productRepository.decrementStockAtomic(productId, quantity);

        if (updatedRows == 1) {
            // Transaction Succeeded: Stock decremented safely
            Order order = new Order(requestId, productId, quantity, "SUCCESS");
            Order savedOrder = orderRepository.save(order);

            Product product = productRepository.findById(productId).orElseThrow();
            return new PurchaseResponse(requestId, "SUCCESS", savedOrder.getId(), product.getStock(), "Purchase completed successfully");
        } else {
            // Stock constraint failed (stock < quantity) -> No order created
            Product product = productRepository.findById(productId).orElse(null);
            int currentStock = (product != null) ? product.getStock() : 0;
            return new PurchaseResponse(requestId, "OUT_OF_STOCK", null, currentStock, "Insufficient inventory available");
        }
    }
}
