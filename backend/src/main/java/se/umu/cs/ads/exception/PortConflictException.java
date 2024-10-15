package se.umu.cs.ads.exception;

import java.util.*;

import io.grpc.Status.Code;

public class PortConflictException extends PicoException {
	private final int[] ports;

	public PortConflictException(int[] ports) {
		super("Port " + Arrays.toString(ports) + " is alredy occupied!", Code.ALREADY_EXISTS);
		this.ports = ports;
	}

	public int[] getPorts() {
		return this.ports;
	}
}
