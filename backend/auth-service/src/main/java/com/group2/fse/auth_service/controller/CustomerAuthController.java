package com.group2.fse.auth_service.controller;

import com.group2.fse.auth_service.dto.LoginRequestDto;
import com.group2.fse.auth_service.dto.LoginResultDto;
import com.group2.fse.auth_service.service.CustomerAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public REST Controller for customer identity and authentication flows.
 */
@RestController
@RequestMapping("/api/v1/auth/customers")
@RequiredArgsConstructor
@Slf4j
public class CustomerAuthController {

    private final CustomerAuthService customerAuthService;

    @PostMapping("/login")
    public ResponseEntity<LoginResultDto> login(@Valid @RequestBody LoginRequestDto request) {
        log.info("REST request for customer login: {}", request.getUsername());
        LoginResultDto result = customerAuthService.login(request);
        return ResponseEntity.ok(result);
    }
}
