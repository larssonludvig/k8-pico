package se.umu.cs.ads.types;

import java.io.Serializable;
import java.net.InetSocketAddress;

import se.umu.cs.ads.serializers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.core.JsonProcessingException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@JsonSerialize(using = PicoMessageSerializer.class)
@JsonDeserialize(using = PicoMessageDeserializer.class)
public class JMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final Logger logger = LogManager.getLogger(JMessage.class);

    private MessageType type;
    private Object payload;
    private String sender;
	private InetSocketAddress destination;
	private boolean broadcast;


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

	public JMessage setDestination(InetSocketAddress address) {
		this.destination = address;
		return this;
	}

	public InetSocketAddress getDestination() {
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

    public JMessage setSender(String sender) {
        this.sender = sender;
		return this;
    }

    public String getSender() {
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
	public int hashCode() {
		return this.toString().hashCode();
	}
}
