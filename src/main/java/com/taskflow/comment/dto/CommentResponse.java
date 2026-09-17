package com.taskflow.comment.dto;

import com.taskflow.comment.model.Comment;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        String content,
        UUID taskId,
        UUID authorId,
        String authorName,
        String authorEmail,
        Instant createdAt,
        Instant updatedAt
) {

    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getTask().getId(),
                comment.getAuthor().getId(),
                comment.getAuthor().getName(),
                comment.getAuthor().getEmail(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}