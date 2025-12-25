package com.pokergame.exception;

public abstract class PokerException extends RuntimeException {
    public PokerException(String message) {
        super(message);
    }

    public PokerException(String message, Throwable cause) {
        super(message, cause);
    }
}
