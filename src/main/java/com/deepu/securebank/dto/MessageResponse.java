package com.deepu.securebank.dto;

/** A very simple reply: {"message": "..."} */
public class MessageResponse {
    private final String message;

    public MessageResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
