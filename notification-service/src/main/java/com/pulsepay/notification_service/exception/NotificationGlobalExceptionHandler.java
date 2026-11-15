package com.pulsepay.notification_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.pulsepay.notification_service.dto.response.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class NotificationGlobalExceptionHandler {
    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotificationNotFound(
            NotificationNotFoundException exception,
            HttpServletRequest request
    )
    {
        ErrorResponse errorResponse = new ErrorResponse(
                "NOTIFICATION_NOT_FOUND",
                exception.getMessage(),
                Instant.now(),
                request.getRequestURI()

        );
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorResponse);

    }
}
