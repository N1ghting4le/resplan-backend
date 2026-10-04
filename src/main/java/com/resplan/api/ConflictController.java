package com.resplan.api;

import com.resplan.api.dto.ConflictDto;
import com.resplan.api.dto.EscalationDto;
import com.resplan.api.dto.ResolutionDto;
import com.resplan.domain.AppUser;
import com.resplan.domain.ConflictStatus;
import com.resplan.service.ConflictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/conflicts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('RM')")
@Tag(name = "UC-6 Разрешить ресурсный конфликт")
public class ConflictController {

    private final ConflictService conflicts;

    @GetMapping
    @Operation(summary = "Панель ресурсных конфликтов (по умолчанию – OPEN и ESCALATED)")
    public List<ConflictDto> list(@RequestParam(required = false) Set<ConflictStatus> status) {
        return conflicts.list(status).stream().map(ConflictDto::of).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Карточка конфликта с пересекающимися бронями и приоритетами проектов")
    public ConflictDto get(@PathVariable int id) {
        return ConflictDto.of(conflicts.get(id));
    }

    @PostMapping("/{id}/resolution")
    @Operation(summary = "Разрешить конфликт: сохранить одну бронь и снять другую")
    public ConflictDto resolve(@CurrentUser AppUser rm, @PathVariable int id, @Valid @RequestBody ResolutionDto dto) {
        return ConflictDto.of(conflicts.resolve(id, dto.keepBookingId(), dto.note(), rm));
    }

    @PostMapping("/{id}/escalation")
    @Operation(summary = "Эскалировать конфликт операционному директору")
    public ConflictDto escalate(@PathVariable int id, @Valid @RequestBody EscalationDto dto) {
        return ConflictDto.of(conflicts.escalate(id, dto.note()));
    }
}
