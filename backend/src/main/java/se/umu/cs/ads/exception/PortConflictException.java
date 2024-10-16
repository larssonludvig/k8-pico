package se.umu.cs.ads.exception;

import java.util.*;

import io.grpc.Status.Code;

public class PortConflictException extends PicoException {
	private final int[] ports;

	public PortConflictException(int[] ports) {
		super("PORT_CONFLICT: Ports " + Arrays.toString(ports) + " is already occupied!", Code.ALREADY_EXISTS);
		this.ports = ports;
	}

	public PortConflictException(String message) {
		super(message);
		this.ports = null;
	}

	public int[] getPorts() {
		return this.ports;
	}
}
