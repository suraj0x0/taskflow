package com.taskflow.project.dto;

import jakarta.validation.constraints.Size;

public class UpdateProjectRequest {

    @Size(max = 150, message = "Name must not exceed 150 characters")
    private String name;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    public UpdateProjectRequest() {
    }

    public UpdateProjectRequest(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}