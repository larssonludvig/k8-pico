package se.umu.cs.ads.serializers;

import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.core.JacksonException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import se.umu.cs.ads.types.*;

public class PicoMessageDeserializer extends StdDeserializer<JMessage> {

    private static final Logger Logger = LogManager.getLogger(PicoMessageDeserializer.class);
    public PicoMessageDeserializer() {
        this(null);
    }

    public PicoMessageDeserializer(Class<PicoContainer> t) {
        super(t);
    }

    @Override
    public JMessage deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JacksonException {
        JsonNode node = jp.getCodec().readTree(jp);
        String sender = node.get("sender").asText();
        String strType = node.get("type").asText();
        MessageType type;
        try {
            type = MessageType.valueOf(strType);
        } catch(IllegalArgumentException e) {
            type = MessageType.UNKNOWN;
        }

        String payload = node.get("payload").asText();

        return new JMessage()
            .setSender(sender)
            .setType(type)
            .setPayload(payload);
    }
    
}