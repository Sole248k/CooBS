package com.group2.fse.customer_service.service;

import com.group2.fse.customer_service.dto.Customer360Response;

public interface Customer360Service {
    Customer360Response getCustomer360(Long customerId, boolean isStaff);
}
