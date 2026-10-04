package com.resplan.domain;

import org.junit.jupiter.api.Test;

import static com.resplan.domain.RequestStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

class RequestStatusTest {

    @Test
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
    void forbiddenTransitionsAreRejected() {
        assertThat(SEARCHING.canTransitionTo(APPROVED)).isFalse();
        assertThat(APPROVED.canTransitionTo(PENDING_PM)).isFalse();
        assertThat(CANCELLED.isFinal()).isTrue();
        assertThat(APPROVED.isFinal()).isFalse();
    }
}
