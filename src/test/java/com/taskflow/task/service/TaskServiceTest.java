package com.taskflow.task.service;

import com.taskflow.activity.model.ActivityType;
import com.taskflow.activity.service.ActivityService;
import com.taskflow.notification.model.NotificationType;
import com.taskflow.notification.service.NotificationService;
import com.taskflow.project.model.Project;
import com.taskflow.project.repository.ProjectRepository;
import com.taskflow.task.dto.AssignTaskRequest;
import com.taskflow.task.dto.CreateTaskRequest;
import com.taskflow.task.dto.UpdateTaskRequest;
import com.taskflow.task.dto.UpdateTaskStatusRequest;
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
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;
    @Mock private ActivityService activityService;
    @Mock private NotificationService notificationService;
    @InjectMocks private TaskService taskService;

    @Test
    void adminCreatesTaskWithTodoAndCorrectProjectAndCreator() {
        User admin = user("admin@example.com", Role.ADMIN);
        Project project = project(admin);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task task = invocation.getArgument(0);
            task.setId(UUID.randomUUID());
            return task;
        });

        var response = taskService.createTask(project.getId(), "admin@example.com",
                new CreateTaskRequest(" Task ", "Description", TaskPriority.HIGH));

        assertEquals("Task", response.title());
        assertEquals(TaskStatus.TODO, response.status());
        assertEquals(project.getId(), response.projectId());
        assertEquals(admin.getId(), response.createdById());
        verify(taskRepository).save(any(Task.class));
        verify(activityService).record(response.id(), admin, ActivityType.TASK_CREATED, "Task created");
    }

    @Test
    void workerCannotCreateUpdateOrDeleteTask() {
        User worker = user("worker@example.com", Role.WORKER);
        when(userRepository.findByEmail("worker@example.com")).thenReturn(Optional.of(worker));

        assertThrows(AccessDeniedException.class, () -> taskService.createTask(
                UUID.randomUUID(), "worker@example.com", new CreateTaskRequest("Task", null, TaskPriority.LOW)));
        assertThrows(AccessDeniedException.class, () -> taskService.updateTask(
                UUID.randomUUID(), "worker@example.com", new UpdateTaskRequest("Task", null, null, null)));
        assertThrows(AccessDeniedException.class, () -> taskService.assignTask(
                UUID.randomUUID(), new AssignTaskRequest(UUID.randomUUID()), "worker@example.com"));
        assertThrows(AccessDeniedException.class, () -> taskService.deleteTask(
                UUID.randomUUID(), "worker@example.com"));
        verify(activityService, never()).record(any(), any(), any(), any());
        verify(notificationService, never()).createNotification(any(), any(), any(), any());
    }

    @Test
    void managerCanUpdateAndDeleteTask() {
        User manager = user("manager@example.com", Role.MANAGER);
        User worker = user("worker@example.com", Role.WORKER);
        Project project = project(manager);
        Task task = new Task("Old", "Old description", TaskStatus.TODO, TaskPriority.LOW, project, manager);
        task.setAssignee(worker);
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
        verify(activityService).record(taskId, manager, ActivityType.TASK_UPDATED, "Task updated");
        verify(activityService).record(taskId, manager, ActivityType.TASK_STATUS_CHANGED, "Task status changed");
        verify(notificationService).createNotification(worker, taskId, NotificationType.TASK_STATUS_CHANGED,
                "Task 'New' changed status to IN_PROGRESS.");

        taskService.deleteTask(taskId, "manager@example.com");
        InOrder inOrder = inOrder(activityService, taskRepository);
        inOrder.verify(activityService).record(taskId, manager, ActivityType.TASK_DELETED, "Task deleted");
        inOrder.verify(taskRepository).delete(task);
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

        @Test
        void adminAndManagerCanAssignWorkers() {
        User admin = user("admin@example.com", Role.ADMIN);
        User manager = user("manager@example.com", Role.MANAGER);
        User worker = user("worker@example.com", Role.WORKER);
        Task adminTask = task(admin);
        Task managerTask = task(manager);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(userRepository.findByEmail("manager@example.com")).thenReturn(Optional.of(manager));
        when(userRepository.findById(worker.getId())).thenReturn(Optional.of(worker));
        when(taskRepository.findById(adminTask.getId())).thenReturn(Optional.of(adminTask));
        when(taskRepository.findById(managerTask.getId())).thenReturn(Optional.of(managerTask));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(worker.getId(), taskService.assignTask(adminTask.getId(),
            new AssignTaskRequest(worker.getId()), "admin@example.com").assigneeId());
        assertEquals(worker.getId(), taskService.assignTask(managerTask.getId(),
            new AssignTaskRequest(worker.getId()), "manager@example.com").assigneeId());

        verify(activityService).record(adminTask.getId(), admin, ActivityType.TASK_ASSIGNED, "Task assigned");
        verify(activityService).record(managerTask.getId(), manager, ActivityType.TASK_ASSIGNED, "Task assigned");
        verify(notificationService).createNotification(worker, adminTask.getId(), NotificationType.TASK_ASSIGNED,
                "You have been assigned a task: Task");
        verify(notificationService).createNotification(worker, managerTask.getId(), NotificationType.TASK_ASSIGNED,
                "You have been assigned a task: Task");
        }

        @Test
        void assigningTaskToCurrentWorkerDoesNotCreateDuplicateNotification() {
        User manager = user("manager@example.com", Role.MANAGER);
        User worker = user("worker@example.com", Role.WORKER);
        Task task = task(manager);
        task.setAssignee(worker);
        when(userRepository.findByEmail("manager@example.com")).thenReturn(Optional.of(manager));
        when(userRepository.findById(worker.getId())).thenReturn(Optional.of(worker));
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        taskService.assignTask(task.getId(), new AssignTaskRequest(worker.getId()), "manager@example.com");

        verify(notificationService, never()).createNotification(any(), any(), any(), any());
        }

        @Test
        void assignmentRejectsMissingTaskMissingUserAndNonWorkers() {
        User admin = user("admin@example.com", Role.ADMIN);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());
        assertThrows(TaskNotFoundException.class, () -> taskService.assignTask(taskId,
            new AssignTaskRequest(userId), "admin@example.com"));

        Task task = task(admin);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> taskService.assignTask(task.getId(),
            new AssignTaskRequest(userId), "admin@example.com"));

        User manager = user("manager-target@example.com", Role.MANAGER);
        when(userRepository.findById(userId)).thenReturn(Optional.of(manager));
        assertThrows(IllegalArgumentException.class, () -> taskService.assignTask(task.getId(),
            new AssignTaskRequest(userId), "admin@example.com"));
        verify(notificationService, never()).createNotification(any(), any(), any(), any());
        }

        @Test
        void assignedWorkerCanAdvanceStatusOnlyThroughValidTransitions() {
        User worker = user("worker@example.com", Role.WORKER);
        Task task = task(worker);
        task.setAssignee(worker);
        when(userRepository.findByEmail("worker@example.com")).thenReturn(Optional.of(worker));
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        taskService.updateTaskStatus(task.getId(), "worker@example.com",
            new UpdateTaskStatusRequest(TaskStatus.IN_PROGRESS));
        assertEquals(TaskStatus.IN_PROGRESS, task.getStatus());
        taskService.updateTaskStatus(task.getId(), "worker@example.com",
            new UpdateTaskStatusRequest(TaskStatus.DONE));
        assertEquals(TaskStatus.DONE, task.getStatus());

        verify(activityService, times(2)).record(task.getId(), worker, ActivityType.TASK_STATUS_CHANGED, "Task status changed");
        verify(notificationService, never()).createNotification(any(), any(), any(), any());
        }

        @Test
        void validStatusChangeNotifiesTaskCreatorWhenCreatorIsNotActor() {
        User creator = user("manager@example.com", Role.MANAGER);
        User worker = user("worker@example.com", Role.WORKER);
        Task task = task(creator);
        task.setAssignee(worker);
        when(userRepository.findByEmail("worker@example.com")).thenReturn(Optional.of(worker));
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        taskService.updateTaskStatus(task.getId(), "worker@example.com",
                new UpdateTaskStatusRequest(TaskStatus.IN_PROGRESS));

        verify(notificationService).createNotification(creator, task.getId(), NotificationType.TASK_STATUS_CHANGED,
                "Task 'Task' changed status to IN_PROGRESS.");
        }

        @Test
        void workerStatusWorkflowRejectsUnassignedDifferentAndInvalidTransitions() {
        User worker = user("worker@example.com", Role.WORKER);
        User otherWorker = user("other@example.com", Role.WORKER);
        Task unassigned = task(worker);
        Task otherTask = task(worker);
        otherTask.setAssignee(otherWorker);
        Task done = task(worker);
        done.setAssignee(worker);
        done.setStatus(TaskStatus.DONE);
        when(userRepository.findByEmail("worker@example.com")).thenReturn(Optional.of(worker));
        when(taskRepository.findById(unassigned.getId())).thenReturn(Optional.of(unassigned));
        when(taskRepository.findById(otherTask.getId())).thenReturn(Optional.of(otherTask));
        when(taskRepository.findById(done.getId())).thenReturn(Optional.of(done));

        assertThrows(AccessDeniedException.class, () -> taskService.updateTaskStatus(unassigned.getId(),
            "worker@example.com", new UpdateTaskStatusRequest(TaskStatus.IN_PROGRESS)));
        assertThrows(AccessDeniedException.class, () -> taskService.updateTaskStatus(otherTask.getId(),
            "worker@example.com", new UpdateTaskStatusRequest(TaskStatus.IN_PROGRESS)));
        assertThrows(IllegalArgumentException.class, () -> taskService.updateTaskStatus(done.getId(),
            "worker@example.com", new UpdateTaskStatusRequest(TaskStatus.TODO)));
        verify(notificationService, never()).createNotification(any(), any(), any(), any());
        }

        @Test
        void workerCannotSkipOrReverseStatusTransitions() {
        User worker = user("worker@example.com", Role.WORKER);
        Task todo = task(worker);
        todo.setAssignee(worker);
        Task inProgress = task(worker);
        inProgress.setAssignee(worker);
        inProgress.setStatus(TaskStatus.IN_PROGRESS);
        when(userRepository.findByEmail("worker@example.com")).thenReturn(Optional.of(worker));
        when(taskRepository.findById(todo.getId())).thenReturn(Optional.of(todo));
        when(taskRepository.findById(inProgress.getId())).thenReturn(Optional.of(inProgress));

        assertThrows(IllegalArgumentException.class, () -> taskService.updateTaskStatus(todo.getId(),
            "worker@example.com", new UpdateTaskStatusRequest(TaskStatus.DONE)));
        assertThrows(IllegalArgumentException.class, () -> taskService.updateTaskStatus(inProgress.getId(),
            "worker@example.com", new UpdateTaskStatusRequest(TaskStatus.TODO)));
        verify(notificationService, never()).createNotification(any(), any(), any(), any());
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

    private Task task(User creator) {
        Task task = new Task("Task", "Description", TaskStatus.TODO, TaskPriority.MEDIUM,
                project(creator), creator);
        task.setId(UUID.randomUUID());
        return task;
    }
}
