package com.resplan.booking;

import com.resplan.domain.Booking;
import com.resplan.error.BusinessRuleException;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/** Результат проверки бронирования: запрещающее нарушение (код и текст) и найденные перегрузки */
@Getter
public class BookingCheckResult {

    private String violationCode;
    private String violation;
    private final List<Booking> overloads = new ArrayList<>();

    void reject(String code, String violation) {
        this.violationCode = code;
        this.violation = violation;
    }

    void addOverload(Booking other) {
        overloads.add(other);
    }

    public boolean isRejected() {
        return violation != null;
    }

    /** Прерывает операцию, если бронирование нарушает запрещающее правило */
    public BookingCheckResult throwIfRejected() {
        if (isRejected()) throw new BusinessRuleException(violationCode, violation);
        return this;
    }
}
