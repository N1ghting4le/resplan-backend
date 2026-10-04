package com.resplan.service;

import com.resplan.booking.BookingCheckResult;
import com.resplan.booking.BookingValidator;
import com.resplan.domain.*;
import com.resplan.error.AccessDeniedException;
import com.resplan.error.BusinessRuleException;
import com.resplan.error.IllegalTransitionException;
import com.resplan.event.*;
import com.resplan.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.resplan.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UC-2, UC-3, UC-5 Запросы на подбор кандидатов: ResourceRequestService")
class ResourceRequestServiceTest {

    @Mock
    ResourceRequestRepository requests;
    @Mock
    TaskRepository tasks;
    @Mock
    GradeRepository grades;
    @Mock
    SkillRepository skills;
    @Mock
    EmployeeRepository employees;
    @Mock
    BookingRepository bookings;
    @Mock
    BookingValidator validator;
    @Mock
    BookingLifecycle lifecycle;
    @Mock
    ApplicationEventPublisher events;

    ResourceRequestService service;
    AppUser pm;
    AppUser rm;
    Task rag;
    Employee aliev;
    ResourceRequest request;

    @BeforeEach
    void setUp() {
        service = new ResourceRequestService(requests, tasks, grades, skills, employees, bookings,
                validator, lifecycle, events);
        pm = user(1, UserRole.PM);
        rm = user(3, UserRole.RM);
        rag = task(5, project(1, "ATLAS", pm, 1), "RAG-модуль GenAI", 12);
        aliev = employee(4, "Алиев", MIDDLE, 20);
        request = request(1, rag, SENIOR, 100, day(15), day(30));
    }

    @Nested
    @DisplayName("UC-2 Создать запрос на подбор кандидата")
    class Submit {

        private NewRequestCommand command(Map<String, Integer> skillLevels) {
            return new NewRequestCommand(5, "ML-инженер (GenAI)", "Senior", 100, day(15), day(30), skillLevels);
        }

        @Test
        @DisplayName("FR2-1 – FR2-3: запрос по задаче создается в статусе SEARCHING, RM уведомляется")
        void submitCreatesSearchingRequest() {
            Map<String, Integer> levels = new LinkedHashMap<>();
            levels.put("llm", 4);
            levels.put("Java", 3);
            when(tasks.require(5, "Задача")).thenReturn(rag);
            when(grades.findByName("Senior")).thenReturn(Optional.of(SENIOR));
            when(skills.findByNameIgnoreCase("llm")).thenReturn(Optional.of(LLM));
            when(skills.findByNameIgnoreCase("Java")).thenReturn(Optional.of(JAVA));

            ResourceRequest created = service.submit(command(levels), pm);

            assertThat(created.getStatus()).isEqualTo(RequestStatus.SEARCHING);
            assertThat(created.getTask()).isSameAs(rag);
            assertThat(created.getGrade()).isSameAs(SENIOR);
            assertThat(created.getRequiredSkills()).extracting(s -> s.getSkill().getName()).containsExactly("LLM", "Java");
            verify(requests).save(created);
            ArgumentCaptor<RequestSubmittedEvent> event = ArgumentCaptor.forClass(RequestSubmittedEvent.class);
            verify(events).publishEvent(event.capture());
            assertThat(event.getValue().request()).isSameAs(created);
        }

        @Test
        @DisplayName("FR2-4: запрос без навыков не отправляется")
        void skillsAreRequired() {
            when(tasks.require(5, "Задача")).thenReturn(rag);

            assertThatThrownBy(() -> service.submit(command(Map.of()), pm))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("code", "SKILLS");
            assertThatThrownBy(() -> service.submit(command(null), pm))
                    .hasFieldOrPropertyWithValue("code", "SKILLS");
            verifyNoInteractions(requests, events);
        }

        @Test
        @DisplayName("FR2-4: неизвестный грейд или навык – запрос не создается")
        void unknownGradeOrSkill() {
            when(tasks.require(5, "Задача")).thenReturn(rag);
            when(grades.findByName("Senior")).thenReturn(Optional.empty(), Optional.of(SENIOR));
            when(skills.findByNameIgnoreCase("Cobol")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.submit(command(Map.of("Cobol", 3)), pm))
                    .hasFieldOrPropertyWithValue("code", "GRADE");
            assertThatThrownBy(() -> service.submit(command(Map.of("Cobol", 3)), pm))
                    .hasFieldOrPropertyWithValue("code", "SKILL");
            verify(requests, never()).save(any());
        }

