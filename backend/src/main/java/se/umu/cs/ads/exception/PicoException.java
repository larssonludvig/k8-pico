package se.umu.cs.ads.exception;

public class PicoException extends RuntimeException {
    private final String message;

    public PicoException(String message) {
        super(message);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
