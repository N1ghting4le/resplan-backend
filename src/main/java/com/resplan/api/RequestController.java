package com.resplan.api;

import com.resplan.api.dto.*;
import com.resplan.domain.AppUser;
import com.resplan.domain.RequestStatus;
import com.resplan.service.MatchingService;
import com.resplan.service.ProposalResult;
import com.resplan.service.ResourceRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
@Tag(name = "UC-2, UC-3, UC-5 Запросы на подбор кандидатов")
public class RequestController {

    private final ResourceRequestService service;
    private final MatchingService matching;

    @GetMapping
    @PreAuthorize("hasAnyRole('PM', 'RM')")
    @Operation(summary = "UC-5: входящие запросы (для PM – запросы его проектов)")
    public List<RequestDto> list(@CurrentUser AppUser user, @RequestParam(required = false) Set<RequestStatus> status) {
        return service.search(status, user).stream().map(RequestDto::of).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PM', 'RM')")
    @Operation(summary = "Карточка запроса")
    public RequestDto get(@CurrentUser AppUser user, @PathVariable int id) {
        return RequestDto.of(service.get(id, user));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PM')")
    @Operation(summary = "UC-2: создать запрос на подбор кандидата")
    public RequestDto submit(@CurrentUser AppUser pm, @Valid @RequestBody NewRequestDto dto) {
        return RequestDto.of(service.submit(dto.toCommand(), pm));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PM')")
    @Operation(summary = "Отменить запрос, бронь кандидата снимается")
    public RequestDto cancel(@CurrentUser AppUser pm, @PathVariable int id) {
        return RequestDto.of(service.cancel(id, pm));
    }

    @GetMapping("/{id}/candidates")
    @PreAuthorize("hasRole('RM')")
    @Operation(summary = "UC-5: ранжированный список кандидатов (Smart Matching)")
    public List<CandidateDto> candidates(@PathVariable int id) {
        return matching.findCandidates(id).stream().map(CandidateDto::of).toList();
    }

    @PostMapping("/{id}/proposal")
    @PreAuthorize("hasRole('RM')")
    @Operation(summary = "UC-5: предложить кандидата PM (мягкая бронь)")
    public RequestDto propose(@CurrentUser AppUser rm, @PathVariable int id, @Valid @RequestBody ProposalDto dto) {
        ProposalResult result = service.propose(id, dto.employeeId(), rm);
        List<String> warnings = result.overloads().stream()
                .map(b -> "Перегрузка с бронью " + b.title() + " (" + b.getLoadPercent() + " %)").toList();
        return RequestDto.of(result.request(), warnings);
    }

    @PostMapping("/{id}/external-hire")
    @PreAuthorize("hasRole('RM')")
    @Operation(summary = "UC-5: передать запрос во внешний найм")
    public RequestDto externalHire(@PathVariable int id) {
        return RequestDto.of(service.sendToExternalHire(id));
    }

    @PostMapping("/{id}/approval")
    @PreAuthorize("hasRole('PM')")
    @Operation(summary = "UC-3: утвердить кандидата (жесткая бронь, регистрация конфликтов)")
    public ApprovalDto approve(@CurrentUser AppUser pm, @PathVariable int id) {
        return ApprovalDto.of(service.approve(id, pm));
    }

    @PostMapping("/{id}/rejection")
    @PreAuthorize("hasRole('PM')")
    @Operation(summary = "UC-3: отклонить кандидата с указанием причины")
    public RequestDto reject(@CurrentUser AppUser pm, @PathVariable int id, @RequestBody RejectionDto dto) {
        return RequestDto.of(service.reject(id, dto.reason(), pm));
    }
}
