package com.thecommitcrew.auth;

public class ErrorResponse {
    private String status;
    private String message;
    private Object data;

    public ErrorResponse(String status, String message, Object data) {
        this.status = status;
        this.message = message;
        this.data = data;
    }

    // Getters
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public Object getData() { return data; }
}