package com.resplan.api.dto;

/** Результат снятия брони; returnedRequestId – запрос, возвращенный на подбор, или null */
public record ReleaseDto(BookingDto booking, Integer returnedRequestId) {
}
