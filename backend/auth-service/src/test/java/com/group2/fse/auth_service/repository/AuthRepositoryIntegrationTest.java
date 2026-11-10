package com.group2.fse.auth_service.repository;

import com.group2.fse.auth_service.entity.AppUser;
import com.group2.fse.auth_service.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Phase 2: Auth JPA Entities & Repository Integration Tests")
class AuthRepositoryIntegrationTest {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    @DisplayName("Should retrieve seed roles from identity_db")
    void shouldFindSeedRoles() {
        Optional<Role> adminRole = roleRepository.findByRoleName("ROLE_ADMIN");
        Optional<Role> tellerRole = roleRepository.findByRoleName("ROLE_TELLER");
        Optional<Role> customerRole = roleRepository.findByRoleName("ROLE_CUSTOMER");

        assertThat(adminRole).isPresent();
        assertThat(adminRole.get().getRoleName()).isEqualTo("ROLE_ADMIN");

        assertThat(tellerRole).isPresent();
        assertThat(tellerRole.get().getRoleName()).isEqualTo("ROLE_TELLER");

        assertThat(customerRole).isPresent();
        assertThat(customerRole.get().getRoleName()).isEqualTo("ROLE_CUSTOMER");
    }

    @Test
    @DisplayName("Should retrieve seed internal staff users with mapped roles from APP_USER")
    void shouldFindSeedStaffUsers() {
        Optional<AppUser> admin = appUserRepository.findByUsernameAndUserType("admin", "STAFF");
        assertThat(admin).isPresent();
        assertThat(admin.get().getEmail()).isEqualTo("admin@corebank.local");
        assertThat(admin.get().getRole().getRoleName()).isEqualTo("ROLE_ADMIN");
        assertThat(admin.get().getStatus()).isEqualTo("ACTIVE");

        Optional<AppUser> tellerAlice = appUserRepository.findByUsernameAndUserType("teller_alice", "STAFF");
        assertThat(tellerAlice).isPresent();
        assertThat(tellerAlice.get().getRole().getRoleName()).isEqualTo("ROLE_TELLER");

        assertThat(appUserRepository.existsByUsername("admin")).isTrue();
        assertThat(appUserRepository.existsByEmail("admin@corebank.local")).isTrue();
        assertThat(appUserRepository.existsByUsername("non_existent_user")).isFalse();
    }

    @Test
    @DisplayName("Should retrieve seed retail customers with customerId from APP_USER")
    void shouldFindSeedCustomers() {
        Optional<AppUser> johnDoe = appUserRepository.findByUsernameAndUserType("john_doe", "CUSTOMER");
        assertThat(johnDoe).isPresent();
        assertThat(johnDoe.get().getEmail()).isEqualTo("john.doe@example.com");
        assertThat(johnDoe.get().getCustomerId()).isEqualTo(1L);
        assertThat(johnDoe.get().getUserType()).isEqualTo("CUSTOMER");

        assertThat(appUserRepository.existsByUsername("john_doe")).isTrue();
        assertThat(appUserRepository.existsByEmail("john.doe@example.com")).isTrue();
        assertThat(appUserRepository.existsByUsername("unknown_customer")).isFalse();
    }

    @Test
    @DisplayName("Should persist and query newly created AppUser")
    void shouldPersistAndQueryAppUser() {
        Role customerRole = roleRepository.findByRoleName("ROLE_CUSTOMER")
                .orElseThrow(() -> new AssertionError("ROLE_CUSTOMER must exist"));

        String uniqueUsername = "test_user_" + System.currentTimeMillis();
        String uniqueEmail = uniqueUsername + "@bank.local";

        Long testCustomerId = 9999L;
        AppUser newUser = AppUser.builder()
                .username(uniqueUsername)
                .email(uniqueEmail)
                .passwordHash("$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi")
                .userType("CUSTOMER")
                .customerId(testCustomerId)
                .role(customerRole)
                .status("ACTIVE")
                .build();

        AppUser saved = appUserRepository.save(newUser);
        assertThat(saved.getUserId()).isNotNull();

        Optional<AppUser> fetched = appUserRepository.findByUsername(uniqueUsername);
        assertThat(fetched).isPresent();
        assertThat(fetched.get().getEmail()).isEqualTo(uniqueEmail);
        assertThat(fetched.get().getCustomerId()).isEqualTo(testCustomerId);
    }
}
