package com.resplan.api.dto;

import com.resplan.domain.AppUser;

public record UserDto(int id, String login, String role, int employeeId, String fullName) {

    public static UserDto of(AppUser u) {
        return new UserDto(u.getId(), u.getLogin(), u.getRole().name(), u.getEmployee().getId(), u.getEmployee().fullName());
    }
}