        @Test
        @DisplayName("FR2-1: запрос по задаче чужого проекта запрещен")
        void foreignPmCannotSubmit() {
            when(tasks.require(5, "Задача")).thenReturn(rag);

            assertThatThrownBy(() -> service.submit(command(Map.of("LLM", 4)), user(2, UserRole.PM)))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("UC-2: отмена запроса снимает бронь предложенного кандидата")
        void cancelReleasesCandidateBooking() {
            ProjectBooking soft = projectBooking(30, aliev, rag.getProject(), BookingKind.SOFT, day(15), day(30), 100);
            request.proposeCandidate(soft);
            when(requests.require(1, "Запрос")).thenReturn(request);

            service.cancel(1, pm);

            assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
            verify(lifecycle).release(soft, pm, "Запрос №1 отменен");
        }

        @Test
        @DisplayName("UC-2: отмена запроса без кандидата не снимает броней")
        void cancelWithoutCandidate() {
            when(requests.require(1, "Запрос")).thenReturn(request);

            service.cancel(1, pm);

            verifyNoInteractions(lifecycle);
        }
    }

    @Nested
    @DisplayName("UC-5 Просматривать выборку кандидатов")
    class Proposal {

        @Test
        @DisplayName("FR5-1: RM видит все запросы, PM – только запросы своих проектов")
        void searchFiltersByPm() {
            when(requests.search(EnumSet.allOf(RequestStatus.class), null)).thenReturn(List.of(request));
            when(requests.search(Set.of(RequestStatus.SEARCHING), pm)).thenReturn(List.of(request));

            assertThat(service.search(null, rm)).containsExactly(request);
            assertThat(service.search(Set.of(), rm)).containsExactly(request);
            assertThat(service.search(Set.of(RequestStatus.SEARCHING), pm)).containsExactly(request);
        }

        @Test
        @DisplayName("FR3-1: PM открывает карточку только своего запроса")
        void pmSeesOnlyOwnRequest() {
            when(requests.require(1, "Запрос")).thenReturn(request);

            assertThat(service.get(1, pm)).isSameAs(request);
            assertThat(service.get(1, rm)).isSameAs(request);
            assertThatThrownBy(() -> service.get(1, user(2, UserRole.PM))).isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("FR5-2: предложение кандидата ставит мягкую бронь и уведомляет PM с предупреждением о перегрузке")
        void proposalCreatesSoftBooking() {
            ProjectBooking other = projectBooking(9, aliev, rag.getProject(), BookingKind.HARD, day(20), day(25), 50);
            when(requests.require(1, "Запрос")).thenReturn(request);
            when(employees.require(4, "Сотрудник")).thenReturn(aliev);
            when(validator.validate(any())).thenReturn(passed(other));
            when(bookings.save(any())).then(returnsFirstArg());

            ProposalResult result = service.propose(1, 4, rm);

            assertThat(result.request().getStatus()).isEqualTo(RequestStatus.PENDING_PM);
            ProjectBooking soft = request.getBooking();
            assertThat(soft.getKind()).isEqualTo(BookingKind.SOFT);
            assertThat(soft.getEmployee()).isSameAs(aliev);
            assertThat(soft.getStartDate()).isEqualTo(day(15));
            assertThat(soft.getLoadPercent()).isEqualTo((short) 100);
            assertThat(result.overloads()).containsExactly(other);
            verify(events).publishEvent(any(CandidateProposedEvent.class));
        }

        @Test
        @DisplayName("FR9-3: кандидат с защищенным обучением в периоде не предлагается")
        void proposalRespectsBookingRules() {
            BookingCheckResult rejected = mock(BookingCheckResult.class);
            when(rejected.throwIfRejected()).thenThrow(new BusinessRuleException("PROTECTED_TIME", "обучение"));
            when(requests.require(1, "Запрос")).thenReturn(request);
            when(employees.require(4, "Сотрудник")).thenReturn(aliev);
            when(validator.validate(any())).thenReturn(rejected);

            assertThatThrownBy(() -> service.propose(1, 4, rm)).hasFieldOrPropertyWithValue("code", "PROTECTED_TIME");
            assertThat(request.getStatus()).isEqualTo(RequestStatus.SEARCHING);
            verify(bookings, never()).save(any());
        }

        @Test
        @DisplayName("FR3-3: ранее отклоненного кандидата повторно предложить нельзя")
        void rejectedCandidateCannotBeProposed() {
            request.proposeCandidate(projectBooking(30, aliev, rag.getProject(), BookingKind.SOFT, day(15), day(30), 100));
            request.rejectCandidate("Не прошел собеседование", pm);
            when(requests.require(1, "Запрос")).thenReturn(request);
            when(employees.require(4, "Сотрудник")).thenReturn(aliev);

            assertThatThrownBy(() -> service.propose(1, 4, rm)).hasFieldOrPropertyWithValue("code", "REJECTED");
            verifyNoInteractions(validator);
        }

        @Test
        @DisplayName("FR5-3: запрос передается во внешний найм")
        void externalHire() {
            when(requests.require(1, "Запрос")).thenReturn(request);

            assertThat(service.sendToExternalHire(1).getStatus()).isEqualTo(RequestStatus.EXTERNAL_HIRE);
        }
    }

