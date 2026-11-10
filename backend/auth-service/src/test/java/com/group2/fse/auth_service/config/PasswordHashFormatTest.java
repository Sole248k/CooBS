package com.group2.fse.auth_service.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cloud deployment replaces the staff password hashes with ones made by htpasswd
 * (infra/k8s/jobs/load-schemas.yaml). htpasswd writes the prefix $2y$ where this service
 * writes $2a$; the hash behind it is computed the same way. If the encoder ever stopped
 * reading $2y$, admin and the tellers could not sign in on Azure, and nothing local would show it.
 */
class PasswordHashFormatTest {

    @Test
    @DisplayName("a bcrypt hash with the $2y$ prefix, as htpasswd writes it, is accepted")
    void acceptsTheHtpasswdPrefix() {
        // The encoder SecurityConfig builds: bcrypt, cost 10.
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
        String ours = encoder.encode("St0a1b2c3d4e5f6a7");
        assertThat(ours).startsWith("$2a$10$");

        String asHtpasswdWritesIt = "$2y$" + ours.substring(4);

        assertThat(encoder.matches("St0a1b2c3d4e5f6a7", asHtpasswdWritesIt)).isTrue();
        assertThat(encoder.matches("Password123!", asHtpasswdWritesIt)).isFalse();
    }
}
