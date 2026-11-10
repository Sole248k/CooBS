package com.group2.fse.order_service.repository;

import com.group2.fse.order_service.entity.ShopOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShopOrderItemRepository extends JpaRepository<ShopOrderItem, Long> {

    List<ShopOrderItem> findByOrderOrderId(Long orderId);
}
