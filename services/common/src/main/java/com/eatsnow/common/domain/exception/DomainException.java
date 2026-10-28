package com.eatsnow.common.domain.exception;

/**
 * Base domain exception representing errors specified in EatsNow contracts (Spec 2.1 & 3.1).
 */
public abstract class DomainException extends RuntimeException {

    private final String code;
    private final int httpStatus;
    private final Object details;

    protected DomainException(String code, String message, int httpStatus, Object details) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.details = details;
    }

    protected DomainException(String code, String message, int httpStatus) {
        this(code, message, httpStatus, null);
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public Object getDetails() {
        return details;
    }
}
