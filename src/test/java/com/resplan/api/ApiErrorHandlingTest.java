package com.resplan.api;

import com.resplan.domain.AppUser;
import com.resplan.domain.RequestStatus;
import com.resplan.domain.UserRole;
import com.resplan.error.*;
import com.resplan.repository.AppUserRepository;
import com.resplan.security.TokenClaims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.resplan.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Веб-слой: ApiExceptionHandler, CurrentUserArgumentResolver")
class ApiErrorHandlingTest {

    @Nested
    @DisplayName("ApiExceptionHandler (ответы RFC 9457)")
    class Handler {

        private final ApiExceptionHandler handler = new ApiExceptionHandler();

        @Test
        @DisplayName("FR1-1: ресурс не найден – 404")
        void notFound() {
            ProblemDetail p = handler.notFound(new NotFoundException("Проект", 42));

            assertThat(p.getStatus()).isEqualTo(404);
            assertThat(p.getDetail()).isEqualTo("Проект с идентификатором 42 не найден");
        }

        @Test
        @DisplayName("FR4-4: неверные учетные данные – 401")
        void unauthorized() {
            assertThat(handler.unauthorized(new AuthenticationFailedException("Неверные учетные данные")).getStatus())
                    .isEqualTo(401);
        }

        @Test
        @DisplayName("FR1-4: действие чужого PM или недоступное роли – 403")
        void forbidden() {
            assertThat(handler.forbidden(new AccessDeniedException("Утвердить кандидата может только PM проекта ATLAS"))
                    .getDetail()).isEqualTo("Утвердить кандидата может только PM проекта ATLAS");
            assertThat(handler.forbidden(new org.springframework.security.access.AccessDeniedException("Access Denied"))
                    .getDetail()).isEqualTo("Операция недоступна для роли пользователя");
        }

        @Test
        @DisplayName("FR3-2, FR9-4: недопустимый переход статуса – 409, нарушение бизнес-правила – 422 с кодом")
        void businessRules() {
            ProblemDetail transition = handler.businessRule(
                    new IllegalTransitionException(RequestStatus.APPROVED, RequestStatus.APPROVED));
            ProblemDetail rule = handler.businessRule(new BusinessRuleException("PROTECTED_TIME", "Пересечение с обучением"));

            assertThat(transition.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
            assertThat(transition.getProperties()).containsEntry("code", "STATUS");
            assertThat(rule.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
            assertThat(rule.getProperties()).containsEntry("code", "PROTECTED_TIME");
        }

        @Test
        @DisplayName("FR2-4: ошибки полей возвращаются списком errors – 400")
        @SuppressWarnings("unchecked")
        void fieldErrorsAreListed() throws Exception {
            BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "newRequestDto");
            binding.addError(new FieldError("newRequestDto", "role", "не должно быть пустым"));
            binding.addError(new FieldError("newRequestDto", "role", "размер должен быть до 80"));
            binding.addError(new FieldError("newRequestDto", "loadPercent", "должно быть не больше 100"));
            binding.addError(new ObjectError("newRequestDto", "Дата окончания раньше даты начала"));
            MethodParameter parameter = new MethodParameter(
                    ApiErrorHandlingTest.class.getDeclaredMethod("sample", String.class), 0);

            ResponseEntity<Object> response = handler.handleMethodArgumentNotValid(
                    new MethodArgumentNotValidException(parameter, binding), new HttpHeaders(),
                    HttpStatus.BAD_REQUEST, new ServletWebRequest(new MockHttpServletRequest()));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ProblemDetail body = (ProblemDetail) response.getBody();
            assertThat(body.getDetail()).isEqualTo("Поля запроса заполнены некорректно");
            assertThat((Map<String, String>) body.getProperties().get("errors")).containsExactly(
                    Map.entry("role", "не должно быть пустым"),
                    Map.entry("loadPercent", "должно быть не больше 100"),
                    Map.entry("newRequestDto", "Дата окончания раньше даты начала"));
        }
    }

    @SuppressWarnings("unused")
    private static void sample(String value) {
    }

    @Nested
    @DisplayName("CurrentUserArgumentResolver")
    class Resolver {

        private final AppUserRepository users = mock(AppUserRepository.class);
        private final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver(users);

        @AfterEach
        void clear() {
            SecurityContextHolder.clearContext();
        }

        private void authenticate(Object principal) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        }

        @Test
        @DisplayName("FR4-3: пользователь сессии определяется по логину из маркера")
        void resolvesActiveUser() {
            AppUser pm = user(1, UserRole.PM);
            authenticate(new TokenClaims("pm1", UserRole.PM));
            when(users.findByLogin("pm1")).thenReturn(Optional.of(pm));

            assertThat(resolver.resolveArgument(null, null, null, null)).isSameAs(pm);
        }

        @Test
        @DisplayName("FR4-3: без аутентификации или для заблокированной учетной записи – 401")
        void rejectsAnonymousAndBlocked() {
            AppUser blocked = user(2, UserRole.EMP);
            ReflectionTestUtils.setField(blocked, "active", false);
            when(users.findByLogin("emp2")).thenReturn(Optional.of(blocked));

            assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                    .isInstanceOf(AuthenticationFailedException.class);
            authenticate("anonymousUser");
            assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                    .hasMessage("Пользователь не аутентифицирован");
            authenticate(new TokenClaims("emp2", UserRole.EMP));
            assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                    .hasMessage("Учетная запись заблокирована или удалена");
        }

        @Test
        @DisplayName("Параметр с аннотацией @CurrentUser типа AppUser поддерживается")
        void supportsAnnotatedAppUserParameter() throws Exception {
            var method = Sample.class.getDeclaredMethod("handle", AppUser.class, AppUser.class, String.class);

            assertThat(resolver.supportsParameter(new MethodParameter(method, 0))).isTrue();
            assertThat(resolver.supportsParameter(new MethodParameter(method, 1))).isFalse();
            assertThat(resolver.supportsParameter(new MethodParameter(method, 2))).isFalse();
        }
    }

    @SuppressWarnings("unused")
    static class Sample {
        void handle(@CurrentUser AppUser user, AppUser plain, @CurrentUser String wrongType) {
        }
    }
}
