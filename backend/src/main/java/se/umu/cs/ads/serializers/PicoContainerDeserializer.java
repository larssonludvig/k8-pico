package se.umu.cs.ads.serializers;

import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.core.JacksonException;

import se.umu.cs.ads.types.*;

public class PicoContainerDeserializer extends StdDeserializer<PicoContainer> {

	public PicoContainerDeserializer() {
		this(null);
	}

	public PicoContainerDeserializer(Class<PicoContainer> t) {
		super(t);
	}

	private PicoContainerState getState(String state) {
		if (state.equals("RUNNING"))
			return PicoContainerState.RUNNING;
		else if (state.equals("RESTARTING"))
			return PicoContainerState.RESTARTING;
		else
			return PicoContainerState.STOPPED;
	}

	@Override
	public PicoContainer deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JacksonException {
		JsonNode node = jp.getCodec().readTree(jp);
		String name = node.get("name").asText();
		String image = node.get("image").asText();

		JsonNode portsRaw = node.get("ports");
		JsonNode envRaw = node.get("env");
		JsonNode stateRaw = node.get("state");

		Map<Integer, Integer> ports = new HashMap<>();
		List<String> env = new ArrayList<>();
		PicoContainerState state = PicoContainerState.UNKNOWN; 
		
		if (stateRaw != null) {
			state = getState(stateRaw.asText());
		}

		if (portsRaw != null && !portsRaw.isNull()) {

			if (!portsRaw.isArray()) 
				throw new IllegalArgumentException("Ports must be a list!");
			

			Iterator<JsonNode> it = portsRaw.elements();

			while (it.hasNext()) {
				String portBinding = it.next().asText();

				String[] binding = portBinding.split(":");
				if (binding.length < 2)
					throw new IllegalArgumentException("Ports must be on the format 'external:internal'. Consult the docker API for more information.");
				int exposed, internal;
				try {
					exposed = Integer.parseInt(binding[0]);
					internal = Integer.parseInt(binding[1]);
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("Ports must be numbers.");
				}

				ports.put(exposed, internal);
			}
		}
		

		if (envRaw != null && !envRaw.isNull()) {
			if (!envRaw.isArray()) 
				throw new IllegalArgumentException("Environment must be as a list: ['A=B', 'C=D']");
			
			Iterator<JsonNode> it = envRaw.elements();
			while (it.hasNext()) 
				env.add(it.next().asText());
	
		}

		return new PicoContainer().setName(name).setImage(image).setEnv(env).setPorts(ports).setState(state);
	}
	

}