package com.taskflow.persistence;

import com.taskflow.activity.model.Activity;
import com.taskflow.activity.model.ActivityType;
import com.taskflow.activity.repository.ActivityRepository;
import com.taskflow.comment.model.Comment;
import com.taskflow.comment.repository.CommentRepository;
import com.taskflow.notification.repository.NotificationRepository;
import com.taskflow.project.model.Project;
import com.taskflow.project.repository.ProjectRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@EnabledIfSystemProperty(named = "taskflow.integration", matches = "true")
class PostgresTaskDeletionIntegrationTest {

    @Autowired private TaskService taskService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private NotificationRepository notificationRepository;

    private final List<UUID> taskIds = new ArrayList<>();
    private User actor;
    private Project project;

    @BeforeEach
    void createFixtures() {
        String email = "step14-delete-" + UUID.randomUUID() + "@example.test";
        actor = userRepository.saveAndFlush(new User("Step 14", email, "hash", Role.ADMIN));
        project = projectRepository.saveAndFlush(new Project("Step 14", null, actor));
    }

    @AfterEach
    void removeFixtures() {
        for (UUID taskId : taskIds) {
            commentRepository.findByTaskIdOrderByCreatedAtAsc(taskId)
                    .forEach(commentRepository::delete);
            activityRepository.findByTaskIdOrderByCreatedAtAsc(taskId)
                    .forEach(activityRepository::delete);
            taskRepository.findById(taskId).ifPresent(taskRepository::delete);
        }
        if (actor != null) {
            notificationRepository.findByRecipient_IdOrderByCreatedAtDesc(actor.getId())
                    .forEach(notificationRepository::delete);
        }
        if (project != null) {
            projectRepository.deleteById(project.getId());
        }
        if (actor != null) {
            userRepository.deleteById(actor.getId());
        }
    }

    @Test
    void commentsRestrictTaskDeletionWithoutRemovingRelatedRecords() {
        Task task = createTask();
        Comment comment = commentRepository.saveAndFlush(new Comment("Retain this comment", task, actor));
        Activity priorActivity = activityRepository.saveAndFlush(new Activity(task.getId(), actor.getId(),
                actor.getName(), ActivityType.TASK_CREATED, "Task created"));

        assertThrows(DataIntegrityViolationException.class,
                () -> taskService.deleteTask(task.getId(), actor.getEmail()));

        assertTrue(taskRepository.existsById(task.getId()));
        assertTrue(commentRepository.existsById(comment.getId()));
        assertEquals(List.of(priorActivity.getId()), activityRepository
                .findByTaskIdOrderByCreatedAtAsc(task.getId()).stream().map(Activity::getId).toList());
    }

    @Test
    void deletingTaskWithoutCommentsPreservesHistoryAndRecordsDeletion() {
        Task task = createTask();
        Activity priorActivity = activityRepository.saveAndFlush(new Activity(task.getId(), actor.getId(),
                actor.getName(), ActivityType.TASK_CREATED, "Task created"));

        taskService.deleteTask(task.getId(), actor.getEmail());

        assertFalse(taskRepository.existsById(task.getId()));
        assertEquals(List.of(ActivityType.TASK_CREATED, ActivityType.TASK_DELETED),
                activityRepository.findByTaskIdOrderByCreatedAtAsc(task.getId())
                        .stream().map(Activity::getType).toList());
        assertTrue(activityRepository.existsById(priorActivity.getId()));
    }

    private Task createTask() {
        Task task = taskRepository.saveAndFlush(new Task("Step 14 task", null, TaskStatus.TODO,
                TaskPriority.MEDIUM, project, actor));
        taskIds.add(task.getId());
        return task;
    }
}
