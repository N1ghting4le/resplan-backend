package com.resplan.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Статус запроса на ресурс и допустимые переходы между статусами.
 * Таблица переходов соответствует диаграмме состояний запроса.
 */
public enum RequestStatus {
    SEARCHING, PENDING_PM, APPROVED, EXTERNAL_HIRE, CANCELLED;

    private static final Map<RequestStatus, Set<RequestStatus>> TRANSITIONS = new EnumMap<>(RequestStatus.class);

    static {
        TRANSITIONS.put(SEARCHING, EnumSet.of(PENDING_PM, EXTERNAL_HIRE, CANCELLED));
        TRANSITIONS.put(PENDING_PM, EnumSet.of(APPROVED, SEARCHING, CANCELLED));
        TRANSITIONS.put(EXTERNAL_HIRE, EnumSet.of(PENDING_PM, CANCELLED));
        TRANSITIONS.put(APPROVED, EnumSet.of(SEARCHING, CANCELLED));
        TRANSITIONS.put(CANCELLED, EnumSet.noneOf(RequestStatus.class));
    }

    public boolean canTransitionTo(RequestStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    public boolean isFinal() {
        return TRANSITIONS.get(this).isEmpty();
    }
}
