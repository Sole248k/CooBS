package com.group2.fse.auth_service.service;

import com.group2.fse.auth_service.dto.LoginRequestDto;
import com.group2.fse.auth_service.dto.LoginResultDto;

public interface StaffAuthService {

    /**
     * Authenticates bank staff (tellers, admins) against internal credentials.
     */
    LoginResultDto login(LoginRequestDto request);
}
