package com.group2.fse.auth_service.repository;

import com.group2.fse.auth_service.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByUsernameAndUserType(String username, String userType);

    Optional<AppUser> findByCustomerId(Long customerId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
