package se.umu.cs.ads.types;

public class Message {
    private MessageType type;
    private Object payload;
    private String sender;

    public Message() {
        this.type = MessageType.EMPTY;
        this.payload = null;
    }

    public Message(MessageType type, Object payload) {
        this.type = type;
        this.payload = payload;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public MessageType getType() {
        return this.type;
    }

    public void setPayload(Object payload) {
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
