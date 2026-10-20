package com.taskflow.comment.service;

import com.taskflow.activity.model.ActivityType;
import com.taskflow.activity.service.ActivityService;
import com.taskflow.comment.dto.CommentResponse;
import com.taskflow.comment.dto.CreateCommentRequest;
import com.taskflow.comment.dto.UpdateCommentRequest;
import com.taskflow.comment.exception.CommentNotFoundException;
import com.taskflow.comment.model.Comment;
import com.taskflow.comment.repository.CommentRepository;
import com.taskflow.task.exception.TaskNotFoundException;
import com.taskflow.task.model.Task;
import com.taskflow.task.repository.TaskRepository;
import com.taskflow.user.exception.UserNotFoundException;
import com.taskflow.user.model.User;
import com.taskflow.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ActivityService activityService;

    public CommentService(CommentRepository commentRepository,
                          TaskRepository taskRepository,
                          UserRepository userRepository,
                          ActivityService activityService) {
        this.commentRepository = commentRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.activityService = activityService;
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
    public CommentResponse createComment(UUID taskId,
                                         CreateCommentRequest request,
                                         String authenticatedEmail) {
        Task task = findTask(taskId);
        User author = findUser(authenticatedEmail);

        Comment comment = new Comment(
                request.getContent().trim(),
                task,
                author
        );

        Comment savedComment = commentRepository.save(comment);

        activityService.record(
                taskId,
                author,
                ActivityType.COMMENT_CREATED,
                "Comment created"
        );

        return CommentResponse.from(savedComment);
    }

    @Transactional
    public CommentResponse updateComment(UUID commentId,
                                         UpdateCommentRequest request,
                                         String authenticatedEmail) {
        Comment comment = findComment(commentId);
        User author = findUser(authenticatedEmail);

        requireAuthor(comment, author);

        comment.setContent(request.getContent().trim());

        Comment savedComment = commentRepository.save(comment);

        activityService.record(
                comment.getTask().getId(),
                author,
                ActivityType.COMMENT_UPDATED,
                "Comment updated"
        );

        return CommentResponse.from(savedComment);
    }

    @Transactional
    public void deleteComment(UUID commentId, String authenticatedEmail) {
        Comment comment = findComment(commentId);
        User author = findUser(authenticatedEmail);

        requireAuthor(comment, author);

        activityService.record(
                comment.getTask().getId(),
                author,
                ActivityType.COMMENT_DELETED,
                "Comment deleted"
        );

        commentRepository.delete(comment);
    }

    private Comment findComment(UUID commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() ->
                        new CommentNotFoundException("Comment not found: " + commentId));
    }

    private Task findTask(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() ->
                        new TaskNotFoundException("Task not found: " + taskId));
    }

    private User findUser(String authenticatedEmail) {
        return userRepository.findByEmail(normalizeEmail(authenticatedEmail))
                .orElseThrow(() ->
                        new UserNotFoundException("User not found"));
    }

    private void requireAuthor(Comment comment, User authenticatedUser) {
        if (!comment.getAuthor().equals(authenticatedUser)) {
            throw new AccessDeniedException("Only the comment author can modify this comment");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
