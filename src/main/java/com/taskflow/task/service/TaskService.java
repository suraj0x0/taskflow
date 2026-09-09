package com.taskflow.task.service;

import com.taskflow.project.exception.ProjectNotFoundException;
import com.taskflow.project.model.Project;
import com.taskflow.project.repository.ProjectRepository;
import com.taskflow.task.dto.CreateTaskRequest;
import com.taskflow.task.dto.TaskResponse;
import com.taskflow.task.dto.UpdateTaskRequest;
import com.taskflow.task.exception.TaskNotFoundException;
import com.taskflow.task.model.Task;
import com.taskflow.task.model.TaskStatus;
import com.taskflow.task.repository.TaskRepository;
import com.taskflow.user.exception.UserNotFoundException;
import com.taskflow.user.model.Role;
import com.taskflow.user.model.User;
import com.taskflow.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository,
                       UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TaskResponse createTask(UUID projectId, String authenticatedEmail, CreateTaskRequest request) {
        User user = findUser(authenticatedEmail);
        requireManagerOrAdmin(user);
        Project project = findProject(projectId);
        Task task = new Task(request.getTitle().trim(), request.getDescription(), TaskStatus.TODO,
                request.getPriority(), project, user);
        return toResponse(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> findAllByProject(UUID projectId) {
        findProject(projectId);
        return taskRepository.findByProjectId(projectId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse findById(UUID id) {
        return toResponse(findTask(id));
    }

    @Transactional
    public TaskResponse updateTask(UUID id, String authenticatedEmail, UpdateTaskRequest request) {
        requireManagerOrAdmin(findUser(authenticatedEmail));
        Task task = findTask(id);
        if (request.getTitle() != null) {
            String title = request.getTitle().trim();
            if (title.isEmpty()) {
                throw new IllegalArgumentException("Title must not be blank");
            }
            task.setTitle(title);
        }
        if (request.getDescription() != null) task.setDescription(request.getDescription());
        if (request.getStatus() != null) task.setStatus(request.getStatus());
        if (request.getPriority() != null) task.setPriority(request.getPriority());
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public void deleteTask(UUID id, String authenticatedEmail) {
        requireManagerOrAdmin(findUser(authenticatedEmail));
        taskRepository.delete(findTask(id));
    }

    private Task findTask(UUID id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task not found: " + id));
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
            throw new AccessDeniedException("Only administrators and managers may modify tasks");
        }
    }

    private TaskResponse toResponse(Task task) {
        return TaskResponse.from(task);
    }
}