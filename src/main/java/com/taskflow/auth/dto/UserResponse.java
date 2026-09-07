package com.taskflow.auth.dto;

import com.taskflow.user.model.Role;
import com.taskflow.user.model.User;

import java.util.UUID;

public record UserResponse(UUID id, String name, String email, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}