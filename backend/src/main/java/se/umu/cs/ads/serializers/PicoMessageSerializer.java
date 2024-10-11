package se.umu.cs.ads.serializers;

import java.io.IOError;
import java.io.IOException;
import java.util.List;
import java.io.IOException;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.jdk8.IntStreamSerializer;
import com.github.dockerjava.api.model.ExposedPort;

import se.umu.cs.ads.types.*;

public class PicoMessageSerializer extends StdSerializer<JMessage> {

	public PicoMessageSerializer() {
		this(null);
	}

	public PicoMessageSerializer(Class<JMessage> t) {
		super(t);
	} 

	@Override
	public void serialize(
		JMessage message, JsonGenerator jgen, SerializerProvider provider)
		throws IOException {
			jgen.writeStartObject();
			
			if (message.getSender() != null) {
				String ip = message.getSender().getAddress().getHostAddress();
				int port = message.getSender().getPort();
				jgen.writeStringField("sender",ip + ":" + port);
			}
			
			if (message.getType() != null)
				jgen.writeStringField("type", message.getType().toString());
			
			if (message.getPayload() != null)
				jgen.writeStringField("payload", message.getPayload().toString());		
			jgen.writeEndObject();		
		}
	
}