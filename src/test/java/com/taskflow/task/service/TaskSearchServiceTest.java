package com.taskflow.task.service;

import com.taskflow.project.model.Project;
import com.taskflow.project.repository.ProjectRepository;
import com.taskflow.task.dto.TaskResponse;
import com.taskflow.task.model.Task;
import com.taskflow.task.model.TaskPriority;
import com.taskflow.task.model.TaskStatus;
import com.taskflow.task.repository.TaskRepository;
import com.taskflow.user.model.Role;
import com.taskflow.user.model.User;
import com.taskflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskSearchServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private TaskService taskService;

    @Test
    void searchMapsSafeResponsesAndPassesCombinedSpecificationAndPageable() {
        User creator = user("creator@example.com", "Creator", Role.MANAGER);
        Project project = project(creator);
        Task task = new Task("Login API", "Implement login flow", TaskStatus.IN_PROGRESS,
                TaskPriority.HIGH, project, creator);
        task.setId(UUID.randomUUID());
        UUID assigneeId = UUID.randomUUID();
        Page<Task> page = new PageImpl<>(List.of(task), PageRequest.of(1, 5,
                Sort.by(Sort.Direction.ASC, "title")), 12);
        when(taskRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Task>>any(),
            any(PageRequest.class))).thenReturn(page);

        Page<TaskResponse> result = taskService.searchTasks("LOGIN", project.getId(), TaskStatus.IN_PROGRESS,
                TaskPriority.HIGH, assigneeId, PageRequest.of(1, 5, Sort.by(Sort.Direction.ASC, "title")));

        assertEquals(1, result.getContent().size());
        assertEquals("Login API", result.getContent().get(0).title());
        assertEquals(1, result.getNumber());
        assertEquals(12, result.getTotalElements());
        ArgumentCaptor<PageRequest> pageableCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(taskRepository).findAll(org.mockito.ArgumentMatchers.<Specification<Task>>any(),
            pageableCaptor.capture());
        assertEquals(1, pageableCaptor.getValue().getPageNumber());
        assertEquals(5, pageableCaptor.getValue().getPageSize());
        assertEquals(Sort.Direction.ASC, pageableCaptor.getValue().getSort().getOrderFor("title").getDirection());
    }

    @Test
    void searchSupportsUnfilteredDefaultPageable() {
        User creator = user("creator@example.com", "Creator", Role.MANAGER);
        Project project = project(creator);
        Task task = new Task("Task", null, TaskStatus.TODO, TaskPriority.LOW, project, creator);
        task.setId(UUID.randomUUID());
        when(taskRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Task>>any(),
            any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(task), PageRequest.of(0, 10), 1));

        Page<TaskResponse> result = taskService.searchTasks(null, null, null, null, null,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertEquals(1, result.getContent().size());
        assertEquals(0, result.getNumber());
        assertEquals(10, result.getSize());
    }

    private User user(String email, String name, Role role) {
        User user = new User(name, email, "hash", role);
        user.setId(UUID.randomUUID());
        return user;
    }

    private Project project(User user) {
        Project project = new Project("Project", "Description", user);
        project.setId(UUID.randomUUID());
        return project;
    }
}
