package com.resplan.api;

import com.resplan.api.dto.BookingResultDto;
import com.resplan.api.dto.CourseDto;
import com.resplan.api.dto.TrainingRequest;
import com.resplan.domain.AppUser;
import com.resplan.service.TrainingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "UC-9 Резервировать время на обучение")
public class TrainingController {

    private final TrainingService trainings;

    @GetMapping("/courses")
    @Operation(summary = "Каталог учебных курсов")
    public List<CourseDto> courses() {
        return trainings.courses().stream().map(CourseDto::of).toList();
    }

    @PostMapping("/trainings")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('LD')")
    @Operation(summary = "Добавить блок обучения в календарь сотрудника")
    public BookingResultDto reserve(@CurrentUser AppUser ld, @Valid @RequestBody TrainingRequest dto) {
        return BookingResultDto.of(trainings.reserve(dto.toCommand(), ld));
    }

    @DeleteMapping("/trainings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('LD')")
    @Operation(summary = "Отменить блок обучения")
    public void cancel(@CurrentUser AppUser ld, @PathVariable long id) {
        trainings.cancel(id, ld);
    }
}
