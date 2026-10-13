package com.group2.fse.order_service.repository;

import com.group2.fse.order_service.entity.ShopOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShopOrderRepository extends JpaRepository<ShopOrder, Long> {

    List<ShopOrder> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Optional<ShopOrder> findByReferenceNo(String referenceNo);

    Optional<ShopOrder> findByPickupPassToken(String pickupPassToken);

    Optional<ShopOrder> findByCustomerIdAndReferenceNo(Long customerId, String referenceNo);
}
