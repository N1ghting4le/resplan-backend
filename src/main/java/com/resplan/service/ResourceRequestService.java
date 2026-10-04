package com.resplan.service;

import com.resplan.booking.BookingCheckResult;
import com.resplan.booking.BookingValidator;
import com.resplan.domain.*;
import com.resplan.error.BusinessRuleException;
import com.resplan.error.IllegalTransitionException;
import com.resplan.event.*;
import com.resplan.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

/**
 * Жизненный цикл запроса на ресурс: создание (UC-2), предложение кандидата (UC-5),
 * утверждение и отклонение кандидата (UC-3). Проверка роли выполняется в контроллере (@PreAuthorize),
 * здесь – только правила предметной области.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ResourceRequestService {

    private final ResourceRequestRepository requests;
    private final TaskRepository tasks;
    private final GradeRepository grades;
    private final SkillRepository skills;
    private final EmployeeRepository employees;
    private final BookingRepository bookings;
    private final BookingValidator validator;
    private final BookingLifecycle lifecycle;
    private final ApplicationEventPublisher events;

    /** UC-5 (FR5-1): входящие запросы для RM; для PM – только запросы его проектов */
    @Transactional(readOnly = true)
    public List<ResourceRequest> search(Collection<RequestStatus> statuses, AppUser user) {
        Collection<RequestStatus> filter = statuses == null || statuses.isEmpty()
                ? EnumSet.allOf(RequestStatus.class) : statuses;
        return requests.search(filter, user.hasRole(UserRole.PM) ? user : null);
    }

    @Transactional(readOnly = true)
    public ResourceRequest get(int requestId, AppUser user) {
        ResourceRequest request = requests.require(requestId, "Запрос");
        if (user.hasRole(UserRole.PM)) request.project().requireManagedBy(user, "Просматривать запрос");
        return request;
    }

    /** UC-2: PM создает запрос на подбор кандидата */
    public ResourceRequest submit(NewRequestCommand cmd, AppUser pm) {
        Task task = tasks.require(cmd.taskId(), "Задача");
        task.getProject().requireManagedBy(pm, "Создать запрос");
        if (cmd.skills() == null || cmd.skills().isEmpty()) {
            throw new BusinessRuleException("SKILLS", "Укажите хотя бы один требуемый навык");
        }
        Grade grade = grades.findByName(cmd.grade())
                .orElseThrow(() -> new BusinessRuleException("GRADE", "Неизвестный грейд " + cmd.grade()));
        ResourceRequest request = new ResourceRequest(task, cmd.role(), grade, cmd.loadPercent(),
                cmd.startDate(), cmd.endDate(), pm);
        cmd.skills().forEach((name, level) -> request.requireSkill(skills.findByNameIgnoreCase(name)
                .orElseThrow(() -> new BusinessRuleException("SKILL", "Неизвестный навык " + name)), level));
        requests.save(request);
        events.publishEvent(new RequestSubmittedEvent(request));
        return request;
    }

    /** UC-5: RM предлагает кандидата, на него ставится мягкая бронь */
    public ProposalResult propose(int requestId, int employeeId, AppUser rm) {
        ResourceRequest request = requests.require(requestId, "Запрос");
        Employee employee = employees.require(employeeId, "Сотрудник");
        if (request.rejectedEmployeeIds().contains(employeeId)) {
            throw new BusinessRuleException("REJECTED", "Кандидат уже отклонен по этому запросу");
        }
        ProjectBooking soft = new ProjectBooking(employee, request.project(), BookingKind.SOFT,
                request.getStartDate(), request.getEndDate(), request.getLoadPercent(), rm);
        BookingCheckResult check = validator.validate(soft).throwIfRejected();
        request.proposeCandidate(bookings.save(soft));
        events.publishEvent(new CandidateProposedEvent(request, check.getOverloads()));
        return new ProposalResult(request, check.getOverloads());
    }

    /** UC-3: PM утверждает кандидата – мягкая бронь становится жесткой, перегрузки регистрируются */
    public ApprovalResult approve(int requestId, AppUser pm) {
        ResourceRequest request = requests.require(requestId, "Запрос");
        request.project().requireManagedBy(pm, "Утвердить кандидата");
        if (request.getStatus() != RequestStatus.PENDING_PM) {
            throw new IllegalTransitionException(request.getStatus(), RequestStatus.APPROVED);
        }
        BookingCheckResult check = validator.validate(request.getBooking()).throwIfRejected();

        ProjectBooking hard = request.approve();
        List<ResourceConflict> registered = lifecycle.registerConflicts(hard, check.getOverloads());
        events.publishEvent(new CandidateApprovedEvent(request));
        return new ApprovalResult(request, registered);
    }

    /** Альтернативный поток UC-3: PM отклоняет кандидата с указанием причины */
    public ResourceRequest reject(int requestId, String reason, AppUser pm) {
        ResourceRequest request = requests.require(requestId, "Запрос");
        request.project().requireManagedBy(pm, "Отклонить кандидата");
        ProjectBooking released = request.rejectCandidate(reason, pm);
        lifecycle.release(released, pm, "Кандидат отклонен PM");
        events.publishEvent(new CandidateRejectedEvent(request, released.getEmployee(), reason.trim()));
        return request;
    }

    /** Альтернативный поток UC-5 (FR5-3): подходящих сотрудников нет, запрос передается во внешний найм */
    public ResourceRequest sendToExternalHire(int requestId) {
        ResourceRequest request = requests.require(requestId, "Запрос");
        request.sendToExternalHire();
        return request;
    }

    /** Отмена запроса проектным менеджером: бронь кандидата снимается */
    public ResourceRequest cancel(int requestId, AppUser pm) {
        ResourceRequest request = requests.require(requestId, "Запрос");
        request.project().requireManagedBy(pm, "Отменить запрос");
        ProjectBooking released = request.cancel();
        if (released != null) lifecycle.release(released, pm, "Запрос №" + requestId + " отменен");
        return request;
    }
}
