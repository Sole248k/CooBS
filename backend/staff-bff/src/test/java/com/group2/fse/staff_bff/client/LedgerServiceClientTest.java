package com.group2.fse.staff_bff.client;

import com.group2.fse.staff_bff.exception.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class LedgerServiceClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Test
    @DisplayName("Counter cash without an Idempotency-Key is refused, never sent with a made-up key")
    void counterCashNeedsTheCallersKey() {
        LedgerServiceClient client = new LedgerServiceClient(restTemplate, "http://ledger");
        Map<String, Object> payload = Map.of("accountId", 1, "amount", "100.00", "referenceNo", "TX-1");

        for (String missing : new String[]{null, " "}) {
            assertThatThrownBy(() -> client.executeCashDeposit(payload, missing, "Bearer t", "corr"))
                    .isInstanceOfSatisfying(ApiException.class, e ->
                            org.assertj.core.api.Assertions.assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
            assertThatThrownBy(() -> client.executeCashWithdrawal(payload, missing, "Bearer t", "corr"))
                    .isInstanceOf(ApiException.class);
        }
        // A retry with a new key would be a second deposit, so nothing reaches the ledger.
        verifyNoInteractions(restTemplate);
    }
}
