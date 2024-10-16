package se.umu.cs.ads.exception;

import io.grpc.Status.Code;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

public class PicoException extends RuntimeException {
    private final String message;
	private final Code errorCode;

	public PicoException() {
		super();
		this.message = "";
		this.errorCode = Code.UNKNOWN;
	}

    public PicoException(String message) {
        super(message);
        this.message = message;
		this.errorCode = Code.UNKNOWN;
	}

	public PicoException(String message, Code code) {
		super(message);
		this.message = message;
		this.errorCode = code;
	}

    public String getMessage() {
        return message;
    }


	public Code getCode() {
		 return this.errorCode;
	}

	public StatusRuntimeException toStatusException() {
		return Status.fromCode(this.errorCode).withDescription(this.message).asRuntimeException();
	}
}
