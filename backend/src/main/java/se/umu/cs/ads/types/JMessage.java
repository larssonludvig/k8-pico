package se.umu.cs.ads.types;

import java.io.Serializable;

public class JMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private MessageType type;
    private Object payload;
    private String sender;

    public JMessage() {
        this.type = MessageType.EMPTY;
        this.payload = null;
    }

    public JMessage(MessageType type, Object payload) {
        this.type = type;

        setPayload(payload);
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public MessageType getType() {
        return this.type;
    }

    public void setPayload(Object payload) {
        if (!(payload instanceof Serializable))
            throw new IllegalArgumentException("Payload must be serializable");

        this.payload = payload;
    }

    public Object getPayload() {
        return this.payload;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getSender() {
        return this.sender;
    }
}
