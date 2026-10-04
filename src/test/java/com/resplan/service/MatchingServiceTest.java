package com.resplan.service;

import com.resplan.domain.*;
import com.resplan.error.BusinessRuleException;
import com.resplan.matching.Candidate;
import com.resplan.matching.CandidateMatcher;
import com.resplan.repository.BookingRepository;
import com.resplan.repository.EmployeeRepository;
import com.resplan.repository.ResourceRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static com.resplan.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UC-5 Подбор кандидатов по запросу: MatchingService")
class MatchingServiceTest {

    @Mock
    ResourceRequestRepository requests;
    @Mock
    EmployeeRepository employees;
    @Mock
    BookingRepository bookings;
    @Mock
    CandidateMatcher matcher;
    @InjectMocks
    MatchingService service;

    ResourceRequest request;
    Project atlas;

    @BeforeEach
    void setUp() {
        atlas = project(1, "ATLAS", user(1, UserRole.PM), 1);
        request = request(1, task(5, atlas, "RAG-модуль GenAI", 12), SENIOR, 100, day(15), day(30));
    }

    @Test
    @DisplayName("FR5-2: брони сотрудников в периоде запроса группируются и передаются алгоритму подбора")
    void bookingsAreGroupedByEmployee() {
        Employee kovalev = employee(1);
        Employee chen = employee(2);
        Booking b1 = projectBooking(1, kovalev, atlas, BookingKind.HARD, day(10), day(20), 50);
        Booking b2 = projectBooking(2, kovalev, atlas, BookingKind.HARD, day(25), day(40), 50);
        Booking b3 = training(3, chen, day(16), day(18), 100, false);
        Candidate best = new Candidate(chen, 87, Map.of());
        when(requests.require(1, "Запрос")).thenReturn(request);
        when(bookings.findOverlapping(day(15), day(30))).thenReturn(List.of(b1, b2, b3));
        when(employees.findActiveWithSkills()).thenReturn(List.of(kovalev, chen));
        when(matcher.rank(request, List.of(kovalev, chen), Map.of(1, List.of(b1, b2), 2, List.of(b3))))
                .thenReturn(List.of(best));

        assertThat(service.findCandidates(1)).containsExactly(best);
    }

    @Test
    @DisplayName("FR5-3: для запроса во внешнем найме подбор по внутреннему пулу продолжается")
    void externalHireRequestIsMatched() {
        request.sendToExternalHire();
        when(requests.require(1, "Запрос")).thenReturn(request);

        assertThat(service.findCandidates(1)).isEmpty();
        verify(matcher).rank(eq(request), anyList(), anyMap());
    }

    @Test
    @DisplayName("FR5-2: для запроса с предложенным кандидатом подбор не выполняется")
    void pendingRequestIsNotMatched() {
        request.proposeCandidate(projectBooking(9, employee(4), atlas, BookingKind.SOFT, day(15), day(30), 100));
        when(requests.require(1, "Запрос")).thenReturn(request);

        assertThatThrownBy(() -> service.findCandidates(1))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "STATUS");
        verifyNoInteractions(matcher, bookings);
    }
}
