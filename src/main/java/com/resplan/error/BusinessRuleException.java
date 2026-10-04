package com.resplan.error;

import lombok.Getter;

/** Нарушение бизнес-правила; code позволяет клиенту распознать причину отказа */
@Getter
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }
}
