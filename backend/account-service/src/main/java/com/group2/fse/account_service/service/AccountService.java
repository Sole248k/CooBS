package com.group2.fse.account_service.service;

import com.group2.fse.account_service.dto.AccountResponse;
import com.group2.fse.account_service.dto.CreateAccountRequest;

import java.util.List;

public interface AccountService {
    AccountResponse createAccount(CreateAccountRequest request);
    List<AccountResponse> getCustomerAccounts(Long customerId);
    List<AccountResponse> getAllAccounts();
    AccountResponse getAccountById(Long accountId, Long customerId, boolean isStaff);
    AccountResponse getAccountByNumber(String accountNumber);
    AccountResponse updateAccountStatus(Long accountId, String status);
}
