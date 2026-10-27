package com.taskflow.task.service;

import com.taskflow.activity.model.ActivityType;
import com.taskflow.activity.service.ActivityService;
import com.taskflow.notification.model.NotificationType;
import com.taskflow.notification.service.NotificationService;
import com.taskflow.project.exception.ProjectNotFoundException;
import com.taskflow.project.model.Project;
import com.taskflow.project.repository.ProjectRepository;
import com.taskflow.task.dto.CreateTaskRequest;
import com.taskflow.task.dto.AssignTaskRequest;
import com.taskflow.task.dto.TaskResponse;
import com.taskflow.task.dto.UpdateTaskStatusRequest;
import com.taskflow.task.dto.UpdateTaskRequest;
import com.taskflow.task.exception.TaskNotFoundException;
import com.taskflow.task.model.Task;
import com.taskflow.task.model.TaskPriority;
import com.taskflow.task.model.TaskStatus;
import com.taskflow.task.repository.TaskRepository;
import com.taskflow.task.repository.TaskSpecifications;
import com.taskflow.user.exception.UserNotFoundException;
import com.taskflow.user.model.Role;
import com.taskflow.user.model.User;
import com.taskflow.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ActivityService activityService;
    private final NotificationService notificationService;

    public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository,
                       UserRepository userRepository, ActivityService activityService,
                       NotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.activityService = activityService;
        this.notificationService = notificationService;
    }

    @Transactional
    public TaskResponse createTask(UUID projectId, String authenticatedEmail, CreateTaskRequest request) {
        User user = findUser(authenticatedEmail);
        requireManagerOrAdmin(user);
        Project project = findProject(projectId);
        Task task = new Task(request.getTitle().trim(), request.getDescription(), TaskStatus.TODO,
                request.getPriority(), project, user);
        Task saved = taskRepository.save(task);
        activityService.record(saved.getId(), user, ActivityType.TASK_CREATED, "Task created");
        return toResponse(saved);
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

    @Transactional(readOnly = true)
    public Page<TaskResponse> searchTasks(String query, UUID projectId, TaskStatus status,
                                          TaskPriority priority, UUID assigneeId, Pageable pageable) {
        Specification<Task> specification = Specification.allOf(
                TaskSpecifications.textContains(query),
                TaskSpecifications.belongsToProject(projectId),
                TaskSpecifications.hasStatus(status),
                TaskSpecifications.hasPriority(priority),
                TaskSpecifications.assignedTo(assigneeId)
        );
        return taskRepository.findAll(specification, pageable).map(this::toResponse);
    }

    @Transactional
    public TaskResponse updateTask(UUID id, String authenticatedEmail, UpdateTaskRequest request) {
        User user = findUser(authenticatedEmail);
        requireManagerOrAdmin(user);
        Task task = findTask(id);
        TaskStatus previousStatus = task.getStatus();
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
        Task saved = taskRepository.save(task);
        activityService.record(saved.getId(), user, ActivityType.TASK_UPDATED, "Task updated");
        if (request.getStatus() != null && request.getStatus() != previousStatus) {
            activityService.record(saved.getId(), user, ActivityType.TASK_STATUS_CHANGED, "Task status changed");
            if (saved.getAssignee() != null) {
                notificationService.createNotification(
                        saved.getAssignee(),
                        saved.getId(),
                        NotificationType.TASK_STATUS_CHANGED,
                        "Task '" + saved.getTitle() + "' changed status to " + saved.getStatus() + "."
                );
            }
        }
        return toResponse(saved);
    }

    @Transactional
    public TaskResponse assignTask(UUID id, AssignTaskRequest request, String authenticatedEmail) {
        User user = findUser(authenticatedEmail);
        requireManagerOrAdmin(user);
        Task task = findTask(id);
        User worker = userRepository.findById(request.getWorkerId())
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        if (worker.getRole() != Role.WORKER) {
            throw new IllegalArgumentException("Tasks may only be assigned to workers");
        }
        User previousAssignee = task.getAssignee();
        task.setAssignee(worker);
        Task saved = taskRepository.save(task);
        activityService.record(saved.getId(), user, ActivityType.TASK_ASSIGNED, "Task assigned");
        if (previousAssignee == null || !previousAssignee.getId().equals(worker.getId())) {
            notificationService.createNotification(
                    worker,
                    saved.getId(),
                    NotificationType.TASK_ASSIGNED,
                    "You have been assigned a task: " + saved.getTitle()
            );
        }
        return toResponse(saved);
    }

    @Transactional
    public TaskResponse updateTaskStatus(UUID id, String authenticatedEmail, UpdateTaskStatusRequest request) {
        User worker = findUser(authenticatedEmail);
        if (worker.getRole() != Role.WORKER) {
            throw new AccessDeniedException("Only assigned workers may use the task status workflow");
        }
        Task task = findTask(id);
        if (task.getAssignee() == null || !task.getAssignee().getId().equals(worker.getId())) {
            throw new AccessDeniedException("Only the assigned worker may update task status");
        }
        if (!isValidWorkerTransition(task.getStatus(), request.getStatus())) {
            throw new IllegalArgumentException("Invalid task status transition");
        }
        task.setStatus(request.getStatus());
        Task saved = taskRepository.save(task);
        activityService.record(saved.getId(), worker, ActivityType.TASK_STATUS_CHANGED, "Task status changed");
        User creator = task.getCreatedBy();
        if (!creator.getId().equals(worker.getId())) {
            notificationService.createNotification(
                    creator,
                    saved.getId(),
                    NotificationType.TASK_STATUS_CHANGED,
                    "Task '" + saved.getTitle() + "' changed status to " + saved.getStatus() + "."
            );
        }
        return toResponse(saved);
    }

    @Transactional
    public void deleteTask(UUID id, String authenticatedEmail) {
        User user = findUser(authenticatedEmail);
        requireManagerOrAdmin(user);
        Task task = findTask(id);
        activityService.record(task.getId(), user, ActivityType.TASK_DELETED, "Task deleted");
        taskRepository.delete(task);
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

    private boolean isValidWorkerTransition(TaskStatus current, TaskStatus requested) {
        return (current == TaskStatus.TODO && requested == TaskStatus.IN_PROGRESS)
                || (current == TaskStatus.IN_PROGRESS && requested == TaskStatus.DONE);
    }

    private TaskResponse toResponse(Task task) {
        return TaskResponse.from(task);
    }
}