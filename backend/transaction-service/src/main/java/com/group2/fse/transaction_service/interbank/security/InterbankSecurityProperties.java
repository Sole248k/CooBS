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
        return partners.get(bankCode.toUpperCase());
    }

    @Data
    public static class PartnerConfig {
        private String bankCode;
        private String sharedSecret;
        private Long settlementAccountId = 9005L;
        private boolean authRequired = true;
        private String baseUrl;
    }
}
