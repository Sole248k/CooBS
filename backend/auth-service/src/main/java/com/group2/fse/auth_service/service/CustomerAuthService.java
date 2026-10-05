package com.group2.fse.auth_service.service;

import com.group2.fse.auth_service.dto.LoginRequestDto;
import com.group2.fse.auth_service.dto.LoginResultDto;

public interface CustomerAuthService {

    /**
     * Authenticates retail customer credentials and returns tokens.
     */
    LoginResultDto login(LoginRequestDto request);
}
