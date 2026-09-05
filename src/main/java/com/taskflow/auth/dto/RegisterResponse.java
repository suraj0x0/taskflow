package com.taskflow.auth.dto;

import com.taskflow.user.model.Role;
import com.taskflow.user.model.User;

import java.time.Instant;
import java.util.UUID;

public class RegisterResponse {

    private UUID id;
    private String name;
    private String email;
    private Role role;
    private Instant createdAt;

    public RegisterResponse() {
    }

    public RegisterResponse(UUID id, String name, String email, Role role, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.createdAt = createdAt;
    }

    public static RegisterResponse from(User user) {
        return new RegisterResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

