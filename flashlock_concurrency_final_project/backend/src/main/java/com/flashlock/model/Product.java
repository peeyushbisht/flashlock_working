package com.flashlock.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    private String category;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stock;

    @Column(nullable = false)
    private Integer version = 0;

    public Product() {}

    public Product(String name, String description, String category, String imageUrl, BigDecimal price, Integer stock) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.imageUrl = imageUrl;
        this.price = price;
        this.stock = stock;
        this.version = 0;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getImageUrl() { return imageUrl; }
    public BigDecimal getPrice() { return price; }
    public Integer getStock() { return stock; }
    public Integer getVersion() { return version != null ? version : 0; }

    public void setStock(Integer stock) { this.stock = stock; }
    public void setVersion(Integer version) { this.version = version; }
}
