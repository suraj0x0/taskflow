package com.taskflow.project.controller;

import com.taskflow.project.dto.CreateProjectRequest;
import com.taskflow.project.dto.ProjectResponse;
import com.taskflow.project.dto.UpdateProjectRequest;
import com.taskflow.project.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateProjectRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.createProject(authentication.getName(), request));
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getAll() {
        return ResponseEntity.ok(projectService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(projectService.findById(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ProjectResponse> update(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProjectRequest request
    ) {
        return ResponseEntity.ok(projectService.updateProject(id, authentication.getName(), request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable UUID id) {
        projectService.deleteProject(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}