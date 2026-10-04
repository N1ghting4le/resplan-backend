package com.resplan.booking;

import com.resplan.domain.Booking;

import java.util.List;

/** Перегрузка (BR-08): суммарная загрузка пересекающихся броней более 100 %; не запрещает бронь */
public class OverloadRule extends BookingRule {

    static final int MAX_LOAD = 100;

    @Override
    protected void apply(Booking candidate, List<Booking> existing, BookingCheckResult result) {
        for (Booking other : existing) {
            if (other.overlaps(candidate) && other.getLoadPercent() + candidate.getLoadPercent() > MAX_LOAD) {
                result.addOverload(other);
            }
        }
    }
}
