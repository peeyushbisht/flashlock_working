package com.flashlock.controller;

import com.flashlock.model.Product;
import com.flashlock.model.PurchaseRequest;
import com.flashlock.model.PurchaseResponse;
import com.flashlock.repository.ProductRepository;
import com.flashlock.service.PurchaseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        return ResponseEntity.ok(productRepository.findAll());
    }

    @PostMapping("/purchase")
    public ResponseEntity<PurchaseResponse> purchase(@RequestBody PurchaseRequest request) {
        Long requestId = purchaseService.generateNextRequestId();
        int priority = (request.getPriority() != null) ? request.getPriority() : 3;
        int quantity = (request.getQuantity() != null) ? request.getQuantity() : 1;

        PurchaseResponse response = purchaseService.executePurchaseTransaction(
                requestId, request.getProductId(), quantity, priority
        );

        if ("SUCCESS".equals(response.getStatus())) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(409).body(response);
        }
    }
}
