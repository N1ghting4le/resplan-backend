package com.resplan.service;

import com.resplan.domain.Booking;
import com.resplan.domain.RequestStatus;
import com.resplan.domain.ResourceRequest;
import com.resplan.error.BusinessRuleException;
import com.resplan.matching.Candidate;
import com.resplan.matching.CandidateMatcher;
import com.resplan.repository.BookingRepository;
import com.resplan.repository.EmployeeRepository;
import com.resplan.repository.ResourceRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** UC-5: интеллектуальный подбор кандидатов по запросу */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchingService {

    private final ResourceRequestRepository requests;
    private final EmployeeRepository employees;
    private final BookingRepository bookings;
    private final CandidateMatcher matcher;

    public List<Candidate> findCandidates(int requestId) {
        ResourceRequest request = requests.require(requestId, "Запрос");
        if (request.getStatus() != RequestStatus.SEARCHING && request.getStatus() != RequestStatus.EXTERNAL_HIRE) {
            throw new BusinessRuleException("STATUS", "Подбор выполняется только для запросов в статусе SEARCHING");
        }
        Map<Integer, List<Booking>> busy = bookings
                .findOverlapping(request.getStartDate(), request.getEndDate()).stream()
                .collect(Collectors.groupingBy(b -> b.getEmployee().getId()));
        return matcher.rank(request, employees.findActiveWithSkills(), busy);
    }
}
