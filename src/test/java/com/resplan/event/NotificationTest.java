package com.resplan.event;

import com.resplan.domain.*;
import com.resplan.error.NotFoundException;
import com.resplan.repository.AppUserRepository;
import com.resplan.repository.NotificationRepository;
import com.resplan.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.resplan.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Уведомления: NotificationListener, NotificationService")
class NotificationTest {

    @Mock
    AppUserRepository users;
    @Mock
    NotificationRepository notifications;

    AppUser pm;
    AppUser pm2;
    AppUser rm;
    AppUser emp;
    Employee kovalev;
    ProjectBooking atlasBooking;
    ProjectBooking orionBooking;
    ProjectBooking helixBooking;

    @BeforeEach
    void setUp() {
        pm = user(1, UserRole.PM);
        pm2 = user(2, UserRole.PM);
        rm = user(3, UserRole.RM);
        kovalev = employee(1, "Ковалёв", MIDDLE, 22);
        emp = withId(new AppUser("emp", "hash", UserRole.EMP, kovalev), 5);
        atlasBooking = projectBooking(1, kovalev, project(1, "ATLAS", pm, 1), BookingKind.HARD, day(9), day(22), 100);
        orionBooking = projectBooking(2, kovalev, project(2, "ORION", pm, 2), BookingKind.HARD, day(14), day(32), 50);
        helixBooking = projectBooking(3, kovalev, project(3, "HELIX", pm2, 2), BookingKind.HARD, day(14), day(32), 50);
    }

