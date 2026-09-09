package com.taskflow.user.dto;

import com.taskflow.user.model.Role;
import jakarta.validation.constraints.NotNull;

public class UpdateRoleRequest {

    @NotNull(message = "Role must be provided")
    private Role role;

    public UpdateRoleRequest() {
    }

    public UpdateRoleRequest(Role role) {
        this.role = role;
    }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
}