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

import se.umu.cs.ads.types.Pod;

public class PodSerializer extends StdSerializer<Pod> {

	public PodSerializer() {
		this(null);
	}

	public PodSerializer(Class<Pod> t) {
		super(t);
	} 

	@Override
	public void serialize(
		Pod container, JsonGenerator jgen, SerializerProvider provider)
		throws IOException {
			jgen.writeStartObject();

			jgen.writeStringField("id", container.getId());
			jgen.writeStringField("name", container.getName());
			jgen.writeStringField("image", container.getImage());
			
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
