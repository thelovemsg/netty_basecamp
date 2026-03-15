package org.example.netty_basecamp.netty.rest.filter;

public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
