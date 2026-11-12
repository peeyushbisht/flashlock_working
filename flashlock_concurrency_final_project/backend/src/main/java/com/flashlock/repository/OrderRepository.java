package com.flashlock.repository;

import com.flashlock.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Modifying
    @Query("DELETE FROM Order o WHERE o.productId = :productId")
    void deleteByProductId(@Param("productId") Long productId);
}
