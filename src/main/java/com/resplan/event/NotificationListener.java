package com.resplan.event;

import com.resplan.domain.*;
import com.resplan.repository.AppUserRepository;
import com.resplan.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Наблюдатель доменных событий (паттерн «Наблюдатель»): формирует уведомления
 * пользователям. Сервисы публикуют события, не зная о получателях.
 */
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final AppUserRepository users;
    private final NotificationRepository notifications;

    @EventListener
    public void on(RequestSubmittedEvent e) {
        ResourceRequest r = e.request();
        notifyRole(UserRole.RM, "Новый запрос №%d: %s для проекта %s".formatted(r.getId(), r.getRole(), r.project().getCode()));
    }

    @EventListener
    public void on(CandidateProposedEvent e) {
        ResourceRequest r = e.request();
        String warning = e.overloads().isEmpty() ? "" : " Внимание: возможна перегрузка сотрудника.";
        notify(r.project().getPm(), "По запросу №%d предложен кандидат %s.%s"
                .formatted(r.getId(), r.getBooking().getEmployee().fullName(), warning));
    }

    @EventListener
    public void on(CandidateApprovedEvent e) {
        ResourceRequest r = e.request();
        Employee employee = r.getBooking().getEmployee();
        notifyRole(UserRole.RM, "Кандидат %s утвержден по запросу №%d, установлена жесткая бронь"
                .formatted(employee.fullName(), r.getId()));
        users.findByEmployee(employee).ifPresent(u -> notify(u, "Вы назначены на проект %s с %s по %s"
                .formatted(r.project().getCode(), r.getStartDate(), r.getEndDate())));
    }

    @EventListener
    public void on(CandidateRejectedEvent e) {
        notifyRole(UserRole.RM, "Кандидат %s отклонен по запросу №%d. Причина: %s"
                .formatted(e.employee().fullName(), e.request().getId(), e.reason()));
    }

    @EventListener
    public void on(ConflictDetectedEvent e) {
        ResourceConflict c = e.conflict();
        notifyRole(UserRole.RM, "Ресурсный конфликт: %s, брони %s и %s, загрузка %d %%"
                .formatted(c.getBookingA().getEmployee().fullName(), c.getBookingA().title(),
                        c.getBookingB().title(), c.totalLoad()));
    }

    @EventListener
    public void on(BookingAssignedEvent e) {
        Booking b = e.booking();
        notifyEmployee(b.getEmployee(), "В ваш календарь добавлено: %s, %s – %s, загрузка %d %%"
                .formatted(b.title(), b.getStartDate(), b.getEndDate(), b.getLoadPercent()));
    }

    @EventListener
    public void on(BookingReleasedEvent e) {
        Booking b = e.booking();
        notifyEmployee(b.getEmployee(), "Бронь %s (%s – %s) снята. Причина: %s"
                .formatted(b.title(), b.getStartDate(), b.getEndDate(), e.reason()));
        if (e.request() != null) {
            ResourceRequest r = e.request();
            notify(r.project().getPm(), "Бронь сотрудника %s по запросу №%d снята, запрос возвращен на подбор. Причина: %s"
                    .formatted(b.getEmployee().fullName(), r.getId(), e.reason()));
        }
    }

    /** FR6-3: PM проектов, участвующих в конфликте, узнают о принятом решении */
    @EventListener
    public void on(ConflictResolvedEvent e) {
        String message = "Конфликт №%d (%s) разрешен: сохранена бронь %s, снята бронь %s"
                .formatted(e.conflict().getId(), e.kept().getEmployee().fullName(), e.kept().title(), e.released().title());
        projectManagers(e.kept(), e.released()).forEach(pm -> notify(pm, message));
    }

    @EventListener
    public void on(ConflictEscalatedEvent e) {
        ResourceConflict c = e.conflict();
        String message = "Конфликт №%d (%s: %s и %s) эскалирован операционному директору: %s"
                .formatted(c.getId(), c.getBookingA().getEmployee().fullName(), c.getBookingA().title(),
                        c.getBookingB().title(), c.getResolutionNote());
        projectManagers(c.getBookingA(), c.getBookingB()).forEach(pm -> notify(pm, message));
    }

    /** PM проектов, к которым относятся брони, без повторов (обучение проекта не имеет) */
    private static Collection<AppUser> projectManagers(Booking... bookings) {
        Map<Integer, AppUser> managers = new LinkedHashMap<>();
        for (Booking b : bookings) {
            if (b instanceof ProjectBooking pb) managers.putIfAbsent(pb.getProject().getPm().getId(), pb.getProject().getPm());
        }
        return managers.values();
    }

    private void notifyEmployee(Employee employee, String message) {
        users.findByEmployee(employee).ifPresent(u -> notify(u, message));
    }

    private void notifyRole(UserRole role, String message) {
        users.findByRole(role).forEach(u -> notify(u, message));
    }

    private void notify(AppUser recipient, String message) {
        notifications.save(new Notification(recipient, message));
    }
}
