package com.pokergame.exception;

@SuppressWarnings("unused")
public abstract class PokerException extends RuntimeException {
    public PokerException(String message) {
        super(message);
    }

    public PokerException(String message, Throwable cause) {
        super(message, cause);
    }
}
