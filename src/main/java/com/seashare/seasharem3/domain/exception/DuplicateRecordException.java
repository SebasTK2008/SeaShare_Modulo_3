package com.seashare.seasharem3.domain.exception;

/** Indica que otro proceso ya persistio el registro inmutable equivalente. */
public final class DuplicateRecordException extends DomainException {

    public DuplicateRecordException(String message, Throwable cause) {
        super(message, cause);
    }
}
