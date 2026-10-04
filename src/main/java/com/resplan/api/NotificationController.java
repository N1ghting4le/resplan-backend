package com.resplan.api;

import com.resplan.api.dto.NotificationDto;
import com.resplan.api.dto.NotificationPatch;
import com.resplan.domain.AppUser;
import com.resplan.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Уведомления")
public class NotificationController {

    private final NotificationService notifications;

    @GetMapping
    @Operation(summary = "Уведомления текущего пользователя, новые – первыми")
    public List<NotificationDto> mine(@CurrentUser AppUser user, @RequestParam(defaultValue = "false") boolean unread) {
        return notifications.list(user, unread).stream().map(NotificationDto::of).toList();
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Отметить уведомление прочитанным")
    public NotificationDto markRead(@CurrentUser AppUser user, @PathVariable long id,
                                    @Valid @RequestBody NotificationPatch dto) {
        return NotificationDto.of(notifications.markRead(id, user));
    }
}
