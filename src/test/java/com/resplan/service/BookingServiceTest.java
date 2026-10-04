package com.resplan.service;

import com.resplan.booking.BookingValidator;
import com.resplan.domain.*;
import com.resplan.error.BusinessRuleException;
import com.resplan.event.BookingAssignedEvent;
import com.resplan.repository.BookingRepository;
import com.resplan.repository.EmployeeRepository;
import com.resplan.repository.ProjectRepository;
import com.resplan.repository.ResourceRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static com.resplan.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UC-7 Управлять бронированием кандидатов: BookingService")
class BookingServiceTest {

    @Mock
    BookingRepository bookings;
    @Mock
    EmployeeRepository employees;
    @Mock
    ProjectRepository projects;
    @Mock
    ResourceRequestRepository requests;
    @Mock
    BookingValidator validator;
    @Mock
    BookingLifecycle lifecycle;
    @Mock
    ApplicationEventPublisher events;
    @InjectMocks
    BookingService service;

    AppUser rm;
    Employee novik;
    Project atlas;

    @BeforeEach
    void setUp() {
        rm = user(3, UserRole.RM);
        novik = employee(6, "Новик", SENIOR, 26);
        atlas = project(1, "ATLAS", user(1, UserRole.PM), 1);
    }

    @Nested
    @DisplayName("FR7-1 Матрица распределения ресурсов")
    class ListBookings {

        @Test
        @DisplayName("FR7-1: брони всех сотрудников или одного сотрудника за период")
        void listByEmployeeOrAll() {
            Booking b = projectBooking(1, novik, atlas, BookingKind.HARD, day(0), day(4), 100);
            when(bookings.findOverlapping(day(0), day(30))).thenReturn(List.of(b));
            when(bookings.findOverlapping(6, day(0), day(30))).thenReturn(List.of(b));

            assertThat(service.list(null, day(0), day(30))).containsExactly(b);
            assertThat(service.list(6, day(0), day(30))).containsExactly(b);
        }

        @Test
        @DisplayName("FR7-1: период с концом раньше начала отклоняется")
        void invalidPeriod() {
            assertThatThrownBy(() -> service.list(null, day(5), day(4))).hasFieldOrPropertyWithValue("code", "PERIOD");
            verifyNoInteractions(bookings);
        }
    }

    @Nested
    @DisplayName("FR7-2, FR7-3 Установка брони")
    class Create {

        @Test
        @DisplayName("FR7-2, FR7-3: жесткая бронь с перегрузкой сохраняется и регистрирует конфликт")
        void hardBookingRegistersConflicts() {
            ProjectBooking orion = projectBooking(2, novik, project(2, "ORION", rm, 2), BookingKind.HARD, day(0), day(9), 50);
            ResourceConflict conflict = mock(ResourceConflict.class);
            when(employees.require(6, "Сотрудник")).thenReturn(novik);
            when(projects.require(1, "Проект")).thenReturn(atlas);
            when(validator.validate(any())).thenReturn(passed(orion));
            when(lifecycle.registerConflicts(any(), eq(List.of(orion)))).thenReturn(List.of(conflict));

            BookingResult result = service.create(new BookingCommand(6, 1, BookingKind.HARD, day(0), day(4), 60), rm);

            ProjectBooking created = (ProjectBooking) result.booking();
            assertThat(created.getKind()).isEqualTo(BookingKind.HARD);
            assertThat(created.getProject()).isSameAs(atlas);
            assertThat(created.getLoadPercent()).isEqualTo((short) 60);
            assertThat(created.getCreatedBy()).isSameAs(rm);
            assertThat(result.overloads()).containsExactly(orion);
            assertThat(result.conflicts()).containsExactly(conflict);
            verify(bookings).save(created);
            verify(events).publishEvent(any(BookingAssignedEvent.class));
        }

        @Test
        @DisplayName("FR7-3: мягкая бронь не порождает ресурсных конфликтов")
        void softBookingDoesNotRegisterConflicts() {
            ProjectBooking orion = projectBooking(2, novik, atlas, BookingKind.HARD, day(0), day(9), 50);
            when(employees.require(6, "Сотрудник")).thenReturn(novik);
            when(projects.require(1, "Проект")).thenReturn(atlas);
            when(validator.validate(any())).thenReturn(passed(orion));

            BookingResult result = service.create(new BookingCommand(6, 1, BookingKind.SOFT, day(0), day(4), 60), rm);

            assertThat(result.overloads()).containsExactly(orion);
            assertThat(result.conflicts()).isEmpty();
            verifyNoInteractions(lifecycle);
        }

        @Test
        @DisplayName("FR7-2: обучение через проектное бронирование не создается")
        void trainingKindIsRejected() {
            assertThatThrownBy(() -> service.create(new BookingCommand(6, 1, BookingKind.TRAINING, day(0), day(4), 60), rm))
                    .hasFieldOrPropertyWithValue("code", "KIND");
            verifyNoInteractions(employees, bookings);
        }
    }

