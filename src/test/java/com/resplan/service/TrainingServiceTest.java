package com.resplan.service;

import com.resplan.booking.BookingValidator;
import com.resplan.domain.*;
import com.resplan.error.BusinessRuleException;
import com.resplan.event.BookingAssignedEvent;
import com.resplan.repository.BookingRepository;
import com.resplan.repository.EmployeeRepository;
import com.resplan.repository.TrainingCourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static com.resplan.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Сервис проверяется вместе с настоящей цепочкой правил BookingValidator:
 * заглушкой заменяется только доступ к базе данных.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UC-9 Резервировать время на обучение: TrainingService")
class TrainingServiceTest {

    @Mock
    TrainingCourseRepository courses;
    @Mock
    EmployeeRepository employees;
    @Mock
    BookingRepository bookings;
    @Mock
    BookingLifecycle lifecycle;
    @Mock
    ApplicationEventPublisher events;

    TrainingService service;
    AppUser ld;
    Employee belova;
    Project helix;

    @BeforeEach
    void setUp() {
        service = new TrainingService(courses, employees, bookings, new BookingValidator(bookings), lifecycle, events);
        ld = user(4, UserRole.LD);
        belova = employee(3, "Белова", SENIOR, 30);
        helix = project(3, "HELIX", user(2, UserRole.PM), 2);
        lenient().when(employees.require(3, "Сотрудник")).thenReturn(belova);
    }

    private TrainingCommand command(String title, String provider, int from, int to, boolean protectedTime) {
        return new TrainingCommand(3, title, provider, day(from), day(to), 100, protectedTime);
    }

    @Test
    @DisplayName("FR9-1, FR9-2: защищенный блок обучения добавляется в календарь, курс создается")
    void reserveProtectedTraining() {
        when(courses.findByTitleIgnoreCase("AWS Certified ML Specialty")).thenReturn(Optional.empty());
        when(courses.save(any())).then(returnsFirstArg());
        when(bookings.findOverlapping(3, day(28), day(32))).thenReturn(List.of());

        BookingResult result = service.reserve(command(" AWS Certified ML Specialty ", " ", 28, 32, true), ld);

        TrainingBooking training = (TrainingBooking) result.booking();
        assertThat(training.isProtectedTime()).isTrue();
        assertThat(training.getCourse().getTitle()).isEqualTo("AWS Certified ML Specialty");
        assertThat(training.getCourse().getProvider()).isEqualTo("EPAM University");
        assertThat(training.getEmployee()).isSameAs(belova);
        assertThat(training.getCreatedBy()).isSameAs(ld);
        verify(bookings).save(training);
        verify(events).publishEvent(any(BookingAssignedEvent.class));
    }

    @Test
    @DisplayName("FR9-2: для нового курса сохраняется указанный провайдер")
    void newCourseKeepsProvider() {
        when(courses.findByTitleIgnoreCase("Kubernetes для разработчиков")).thenReturn(Optional.empty());
        when(courses.save(any())).then(returnsFirstArg());
        when(bookings.findOverlapping(3, day(7), day(8))).thenReturn(List.of());

        BookingResult result = service.reserve(command("Kubernetes для разработчиков", " Coursera ", 7, 8, false), ld);

        assertThat(((TrainingBooking) result.booking()).getCourse().getProvider()).isEqualTo("Coursera");
    }

    @Test
    @DisplayName("FR9-2: существующий курс из каталога используется повторно")
    void existingCourseIsReused() {
        TrainingCourse azure = new TrainingCourse("Сертификация Azure AI Engineer", "Microsoft");
        when(courses.findByTitleIgnoreCase("сертификация azure ai engineer")).thenReturn(Optional.of(azure));
        when(bookings.findOverlapping(3, day(21), day(25))).thenReturn(List.of());

        BookingResult result = service.reserve(command("сертификация azure ai engineer", "Coursera", 21, 25, false), ld);

        assertThat(((TrainingBooking) result.booking()).getCourse()).isSameAs(azure);
        verify(courses, never()).save(any());
    }

    @Test
    @DisplayName("FR9-4: пересечение защищенного обучения с жесткой бронью – предупреждение, бронь не создается")
    void protectedTrainingOverlappingHardBookingIsRejected() {
        when(courses.findByTitleIgnoreCase("Курс")).thenReturn(Optional.of(new TrainingCourse("Курс", "EPAM University")));
        when(bookings.findOverlapping(3, day(10), day(14))).thenReturn(
                List.of(projectBooking(5, belova, helix, BookingKind.HARD, day(0), day(25), 100)));

        assertThatThrownBy(() -> service.reserve(command("Курс", null, 10, 14, true), ld))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "PROTECTED_TIME")
                .hasMessageContaining("HELIX");
        verify(bookings, never()).save(any());
        verifyNoInteractions(lifecycle, events);
    }

    @Test
    @DisplayName("FR9-3: незащищенное обучение поверх жесткой брони регистрирует перегрузку как конфликт")
    void unprotectedTrainingRegistersOverload() {
        ProjectBooking hard = projectBooking(5, belova, helix, BookingKind.HARD, day(0), day(25), 100);
        when(courses.findByTitleIgnoreCase("Курс")).thenReturn(Optional.of(new TrainingCourse("Курс", "EPAM University")));
        when(bookings.findOverlapping(3, day(10), day(14))).thenReturn(List.of(hard));
        when(lifecycle.registerConflicts(any(), eq(List.of(hard)))).thenReturn(List.of());

        BookingResult result = service.reserve(command("Курс", null, 10, 14, false), ld);

        assertThat(result.overloads()).containsExactly(hard);
    }

    @Test
    @DisplayName("FR9-3: отмена обучения возвращает сотрудника в подбор")
    void cancelTraining() {
        TrainingBooking training = training(20, belova, day(0), day(4), 100, true);
        when(bookings.require(20L, "Бронирование")).thenReturn(training);

        service.cancel(20, ld);

        verify(lifecycle).release(training, ld, "Обучение отменено сотрудником L&D");
    }

    @Test
    @DisplayName("FR9-1: проектную бронь или отмененное обучение отменить как обучение нельзя")
    void cancelRejectsWrongBookings() {
        TrainingBooking cancelled = training(21, belova, day(0), day(4), 100, true);
        cancelled.release(ld);
        when(bookings.require(5L, "Бронирование"))
                .thenReturn(projectBooking(5, belova, helix, BookingKind.HARD, day(0), day(25), 100));
        when(bookings.require(21L, "Бронирование")).thenReturn(cancelled);

        assertThatThrownBy(() -> service.cancel(5, ld)).hasFieldOrPropertyWithValue("code", "KIND");
        assertThatThrownBy(() -> service.cancel(21, ld)).hasFieldOrPropertyWithValue("code", "RELEASED");
        verifyNoInteractions(lifecycle);
    }

    @Test
    @DisplayName("FR9-2: каталог курсов упорядочен по названию")
    void courseCatalog() {
        List<TrainingCourse> catalog = List.of(new TrainingCourse("A", "B"));
        when(courses.findAllByOrderByTitle()).thenReturn(catalog);

        assertThat(service.courses()).isSameAs(catalog);
    }
}
