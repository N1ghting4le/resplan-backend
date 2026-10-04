package com.resplan.api;

import com.resplan.api.dto.*;
import com.resplan.domain.AppUser;
import com.resplan.domain.ResourceRequest;
import com.resplan.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "UC-7 Управлять бронированием кандидатов")
public class BookingController {

    private final BookingService bookings;

    @GetMapping
    @PreAuthorize("hasAnyRole('RM', 'PM', 'LD')")
    @Operation(summary = "Матрица распределения: действующие брони в периоде")
    public List<BookingDto> list(@RequestParam(required = false) Integer employeeId,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return bookings.list(employeeId, from, to).stream().map(BookingDto::of).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('RM', 'PM', 'LD')")
    @Operation(summary = "Бронирование по идентификатору")
    public BookingDto get(@PathVariable long id) {
        return BookingDto.of(bookings.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('RM')")
    @Operation(summary = "Забронировать сотрудника на проект (SOFT или HARD)")
    public BookingResultDto create(@CurrentUser AppUser rm, @Valid @RequestBody BookingRequest dto) {
        return BookingResultDto.of(bookings.create(dto.toCommand(), rm));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('RM')")
    @Operation(summary = "Изменить даты и загрузку или перевести мягкую бронь в жесткую")
    public BookingResultDto update(@PathVariable long id, @Valid @RequestBody BookingPatch dto) {
        return BookingResultDto.of(bookings.update(id, dto.toCommand()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('RM')")
    @Operation(summary = "Снять бронь и вернуть сотрудника в пул доступных ресурсов (Bench)")
    public ReleaseDto release(@CurrentUser AppUser rm, @PathVariable long id) {
        ResourceRequest returned = bookings.release(id, rm);
        return new ReleaseDto(BookingDto.of(bookings.get(id)), returned == null ? null : returned.getId());
    }
}
