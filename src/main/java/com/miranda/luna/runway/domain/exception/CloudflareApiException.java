package com.miranda.luna.runway.domain.exception;

public class CloudflareApiException extends RuntimeException {
    public CloudflareApiException(String message) {
        super(message);
    }

    public CloudflareApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
