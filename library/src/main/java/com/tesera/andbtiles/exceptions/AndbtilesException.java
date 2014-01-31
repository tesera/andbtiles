package com.tesera.andbtiles.exceptions;


public class AndbtilesException extends Exception {
    /**
     * Constructs a new {@code AndbtilesException} with its stack trace
     * filled in.
     */
    public AndbtilesException() {
    }

    /**
     * Constructs a new {@code AndbtilesException} with its stack trace and
     * detail message filled in.
     *
     * @param detailMessage the detail message for this exception.
     */
    public AndbtilesException(String detailMessage) {
        super(detailMessage);
    }
}
