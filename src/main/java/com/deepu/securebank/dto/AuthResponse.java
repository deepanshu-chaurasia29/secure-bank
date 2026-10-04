package com.deepu.securebank.dto;

public class AuthResponse {
    private String token;
    private String accountNumber;
    private String fullName;
    private String role;

    public AuthResponse(String token, String accountNumber, String fullName, String role) {
        this.token = token;
        this.accountNumber = accountNumber;
        this.fullName = fullName;
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public String getRole() {
        return role;
    }
}