    private List<Notification> sent(int times) {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, times(times)).save(captor.capture());
        return captor.getAllValues();
    }

    @Nested
    @DisplayName("NotificationListener (наблюдатель доменных событий)")
    class Listener {

        NotificationListener listener;

        @BeforeEach
        void setUp() {
            listener = new NotificationListener(users, notifications);
        }

        @Test
        @DisplayName("FR6-3: о разрешении конфликта уведомляется PM проектов – один раз, без повторов")
        void resolvedConflictNotifiesPmOnce() {
            ResourceConflict conflict = withId(new ResourceConflict(atlasBooking, orionBooking), 1);

            listener.on(new ConflictResolvedEvent(conflict, atlasBooking, orionBooking));

            assertThat(sent(1)).singleElement().satisfies(n -> {
                assertThat(n.getRecipient()).isSameAs(pm);
                assertThat(n.getMessage()).isEqualTo(
                        "Конфликт №1 (Ковалёв Тест) разрешен: сохранена бронь ATLAS, снята бронь ORION");
            });
        }

        @Test
        @DisplayName("FR6-3, FR6-4: об эскалации уведомляются PM обоих проектов")
        void escalationNotifiesBothPms() {
            ResourceConflict conflict = withId(new ResourceConflict(atlasBooking, helixBooking), 2);
            conflict.escalate("Равный приоритет");

            listener.on(new ConflictEscalatedEvent(conflict));

            assertThat(sent(2)).extracting(Notification::getRecipient).containsExactly(pm, pm2);
        }

        @Test
        @DisplayName("FR6-3: конфликт с обучением – уведомляется только PM проекта")
        void trainingHasNoPm() {
            TrainingBooking training = training(7, kovalev, day(14), day(16), 100, false);
            ResourceConflict conflict = withId(new ResourceConflict(atlasBooking, training), 3);

            listener.on(new ConflictResolvedEvent(conflict, training, atlasBooking));

            assertThat(sent(1)).extracting(Notification::getRecipient).containsExactly(pm);
        }

        @Test
        @DisplayName("FR2-3: о новом запросе уведомляются все ресурсные менеджеры")
        void newRequestNotifiesAllRms() {
            AppUser rm2 = user(6, UserRole.RM);
            ResourceRequest request = request(4, task(5, atlasBooking.getProject(), "RAG", 12), SENIOR, 100, day(0), day(9));
            when(users.findByRole(UserRole.RM)).thenReturn(List.of(rm, rm2));

            listener.on(new RequestSubmittedEvent(request));

            assertThat(sent(2)).extracting(Notification::getMessage)
                    .containsOnly("Новый запрос №4: Java-разработчик для проекта ATLAS");
        }

        @Test
        @DisplayName("FR3-2: при утверждении уведомляются RM и назначенный сотрудник")
        void approvalNotifiesRmAndEmployee() {
            ResourceRequest request = request(4, task(5, atlasBooking.getProject(), "RAG", 12), SENIOR, 100, day(0), day(9));
            request.proposeCandidate(projectBooking(9, kovalev, atlasBooking.getProject(), BookingKind.SOFT, day(0), day(9), 100));
            request.approve();
            when(users.findByRole(UserRole.RM)).thenReturn(List.of(rm));
            when(users.findByEmployee(kovalev)).thenReturn(Optional.of(emp));

            listener.on(new CandidateApprovedEvent(request));

            List<Notification> messages = sent(2);
            assertThat(messages.get(1).getRecipient()).isSameAs(emp);
            assertThat(messages.get(1).getMessage()).startsWith("Вы назначены на проект ATLAS");
        }

        @Test
        @DisplayName("FR5-2: PM получает предложение кандидата с предупреждением о перегрузке")
        void proposalWarnsAboutOverload() {
            ResourceRequest request = request(4, task(5, atlasBooking.getProject(), "RAG", 12), SENIOR, 100, day(0), day(9));
            request.proposeCandidate(projectBooking(9, kovalev, atlasBooking.getProject(), BookingKind.SOFT, day(0), day(9), 100));

            listener.on(new CandidateProposedEvent(request, List.of(orionBooking)));
            listener.on(new CandidateProposedEvent(request, List.of()));

            List<Notification> messages = sent(2);
            assertThat(messages.get(0).getMessage()).endsWith("Внимание: возможна перегрузка сотрудника.");
            assertThat(messages.get(1).getMessage()).isEqualTo("По запросу №4 предложен кандидат Ковалёв Тест.");
        }

        @Test
        @DisplayName("FR7-4: о снятии брони уведомляются сотрудник и PM запроса")
        void releaseNotifiesEmployeeAndPm() {
            ResourceRequest request = request(4, task(5, atlasBooking.getProject(), "RAG", 12), SENIOR, 100, day(0), day(9));
            when(users.findByEmployee(kovalev)).thenReturn(Optional.of(emp));

            listener.on(new BookingReleasedEvent(atlasBooking, request, "Проект отменен"));
            listener.on(new BookingReleasedEvent(orionBooking, null, "Снята RM"));

            assertThat(sent(3)).extracting(Notification::getRecipient).containsExactly(emp, pm, emp);
        }

        @Test
        @DisplayName("FR7-3: о новой брони и конфликте уведомляются сотрудник и RM")
        void assignmentAndConflictNotifications() {
            when(users.findByEmployee(kovalev)).thenReturn(Optional.empty());
            when(users.findByRole(UserRole.RM)).thenReturn(List.of(rm));

            listener.on(new BookingAssignedEvent(atlasBooking));
            listener.on(new ConflictDetectedEvent(new ResourceConflict(atlasBooking, orionBooking)));
            listener.on(new CandidateRejectedEvent(
                    request(4, task(5, atlasBooking.getProject(), "RAG", 12), SENIOR, 100, day(0), day(9)),
                    kovalev, "Нет опыта"));

            assertThat(sent(2)).extracting(Notification::getMessage).containsExactly(
                    "Ресурсный конфликт: Ковалёв Тест, брони ATLAS и ORION, загрузка 150 %",
                    "Кандидат Ковалёв Тест отклонен по запросу №4. Причина: Нет опыта");
        }
    }

    @Nested
    @DisplayName("NotificationService")
    class Service {

        NotificationService service;

        @BeforeEach
        void setUp() {
            service = new NotificationService(notifications);
        }

        @Test
        @DisplayName("FR6-3: пользователь отмечает свое уведомление прочитанным")
        void markOwnNotificationRead() {
            Notification n = new Notification(pm, "Конфликт разрешен");
            when(notifications.findById(10L)).thenReturn(Optional.of(n));

            assertThat(service.markRead(10, pm).isRead()).isTrue();
        }

        @Test
        @DisplayName("FR6-3: чужое уведомление для пользователя не существует (404)")
        void foreignNotificationIsHidden() {
            when(notifications.findById(10L)).thenReturn(Optional.of(new Notification(pm, "Конфликт разрешен")));

            assertThatThrownBy(() -> service.markRead(10, pm2)).isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("FR6-3: список уведомлений – все или только непрочитанные")
        void listNotifications() {
            service.list(pm, true);
            service.list(pm, false);

            verify(notifications).findByRecipientAndReadFalseOrderByIdDesc(pm);
            verify(notifications).findByRecipientOrderByIdDesc(pm);
            verify(notifications, never()).save(any());
        }
    }
}
