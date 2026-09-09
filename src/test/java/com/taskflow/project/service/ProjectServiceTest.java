package com.taskflow.project.service;

import com.taskflow.project.dto.CreateProjectRequest;
import com.taskflow.project.dto.UpdateProjectRequest;
import com.taskflow.project.exception.ProjectNotFoundException;
import com.taskflow.project.model.Project;
import com.taskflow.project.repository.ProjectRepository;
import com.taskflow.user.model.Role;
import com.taskflow.user.model.User;
import com.taskflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private ProjectService projectService;

    @Test
    void adminCanCreateProjectAndResponseContainsSafeCreatorFields() {
        User admin = user("admin@example.com", "Admin", Role.ADMIN);
        Project project = new Project("Project", "Description", admin);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(projectRepository.save(any(Project.class))).thenReturn(project);

        var response = projectService.createProject("admin@example.com",
                new CreateProjectRequest(" Project ", "Description"));

        assertEquals("Project", response.name());
        assertEquals("Admin", response.createdByName());
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void workerCannotCreateUpdateOrDeleteProject() {
        User worker = user("worker@example.com", "Worker", Role.WORKER);
        when(userRepository.findByEmail("worker@example.com")).thenReturn(Optional.of(worker));

        assertThrows(AccessDeniedException.class, () -> projectService.createProject(
                "worker@example.com", new CreateProjectRequest("Project", null)));
        assertThrows(AccessDeniedException.class, () -> projectService.updateProject(
                UUID.randomUUID(), "worker@example.com", new UpdateProjectRequest("Project", null)));
        assertThrows(AccessDeniedException.class, () -> projectService.deleteProject(
                UUID.randomUUID(), "worker@example.com"));
    }

    @Test
    void missingProjectThrowsProjectNotFoundException() {
        UUID id = UUID.randomUUID();
        when(projectRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ProjectNotFoundException.class, () -> projectService.findById(id));
    }

    @Test
    void managerCanUpdateAndDeleteProject() {
        User manager = user("manager@example.com", "Manager", Role.MANAGER);
        Project project = new Project("Old", "Old description", manager);
        UUID id = UUID.randomUUID();
        project.setId(id);
        when(userRepository.findByEmail("manager@example.com")).thenReturn(Optional.of(manager));
        when(projectRepository.findById(id)).thenReturn(Optional.of(project));
        when(projectRepository.save(project)).thenReturn(project);

        var response = projectService.updateProject(id, "manager@example.com",
                new UpdateProjectRequest(" New ", "New description"));
        assertEquals("New", response.name());
        projectService.deleteProject(id, "manager@example.com");
        verify(projectRepository).delete(project);
    }

    private User user(String email, String name, Role role) {
        User user = new User(name, email, "hash", role);
        user.setId(UUID.randomUUID());
        return user;
    }
}