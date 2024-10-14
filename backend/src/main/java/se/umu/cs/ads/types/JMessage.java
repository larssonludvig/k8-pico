package se.umu.cs.ads.types;

import java.io.Serializable;
import se.umu.cs.ads.types.PicoAddress;

import se.umu.cs.ads.communication.RpcMessage;
import se.umu.cs.ads.serializers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.core.JsonProcessingException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger logger = LogManager.getLogger(JMessage.class);

    private MessageType type;
    private Object payload;
    private PicoAddress sender;
	private PicoAddress destination;
	private boolean broadcast = false;


	/**
	 * Consutrcts a new empty JMessage 
	 */
    public JMessage() {
        this.type = MessageType.EMPTY;
        this.payload = null;
    }

    public JMessage(MessageType type, Object payload) {
        this.type = type;

        setPayload(payload);
    }

	public JMessage setIsBroadcast(boolean isBroadcast) {
		this.broadcast = isBroadcast;
		return this;
	}

	public JMessage setDestination(PicoAddress address) {
		this.destination = address;
		return this;
	}

	public PicoAddress getDestination() {
		return this.destination;
	}

    public JMessage setType(MessageType type) {
        this.type = type;
		return this;
    }

    public MessageType getType() {
        return this.type;
    }

    public JMessage setPayload(Object payload) {
        if (!(payload instanceof Serializable))
            throw new IllegalArgumentException("Payload must be serializable");

        this.payload = payload;
		return this;
    }

    public Object getPayload() {
        return this.payload;
    }

    public JMessage setSender(PicoAddress sender) {
        this.sender = sender;
		return this;
    }

    public PicoAddress getSender() {
        return this.sender;
    }


    public static JMessage fromJson(String json) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, JMessage.class);
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse JSON: " + e.getMessage());
            return null;
        }
    }

	public static RpcMessage toRPC(JMessage msg) {
		return RpcMessage.newBuilder()
			.setPayload(msg.toString())
			.build();
	}

	public static JMessage ERROR(String cause) {
		return new JMessage().setType(MessageType.ERROR).setPayload(cause);
	}

    @Override
    public String toString() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize message: " + e.getMessage());
            return "null";
        }
    }

	@Override
	public boolean equals(Object other) {
		if (other == null)
			return false;

		if (this == other) 
			return true;
		
		if (!(other instanceof JMessage))
			return false;
		JMessage o = (JMessage) other;
		return this.toString().equals(o.toString());
	}

	@Override
	public int hashCode() {
		return this.toString().hashCode();
	}
}
