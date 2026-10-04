package com.resplan.api;

import com.resplan.error.AccessDeniedException;
import com.resplan.error.AuthenticationFailedException;
import com.resplan.error.BusinessRuleException;
import com.resplan.error.IllegalTransitionException;
import com.resplan.error.NotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Единая точка преобразования исключений в ответы HTTP в формате RFC 9457 (application/problem+json).
 * Контроллеры и сервисы не формируют ответы с ошибками сами (принципы SRP и DRY).
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    ProblemDetail unauthorized(AuthenticationFailedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler({AccessDeniedException.class, org.springframework.security.access.AccessDeniedException.class})
    ProblemDetail forbidden(RuntimeException e) {
        String detail = e instanceof AccessDeniedException ? e.getMessage() : "Операция недоступна для роли пользователя";
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, detail);
    }

    /** 409 – недопустимый переход состояния, 422 – нарушение иного бизнес-правила */
    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail businessRule(BusinessRuleException e) {
        HttpStatus status = e instanceof IllegalTransitionException ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setProperty("code", e.getCode());
        return problem;
    }

    /** 400 – ошибки проверки полей запроса; errors позволяет клиенту подсветить поля формы (FR2-4) */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException e,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> errors.putIfAbsent(f.getField(), f.getDefaultMessage()));
        e.getBindingResult().getGlobalErrors().forEach(g -> errors.putIfAbsent(g.getObjectName(), g.getDefaultMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Поля запроса заполнены некорректно");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }
}
