package com.flashlock.model;

import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", unique = true, nullable = false)
    private Long requestId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    private Integer quantity;
    private String status;

    @Column(name = "created_at")
    private ZonedDateTime createdAt = ZonedDateTime.now();

    public Order() {}

    public Order(Long requestId, Long productId, Integer quantity, String status) {
        this.requestId = requestId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = status;
    }

    public Long getId() { return id; }
    public Long getRequestId() { return requestId; }
    public Long getProductId() { return productId; }
    public Integer getQuantity() { return quantity; }
    public String getStatus() { return status; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
}
