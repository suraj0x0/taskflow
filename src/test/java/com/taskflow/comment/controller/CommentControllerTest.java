package com.taskflow.comment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.comment.dto.CommentResponse;
import com.taskflow.comment.dto.CreateCommentRequest;
import com.taskflow.comment.service.CommentService;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    private CommentResponse response(UUID taskId) {
        return new CommentResponse(UUID.randomUUID(), "Comment", taskId, UUID.randomUUID(),
                "Author", "author@example.com", Instant.now(), Instant.now());
    }
}
