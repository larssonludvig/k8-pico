package se.umu.cs.ads.exception;

public class PicoException extends Exception {
    private final String message;

    public PicoException(String message) {
        super(message);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
