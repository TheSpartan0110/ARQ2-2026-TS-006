package com.eatsnow.common.domain.exception;

public class ItemUnavailableException extends DomainException {
    public ItemUnavailableException(String message) {
        super("ITEM_UNAVAILABLE", message, 409);
    }
}
