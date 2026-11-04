package com.horairesync.error;

/** Business exception carrying an {@link ErrorCode}; translated into the API error format by the web layer. */
public class HoraireSyncException extends RuntimeException {

    private final ErrorCode code;

    public HoraireSyncException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public HoraireSyncException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
