package com.resplan.scheduling;

import com.resplan.error.BusinessRuleException;

public class CycleException extends BusinessRuleException {

    public CycleException() {
        super("CYCLE", "Зависимости задач образуют цикл");
    }
}
