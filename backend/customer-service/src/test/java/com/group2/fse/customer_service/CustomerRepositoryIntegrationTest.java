package com.group2.fse.customer_service;

import com.group2.fse.customer_service.entity.Beneficiary;
import com.group2.fse.customer_service.entity.ChannelProfile;
import com.group2.fse.customer_service.repository.BeneficiaryRepository;
import com.group2.fse.customer_service.repository.ChannelProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DisplayName("Customer-Service: PostgreSQL customer_db Integration Tests")
class CustomerRepositoryIntegrationTest {

    @Autowired
    private ChannelProfileRepository channelProfileRepository;

    @Autowired
    private BeneficiaryRepository beneficiaryRepository;

    @Test
    @DisplayName("Should retrieve seeded John Doe channel profile from customer_db")
    void shouldFindSeedChannelProfile() {
        Optional<ChannelProfile> profile = channelProfileRepository.findById(1L);

        assertThat(profile).isPresent();
        assertThat(profile.get().getUsername()).isEqualTo("john_doe");
        assertThat(profile.get().getMembershipNumber()).isEqualTo("MBR-000001");
        assertThat(profile.get().getDisplayName()).isEqualTo("John D.");
        assertThat(profile.get().getKycTier()).isEqualTo("TIER_3_FULL");
        assertThat(profile.get().getKycStatus()).isEqualTo("VERIFIED");
    }

    @Test
    @DisplayName("Should retrieve seeded active beneficiaries for customer 1")
    void shouldFindSeedBeneficiaries() {
        List<Beneficiary> beneficiaries = beneficiaryRepository.findByCustomerIdAndActiveTrue(1L);

        assertThat(beneficiaries).isNotEmpty();
        assertThat(beneficiaries.get(0).getNickname()).isEqualTo("Maria");
        assertThat(beneficiaries.get(0).getAccountNumber()).isEqualTo("ACC_10000002");
        assertThat(beneficiaries.get(0).getBankCode()).isEqualTo("COOBS");
    }
}
