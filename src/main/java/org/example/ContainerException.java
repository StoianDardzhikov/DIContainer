package org.example;

public class ContainerException extends Exception {
    String message = "";

    ContainerException() {}

    ContainerException(String message) {
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
