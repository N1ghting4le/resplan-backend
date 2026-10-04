package com.resplan.error;

import com.resplan.domain.RequestStatus;

public class IllegalTransitionException extends BusinessRuleException {

    public IllegalTransitionException(RequestStatus from, RequestStatus to) {
        super("STATUS", "Недопустимый переход запроса из статуса " + from + " в статус " + to);
    }
}
