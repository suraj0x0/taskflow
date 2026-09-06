package com.taskflow.auth.dto;

import com.taskflow.user.model.Role;
import com.taskflow.user.model.User;

import java.util.UUID;

public class LoginResponse {

    private UUID id;
    private String name;
    private String email;
    private Role role;

    public LoginResponse() {
    }

    public LoginResponse(UUID id, String name, String email, Role role) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public static LoginResponse from(User user) {
        return new LoginResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}

