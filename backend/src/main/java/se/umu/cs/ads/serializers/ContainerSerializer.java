package se.umu.cs.ads.serializers;

import java.io.IOException;
import java.util.*;
import org.apache.logging.log4j.*;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import se.umu.cs.ads.types.*;
import se.umu.cs.ads.communication.*;

public final class ContainerSerializer extends StdSerializer<PicoContainer> {

	private final static Logger logger = LogManager.getLogger(ContainerSerializer.class);
	
	public ContainerSerializer() {
		this(null);
	}

	public ContainerSerializer(Class<PicoContainer> t) {
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
	
	public static PicoContainer fromRPC(RpcContainer rpc) {
		String name = rpc.getName();
		String image = rpc.getImage();
		Map<Integer, Integer> unprocessedPorts = rpc.getPorts().getMap();
		List<String> env = rpc.getEnvs().getStringsList();
		PicoContainerState state = PicoContainerState.UNKNOWN;
		try {
			state = PicoContainerState.valueOf(rpc.getState().toString());
		} catch (IllegalArgumentException e) {
			logger.warn("Could not parse rpc state: {}. Treating as UNKNOWN", state);
			state = PicoContainerState.UNKNOWN;
		}
		
		return new PicoContainer()
			.setName(name)
			.setImage(image)
			.setPorts(unprocessedPorts)
			.setEnv(env)
			.setState(state);
	}

	public static List<PicoContainer> fromRPC(RpcContainers rpc) {
		List<PicoContainer> containers = new ArrayList<>();
		for (RpcContainer rpcContainer : rpc.getContainersList()) {
			containers.add(fromRPC(rpcContainer));
		}
		return containers;
	}

	public static RpcContainer toRPC(PicoContainer container) {
		
		RpcStrings.Builder builder = RpcStrings.newBuilder();

		for (String env : container.getEnv())
			builder.addStrings(env);

		RpcMap.Builder mapBuilder = RpcMap.newBuilder();
		Map<Integer, Integer> portsMap = container.getPortsMap();
		portsMap.forEach((key, value) -> mapBuilder.putMap(key, value));

		return RpcContainer.newBuilder()
            .setName(container.getName())
			.setImage(container.getImage())
			.setPorts(mapBuilder.build())
			.setEnvs(builder.build())
			.setState(parseState(container.getState()))
			.build();
    }

	public static RpcContainers toRPC(List<PicoContainer> containers) {
		RpcContainers.Builder builder = RpcContainers.newBuilder();
		for (PicoContainer container : containers) {
			builder.addContainers(toRPC(container));
		}
		return builder.build();
	}

	private static RpcContainerState parseState(PicoContainerState state) {
		switch (state) {
			case RUNNING:
				return RpcContainerState.RUNNING;
			case STOPPED:
				return RpcContainerState.STOPPED;
			case RESTARTING:
				return RpcContainerState.RESTARTING;
			case NAME_CONFLICT:
				return RpcContainerState.NAME_CONFLICT;
			case PORT_CONFLICT:
				return RpcContainerState.PORT_CONFLICT;
			case UNKNOWN:
				return RpcContainerState.UNKNOWN;
			default:
				logger.warn("Could not parse state: {}. Treating as UNKNOWN", state);
				return RpcContainerState.UNKNOWN;
		}
	}

	private static PicoContainerState parseState(RpcContainerState state) {
		switch (state) {
			case RUNNING:
				return PicoContainerState.RUNNING;
			case STOPPED:
				return PicoContainerState.STOPPED;
			case RESTARTING:
				return PicoContainerState.RESTARTING;
			case NAME_CONFLICT:
				return PicoContainerState.NAME_CONFLICT;
			case PORT_CONFLICT:
				return PicoContainerState.PORT_CONFLICT;
			case UNKNOWN:
				return PicoContainerState.UNKNOWN;
			default:
				logger.warn("Could not parse state: {}. Treating as UNKNOWN", state);
				return PicoContainerState.UNKNOWN;
		}
	}
}
