package se.umu.cs.ads.types;

/**
 * Class for the PicoAddress object
 */
public class PicoAddress implements Comparable<PicoAddress> {
	private final String ip;
	private final int port;
	
	/**
	 * Constructor for the PicoAddress object
	 * @param ip String object
	 * @param port int object
	 */
	public PicoAddress(String ip, int port) {
		this.ip = ip;
		this.port = port;
	}

	/**
	 * Gets the IP address of the node
	 * @return String object
	 */
	public String getIP() {
		return this.ip;
	}

	/**
	 * Gets the port of the node
	 * @return int object
	 */
	public int getPort() {
		return this.port;
	}

	/**
	 * Method to compare two PicoAddress objects
	 * @param other PicoAddress object
	 * @return int object
	 */
	@Override 
	public int compareTo(PicoAddress other) {
		return this.toString().compareTo(other.toString());
	}
	
	/**
	 * Method to get the string representation of the PicoAddress object
	 * @return String object
	 */
	@Override
	public String toString() {
		return this.ip + ":" + this.port;
	}

	/**
	 * Method to check if two PicoAddress objects are equal
	 * @param o Object
	 * @return boolean object
	 */
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

	/**
	 * Method to get the hash code of the PicoAddress object
	 * @return int object
	 */
	@Override
	public int hashCode() {
		return this.toString().hashCode();
	}
}
