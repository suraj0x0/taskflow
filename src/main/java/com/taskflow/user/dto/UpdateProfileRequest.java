package com.taskflow.user.dto;

import jakarta.validation.constraints.Email;

import jakarta.validation.constraints.Size;

public class UpdateProfileRequest {

    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    @Email(message = "Email must be a valid email address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    public UpdateProfileRequest() {
    }

    public UpdateProfileRequest(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

}