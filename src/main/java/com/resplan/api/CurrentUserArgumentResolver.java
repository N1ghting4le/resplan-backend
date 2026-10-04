package com.resplan.api;

import com.resplan.domain.AppUser;
import com.resplan.error.AuthenticationFailedException;
import com.resplan.repository.AppUserRepository;
import com.resplan.security.TokenClaims;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Подставляет в параметр с аннотацией @CurrentUser учетную запись пользователя из контекста безопасности.
 * Контроллеры не зависят от способа аутентификации (JWT, LDAP и т. п.).
 */
@Component
@RequiredArgsConstructor
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final AppUserRepository users;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && AppUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public AppUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                                   NativeWebRequest request, WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof TokenClaims claims)) {
            throw new AuthenticationFailedException("Пользователь не аутентифицирован");
        }
        return users.findByLogin(claims.login())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new AuthenticationFailedException("Учетная запись заблокирована или удалена"));
    }
}
