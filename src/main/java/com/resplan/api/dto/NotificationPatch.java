package com.resplan.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/** Уведомление можно только отметить прочитанным */
public record NotificationPatch(@NotNull @AssertTrue(message = "Допустимо только значение true") Boolean read) {
}
