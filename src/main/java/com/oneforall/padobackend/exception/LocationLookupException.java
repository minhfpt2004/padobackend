package com.oneforall.padobackend.exception;

public class LocationLookupException extends RuntimeException {
    public LocationLookupException(String message) {
        super(message);
    }

    public LocationLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
