package com.resplan.error;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String entity, Object id) {
        super(entity + " с идентификатором " + id + " не найден");
    }
}
