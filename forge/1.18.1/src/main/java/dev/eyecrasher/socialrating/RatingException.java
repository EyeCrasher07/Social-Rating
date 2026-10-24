package dev.eyecrasher.socialrating;

import java.util.concurrent.CompletionException;

public final class RatingException extends RuntimeException {
    public final String code;
    public RatingException(String code) { super(code); this.code = code; }
    public RatingException(String code, Throwable cause) { super(code, cause); this.code = code; }
    public static String errorCode(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
        return error instanceof RatingException rating ? rating.code : "unavailable";
    }
}
