package se.umu.cs.ads.types;

public class PicoAddress implements Comparable<PicoAddress> {
	private final String ip;
	private final int port;
	
	public PicoAddress(String ip, int port) {
		this.ip = ip;
		this.port = port;
	}

	public String getIP() {
		return this.ip;
	}

	public int getPort() {
		return this.port;
	}


	@Override 
	public int compareTo(PicoAddress other) {
		return this.toString().compareTo(other.toString());
	}
	
	@Override
	public String toString() {
		return this.ip + ":" + this.port;
	}

	@Override 
	public boolean equals(Object o) {
		if (o == null)
			return false;

		if (this == o)
			return true;

		if (!(o instanceof PicoAddress))
			return false;

		PicoAddress other = (PicoAddress) o;
		return this.ip.equals(other.ip) && this.port == other.port;
	}

	@Override
	public int hashCode() {
		return this.toString().hashCode();
	}
}
