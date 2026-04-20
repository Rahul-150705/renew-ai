package com.renewai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegistrationRequest {
    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "Full name is required")
    private String fullName;

    private String phoneNumber;
}
