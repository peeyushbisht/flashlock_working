package com.flashlock.repository;

import com.flashlock.model.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByOrderByIdAsc();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.stock = p.stock - :quantity, p.version = p.version + 1 WHERE p.id = :productId AND p.stock >= :quantity")
    int decrementStockAtomic(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :productId")
    Optional<Product> findByIdForUpdate(@Param("productId") Long productId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.stock = p.stock - :quantity, p.version = p.version + 1 WHERE p.id = :productId AND p.version = :expectedVersion AND p.stock >= :quantity")
    int decrementStockOptimistic(@Param("productId") Long productId,
                                 @Param("quantity") Integer quantity,
                                 @Param("expectedVersion") Integer expectedVersion);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.stock = :stock, p.version = 0 WHERE p.id = :productId")
    int resetProductStock(@Param("productId") Long productId, @Param("stock") Integer stock);
}
