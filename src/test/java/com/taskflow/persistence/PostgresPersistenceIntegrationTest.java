package com.taskflow.persistence;

import com.taskflow.activity.model.ActivityType;
import com.taskflow.activity.repository.ActivityRepository;
import com.taskflow.activity.service.ActivityService;
import com.taskflow.comment.repository.CommentRepository;
import com.taskflow.notification.repository.NotificationRepository;
import com.taskflow.notification.model.NotificationType;
import com.taskflow.notification.service.NotificationService;
import com.taskflow.project.model.Project;
import com.taskflow.project.repository.ProjectRepository;
import com.taskflow.task.dto.AssignTaskRequest;
import com.taskflow.task.dto.CreateTaskRequest;
import com.taskflow.task.dto.UpdateTaskStatusRequest;
import com.taskflow.task.model.Task;
import com.taskflow.task.model.TaskPriority;
import com.taskflow.task.model.TaskStatus;
import com.taskflow.task.repository.TaskRepository;
import com.taskflow.task.service.TaskService;
import com.taskflow.user.model.Role;
import com.taskflow.user.model.User;
import com.taskflow.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@EnabledIfSystemProperty(named = "taskflow.integration", matches = "true")
class PostgresPersistenceIntegrationTest {

    @Autowired private TaskService taskService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private NotificationRepository notificationRepository;
    @MockitoBean private ActivityService activityService;
    @MockitoBean private NotificationService notificationService;

    private User actor;
    private User worker;
    private Project project;

    @BeforeEach
    void createFixtures() {
        String email = "step14-" + UUID.randomUUID() + "@example.test";
        actor = userRepository.saveAndFlush(new User("Step 14", email, "hash", Role.ADMIN));
        worker = userRepository.saveAndFlush(new User("Step 14 worker",
                "worker-" + email, "hash", Role.WORKER));
        project = projectRepository.saveAndFlush(new Project("Step 14", null, actor));
    }

    @AfterEach
    void removeFixtures() {
        if (project != null) {
            for (Task task : taskRepository.findByProjectId(project.getId())) {
                commentRepository.findByTaskIdOrderByCreatedAtAsc(task.getId())
                        .forEach(commentRepository::delete);
                activityRepository.findByTaskIdOrderByCreatedAtAsc(task.getId())
                        .forEach(activityRepository::delete);
                taskRepository.delete(task);
            }
        }
        if (actor != null) {
            notificationRepository.findByRecipient_IdOrderByCreatedAtDesc(actor.getId())
                    .forEach(notificationRepository::delete);
        }
        if (worker != null) {
            notificationRepository.findByRecipient_IdOrderByCreatedAtDesc(worker.getId())
                    .forEach(notificationRepository::delete);
        }
        if (project != null) {
            projectRepository.deleteById(project.getId());
        }
        if (actor != null) {
            userRepository.deleteById(actor.getId());
        }
        if (worker != null) {
            userRepository.deleteById(worker.getId());
        }
    }

    @Test
    void taskWriteIsRolledBackWhenActivityRecordingFails() {
        doAnswer(invocation -> {
            taskRepository.flush();
            throw new IllegalStateException("Activity persistence failed");
        }).when(activityService).record(any(UUID.class), any(User.class),
                eq(ActivityType.TASK_CREATED), anyString());

        assertThrows(IllegalStateException.class, () -> taskService.createTask(
                project.getId(), actor.getEmail(),
                new CreateTaskRequest("Transactional task", null, TaskPriority.MEDIUM)));

        assertTrue(taskRepository.findByProjectId(project.getId()).isEmpty());
    }

    @Test
    void databaseRejectsDuplicateUserEmail() {
        User duplicate = new User("Duplicate", actor.getEmail(), "hash", Role.WORKER);

        assertThrows(DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(duplicate));

        assertEquals(actor.getId(),
                userRepository.findByEmail(actor.getEmail()).orElseThrow().getId());
    }

    @Test
    void assignmentIsRolledBackWhenNotificationRecordingFails() {
        Task task = createTask("Assignment rollback", TaskStatus.TODO);
        doAnswer(invocation -> {
            taskRepository.flush();
            throw new IllegalStateException("Notification persistence failed");
        }).when(notificationService).createNotification(eq(worker), eq(task.getId()),
                eq(NotificationType.TASK_ASSIGNED), anyString());

        assertThrows(IllegalStateException.class, () -> taskService.assignTask(task.getId(),
                new AssignTaskRequest(worker.getId()), actor.getEmail()));

        assertTrue(taskRepository.findById(task.getId()).orElseThrow().getAssignee() == null);
    }

    @Test
    void statusChangeIsRolledBackWhenNotificationRecordingFails() {
        Task task = new Task("Status rollback", null, TaskStatus.IN_PROGRESS,
                TaskPriority.MEDIUM, project, actor);
        task.setAssignee(worker);
        task = taskRepository.saveAndFlush(task);
        UUID taskId = task.getId();
        doAnswer(invocation -> {
            taskRepository.flush();
            throw new IllegalStateException("Notification persistence failed");
        }).when(notificationService).createNotification(any(User.class), eq(taskId),
                eq(NotificationType.TASK_STATUS_CHANGED), anyString());

        assertThrows(IllegalStateException.class, () -> taskService.updateTaskStatus(taskId,
                worker.getEmail(), new UpdateTaskStatusRequest(TaskStatus.DONE)));

        assertEquals(TaskStatus.IN_PROGRESS, taskRepository.findById(taskId).orElseThrow().getStatus());
    }

    private Task createTask(String title, TaskStatus status) {
        Task task = taskRepository.saveAndFlush(new Task(title, null, status,
                TaskPriority.MEDIUM, project, actor));
        return task;
    }
}
