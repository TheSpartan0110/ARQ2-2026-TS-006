package com.eatsnow.common.web;

import com.eatsnow.common.domain.exception.DomainException;
import com.eatsnow.common.domain.exception.RequestInProgressException;
import com.eatsnow.common.web.dto.ErrorResponseDto;
import com.eatsnow.common.web.filter.CorrelationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Standard global exception handler for EatsNow services conforming to Spec 2.1 & 3.1.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponseDto> handleDomainException(DomainException ex) {
        UUID correlationId = CorrelationContext.getCurrentCorrelationId();
        log.warn("Domain exception: code={}, status={}, message={}, correlationId={}",
                ex.getCode(), ex.getHttpStatus(), ex.getMessage(), correlationId);

        HttpHeaders headers = new HttpHeaders();
        if (ex instanceof RequestInProgressException) {
            headers.set("Retry-After", "1"); // Spec 3.1
        }

        ErrorResponseDto errorDto = new ErrorResponseDto(
                ex.getCode(),
                ex.getMessage(),
                correlationId,
                ex.getDetails()
        );

        return new ResponseEntity<>(errorDto, headers, HttpStatus.valueOf(ex.getHttpStatus()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationException(MethodArgumentNotValidException ex) {
        UUID correlationId = CorrelationContext.getCurrentCorrelationId();
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        if (message.isBlank()) {
            message = "Validation failed for request";
        }

        log.warn("Validation error: message={}, correlationId={}", message, correlationId);

        ErrorResponseDto errorDto = new ErrorResponseDto(
                "INVALID_REQUEST",
                message,
                correlationId,
                null
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDto);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponseDto> handleMissingHeader(MissingRequestHeaderException ex) {
        UUID correlationId = CorrelationContext.getCurrentCorrelationId();
        log.warn("Missing required header: {}, correlationId={}", ex.getHeaderName(), correlationId);

        ErrorResponseDto errorDto = new ErrorResponseDto(
                "INVALID_REQUEST",
                "Missing required header: " + ex.getHeaderName(),
                correlationId,
                null
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDto);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto> handleNotReadable(HttpMessageNotReadableException ex) {
        UUID correlationId = CorrelationContext.getCurrentCorrelationId();
        log.warn("Malformed JSON request: {}, correlationId={}", ex.getMessage(), correlationId);

        ErrorResponseDto errorDto = new ErrorResponseDto(
                "INVALID_REQUEST",
                "Malformed request body",
                correlationId,
                null
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDto);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGenericException(Exception ex) {
        UUID correlationId = CorrelationContext.getCurrentCorrelationId();
        log.error("Unhandled exception: correlationId={}", correlationId, ex);

        ErrorResponseDto errorDto = new ErrorResponseDto(
                "INTERNAL_ERROR",
                "An unexpected internal error occurred",
                correlationId,
                null
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorDto);
    }
}
