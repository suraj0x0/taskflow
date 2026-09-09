package com.taskflow.project.service;

import com.taskflow.project.dto.CreateProjectRequest;
import com.taskflow.project.dto.ProjectResponse;
import com.taskflow.project.dto.UpdateProjectRequest;
import com.taskflow.project.exception.ProjectNotFoundException;
import com.taskflow.project.model.Project;
import com.taskflow.project.repository.ProjectRepository;
import com.taskflow.user.model.Role;
import com.taskflow.user.model.User;
import com.taskflow.user.repository.UserRepository;
import com.taskflow.user.exception.UserNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public ProjectService(ProjectRepository projectRepository, UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ProjectResponse createProject(String authenticatedEmail, CreateProjectRequest request) {
        User user = findUser(authenticatedEmail);
        requireManagerOrAdmin(user);
        Project project = new Project(request.getName().trim(), request.getDescription(), user);
        return toResponse(projectRepository.save(project));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> findAll() {
        return projectRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse findById(UUID id) {
        return toResponse(findProject(id));
    }

    @Transactional
    public ProjectResponse updateProject(UUID id, String authenticatedEmail, UpdateProjectRequest request) {
        User user = findUser(authenticatedEmail);
        requireManagerOrAdmin(user);
        Project project = findProject(id);
        if (request.getName() != null) {
            String name = request.getName().trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Name must not be blank");
            }
            project.setName(name);
        }
        if (request.getDescription() != null) {
            project.setDescription(request.getDescription());
        }
        return toResponse(projectRepository.save(project));
    }

    @Transactional
    public void deleteProject(UUID id, String authenticatedEmail) {
        User user = findUser(authenticatedEmail);
        requireManagerOrAdmin(user);
        projectRepository.delete(findProject(id));
    }

    private Project findProject(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException("Project not found: " + id));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase(java.util.Locale.ROOT))
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    private void requireManagerOrAdmin(User user) {
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.MANAGER) {
            throw new AccessDeniedException("Only administrators and managers may modify projects");
        }
    }

    private ProjectResponse toResponse(Project project) {
        return ProjectResponse.from(project);
    }
}