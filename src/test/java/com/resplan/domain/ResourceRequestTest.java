package com.resplan.domain;

import com.resplan.error.BusinessRuleException;
import com.resplan.error.IllegalTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.resplan.support.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Запрос на ресурс (ResourceRequest)")
class ResourceRequestTest {

    private AppUser pm;
    private Task rag;
    private Employee aliev;
    private ResourceRequest request;
    private ProjectBooking soft;

    @BeforeEach
    void setUp() {
        pm = user(1, UserRole.PM);
        Project atlas = project(1, "ATLAS", pm, 1);
        rag = task(5, atlas, "RAG-модуль GenAI", 12);
        aliev = employee(4, "Алиев", MIDDLE, 20);
        request = request(1, rag, SENIOR, 100, day(15), day(30));
        soft = projectBooking(30, aliev, atlas, BookingKind.SOFT, day(15), day(30), 100);
    }

    @Nested
    @DisplayName("UC-2 Создать запрос на подбор кандидата")
    class Submit {

        @Test
        @DisplayName("FR2-3: новый запрос получает статус SEARCHING («Поиск кандидатов»)")
        void newRequestIsSearching() {
            request.requireSkill(LLM, 4);

            assertThat(request.getStatus()).isEqualTo(RequestStatus.SEARCHING);
            assertThat(request.isActive()).isFalse();
            assertThat(request.project().getCode()).isEqualTo("ATLAS");
            assertThat(request.getRequiredSkills()).singleElement()
                    .satisfies(s -> assertThat(s.getLevel()).isEqualTo((short) 4));
        }

        @Test
        @DisplayName("FR2-4: дата окончания раньше даты начала – запрос не создается")
        void periodIsValidated() {
            assertThatThrownBy(() -> request(2, rag, SENIOR, 50, day(10), day(9)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("code", "PERIOD");
        }

        @ParameterizedTest(name = "загрузка {0} %")
        @ValueSource(ints = {0, -5, 101})
        @DisplayName("FR2-4: загрузка вне диапазона 1–100 % – запрос не создается")
        void loadIsValidated(int load) {
            assertThatThrownBy(() -> request(2, rag, SENIOR, load, day(10), day(12)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("code", "LOAD");
        }
    }

    @Nested
    @DisplayName("UC-3 Утвердить кандидата")
    class Approve {

        @BeforeEach
        void propose() {
            request.proposeCandidate(soft);
        }

        @Test
        @DisplayName("UC-5: предложение кандидата переводит запрос в PENDING_PM")
        void proposalWaitsForPm() {
            assertThat(request.getStatus()).isEqualTo(RequestStatus.PENDING_PM);
            assertThat(request.getBooking()).isSameAs(soft);
            assertThat(request.isActive()).isTrue();
        }

        @Test
        @DisplayName("FR3-2: утверждение делает бронь жесткой и назначает исполнителя задачи")
        void approvalHardensBookingAndAssignsTask() {
            ProjectBooking hard = request.approve();

            assertThat(request.getStatus()).isEqualTo(RequestStatus.APPROVED);
            assertThat(hard.getKind()).isEqualTo(BookingKind.HARD);
            assertThat(rag.getAssignee()).isSameAs(aliev);
        }

        @Test
        @DisplayName("FR3-2: повторное утверждение – недопустимый переход статуса")
        void secondApprovalIsIllegal() {
            request.approve();

            assertThatThrownBy(request::approve).isInstanceOf(IllegalTransitionException.class)
                    .hasMessageContaining("APPROVED");
        }

        @ParameterizedTest(name = "причина «{0}»")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "нет", " abc "})
        @DisplayName("FR3-3: отклонение без причины (короче 5 символов) запрещено")
        void rejectionRequiresReason(String reason) {
            assertThatThrownBy(() -> request.rejectCandidate(reason, pm))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("code", "REASON");
            assertThat(request.getStatus()).isEqualTo(RequestStatus.PENDING_PM);
        }

        @Test
        @DisplayName("FR3-3: отклонение возвращает запрос на подбор и запоминает кандидата")
        void rejectionReturnsRequestToSearch() {
            ProjectBooking released = request.rejectCandidate("  Нет опыта с RAG  ", pm);

            assertThat(released).isSameAs(soft);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.SEARCHING);
            assertThat(request.getBooking()).isNull();
            assertThat(request.rejectedEmployeeIds()).containsExactly(4);
            assertThat(request.getRejections()).singleElement()
                    .satisfies(r -> assertThat(r.getReason()).isEqualTo("Нет опыта с RAG"));
        }

        @Test
        @DisplayName("FR3-3: утвержденного кандидата отклонить нельзя – решение по нему уже принято")
        void approvedCandidateCannotBeRejected() {
            request.approve();

            assertThatThrownBy(() -> request.rejectCandidate("Передумали после утверждения", pm))
                    .isInstanceOf(IllegalTransitionException.class)
                    .hasMessageContaining("APPROVED");
            assertThat(request.getStatus()).isEqualTo(RequestStatus.APPROVED);
            assertThat(request.getRejections()).isEmpty();
            assertThat(rag.getAssignee()).isSameAs(aliev);
        }

        @Test
        @DisplayName("FR7-4: снятие брони утвержденного кандидата освобождает задачу")
        void releasedBookingUnassignsTask() {
            request.approve();

            request.bookingReleased();

            assertThat(request.getStatus()).isEqualTo(RequestStatus.SEARCHING);
            assertThat(request.getBooking()).isNull();
            assertThat(rag.getAssignee()).isNull();
        }

        @Test
        @DisplayName("FR6-2: снятие мягкой брони кандидата при разрешении конфликта возвращает запрос на подбор")
        void releasedSoftBookingReturnsRequestToSearch() {
            request.bookingReleased();

            assertThat(request.getStatus()).isEqualTo(RequestStatus.SEARCHING);
            assertThat(request.getBooking()).isNull();
            assertThat(rag.getAssignee()).isNull();
        }

        @Test
        @DisplayName("UC-2: отмена утвержденного запроса возвращает бронь для снятия")
        void cancelApprovedRequest() {
            request.approve();

            ProjectBooking released = request.cancel();

            assertThat(released).isSameAs(soft);
            assertThat(request.getStatus()).isEqualTo(RequestStatus.CANCELLED);
            assertThat(rag.getAssignee()).isNull();
            assertThat(request.getStatus().isFinal()).isTrue();
        }
    }

    @Nested
    @DisplayName("UC-5 Просматривать выборку кандидатов")
    class ExternalHire {

        @Test
        @DisplayName("FR5-3: запрос без кандидатов передается во внешний найм")
        void searchingRequestGoesToExternalHire() {
            request.sendToExternalHire();

            assertThat(request.getStatus()).isEqualTo(RequestStatus.EXTERNAL_HIRE);
            assertThat(request.cancel()).isNull();
        }

        @Test
        @DisplayName("FR5-3: запрос с предложенным кандидатом во внешний найм не передается")
        void pendingRequestCannotGoToExternalHire() {
            request.proposeCandidate(soft);

            assertThatThrownBy(request::sendToExternalHire).isInstanceOf(IllegalTransitionException.class);
        }
    }
}
