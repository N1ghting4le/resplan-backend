package com.resplan.domain;

import com.resplan.error.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.resplan.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Ресурсный конфликт (ResourceConflict)")
class ResourceConflictTest {

    private ProjectBooking atlas;
    private ProjectBooking orion;
    private ResourceConflict conflict;
    private AppUser rm;

    @BeforeEach
    void setUp() {
        AppUser pm = user(1, UserRole.PM);
        rm = user(3, UserRole.RM);
        Employee kovalev = employee(1);
        atlas = projectBooking(7, kovalev, project(1, "ATLAS", pm, 1), BookingKind.HARD, day(9), day(22), 100);
        orion = projectBooking(3, kovalev, project(2, "ORION", pm, 2), BookingKind.HARD, day(14), day(32), 50);
        conflict = withId(new ResourceConflict(atlas, orion), 1);
    }

    @Test
    @DisplayName("FR6-1: пара броней хранится упорядоченно по идентификатору, суммарная загрузка 150 %")
    void pairIsOrderedById() {
        assertThat(conflict.getBookingA()).isSameAs(orion);
        assertThat(conflict.getBookingB()).isSameAs(atlas);
        assertThat(conflict.getStatus()).isEqualTo(ConflictStatus.OPEN);
        assertThat(conflict.totalLoad()).isEqualTo(150);
        assertThat(new ResourceConflict(orion, atlas).getBookingA()).isSameAs(orion);
    }

    @Test
    @DisplayName("FR6-2: для сохраняемой брони определяется снимаемая бронь пары")
    void otherBookingOfPair() {
        assertThat(conflict.other(atlas)).isSameAs(orion);
        assertThat(conflict.other(orion)).isSameAs(atlas);
    }

    @Test
    @DisplayName("FR6-2: бронь, не участвующая в конфликте, отклоняется")
    void foreignBookingIsRejected() {
        ProjectBooking foreign = projectBooking(99, employee(2), atlas.getProject(), BookingKind.HARD, day(0), day(1), 10);

        assertThatThrownBy(() -> conflict.other(foreign))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "BOOKING");
    }

    @Test
    @DisplayName("FR6-2: разрешение фиксирует решение, автора и время")
    void resolveClosesConflict() {
        conflict.resolve("Сохранена бронь ATLAS", rm);

        assertThat(conflict.getStatus()).isEqualTo(ConflictStatus.RESOLVED);
        assertThat(conflict.getResolutionNote()).isEqualTo("Сохранена бронь ATLAS");
        assertThat(conflict.getResolvedBy()).isSameAs(rm);
        assertThat(conflict.getResolvedAt()).isNotNull();
    }

    @ParameterizedTest(name = "причина «{0}»")
    @NullAndEmptySource
    @ValueSource(strings = "  ")
    @DisplayName("FR6-4: эскалация без причины запрещена")
    void escalationRequiresNote(String note) {
        assertThatThrownBy(() -> conflict.escalate(note))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "NOTE");
        assertThat(conflict.getStatus()).isEqualTo(ConflictStatus.OPEN);
    }

    @Test
    @DisplayName("FR6-4: эскалированный конфликт нельзя разрешить или эскалировать повторно")
    void escalatedConflictIsClosedForRm() {
        conflict.escalate("Оба проекта критичны для заказчика");

        assertThat(conflict.getStatus()).isEqualTo(ConflictStatus.ESCALATED);
        assertThatThrownBy(() -> conflict.resolve("решение", rm))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "CONFLICT_CLOSED");
        assertThatThrownBy(() -> conflict.escalate("еще раз"))
                .hasFieldOrPropertyWithValue("code", "CONFLICT_CLOSED");
    }
}