    @Nested
    @DisplayName("UC-3 Утвердить кандидата")
    class Decision {

        private ProjectBooking soft;

        @BeforeEach
        void propose() {
            soft = projectBooking(30, aliev, rag.getProject(), BookingKind.SOFT, day(15), day(30), 100);
            request.proposeCandidate(soft);
            when(requests.require(1, "Запрос")).thenReturn(request);
        }

        @Test
        @DisplayName("FR3-2: утверждение – жесткая бронь, регистрация конфликтов, уведомления")
        void approvalHardensBooking() {
            ProjectBooking orion = projectBooking(3, aliev, project(2, "ORION", pm, 2), BookingKind.HARD, day(20), day(40), 50);
            ResourceConflict conflict = new ResourceConflict(orion, soft);
            when(validator.validate(soft)).thenReturn(passed(orion));
            when(lifecycle.registerConflicts(soft, List.of(orion))).thenReturn(List.of(conflict));

            ApprovalResult result = service.approve(1, pm);

            assertThat(result.request().getStatus()).isEqualTo(RequestStatus.APPROVED);
            assertThat(soft.getKind()).isEqualTo(BookingKind.HARD);
            assertThat(rag.getAssignee()).isSameAs(aliev);
            assertThat(result.conflicts()).containsExactly(conflict);
            verify(events).publishEvent(any(CandidateApprovedEvent.class));
        }

        @Test
        @DisplayName("FR3-2: утвердить можно только запрос в статусе PENDING_PM")
        void approvalRequiresPendingStatus() {
            when(validator.validate(soft)).thenReturn(passed());
            service.approve(1, pm);

            assertThatThrownBy(() -> service.approve(1, pm)).isInstanceOf(IllegalTransitionException.class);
        }

        @Test
        @DisplayName("FR3-2: чужой PM не может утвердить кандидата")
        void foreignPmCannotApprove() {
            assertThatThrownBy(() -> service.approve(1, user(2, UserRole.PM))).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(validator);
        }

        @Test
        @DisplayName("FR3-3: отклонение с причиной снимает бронь и уведомляет RM")
        void rejectionReleasesBooking() {
            service.reject(1, " Нет опыта RAG ", pm);

            assertThat(request.getStatus()).isEqualTo(RequestStatus.SEARCHING);
            verify(lifecycle).release(soft, pm, "Кандидат отклонен PM");
            ArgumentCaptor<CandidateRejectedEvent> event = ArgumentCaptor.forClass(CandidateRejectedEvent.class);
            verify(events).publishEvent(event.capture());
            assertThat(event.getValue().reason()).isEqualTo("Нет опыта RAG");
            assertThat(event.getValue().employee()).isSameAs(aliev);
        }

        @Test
        @DisplayName("FR3-3: после утверждения кандидата отклонение запрещено, бронь не снимается")
        void approvedCandidateCannotBeRejected() {
            when(validator.validate(soft)).thenReturn(passed());
            service.approve(1, pm);

            assertThatThrownBy(() -> service.reject(1, "Передумали после утверждения", pm))
                    .isInstanceOf(IllegalTransitionException.class);
            assertThat(rag.getAssignee()).isSameAs(aliev);
            verify(lifecycle, never()).release(any(), any(), anyString());
        }

        @Test
        @DisplayName("FR3-3: отклонение без причины не выполняется")
        void rejectionWithoutReason() {
            assertThatThrownBy(() -> service.reject(1, "", pm)).hasFieldOrPropertyWithValue("code", "REASON");
            verify(lifecycle, never()).release(any(), any(), anyString());
        }
    }
}
