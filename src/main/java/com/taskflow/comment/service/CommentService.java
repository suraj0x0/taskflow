package com.taskflow.comment.service;

import com.taskflow.comment.dto.CommentResponse;
import com.taskflow.comment.dto.CreateCommentRequest;
import com.taskflow.comment.model.Comment;
import com.taskflow.comment.repository.CommentRepository;
import com.taskflow.task.exception.TaskNotFoundException;
import com.taskflow.task.model.Task;
import com.taskflow.task.repository.TaskRepository;
import com.taskflow.user.exception.UserNotFoundException;
import com.taskflow.user.model.User;
import com.taskflow.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public CommentService(CommentRepository commentRepository, TaskRepository taskRepository,
                          UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsByTask(UUID taskId) {
        findTask(taskId);
        return commentRepository.findByTaskIdOrderByCreatedAtAsc(taskId)
                .stream()
                .map(CommentResponse::from)
                .toList();
    }

    @Transactional
    public CommentResponse createComment(UUID taskId, CreateCommentRequest request, String authenticatedEmail) {
        Task task = findTask(taskId);
        User author = userRepository.findByEmail(normalizeEmail(authenticatedEmail))
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        Comment comment = new Comment(request.getContent().trim(), task, author);
        return CommentResponse.from(commentRepository.save(comment));
    }

    private Task findTask(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Task not found: " + taskId));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}