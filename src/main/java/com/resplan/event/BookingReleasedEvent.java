package com.resplan.event;

import com.resplan.domain.Booking;
import com.resplan.domain.ResourceRequest;

/** Бронь снята; request – запрос, возвращенный на подбор, или null */
public record BookingReleasedEvent(Booking booking, ResourceRequest request, String reason) {
}
