package com.pulsepay.identity_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class RegisterAccountRequest {
    @NotBlank(message = "Name is Required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;
    @NotBlank(message = "Email is Required")
    @Email(message = "Email must be valid")
    @Size(max=255, message = "Email must not exceed 255 characters")
    private String email;
    @NotBlank(message = "Phone is Required")
    @Size(max=30, message = "Phone must not exceed 30 characters")
    private String phone;
    @NotBlank(message = "Password is Required")
    @Size(min=12, max = 72, message = "Password must be between 12 and 72 characters")
    private String password;
}
