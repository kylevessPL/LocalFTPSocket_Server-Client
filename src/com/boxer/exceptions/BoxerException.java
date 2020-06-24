package com.boxer.exceptions;

/**
 * The type Boxer exception.
 */
public class BoxerException extends Exception {
    /**
     * The Exception type.
     */
    private final ExceptionType exceptionType;

    /**
     * Instantiates a new Boxer exception.
     *
     * @param exceptionType the exception type
     * @param message       the message
     */
    public BoxerException(ExceptionType exceptionType, String message) {
        super(message);
        this.exceptionType = exceptionType;
    }

    /**
     * Gets exception type.
     *
     * @return the exception type
     */
    public ExceptionType getExceptionType() {
        return exceptionType;
    }

    /**
     * The enum Exception type.
     */
    public enum ExceptionType {
        /**
         * Introduce error exception type.
         */
        INTRODUCE_ERROR,
        /**
         * Get user list error exception type.
         */
        GET_USER_LIST_ERROR,
        /**
         * Sync files error exception type.
         */
        SYNC_FILES_ERROR,
        /**
         * Share file error exception type.
         */
        SHARE_FILE_ERROR
    }
}
