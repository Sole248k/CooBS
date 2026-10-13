package com.group2.fse.order_service.repository;

import com.group2.fse.order_service.entity.ShopProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShopProductRepository extends JpaRepository<ShopProduct, Long> {

    List<ShopProduct> findByIsActiveOrderByProductIdAsc(Integer isActive);

    Optional<ShopProduct> findBySku(String sku);
}