    @Nested
    @DisplayName("Изменение брони")
    class Update {

        ProjectBooking soft;

        @BeforeEach
        void setUp() {
            soft = projectBooking(5, novik, atlas, BookingKind.SOFT, day(0), day(9), 50);
            when(bookings.require(5L, "Бронирование")).thenReturn(soft);
        }

        @Test
        @DisplayName("FR7-2: мягкая бронь переводится в жесткую, незаданные поля не меняются")
        void hardenSoftBooking() {
            when(requests.findByBooking(soft)).thenReturn(Optional.empty());
            when(validator.validate(soft)).thenReturn(passed());
            when(lifecycle.registerConflicts(soft, List.of())).thenReturn(List.of());

            BookingResult result = service.update(5, new BookingCommand(null, null, BookingKind.HARD, null, day(14), null));

            assertThat(result.booking().getKind()).isEqualTo(BookingKind.HARD);
            assertThat(soft.getStartDate()).isEqualTo(day(0));
            assertThat(soft.getEndDate()).isEqualTo(day(14));
            assertThat(soft.getLoadPercent()).isEqualTo((short) 50);
        }

        @Test
        @DisplayName("FR7-2: у жесткой брони меняются даты аллокации и загрузка, вид сохраняется")
        void rescheduleHardBooking() {
            soft.harden();
            when(requests.findByBooking(soft)).thenReturn(Optional.empty());
            when(validator.validate(soft)).thenReturn(passed());
            when(lifecycle.registerConflicts(soft, List.of())).thenReturn(List.of());

            service.update(5, new BookingCommand(null, null, null, day(2), null, 70));
            service.update(5, new BookingCommand(null, null, BookingKind.HARD, null, null, null));

            assertThat(soft.getKind()).isEqualTo(BookingKind.HARD);
            assertThat(soft.getStartDate()).isEqualTo(day(2));
            assertThat(soft.getEndDate()).isEqualTo(day(9));
            assertThat(soft.getLoadPercent()).isEqualTo((short) 70);
        }

        @Test
        @DisplayName("FR7-2: жесткую бронь нельзя перевести в мягкую")
        void hardCannotBecomeSoft() {
            soft.harden();
            when(requests.findByBooking(soft)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(5, new BookingCommand(null, null, BookingKind.SOFT, null, null, 40)))
                    .hasFieldOrPropertyWithValue("code", "KIND");
        }

        @Test
        @DisplayName("FR7-2: бронь, закрепленную за активным запросом, изменяет только PM через UC-3")
        void bookingLinkedToRequestIsLocked() {
            ResourceRequest r = request(7, task(1, atlas, "Web", 8), SENIOR, 50, day(0), day(9));
            r.proposeCandidate(soft);
            when(requests.findByBooking(soft)).thenReturn(Optional.of(r));

            assertThatThrownBy(() -> service.update(5, new BookingCommand(null, null, BookingKind.HARD, null, null, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("code", "REQUEST_LINKED")
                    .hasMessageContaining("№7");
        }

        @Test
        @DisplayName("FR7-4: снятую бронь изменить нельзя")
        void releasedBookingIsReadOnly() {
            soft.release(rm);

            assertThatThrownBy(() -> service.update(5, new BookingCommand(null, null, null, null, null, 30)))
                    .hasFieldOrPropertyWithValue("code", "RELEASED");
        }
    }

    @Nested
    @DisplayName("FR7-4 Снятие брони")
    class Release {

        @Test
        @DisplayName("FR7-4: бронь снимается, сотрудник возвращается в пул ресурсов")
        void releaseProjectBooking() {
            ProjectBooking hard = projectBooking(5, novik, atlas, BookingKind.HARD, day(0), day(9), 100);
            when(bookings.require(5L, "Бронирование")).thenReturn(hard);

            service.release(5, rm);

            verify(lifecycle).release(hard, rm, "Бронь снята ресурсным менеджером");
        }

        @Test
        @DisplayName("FR7-4: бронь обучения через UC-7 не снимается")
        void trainingIsReleasedByLdOnly() {
            when(bookings.require(8L, "Бронирование")).thenReturn(training(8, novik, day(0), day(4), 100, true));

            assertThatThrownBy(() -> service.release(8, rm)).hasFieldOrPropertyWithValue("code", "KIND");
            assertThatThrownBy(() -> service.update(8, new BookingCommand(null, null, null, null, null, 30)))
                    .hasFieldOrPropertyWithValue("code", "KIND");
            verifyNoInteractions(lifecycle);
        }

        @Test
        @DisplayName("FR7-4: повторное снятие брони запрещено")
        void doubleReleaseIsRejected() {
            ProjectBooking hard = projectBooking(5, novik, atlas, BookingKind.HARD, day(0), day(9), 100);
            hard.release(rm);
            when(bookings.require(5L, "Бронирование")).thenReturn(hard);

            assertThatThrownBy(() -> service.release(5, rm)).hasFieldOrPropertyWithValue("code", "RELEASED");
        }
    }
}
