package com.resplan.error;

/** Неверные учетные данные или недействительная учетная запись (HTTP 401) */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
