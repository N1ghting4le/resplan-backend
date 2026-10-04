package com.resplan.service;

import com.resplan.domain.*;
import com.resplan.event.BookingReleasedEvent;
import com.resplan.event.ConflictDetectedEvent;
import com.resplan.repository.ResourceConflictRepository;
import com.resplan.repository.ResourceRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static com.resplan.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Жизненный цикл брони: BookingLifecycle")
class BookingLifecycleTest {

    @Mock
    ResourceConflictRepository conflicts;
    @Mock
    ResourceRequestRepository requests;
    @Mock
    ApplicationEventPublisher events;
    @InjectMocks
    BookingLifecycle lifecycle;

    AppUser rm;
    Employee zelinski;
    Project orion;
    ProjectBooking orionBooking;

    @BeforeEach
    void setUp() {
        rm = user(3, UserRole.RM);
        zelinski = employee(10, "Зелински", SENIOR, 28);
        orion = project(2, "ORION", user(1, UserRole.PM), 2);
        orionBooking = projectBooking(12, zelinski, orion, BookingKind.HARD, day(0), day(50), 80);
    }

    @Test
    @DisplayName("FR7-3: каждая новая перегрузка регистрируется как конфликт, известная пара пропускается")
    void registerOnlyNewConflicts() {
        Project helix = project(3, "HELIX", user(2, UserRole.PM), 2);
        ProjectBooking helixBooking = projectBooking(13, zelinski, helix, BookingKind.HARD, day(28), day(46), 40);
        ProjectBooking known = projectBooking(14, zelinski, helix, BookingKind.HARD, day(30), day(31), 30);
        when(conflicts.existsByBookingAAndBookingB(orionBooking, helixBooking)).thenReturn(false);
        when(conflicts.existsByBookingAAndBookingB(orionBooking, known)).thenReturn(true);
        when(conflicts.save(any())).then(returnsFirstArg());

        List<ResourceConflict> registered = lifecycle.registerConflicts(orionBooking, List.of(helixBooking, known));

        assertThat(registered).singleElement().satisfies(c -> {
            assertThat(c.getBookingA()).isSameAs(orionBooking);
            assertThat(c.getBookingB()).isSameAs(helixBooking);
            assertThat(c.getStatus()).isEqualTo(ConflictStatus.OPEN);
        });
        verify(events, times(1)).publishEvent(any(ConflictDetectedEvent.class));
    }

    @Test
    @DisplayName("FR7-4: снятие брони возвращает связанный запрос на подбор и закрывает конфликты")
    void releaseReturnsRequestToSearch() {
        Task audit = task(1, orion, "Аудит", 5);
        ResourceRequest request = request(3, audit, SENIOR, 40, day(28), day(46));
        ProjectBooking candidate = projectBooking(15, zelinski, orion, BookingKind.SOFT, day(28), day(46), 40);
        request.proposeCandidate(candidate);
        request.approve();
        ResourceConflict open = new ResourceConflict(orionBooking, candidate);
        when(requests.findByBooking(candidate)).thenReturn(Optional.of(request));
        when(conflicts.findOpenInvolving(candidate)).thenReturn(List.of(open));

        ResourceRequest returned = lifecycle.release(candidate, rm, "Проект отменен");

        assertThat(candidate.isActive()).isFalse();
        assertThat(audit.getAssignee()).isNull();
        assertThat(returned).isSameAs(request);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.SEARCHING);
        assertThat(open.getStatus()).isEqualTo(ConflictStatus.RESOLVED);
        assertThat(open.getResolutionNote()).isEqualTo("Проект отменен");
        ArgumentCaptor<BookingReleasedEvent> event = ArgumentCaptor.forClass(BookingReleasedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().request()).isSameAs(request);
        assertThat(event.getValue().reason()).isEqualTo("Проект отменен");
    }

    @Test
    @DisplayName("FR7-4: бронь без активного запроса снимается без изменения запросов")
    void releaseWithoutActiveRequest() {
        when(requests.findByBooking(orionBooking)).thenReturn(Optional.empty());

        assertThat(lifecycle.release(orionBooking, rm, "Снята RM")).isNull();
        assertThat(orionBooking.isActive()).isFalse();
    }

    @Test
    @DisplayName("FR9-1: снятие обучения не затрагивает запросы на ресурсы")
    void releaseTrainingSkipsRequests() {
        TrainingBooking training = training(20, zelinski, day(0), day(4), 100, true);

        lifecycle.release(training, rm, "Обучение отменено");

        assertThat(training.isActive()).isFalse();
        verifyNoInteractions(requests);
    }
}
