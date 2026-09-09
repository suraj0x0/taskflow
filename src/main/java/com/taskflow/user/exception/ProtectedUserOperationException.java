package com.taskflow.user.exception;

public class ProtectedUserOperationException extends RuntimeException {

    public ProtectedUserOperationException(String message) {
        super(message);
    }
}