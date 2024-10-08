package se.umu.cs.ads.exception;

public class PicoException extends RuntimeException {
    private final String message;
	private final PicoExceptionCause cause;

    public PicoException(String message) {
        super(message);
        this.message = message;
		this.cause = PicoExceptionCause.UNKNOWN;
	}


	public PicoException(String message, PicoExceptionCause cause) {
		super(message);
		this.message = message;
		this.cause = cause;
	}
	
    public String getMessage() {
        return message;
    }

	public PicoExceptionCause getPicoCause() {
		return cause;
	}
}
