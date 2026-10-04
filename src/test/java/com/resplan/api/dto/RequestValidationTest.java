package com.resplan.api.dto;

import com.resplan.domain.BookingKind;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверка полей входных DTO (Bean Validation) – основа подсветки полей формы на клиенте */
@DisplayName("Проверка полей запросов REST API (Bean Validation)")
class RequestValidationTest {

    private static final LocalDate START = LocalDate.parse("2026-11-23");
    private static final LocalDate END = LocalDate.parse("2026-11-27");
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static Set<String> invalidFields(Object dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(p -> p.toString().replaceAll("[\\[<].*", ""))
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("FR4-1: логин и пароль обязательны")
    void loginRequiresCredentials() {
        assertThat(invalidFields(new LoginRequest("pm", "resplan"))).isEmpty();
        assertThat(invalidFields(new LoginRequest(" ", null))).containsExactlyInAnyOrder("login", "password");
    }

    @Test
    @DisplayName("FR1-2, FR1-5: задача – непустое название и длительность 0–500 дней")
    void taskRequestFields() {
        assertThat(invalidFields(new TaskRequest("Нагрузочное тестирование", 4, List.of(7)))).isEmpty();
        assertThat(invalidFields(new TaskRequest("", -2, null))).containsExactlyInAnyOrder("name", "durationDays");
        assertThat(invalidFields(new TaskRequest("Х".repeat(151), 501, null))).containsExactlyInAnyOrder("name", "durationDays");
        assertThat(new TaskRequest("  Тест  ", 3, null).toCommand().predecessorIds()).isEmpty();
        assertThat(new TaskRequest("  Тест  ", 3, null).toCommand().name()).isEqualTo("Тест");
    }

    @Test
    @DisplayName("FR2-2, FR2-4: незаполненные обязательные поля запроса на ресурс перечисляются для подсветки")
    void newRequestHighlightsAllInvalidFields() {
        NewRequestDto valid = new NewRequestDto(12, "QA-инженер", "Senior", 50, START, END, Map.of("Playwright", 3));
        NewRequestDto invalid = new NewRequestDto(null, " ", "", 150, END, START, Map.of());

        assertThat(invalidFields(valid)).isEmpty();
        assertThat(invalidFields(invalid))
                .containsExactlyInAnyOrder("taskId", "role", "grade", "loadPercent", "skills", "periodValid");
        assertThat(invalidFields(new NewRequestDto(12, "QA", "Senior", 50, START, END, Map.of("Playwright", 6))))
                .containsExactly("skills");
    }

    @Test
    @DisplayName("FR1-5: дата окончания раньше даты начала – ошибка «Дата окончания раньше даты начала»")
    void periodCheckIsShared() {
        Set<ConstraintViolation<BookingRequest>> violations = validator.validate(
                new BookingRequest(1, 1, BookingKind.HARD, END, START, 50));

        assertThat(violations).singleElement()
                .satisfies(v -> assertThat(v.getMessage()).isEqualTo("Дата окончания раньше даты начала"));
        assertThat(new BookingPatch(null, null, END, null).isPeriodValid()).isTrue();
        assertThat(invalidFields(new BookingPatch(BookingKind.HARD, END, START, 0)))
                .containsExactlyInAnyOrder("loadPercent", "periodValid");
    }

    @Test
    @DisplayName("FR9-2: блок обучения – курс, период, загрузка и флаг защиты обязательны")
    void trainingRequestFields() {
        assertThat(invalidFields(new TrainingRequest(3, "AWS", null, START, END, 100, true))).isEmpty();
        assertThat(invalidFields(new TrainingRequest(null, "", "P".repeat(101), null, END, 0, null)))
                .containsExactlyInAnyOrder("employeeId", "courseTitle", "provider", "startDate", "loadPercent",
                        "protectedTime");
    }

    @Test
    @DisplayName("FR6-2, FR6-4: решение по конфликту указывает бронь, эскалация – причину")
    void conflictDecisionFields() {
        assertThat(invalidFields(new ResolutionDto(null, "x".repeat(501)))).containsExactlyInAnyOrder("keepBookingId", "note");
        assertThat(invalidFields(new ResolutionDto(7L, null))).isEmpty();
        assertThat(invalidFields(new EscalationDto(" "))).containsExactly("note");
        assertThat(invalidFields(new ProposalDto(null))).containsExactly("employeeId");
        assertThat(invalidFields(new NotificationPatch(false))).containsExactly("read");
        assertThat(invalidFields(new NotificationPatch(true))).isEmpty();
    }
}
