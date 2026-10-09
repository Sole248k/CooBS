package com.group2.fse.staff_bff.service;

import com.group2.fse.staff_bff.client.AccountServiceClient;
import com.group2.fse.staff_bff.client.CustomerServiceClient;
import com.group2.fse.staff_bff.service.impl.StaffBffServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffBffAccountNamesTest {

    @Test
    @DisplayName("The accounts list carries the member's name from customer-service, read once per customer")
    void accountsCarryTheCustomersName() {
        AccountServiceClient accounts = mock(AccountServiceClient.class);
        CustomerServiceClient customers = mock(CustomerServiceClient.class);
        when(accounts.fetchAllAccounts("token", "corr")).thenReturn(List.of(
                Map.of("accountId", 4, "customerId", 4, "customerName", "Customer #4"),
                Map.of("accountId", 5, "customerId", 4, "customerName", "Customer #4"),
                Map.of("accountId", 7, "customerId", 5, "customerName", "Customer #5")));
        when(customers.fetchCustomerProfile(4L, "token", "corr")).thenReturn(Map.of("displayName", "Juan Dela Cruz"));
        // customer-service gave no answer for this one: the placeholder stays, the list still loads.
        when(customers.fetchCustomerProfile(5L, "token", "corr")).thenReturn(Collections.emptyMap());

        StaffBffServiceImpl service = new StaffBffServiceImpl(customers, accounts, null, null, null, null, null, Runnable::run);
        List<Map<String, Object>> result = service.getAllAccounts("corr", "token");

        assertThat(result).extracting(a -> a.get("customerName"))
                .containsExactly("Juan Dela Cruz", "Juan Dela Cruz", "Customer #5");
        verify(customers, times(1)).fetchCustomerProfile(4L, "token", "corr");
    }
}
