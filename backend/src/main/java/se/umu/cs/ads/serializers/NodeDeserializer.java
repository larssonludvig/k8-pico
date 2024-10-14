package se.umu.cs.ads.serializers;

import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.core.JacksonException;
import java.net.InetSocketAddress;

import se.umu.cs.ads.exception.PicoException;
import se.umu.cs.ads.types.*;

public class NodeDeserializer extends StdDeserializer<Node> {

	public NodeDeserializer() {
		this(null);
	}

	public NodeDeserializer(Class<Node> t) {
		super(t);
	}


	@Override
	public Node deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JacksonException {
		JsonNode node = jp.getCodec().readTree(jp);
		String name = node.get("name").asText();
		String address = node.get("address").asText();
		String clusterName = node.get("cluster").asText();
		int port = node.get("port").asInt();

		if (!address.contains(":"))
		throw new PicoException("Address field must be formatted as ip:port");

		String[] buf = address.split(":");
		if (buf.length != 2)
			throw new PicoException("Address field must be formatted as ip:port");

		String ip = buf[0];
		int nodePort = Integer.parseInt(buf[1]);
		InetSocketAddress addr = new InetSocketAddress(ip, nodePort);
		
		List<PicoContainer> containers = new ArrayList<>();
		
		
		Node n = new Node();
		n.setName(name);
		n.setAddress(addr);
		n.setPort(port);
		n.setCluster(clusterName);
		n.setContainers(containers);
		return n;
	}
	

}