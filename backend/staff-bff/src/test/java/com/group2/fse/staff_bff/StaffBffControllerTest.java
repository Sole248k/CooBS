package com.group2.fse.staff_bff;

import com.group2.fse.staff_bff.controller.StaffBffController;
import com.group2.fse.staff_bff.dto.StaffCustomer360Response;
import com.group2.fse.staff_bff.dto.StaffQueuesSummaryResponse;
import com.group2.fse.staff_bff.exception.GlobalExceptionHandler;
import com.group2.fse.staff_bff.security.UserPrincipal;
import com.group2.fse.staff_bff.service.StaffBffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StaffBffControllerTest {

    private MockMvc mockMvc;

    @Mock
    private StaffBffService staffBffService;

    @InjectMocks
    private StaffBffController staffBffController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(staffBffController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        UserPrincipal principal = UserPrincipal.builder()
                .userId(101L)
                .username("teller_alice")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_TELLER")))
                .build();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, "mock-token", principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void testGetCustomer360ReturnsOk() throws Exception {
        StaffCustomer360Response mockResponse = StaffCustomer360Response.builder()
                .customerId(1L)
                .displayName("John Doe")
                .kycStatus("VERIFIED")
                .creditScore(750)
                .riskScore("0.15")
                .accounts(Collections.emptyList())
                .totals(new StaffCustomer360Response.StaffTotals("15000.00", "0.00"))
                .build();

        when(staffBffService.getCustomer360(eq(1L), any(), any())).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/staff/customers/1/360")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(1))
                .andExpect(jsonPath("$.displayName").value("John Doe"))
                .andExpect(jsonPath("$.kycStatus").value("VERIFIED"));
    }

    @Test
    void testGetQueuesSummaryReturnsOk() throws Exception {
        StaffQueuesSummaryResponse mockQueues = StaffQueuesSummaryResponse.builder()
                .totalPendingKyc(2)
                .totalPendingClosures(1)
                .pendingKycRequests(Collections.emptyList())
                .pendingClosureRequests(Collections.emptyList())
                .build();

        when(staffBffService.getQueuesSummary(any(), any())).thenReturn(mockQueues);

        mockMvc.perform(get("/api/v1/staff/queues/summary")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPendingKyc").value(2))
                .andExpect(jsonPath("$.totalPendingClosures").value(1));
    }
}
