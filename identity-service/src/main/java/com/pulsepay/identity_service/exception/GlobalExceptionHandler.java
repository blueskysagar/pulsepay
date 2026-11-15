package com.pulsepay.identity_service.exception;

import com.pulsepay.identity_service.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;

// this class is the centralized exception handlers for all my rest controllers
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {
    private final PersistenceExceptionTranslator persistenceExceptionTranslator;
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ){
        String message = exception.getBindingResult()//Give me the validation result stored in BindingResult
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Invalid Request");
        ErrorResponse errorResponse = new ErrorResponse(
                "VALIDATION_ERROR",
                message,
                OffsetDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);

    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ){
        RuntimeException translatedException = persistenceExceptionTranslator.translate(exception);
        if(translatedException instanceof DuplicateAccountException duplicateException){
            ErrorResponse errorResponse = new ErrorResponse(
                    duplicateException.getErrorCode().name(),
                    duplicateException.getMessage(),
                    OffsetDateTime.now(),
                    request.getRequestURI()
            );
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(errorResponse);
        }
        //Unknown data base integrity violation
        // Do not expose internal data base details to the client
        ErrorResponse errorResponse = new ErrorResponse(
                "DATA_INTEGRITY_VIOLATION",
                "The request could not be completed.",
                OffsetDateTime.now(),
                request.getRequestURI()

        );
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        ErrorResponse errorResponse = new ErrorResponse(
                "INVALID_REQUEST_BODY",
                "The request body is invalid.",
                OffsetDateTime.now(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request
    ) {
        ErrorResponse errorResponse = new ErrorResponse(
                "UNSUPPORTED_MEDIA_TYPE",
                "Content-Type must be application/json.",
                OffsetDateTime.now(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(errorResponse);
    }
    //When DuplicateAccountException hits at web layer, execute this method
    @ExceptionHandler(DuplicateAccountException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateAccount(
            DuplicateAccountException exception,
            HttpServletRequest request
    ){
        ErrorResponse errorResponse = new ErrorResponse(
                exception.getErrorCode().name(),
                exception.getMessage(),
                OffsetDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(errorResponse);
    }
    @ExceptionHandler(InvalidPhoneNumberException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPhoneNumber(
            InvalidPhoneNumberException exception,
            HttpServletRequest request
    ) {
        ErrorResponse errorResponse = new ErrorResponse(
                "INVALID_PHONE_NUMBER",
                exception.getMessage(),
                OffsetDateTime.now(),
                request.getRequestURI()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }
    //If any exceptions occurs other than above then we handle this way for now.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    )
    {
        log.error("Unexpected error at {}", request.getRequestURI(), exception);
        //Never expose internal exception details to the client
        ErrorResponse errorResponse = new ErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred",
                OffsetDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
    }
    @ExceptionHandler (InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException exception,
            HttpServletRequest request
    ) {
        ErrorResponse errorResponse = new ErrorResponse(
                "INVALID_CREDENTIALS",
                "Invalid email or password.",
                OffsetDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(errorResponse);
    }
}
