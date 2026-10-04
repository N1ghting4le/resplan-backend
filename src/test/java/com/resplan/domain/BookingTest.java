package com.resplan.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.resplan.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Бронирование (Booking, ProjectBooking, TrainingBooking)")
class BookingTest {

    private Employee kovalev;
    private Project atlas;
    private ProjectBooking booking;

    @BeforeEach
    void setUp() {
        kovalev = employee(1);
        atlas = project(1, "ATLAS", user(1, UserRole.PM), 1);
        booking = projectBooking(10, kovalev, atlas, BookingKind.SOFT, day(7), day(11), 60);
    }

    @ParameterizedTest(name = "период [{0}; {1}] от начала недели -> {2}")
    @CsvSource({"0, 6, false", "0, 7, true", "11, 20, true", "12, 20, false", "8, 9, true", "0, 30, true"})
    @DisplayName("FR7-3: пересечение с периодом учитывает обе границы")
    void overlapsPeriodWithInclusiveBounds(int from, int to, boolean expected) {
        assertThat(booking.overlaps(day(from), day(to))).isEqualTo(expected);
    }

    @Test
    @DisplayName("FR7-3: брони разных сотрудников не пересекаются даже в одном периоде")
    void bookingsOfDifferentEmployeesDoNotOverlap() {
        ProjectBooking sameEmployee = projectBooking(11, kovalev, atlas, BookingKind.HARD, day(9), day(14), 50);
        ProjectBooking otherEmployee = projectBooking(12, employee(2), atlas, BookingKind.HARD, day(9), day(14), 50);

        assertThat(booking.overlaps(sameEmployee)).isTrue();
        assertThat(booking.overlaps(otherEmployee)).isFalse();
        assertThat(booking.overlapStart(sameEmployee)).isEqualTo(day(9));
        assertThat(booking.overlapEnd(sameEmployee)).isEqualTo(day(11));
        assertThat(sameEmployee.overlapStart(booking)).isEqualTo(day(9));
        assertThat(sameEmployee.overlapEnd(booking)).isEqualTo(day(11));
    }

    @Test
    @DisplayName("FR7-2: мягкая бронь переводится в жесткую, повторный перевод запрещен")
    void softBookingIsHardenedOnce() {
        booking.harden();

        assertThat(booking.getKind()).isEqualTo(BookingKind.HARD);
        assertThatThrownBy(booking::harden).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("FR7-2: проектная бронь не может иметь вид TRAINING")
    void projectBookingCannotBeTraining() {
        assertThatThrownBy(() -> new ProjectBooking(kovalev, atlas, BookingKind.TRAINING, day(0), day(1), 50, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("FR7-4: снятая бронь становится недействующей, повторное снятие запрещено")
    void releasedBookingIsInactive() {
        AppUser rm = user(3, UserRole.RM);

        booking.release(rm);

        assertThat(booking.isActive()).isFalse();
        assertThat(booking.getReleasedBy()).isSameAs(rm);
        assertThat(booking.getReleasedAt()).isNotNull();
        assertThatThrownBy(() -> booking.release(rm)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("UC-7: изменение периода и загрузки брони")
    void rescheduleChangesPeriodAndLoad() {
        booking.reschedule(day(14), day(18), 80);

        assertThat(booking.getStartDate()).isEqualTo(day(14));
        assertThat(booking.getEndDate()).isEqualTo(day(18));
        assertThat(booking.getLoadPercent()).isEqualTo((short) 80);
        assertThat(booking.title()).isEqualTo("ATLAS");
    }

    @Test
    @DisplayName("FR9-2: обучение не является проектной работой, флаг защиты задается при создании")
    void trainingBookingKeepsProtectedFlag() {
        TrainingBooking protectedTraining = training(20, kovalev, day(0), day(4), 100, true);
        TrainingBooking optionalTraining = training(21, kovalev, day(0), day(4), 50, false);

        assertThat(protectedTraining.getKind()).isEqualTo(BookingKind.TRAINING);
        assertThat(protectedTraining.isProjectWork()).isFalse();
        assertThat(protectedTraining.isProtectedTime()).isTrue();
        assertThat(optionalTraining.isProtectedTime()).isFalse();
        assertThat(protectedTraining.title()).isEqualTo("Курс 20");
        assertThat(booking.isProjectWork()).isTrue();
        assertThat(booking.isProtectedTime()).isFalse();
    }
}
