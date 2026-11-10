package com.group2.fse.customer_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycSubmitRequest {

    @NotBlank(message = "First name is mandatory")
    private String firstName;

    private String middleInitial;

    @NotBlank(message = "Last name is mandatory")
    private String lastName;

    @NotBlank(message = "Address is mandatory")
    private String address;

    private String civilStatus;

    private String occupation;

    @NotBlank(message = "Mobile number is mandatory")
    private String mobileNumber;
}
