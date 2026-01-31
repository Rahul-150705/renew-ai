// package com.renewai.dto;

// import jakarta.validation.constraints.Email;
// import jakarta.validation.constraints.NotBlank;
// import jakarta.validation.constraints.Pattern;
// import lombok.AllArgsConstructor;
// import lombok.Data;
// import lombok.NoArgsConstructor;

// /**
//  * DTO for creating a new client
//  */
// @Data
// @NoArgsConstructor
// @AllArgsConstructor
// public class ClientRequest {
    
//     @NotBlank(message = "Full name is required")
//     private String fullName;
    
//     @NotBlank(message = "Email is required")
//     @Email(message = "Invalid email format")
//     private String email;
    
//     @NotBlank(message = "Phone number is required")
//     @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format. Use E.164 format (e.g., +1234567890)")
//     private String phoneNumber;
    
//     private String address;
// }