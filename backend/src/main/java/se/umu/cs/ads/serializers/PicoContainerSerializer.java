package se.umu.cs.ads.serializers;

import java.io.IOError;
import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.jdk8.IntStreamSerializer;
import com.github.dockerjava.api.model.ExposedPort;

import se.umu.cs.ads.types.PicoContainer;

public class PicoContainerSerializer extends StdSerializer<PicoContainer> {

	public PicoContainerSerializer() {
		this(null);
	}

	public PicoContainerSerializer(Class<PicoContainer> t) {
		super(t);
	} 

	@Override
	public void serialize(
		PicoContainer container, JsonGenerator jgen, SerializerProvider provider)
		throws IOException {
			jgen.writeStartObject();

			jgen.writeStringField("name", container.getName());
			jgen.writeStringField("image", container.getImage());
			jgen.writeStringField("state", container.getState().toString());			
			List<String> ports = container.getPorts();
			if (ports.size() > 0) {
				jgen.writeArrayFieldStart("ports");
				for (String port : ports) 
					jgen.writeString(port);
				jgen.writeEndArray();
			}
			
			List<String> env = container.getEnv();
			if (env.size() > 0) {
				jgen.writeArrayFieldStart("env");
				for (String var : env)
					jgen.writeString(var);
				jgen.writeEndArray();
			}

			jgen.writeEndObject();		
		}
	
}
