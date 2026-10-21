package com.deepu.securebank.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Safe profile fields returned for the authenticated user. Never include password data here. */
public class CustomerProfileResponse {

    private final String fullName;
    private final String email;
    private final String phone;
    private final LocalDate dateOfBirth;
    private final LocalDateTime memberSince;

    public CustomerProfileResponse(String fullName, String email, String phone,
                                   LocalDate dateOfBirth, LocalDateTime memberSince) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.dateOfBirth = dateOfBirth;
        this.memberSince = memberSince;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public LocalDateTime getMemberSince() {
        return memberSince;
    }
}
