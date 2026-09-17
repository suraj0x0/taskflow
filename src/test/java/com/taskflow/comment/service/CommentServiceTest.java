package com.taskflow.comment.service;

import com.taskflow.comment.dto.CommentResponse;
import com.taskflow.comment.dto.CreateCommentRequest;
import com.taskflow.comment.model.Comment;
import com.taskflow.comment.repository.CommentRepository;
import com.taskflow.project.model.Project;
import com.taskflow.task.exception.TaskNotFoundException;
import com.taskflow.task.model.Task;
import com.taskflow.task.model.TaskPriority;
import com.taskflow.task.model.TaskStatus;
import com.taskflow.task.repository.TaskRepository;
import com.taskflow.user.exception.UserNotFoundException;
import com.taskflow.user.model.Role;
import com.taskflow.user.model.User;
import com.taskflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private CommentService commentService;

    @Test
    void authenticatedUserCanCreateCommentForTask() {
        User author = user("author@example.com", "Author");
        Task task = task(author);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(userRepository.findByEmail("author@example.com")).thenReturn(Optional.of(author));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0);
            comment.setId(UUID.randomUUID());
            return comment;
        });

        CommentResponse response = commentService.createComment(task.getId(),
                new CreateCommentRequest("  Ready for review.  "), " AUTHOR@EXAMPLE.COM ");

        assertEquals("Ready for review.", response.content());
        assertEquals(task.getId(), response.taskId());
        assertEquals(author.getId(), response.authorId());
        assertEquals("Author", response.authorName());
        assertEquals("author@example.com", response.authorEmail());

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(captor.capture());
        assertEquals(task, captor.getValue().getTask());
        assertEquals(author, captor.getValue().getAuthor());
    }

    @Test
    void commentsAreReturnedOldestFirstInRepositoryOrder() {
        User author = user("author@example.com", "Author");
        Task task = task(author);
        Comment first = comment("First", task, author, Instant.parse("2026-01-01T00:00:00Z"));
        Comment second = comment("Second", task, author, Instant.parse("2026-01-02T00:00:00Z"));
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(commentRepository.findByTaskIdOrderByCreatedAtAsc(task.getId())).thenReturn(List.of(first, second));

        List<CommentResponse> responses = commentService.getCommentsByTask(task.getId());

        assertEquals(List.of("First", "Second"), responses.stream().map(CommentResponse::content).toList());
    }

    @Test
    void missingTaskRejectsCreationAndRetrieval() {
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThrows(TaskNotFoundException.class, () -> commentService.createComment(
                taskId, new CreateCommentRequest("Comment"), "author@example.com"));
        assertThrows(TaskNotFoundException.class, () -> commentService.getCommentsByTask(taskId));
    }

    @Test
    void authenticatedUserNotFoundRejectsCommentCreation() {
        User author = user("author@example.com", "Author");
        Task task = task(author);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> commentService.createComment(
                task.getId(), new CreateCommentRequest("Comment"), "missing@example.com"));
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void createCommentTrimsContentBeforeSaving() {
        User author = user("author@example.com", "Author");
        Task task = task(author);
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(userRepository.findByEmail("author@example.com")).thenReturn(Optional.of(author));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        commentService.createComment(task.getId(), new CreateCommentRequest("  Trimmed content  "),
                "author@example.com");

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(captor.capture());
        assertEquals("Trimmed content", captor.getValue().getContent());
    }

    @Test
    void getCommentsChecksTaskExistsBeforeQueryingComments() {
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThrows(TaskNotFoundException.class, () -> commentService.getCommentsByTask(taskId));
        verify(commentRepository, never()).findByTaskIdOrderByCreatedAtAsc(taskId);
    }

    @Test
    void commentResponseContainsSafeTaskAndAuthorMetadata() {
        User author = user("author@example.com", "Author");
        Task task = task(author);
        Comment comment = comment("Comment", task, author, Instant.now());
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(commentRepository.findByTaskIdOrderByCreatedAtAsc(task.getId())).thenReturn(List.of(comment));

        CommentResponse response = commentService.getCommentsByTask(task.getId()).get(0);

        assertEquals(task.getId(), response.taskId());
        assertEquals(author.getId(), response.authorId());
        assertEquals("Author", response.authorName());
        assertEquals("author@example.com", response.authorEmail());
    }

    private User user(String email, String name) {
        User user = new User(name, email, "hash", Role.WORKER);
        user.setId(UUID.randomUUID());
        return user;
    }

    private Task task(User creator) {
        Project project = new Project("Project", "Description", creator);
        project.setId(UUID.randomUUID());
        Task task = new Task("Task", "Description", TaskStatus.TODO, TaskPriority.MEDIUM, project, creator);
        task.setId(UUID.randomUUID());
        return task;
    }

    private Comment comment(String content, Task task, User author, Instant createdAt) {
        Comment comment = new Comment(content, task, author);
        comment.setId(UUID.randomUUID());
        comment.setCreatedAt(createdAt);
        comment.setUpdatedAt(createdAt);
        return comment;
    }
}
