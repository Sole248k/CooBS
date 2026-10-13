package com.group2.fse.account_service.service;

import com.group2.fse.account_service.dto.sharecapital.*;

import java.util.List;

public interface ShareCapitalService {

    ShareCapitalResponse getMemberShareCapital(Long customerId);

    ShareCapitalTopUpResponse topUpShareCapital(Long customerId, ShareCapitalTopUpRequest request);

    List<DividendPayoutResponse> getDividendHistory(Long customerId);

    List<ShareCapitalTransactionResponse> getTransactions(Long customerId);
}
