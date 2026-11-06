package dev.blog.common;

/** Throw from services when an entity does not exist; mapped to a 404 ProblemDetail. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
