package org.suai.models;

import lombok.Getter;

public class Response {
    @Getter
    private int code;
    private String message;
    private boolean success;

    private final static int OK = 0;

    public Response(int code, String message) {
        this.code = code;
        this.message = message;
        this.success = code == OK;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSuccess() {
        return success;
    }

    // Example of response: code + ";" + message
    public static Response parse(String responseMessage) {
        System.out.println(responseMessage.length() + " " + responseMessage);
        try {
            String[] parts = responseMessage.split(";");
            int code = Integer.parseInt(parts[0]);
            String message = parts[1];

            return new Response(code, message);
        }
        catch (Exception e) {
            String message = "Error parsing response: " + responseMessage + "\n";
            throw new RuntimeException(message);
        }
    }

    @Override
    public String toString() {
        return code + ";" + message;
    }
}