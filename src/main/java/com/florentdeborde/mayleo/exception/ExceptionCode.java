package com.florentdeborde.mayleo.exception;

import lombok.Getter;

@Getter
public enum ExceptionCode {

    INCORRECT_API_KEY("Api key provided does not match any api client"),
    CLIENT_DISABLED("Api client is disabled"),
    EMAIL_CONFIG_NOT_FOUND("No email configuration related to the api key provided"),
    EMAIL_CONFIG_INCOMPLETE("Email configuration related to the api key provided is incomplete"),
    DAILY_QUOTA_EXCEEDED("Daily quota exceeded"),
    RPM_LIMIT_EXCEEDED("Request per minute limit exceeded"),
    INTERNAL_ERROR("Internal server error"),

    INVALID_SIGNATURE("Internal server error"),
    INVALID_ORIGIN("Provided HMAC signature is invalid or missing."),

    PAYLOAD_TOO_LARGE("Request body exceeds the maximum allowed size");

    private final String defaultMessage;

    ExceptionCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

}
