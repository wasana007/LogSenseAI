package com.logai.exception;

public class LogsenseAISerializationException extends RuntimeException {

    public LogsenseAISerializationException(String message) {
        super(message);
    }

    public LogsenseAISerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
