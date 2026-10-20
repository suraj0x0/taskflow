package com.taskflow.activity.service;

import com.taskflow.activity.dto.ActivityResponse;
import com.taskflow.activity.model.Activity;
import com.taskflow.activity.model.ActivityType;
import com.taskflow.activity.repository.ActivityRepository;
import com.taskflow.task.exception.TaskNotFoundException;
import com.taskflow.task.repository.TaskRepository;
import com.taskflow.user.model.Role;
import com.taskflow.user.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock private ActivityRepository activityRepository;
    @Mock private TaskRepository taskRepository;
    @InjectMocks private ActivityService activityService;

    @Test
    void getByTaskReturnsActivitiesOrderedByCreatedAt() {
        UUID taskId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(taskRepository.existsById(taskId)).thenReturn(true);

        Activity first = new Activity(taskId, actorId, "Alice", ActivityType.TASK_CREATED, "Task created");
        first.setId(UUID.randomUUID());
        first.setCreatedAt(Instant.parse("2026-01-01T10:00:00Z"));

        Activity second = new Activity(taskId, actorId, "Alice", ActivityType.TASK_UPDATED, "Task updated");
        second.setId(UUID.randomUUID());
        second.setCreatedAt(Instant.parse("2026-01-01T11:00:00Z"));

        when(activityRepository.findByTaskIdOrderByCreatedAtAsc(taskId)).thenReturn(List.of(first, second));

        List<ActivityResponse> responses = activityService.getByTask(taskId);

        assertEquals(2, responses.size());
        assertEquals(first.getId(), responses.get(0).id());
        assertEquals("Task created", responses.get(0).description());
        assertEquals(ActivityType.TASK_CREATED, responses.get(0).type());
        assertEquals(second.getId(), responses.get(1).id());
        assertEquals("Task updated", responses.get(1).description());
        assertEquals(ActivityType.TASK_UPDATED, responses.get(1).type());
        verify(activityRepository).findByTaskIdOrderByCreatedAtAsc(taskId);
    }

    @Test
    void getByTaskThrowsTaskNotFoundExceptionWhenTaskDoesNotExist() {
        UUID taskId = UUID.randomUUID();
        when(taskRepository.existsById(taskId)).thenReturn(false);

        assertThrows(TaskNotFoundException.class, () -> activityService.getByTask(taskId));
        verify(activityRepository, never()).findByTaskIdOrderByCreatedAtAsc(any());
    }

    @Test
    void recordCreatesActivityWithExpectedAttributesAndSavesIt() {
        UUID taskId = UUID.randomUUID();
        User actor = new User("Alice Admin", "alice@example.com", "hash", Role.ADMIN);
        actor.setId(UUID.randomUUID());

        activityService.record(taskId, actor, ActivityType.TASK_CREATED, "Task created");

        ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
        verify(activityRepository).save(captor.capture());
        Activity saved = captor.getValue();
        assertEquals(taskId, saved.getTaskId());
        assertEquals(actor.getId(), saved.getActorId());
        assertEquals("Alice Admin", saved.getActorName());
        assertEquals(ActivityType.TASK_CREATED, saved.getType());
        assertEquals("Task created", saved.getDescription());
    }
}
