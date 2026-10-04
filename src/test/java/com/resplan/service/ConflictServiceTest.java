package com.resplan.service;

import com.resplan.domain.*;
import com.resplan.error.BusinessRuleException;
import com.resplan.event.ConflictEscalatedEvent;
import com.resplan.event.ConflictResolvedEvent;
import com.resplan.repository.BookingRepository;
import com.resplan.repository.ResourceConflictRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.resplan.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UC-6 Разрешить ресурсный конфликт: ConflictService")
class ConflictServiceTest {

    @Mock
    ResourceConflictRepository conflicts;
    @Mock
    BookingRepository bookings;
    @Mock
    BookingLifecycle lifecycle;
    @Mock
    ApplicationEventPublisher events;
    @InjectMocks
    ConflictService service;

    AppUser rm;
    ProjectBooking atlas;
    ProjectBooking orion;
    ResourceConflict conflict;

    @BeforeEach
    void setUp() {
        AppUser pm = user(1, UserRole.PM);
        rm = user(3, UserRole.RM);
        Employee kovalev = employee(1);
        atlas = projectBooking(1, kovalev, project(1, "ATLAS", pm, 1), BookingKind.HARD, day(9), day(22), 100);
        orion = projectBooking(2, kovalev, project(2, "ORION", pm, 2), BookingKind.HARD, day(14), day(32), 50);
        conflict = withId(new ResourceConflict(atlas, orion), 1);
    }

    @Test
    @DisplayName("FR6-1: панель по умолчанию показывает открытые и эскалированные конфликты")
    void defaultFilterIsOpenAndEscalated() {
        when(conflicts.findByStatusInOrderById(EnumSet.of(ConflictStatus.OPEN, ConflictStatus.ESCALATED)))
                .thenReturn(List.of(conflict));
        when(conflicts.findByStatusInOrderById(Set.of(ConflictStatus.RESOLVED))).thenReturn(List.of());

        assertThat(service.list(null)).containsExactly(conflict);
        assertThat(service.list(Set.of())).containsExactly(conflict);
        assertThat(service.list(Set.of(ConflictStatus.RESOLVED))).isEmpty();
    }

    @Test
    @DisplayName("FR6-1: карточка конфликта показывает приоритеты проектов и суммарную загрузку")
    void conflictCardShowsPriorities() {
        when(conflicts.require(1, "Конфликт")).thenReturn(conflict);

        ResourceConflict card = service.get(1);

        assertThat(((ProjectBooking) card.getBookingA()).getProject().getPriority()).isEqualTo((short) 1);
        assertThat(((ProjectBooking) card.getBookingB()).getProject().getPriority()).isEqualTo((short) 2);
        assertThat(card.totalLoad()).isEqualTo(150);
    }

    @Test
    @DisplayName("FR6-2, FR6-3: сохраняется бронь приоритетного проекта, вторая снимается, PM уведомляются")
    void resolveKeepsOneBookingAndReleasesOther() {
        when(conflicts.require(1, "Конфликт")).thenReturn(conflict);
        when(bookings.require(1L, "Бронирование")).thenReturn(atlas);

        service.resolve(1, 1L, "  ", rm);

        assertThat(conflict.getStatus()).isEqualTo(ConflictStatus.RESOLVED);
        assertThat(conflict.getResolutionNote()).isEqualTo("Сохранена бронь ATLAS");
        verify(lifecycle).release(orion, rm, "Сохранена бронь ATLAS");
        ArgumentCaptor<ConflictResolvedEvent> event = ArgumentCaptor.forClass(ConflictResolvedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().kept()).isSameAs(atlas);
        assertThat(event.getValue().released()).isSameAs(orion);
    }

    @Test
    @DisplayName("FR6-2: комментарий RM сохраняется как решение по конфликту")
    void resolveWithNote() {
        when(conflicts.require(1, "Конфликт")).thenReturn(conflict);
        when(bookings.require(2L, "Бронирование")).thenReturn(orion);

        service.resolve(1, 2L, " Перенос ATLAS согласован с заказчиком ", rm);

        assertThat(conflict.getResolutionNote()).isEqualTo("Перенос ATLAS согласован с заказчиком");
        verify(lifecycle).release(atlas, rm, "Перенос ATLAS согласован с заказчиком");
    }

    @Test
    @DisplayName("FR6-2: повторное разрешение закрытого конфликта запрещено")
    void resolvedConflictCannotBeResolvedAgain() {
        conflict.resolve("решено", rm);
        when(conflicts.require(1, "Конфликт")).thenReturn(conflict);
        when(bookings.require(1L, "Бронирование")).thenReturn(atlas);

        assertThatThrownBy(() -> service.resolve(1, 1L, null, rm))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "CONFLICT_CLOSED");
        verifyNoInteractions(lifecycle, events);
    }

    @Test
    @DisplayName("FR6-4: эскалация передает конфликт операционному директору с уведомлением PM")
    void escalate() {
        when(conflicts.require(1, "Конфликт")).thenReturn(conflict);

        service.escalate(1, "Оба проекта имеют высший приоритет");

        assertThat(conflict.getStatus()).isEqualTo(ConflictStatus.ESCALATED);
        verify(events).publishEvent(any(ConflictEscalatedEvent.class));
        verifyNoInteractions(lifecycle);
    }
}
