package com.group2.fse.transaction_service.interbank.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "app.interbank")
public class InterbankSecurityProperties {

    private String ownBankCode = "COOBS";
    private int allowedTimestampSkewSeconds = 300; // 5 minutes as per contract Section 4
    private Long defaultSettlementAccountId = 9005L; // GL_SETTLEMENT_BANKB
    private String defaultSharedSecret = "coobs-bankb-secret-2026";
    private Map<String, PartnerConfig> partners = new HashMap<>();

    public PartnerConfig getPartner(String bankCode) {
        if (bankCode == null) {
            return null;
        }
        PartnerConfig config = partners.get(bankCode.toUpperCase());
        if (config == null) {
            // Provide fallback configuration for testing / demo partner banks
            config = new PartnerConfig();
            config.setBankCode(bankCode.toUpperCase());
            config.setSharedSecret(defaultSharedSecret);
            config.setSettlementAccountId(defaultSettlementAccountId);
        }
        return config;
    }

    @Data
    public static class PartnerConfig {
        private String bankCode;
        private String sharedSecret;
        private Long settlementAccountId = 9005L;
    }
}
