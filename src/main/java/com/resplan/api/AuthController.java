package com.resplan.api;

import com.resplan.api.dto.LoginRequest;
import com.resplan.api.dto.LoginResponse;
import com.resplan.api.dto.UserDto;
import com.resplan.domain.AppUser;
import com.resplan.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "UC-4 Войти в систему")
public class AuthController {

    private final AuthService auth;

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Аутентификация по логину и паролю, выдача маркера доступа JWT")
    public LoginResponse login(@Valid @RequestBody LoginRequest dto) {
        return LoginResponse.of(auth.login(dto.login(), dto.password()));
    }

    @GetMapping("/me")
    @Operation(summary = "Сведения о текущем пользователе и его роли (выбор стартовой панели)")
    public UserDto me(@CurrentUser AppUser user) {
        return UserDto.of(user);
    }
}
