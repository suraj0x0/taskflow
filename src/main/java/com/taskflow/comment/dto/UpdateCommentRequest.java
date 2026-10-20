package com.taskflow.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateCommentRequest {
    @NotBlank(message = "Comment content must not be blank")
    @Size(max = 2000, message = "Comment content must not exceed 2000 characters")
    private String content;
    public UpdateCommentRequest() { }
    public UpdateCommentRequest(String content) { this.content = content; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}