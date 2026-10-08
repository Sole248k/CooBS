package com.group2.fse.mobile_bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Mobile Backend-for-Frontend (BFF) Application (:8094)
 * Serves Flutter retail mobile banking client at /api/v1/mobile/**.
 */
@SpringBootApplication
@EnableAsync
public class MobileBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(MobileBffApplication.class, args);
    }
}
