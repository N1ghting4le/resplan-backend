package com.resplan.booking;

import com.resplan.domain.Booking;
import com.resplan.domain.BookingKind;

import java.util.List;

/** Защищенное время на обучение (FR9-3, FR9-4): запрещающее правило */
public class ProtectedTrainingRule extends BookingRule {

    @Override
    protected void apply(Booking candidate, List<Booking> existing, BookingCheckResult result) {
        for (Booking other : existing) {
            if (!other.overlaps(candidate)) continue;
            if (candidate.isProjectWork() && other.isProtectedTime()) {
                result.reject("PROTECTED_TIME", "Период пересекается с защищенным обучением «" + other.title() + "» ("
                        + other.getStartDate() + " – " + other.getEndDate() + ")");
                return;
            }
            if (candidate.isProtectedTime() && other.getKind() == BookingKind.HARD) {
                result.reject("PROTECTED_TIME", "Период обучения пересекается с жесткой бронью на проект " + other.title()
                        + " (" + other.getStartDate() + " – " + other.getEndDate() + "), выберите другие даты");
                return;
            }
        }
    }
}
