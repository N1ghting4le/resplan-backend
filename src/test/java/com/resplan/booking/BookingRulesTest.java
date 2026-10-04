package com.resplan.booking;

import com.resplan.domain.*;
import com.resplan.error.BusinessRuleException;
import com.resplan.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.resplan.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@DisplayName("Правила проверки бронирования (цепочка обязанностей)")
class BookingRulesTest {

    private final Employee belova = employee(3, "Белова", SENIOR, 30);
    private final Project atlas = project(1, "ATLAS", user(1, UserRole.PM), 1);

    private ProjectBooking hard(long id, int from, int to, int load) {
        return projectBooking(id, belova, atlas, BookingKind.HARD, day(from), day(to), load);
    }

    private static BookingCheckResult check(BookingRule rule, Booking candidate, List<Booking> existing) {
        BookingCheckResult result = new BookingCheckResult();
        rule.check(candidate, existing, result);
        return result;
    }

    @Nested
    @DisplayName("PeriodRule")
    class Period {

        @Test
        @DisplayName("FR7-2: бронь с датой окончания раньше даты начала отклоняется (PERIOD)")
        void endBeforeStartIsRejected() {
            BookingCheckResult result = check(new PeriodRule(), hard(1, 5, 4, 50), List.of());

            assertThat(result.isRejected()).isTrue();
            assertThat(result.getViolationCode()).isEqualTo("PERIOD");
            assertThatThrownBy(result::throwIfRejected).isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Дата окончания бронирования раньше даты начала");
        }

        @Test
        @DisplayName("FR7-2: загрузка 0 % или более 100 % отклоняется (LOAD)")
        void loadOutOfRangeIsRejected() {
            assertThat(check(new PeriodRule(), hard(1, 0, 4, 0), List.of()).getViolationCode()).isEqualTo("LOAD");
            assertThat(check(new PeriodRule(), hard(1, 0, 4, 101), List.of()).getViolationCode()).isEqualTo("LOAD");
            assertThat(check(new PeriodRule(), hard(1, 0, 0, 100), List.of()).isRejected()).isFalse();
        }
    }

    @Nested
    @DisplayName("ProtectedTrainingRule")
    class ProtectedTraining {

        @Test
        @DisplayName("FR9-3: проектная бронь на период защищенного обучения запрещена")
        void projectWorkCannotOverlapProtectedTraining() {
            TrainingBooking aws = training(5, belova, day(28), day(32), 100, true);

            BookingCheckResult result = check(new ProtectedTrainingRule(), hard(1, 30, 40, 20), List.of(aws));

            assertThat(result.getViolationCode()).isEqualTo("PROTECTED_TIME");
            assertThat(result.getViolation()).contains("Курс 5");
        }

        @Test
        @DisplayName("FR9-4: защищенное обучение на период жесткой брони запрещено")
        void protectedTrainingCannotOverlapHardBooking() {
            TrainingBooking candidate = training(6, belova, day(10), day(14), 100, true);

            BookingCheckResult result = check(new ProtectedTrainingRule(), candidate, List.of(hard(1, 0, 25, 100)));

            assertThat(result.getViolationCode()).isEqualTo("PROTECTED_TIME");
            assertThat(result.getViolation()).contains("ATLAS").contains("выберите другие даты");
        }

        @Test
        @DisplayName("FR9-4: обучение без флага защиты и мягкая бронь не блокируют друг друга")
        void unprotectedTrainingAndSoftBookingAreAllowed() {
            TrainingBooking optional = training(7, belova, day(10), day(14), 50, false);
            ProjectBooking soft = projectBooking(2, belova, atlas, BookingKind.SOFT, day(0), day(25), 50);
            TrainingBooking protectedTraining = training(8, belova, day(10), day(14), 50, true);

            assertThat(check(new ProtectedTrainingRule(), optional, List.of(hard(1, 0, 25, 50))).isRejected()).isFalse();
            assertThat(check(new ProtectedTrainingRule(), protectedTraining, List.of(soft)).isRejected()).isFalse();
            assertThat(check(new ProtectedTrainingRule(), hard(3, 30, 31, 50), List.of(protectedTraining)).isRejected())
                    .as("брони не пересекаются по датам").isFalse();
        }
    }

    @Nested
    @DisplayName("OverloadRule")
    class Overload {

        @Test
        @DisplayName("FR7-3: перегрузка более 100 % фиксируется, но не запрещает бронь")
        void overloadIsReportedNotRejected() {
            ProjectBooking orion = hard(2, 5, 20, 50);
            ProjectBooking helix = hard(3, 5, 20, 50);

            BookingCheckResult result = check(new OverloadRule(), hard(1, 0, 10, 60), List.of(orion, helix));

            assertThat(result.isRejected()).isFalse();
            assertThat(result.getOverloads()).containsExactly(orion, helix);
        }

        @Test
        @DisplayName("FR7-3: суммарная загрузка ровно 100 % перегрузкой не считается")
        void exactlyHundredPercentIsAllowed() {
            BookingCheckResult result = check(new OverloadRule(), hard(1, 0, 10, 50), List.of(hard(2, 5, 20, 50)));
            BookingCheckResult disjoint = check(new OverloadRule(), hard(1, 0, 10, 100), List.of(hard(2, 11, 20, 100)));

            assertThat(result.getOverloads()).isEmpty();
            assertThat(disjoint.getOverloads()).as("периоды не пересекаются").isEmpty();
        }
    }

    @Test
    @DisplayName("Цепочка прерывается на первом запрещающем правиле")
    void chainStopsAfterRejection() {
        BookingRule head = new PeriodRule();
        head.linkWith(new ProtectedTrainingRule()).linkWith(new OverloadRule());

        BookingCheckResult rejected = check(head, hard(1, 4, 2, 100), List.of(hard(2, 0, 10, 100)));
        BookingCheckResult passed = check(head, hard(1, 0, 4, 100), List.of(hard(2, 0, 10, 100)));

        assertThat(rejected.getViolationCode()).isEqualTo("PERIOD");
        assertThat(rejected.getOverloads()).as("OverloadRule не вызывалось").isEmpty();
        assertThat(passed.isRejected()).isFalse();
        assertThat(passed.getOverloads()).hasSize(1);
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("BookingValidator")
    class Validator {

        @Mock
        BookingRepository bookings;
        BookingValidator validator;

        @BeforeEach
        void setUp() {
            validator = new BookingValidator(bookings);
        }

        @Test
        @DisplayName("FR7-3: проверяемая бронь не сравнивается сама с собой при изменении")
        void bookingIsNotComparedWithItself() {
            ProjectBooking edited = hard(1, 0, 10, 100);
            ProjectBooking other = hard(2, 5, 6, 30);
            when(bookings.findOverlapping(3, day(0), day(10))).thenReturn(List.of(edited, other));

            BookingCheckResult result = validator.validate(edited);

            assertThat(result.getOverloads()).containsExactly(other);
        }

        @Test
        @DisplayName("FR9-4: валидатор применяет все правила к броням сотрудника из репозитория")
        void validatorAppliesWholeChain() {
            TrainingBooking candidate = training(9, belova, day(0), day(4), 100, true);
            when(bookings.findOverlapping(3, day(0), day(4))).thenReturn(List.of(hard(1, 3, 10, 100)));

            assertThatThrownBy(() -> validator.validate(candidate).throwIfRejected())
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("code", "PROTECTED_TIME");
        }
    }
}
