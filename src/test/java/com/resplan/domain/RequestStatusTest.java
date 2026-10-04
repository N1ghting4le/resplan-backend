package com.resplan.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.resplan.domain.RequestStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Статусы запроса на ресурс (RequestStatus)")
class RequestStatusTest {

    @Test
    @DisplayName("FR2-3, FR3-2: разрешенные переходы соответствуют диаграмме состояний")
    void allowedTransitionsMatchStateDiagram() {
        assertThat(SEARCHING.canTransitionTo(PENDING_PM)).isTrue();
        assertThat(SEARCHING.canTransitionTo(EXTERNAL_HIRE)).isTrue();
        assertThat(PENDING_PM.canTransitionTo(APPROVED)).isTrue();
        assertThat(PENDING_PM.canTransitionTo(SEARCHING)).isTrue();
        assertThat(EXTERNAL_HIRE.canTransitionTo(PENDING_PM)).isTrue();
        assertThat(APPROVED.canTransitionTo(CANCELLED)).isTrue();
        assertThat(APPROVED.canTransitionTo(SEARCHING)).isTrue(); // бронь снята RM (UC-6, UC-7)
    }

    @Test
    @DisplayName("FR3-2: запрещенные переходы отклоняются, CANCELLED – конечный статус")
    void forbiddenTransitionsAreRejected() {
        assertThat(SEARCHING.canTransitionTo(APPROVED)).isFalse();
        assertThat(APPROVED.canTransitionTo(PENDING_PM)).isFalse();
        assertThat(CANCELLED.isFinal()).isTrue();
        assertThat(APPROVED.isFinal()).isFalse();
    }
}
