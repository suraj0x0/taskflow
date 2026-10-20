package com.taskflow.comment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.comment.dto.CommentResponse;
import com.taskflow.comment.dto.CreateCommentRequest;
import com.taskflow.comment.service.CommentService;
import com.taskflow.comment.dto.UpdateCommentRequest;
import com.taskflow.comment.exception.CommentNotFoundException;
import com.taskflow.security.JwtAuthenticationFilter;
import com.taskflow.security.JwtService;
import com.taskflow.security.config.SecurityConfig;
import com.taskflow.task.exception.TaskNotFoundException;
import com.taskflow.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommentController.class)
@AutoConfigureMockMvc
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class})
class CommentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private CommentService commentService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private com.taskflow.user.repository.UserRepository userRepository;

    @Test
    @WithMockUser(username = "author@example.com", roles = "WORKER")
    void authenticatedUserCanGetComments() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(commentService.getCommentsByTask(taskId)).thenReturn(List.of(response(taskId)));

        mockMvc.perform(get("/api/tasks/{taskId}/comments", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].taskId").value(taskId.toString()))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    @WithMockUser(username = "author@example.com", roles = "WORKER")
    void authenticatedUserCanPostComment() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(commentService.createComment(any(UUID.class), any(CreateCommentRequest.class), any(String.class)))
                .thenReturn(response(taskId));

        mockMvc.perform(post("/api/tasks/{taskId}/comments", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("Comment"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Comment"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void unauthenticatedUsersCannotReadOrCreateComments() throws Exception {
        UUID taskId = UUID.randomUUID();
        mockMvc.perform(get("/api/tasks/{taskId}/comments", taskId)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/tasks/{taskId}/comments", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("Comment"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "WORKER")
    void blankAndOversizedContentReturnBadRequest() throws Exception {
        UUID taskId = UUID.randomUUID();
        mockMvc.perform(post("/api/tasks/{taskId}/comments", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.content").exists());
        mockMvc.perform(post("/api/tasks/{taskId}/comments", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("x".repeat(2001)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.content").exists());
    }

    @Test
    @WithMockUser(roles = "WORKER")
    void missingTaskReturnsNotFound() throws Exception {
        UUID taskId = UUID.randomUUID();
        when(commentService.getCommentsByTask(taskId)).thenThrow(new TaskNotFoundException("Task not found"));
        when(commentService.createComment(any(UUID.class), any(CreateCommentRequest.class), any(String.class)))
                .thenThrow(new TaskNotFoundException("Task not found"));

        mockMvc.perform(get("/api/tasks/{taskId}/comments", taskId)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/tasks/{taskId}/comments", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCommentRequest("Comment"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "author@example.com", roles = "WORKER")
    void authenticatedUserCanUpdateComment() throws Exception {
        UUID commentId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        CommentResponse updated = new CommentResponse(commentId, "Updated text", taskId,
                UUID.randomUUID(), "Author", "author@example.com", Instant.now(), Instant.now());
        when(commentService.updateComment(eq(commentId), any(UpdateCommentRequest.class), any(String.class)))
                .thenReturn(updated);

        mockMvc.perform(patch("/api/comments/{commentId}", commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCommentRequest("Updated text"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(commentId.toString()))
                .andExpect(jsonPath("$.content").value("Updated text"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "WORKER")
    void blankAndOversizedContentReturnBadRequestOnUpdate() throws Exception {
        UUID commentId = UUID.randomUUID();
        mockMvc.perform(patch("/api/comments/{commentId}", commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCommentRequest("   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.content").exists());

        mockMvc.perform(patch("/api/comments/{commentId}", commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCommentRequest("x".repeat(2001)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.content").exists());
    }

    @Test
    void unauthenticatedUserCannotUpdateOrDeleteComment() throws Exception {
        UUID commentId = UUID.randomUUID();
        mockMvc.perform(patch("/api/comments/{commentId}", commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCommentRequest("Updated"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/comments/{commentId}", commentId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "other@example.com", roles = "WORKER")
    void updateCommentReturnsForbiddenWhenNotAuthor() throws Exception {
        UUID commentId = UUID.randomUUID();
        when(commentService.updateComment(eq(commentId), any(UpdateCommentRequest.class), any(String.class)))
                .thenThrow(new AccessDeniedException("Only the comment author can modify this comment"));

        mockMvc.perform(patch("/api/comments/{commentId}", commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCommentRequest("Updated"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WORKER")
    void updateCommentReturnsNotFoundWhenCommentDoesNotExist() throws Exception {
        UUID commentId = UUID.randomUUID();
        when(commentService.updateComment(eq(commentId), any(UpdateCommentRequest.class), any(String.class)))
                .thenThrow(new CommentNotFoundException("Comment not found: " + commentId));

        mockMvc.perform(patch("/api/comments/{commentId}", commentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCommentRequest("Updated"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "author@example.com", roles = "WORKER")
    void authenticatedUserCanDeleteComment() throws Exception {
        UUID commentId = UUID.randomUUID();
        doNothing().when(commentService).deleteComment(eq(commentId), any(String.class));

        mockMvc.perform(delete("/api/comments/{commentId}", commentId))
                .andExpect(status().isNoContent());

        verify(commentService).deleteComment(eq(commentId), eq("author@example.com"));
    }

    @Test
    @WithMockUser(username = "other@example.com", roles = "WORKER")
    void deleteCommentReturnsForbiddenWhenNotAuthor() throws Exception {
        UUID commentId = UUID.randomUUID();
        doThrow(new AccessDeniedException("Only the comment author can modify this comment"))
                .when(commentService).deleteComment(eq(commentId), any(String.class));

        mockMvc.perform(delete("/api/comments/{commentId}", commentId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WORKER")
    void deleteCommentReturnsNotFoundWhenCommentDoesNotExist() throws Exception {
        UUID commentId = UUID.randomUUID();
        doThrow(new CommentNotFoundException("Comment not found: " + commentId))
                .when(commentService).deleteComment(eq(commentId), any(String.class));

        mockMvc.perform(delete("/api/comments/{commentId}", commentId))
                .andExpect(status().isNotFound());
    }

    private CommentResponse response(UUID taskId) {
        return new CommentResponse(UUID.randomUUID(), "Comment", taskId, UUID.randomUUID(),
                "Author", "author@example.com", Instant.now(), Instant.now());
    }
}
