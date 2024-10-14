package se.umu.cs.ads.messagehandler;

import se.umu.cs.ads.types.*;
import java.util.*;

public final class MessageVerifier {
	private static final HashMap<MessageType, Class<?>> correct = new HashMap<>();

	public static boolean hasCorrectPayload(MessageType type, Object payload) {
		if (payload == null)
			return true;
		init();
		Class<?> clazz = payload.getClass();
		Class<?> claz = correct.get(type);
		if (claz == null)
			return true;
		
		return claz == clazz;	
	}

	public static Class<?> getCorrectClass(MessageType type) {
		init();
		return correct.get(type);
	}

	private static void init() {
		correct.put(MessageType.JOIN_REQUEST, List.class);
		correct.put(MessageType.FETCH_NODES, List.class);
		correct.put(MessageType.FETCH_NODE_PERFORMANCE, Performance.class);
		correct.put(MessageType.CREATE_CONTAINER, PicoContainer.class);
		correct.put(MessageType.CONTAINER_LIST, List.class);
		correct.put(MessageType.CONTAINER_ELECTION_START, PicoContainer.class);
		correct.put(MessageType.CONTAINER_ELECTION_END, PicoContainer.class);
		correct.put(MessageType.EVALUATE_CONTAINER_REQUEST, PicoContainer.class);		
	}

}