package com.group2.fse.transaction_service.repository;

import com.group2.fse.transaction_service.entity.InterbankMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InterbankMessageRepository extends JpaRepository<InterbankMessage, UUID> {
}
