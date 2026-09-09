package com.taskflow.task.service;

import com.taskflow.project.model.Project;
import com.taskflow.project.repository.ProjectRepository;
import com.taskflow.task.dto.CreateTaskRequest;
import com.taskflow.task.dto.UpdateTaskRequest;
import com.taskflow.task.exception.TaskNotFoundException;
import com.taskflow.task.model.Task;
import com.taskflow.task.model.TaskPriority;
import com.taskflow.task.model.TaskStatus;
import com.taskflow.task.repository.TaskRepository;
import com.taskflow.user.model.Role;
import com.taskflow.user.model.User;
import com.taskflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private TaskService taskService;

    @Test
    void adminCreatesTaskWithTodoAndCorrectProjectAndCreator() {
        User admin = user("admin@example.com", Role.ADMIN);
        Project project = project(admin);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = taskService.createTask(project.getId(), "admin@example.com",
                new CreateTaskRequest(" Task ", "Description", TaskPriority.HIGH));

        assertEquals("Task", response.title());
        assertEquals(TaskStatus.TODO, response.status());
        assertEquals(project.getId(), response.projectId());
        assertEquals(admin.getId(), response.createdById());
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void workerCannotCreateUpdateOrDeleteTask() {
        User worker = user("worker@example.com", Role.WORKER);
        when(userRepository.findByEmail("worker@example.com")).thenReturn(Optional.of(worker));

        assertThrows(AccessDeniedException.class, () -> taskService.createTask(
                UUID.randomUUID(), "worker@example.com", new CreateTaskRequest("Task", null, TaskPriority.LOW)));
        assertThrows(AccessDeniedException.class, () -> taskService.updateTask(
                UUID.randomUUID(), "worker@example.com", new UpdateTaskRequest("Task", null, null, null)));
        assertThrows(AccessDeniedException.class, () -> taskService.deleteTask(
                UUID.randomUUID(), "worker@example.com"));
    }

    @Test
    void managerCanUpdateAndDeleteTask() {
        User manager = user("manager@example.com", Role.MANAGER);
        Project project = project(manager);
        Task task = new Task("Old", "Old description", TaskStatus.TODO, TaskPriority.LOW, project, manager);
        UUID taskId = UUID.randomUUID();
        task.setId(taskId);
        when(userRepository.findByEmail("manager@example.com")).thenReturn(Optional.of(manager));
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        var response = taskService.updateTask(taskId, "manager@example.com",
                new UpdateTaskRequest(" New ", "New description", TaskStatus.IN_PROGRESS, TaskPriority.HIGH));

        assertEquals("New", response.title());
        assertEquals(TaskStatus.IN_PROGRESS, response.status());
        assertEquals(TaskPriority.HIGH, response.priority());
        taskService.deleteTask(taskId, "manager@example.com");
        verify(taskRepository).delete(task);
    }

    @Test
    void missingProjectAndTaskAreRejected() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());
        User admin = user("admin@example.com", Role.ADMIN);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        assertThrows(RuntimeException.class, () -> taskService.createTask(
                projectId, "admin@example.com", new CreateTaskRequest("Task", null, TaskPriority.LOW)));

        UUID taskId = UUID.randomUUID();
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());
        assertThrows(TaskNotFoundException.class, () -> taskService.findById(taskId));
    }

    @Test
    void listingVerifiesProjectAndReturnsOnlyProjectTasks() {
        UUID projectId = UUID.randomUUID();
        User admin = user("admin@example.com", Role.ADMIN);
        Project project = project(admin);
        project.setId(projectId);
        Task task = new Task("Task", null, TaskStatus.TODO, TaskPriority.MEDIUM, project, admin);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(taskRepository.findByProjectId(projectId)).thenReturn(List.of(task));

        var response = taskService.findAllByProject(projectId);

        assertEquals(1, response.size());
        assertEquals(projectId, response.get(0).projectId());
        verify(taskRepository).findByProjectId(projectId);
    }

    private User user(String email, Role role) {
        User user = new User("User", email, "hash", role);
        user.setId(UUID.randomUUID());
        return user;
    }

    private Project project(User user) {
        Project project = new Project("Project", "Description", user);
        project.setId(UUID.randomUUID());
        return project;
    }
}
