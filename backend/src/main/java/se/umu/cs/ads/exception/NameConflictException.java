package se.umu.cs.ads.exception;

import io.grpc.Status.Code;

public class NameConflictException extends PicoException {
	private final String name;
	public NameConflictException(String name) {
		super("Container name " + name + " is already occupied!", Code.ALREADY_EXISTS);
		this.name = name;
	}

	public String getName() {
		return this.name;
	}
}
