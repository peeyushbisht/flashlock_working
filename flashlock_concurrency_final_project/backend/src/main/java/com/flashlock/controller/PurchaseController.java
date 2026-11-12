package com.flashlock.controller;

import com.flashlock.model.Product;
import com.flashlock.model.PurchaseRequest;
import com.flashlock.model.PurchaseResponse;
import com.flashlock.repository.ProductRepository;
import com.flashlock.service.PurchaseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class PurchaseController {

    private final PurchaseService purchaseService;
    private final ProductRepository productRepository;

    public PurchaseController(PurchaseService purchaseService, ProductRepository productRepository) {
        this.purchaseService = purchaseService;
        this.productRepository = productRepository;
    }

    @GetMapping("/products")
    public ResponseEntity<List<Product>> getProducts() {
        return ResponseEntity.ok(productRepository.findAllByOrderByIdAsc());
    }

    @PostMapping("/purchase")
    public ResponseEntity<PurchaseResponse> purchase(@RequestBody PurchaseRequest request) {
        Long requestId = purchaseService.generateNextRequestId();
        int priority = (request.getPriority() != null) ? request.getPriority() : 3;
        int quantity = (request.getQuantity() != null) ? request.getQuantity() : 1;

        PurchaseResponse response = purchaseService.executePurchaseTransaction(
                requestId, request.getProductId(), quantity, priority, request.getStrategy()
        );

        return switch (response.getStatus()) {
            case "SUCCESS" -> ResponseEntity.ok(response);
            case "INVALID_REQUEST", "INVALID_PRODUCT" -> ResponseEntity.badRequest().body(response);
            case "OUT_OF_STOCK", "CONFLICT" -> ResponseEntity.status(409).body(response);
            case "QUEUE_FULL" -> ResponseEntity.status(503).body(response);
            default -> ResponseEntity.status(500).body(response);
        };
    }

    @PostMapping("/products/{id}/reset")
    public ResponseEntity<Product> resetProduct(@PathVariable Long id, @RequestBody(required = false) Map<String, Integer> body) {
        int stock = (body != null && body.get("stock") != null) ? body.get("stock") : 100;
        return ResponseEntity.ok(purchaseService.resetProductStock(id, stock));
    }

    @PostMapping("/products/reset-all")
    public ResponseEntity<List<Product>> resetAllProducts() {
        return ResponseEntity.ok(purchaseService.resetAllProducts());
    }
}
