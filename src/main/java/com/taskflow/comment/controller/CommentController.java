package com.taskflow.comment.controller;

import com.taskflow.comment.dto.CommentResponse;
import com.taskflow.comment.dto.CreateCommentRequest;
import com.taskflow.comment.service.CommentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/api/tasks/{taskId}/comments")
    public ResponseEntity<List<CommentResponse>> getComments(@PathVariable UUID taskId) {
        return ResponseEntity.ok(commentService.getCommentsByTask(taskId));
    }

    @PostMapping("/api/tasks/{taskId}/comments")
    public ResponseEntity<CommentResponse> createComment(
            Authentication authentication,
            @PathVariable UUID taskId,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.createComment(taskId, request, authentication.getName()));
    }
}