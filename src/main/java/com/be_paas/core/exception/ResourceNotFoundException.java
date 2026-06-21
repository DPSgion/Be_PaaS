package com.be_paas.core.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName, Object id) {
        super("%s not found with id: %s".formatted(resourceName, id));
    }
}
