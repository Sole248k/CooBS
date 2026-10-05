package com.group2.fse.auth_service.controller;

import com.group2.fse.auth_service.dto.LoginRequestDto;
import com.group2.fse.auth_service.dto.LoginResultDto;
import com.group2.fse.auth_service.service.StaffAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for internal bank staff (Admins, Tellers) authentication.
 */
@RestController
@RequestMapping("/api/v1/auth/staff")
@RequiredArgsConstructor
@Slf4j
public class StaffAuthController {

    private final StaffAuthService staffAuthService;

    @PostMapping("/login")
    public ResponseEntity<LoginResultDto> login(@Valid @RequestBody LoginRequestDto request) {
        log.info("REST request for bank staff login: {}", request.getUsername());
        LoginResultDto result = staffAuthService.login(request);
        return ResponseEntity.ok(result);
    }
}
