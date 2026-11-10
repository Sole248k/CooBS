package com.group2.fse.customer_service.repository;

import com.group2.fse.customer_service.entity.ChannelProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChannelProfileRepository extends JpaRepository<ChannelProfile, Long> {
    Optional<ChannelProfile> findByUsername(String username);
    Optional<ChannelProfile> findByMembershipNumber(String membershipNumber);
}
