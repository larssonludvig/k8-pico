// package se.umu.cs.ads.serializers;

// import java.io.IOException;
// import se.umu.cs.ads.types.PicoAddress;
// import java.util.*;

// import com.fasterxml.jackson.core.JsonParser;
// import com.fasterxml.jackson.databind.DeserializationContext;
// import com.fasterxml.jackson.databind.JsonNode;
// import com.fasterxml.jackson.databind.ObjectMapper;
// import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
// import com.fasterxml.jackson.core.JacksonException;

// import org.apache.logging.log4j.LogManager;
// import org.apache.logging.log4j.Logger;

// import se.umu.cs.ads.messagehandler.MessageVerifier;
// import se.umu.cs.ads.types.*;

// public class PicoMessageDeserializer {

//     private static final Logger logger = LogManager.getLogger(PicoMessageDeserializer.class);
   
//     public PicoMessageDeserializer() {
//         this(null);
//     }

//     public PicoMessageDeserializer(Class<PicoContainer> t) {
//         super(t);
//     }

//     @Override
//     public JMessage deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JacksonException {
//         JMessage result = new JMessage();
// 		ObjectMapper mapper = new ObjectMapper();
// 		JsonNode node = jp.getCodec().readTree(jp);
// 		JsonNode senderObj = node.get("sender");

// 		if (senderObj != null) {
// 			String sender = senderObj.asText();
// 			if (!sender.contains(":"))
// 				throw new IOException("Sender address field was not in the format ip:port");

// 			String[] buf = sender.split(":");
// 			if (buf.length != 2)
// 				throw new IOException("Sender address field was not in the format ip:port");
			
// 			String ip = buf[0];
// 			int port;
// 			try {
// 				port = Integer.parseInt(buf[1]);
// 				result.setSender(new PicoAddress(ip, port));
// 			} catch (IllegalArgumentException e) {
// 				throw new IOException("Address field could not be correctly interpreted");
// 			}
// 		}
		
// 		JsonNode typeObj = node.get("type");
// 		if (typeObj != null) {
// 			MessageType type = null;
// 			try {
// 				type = MessageType.valueOf(typeObj.asText());
// 			} catch(IllegalArgumentException e) {
// 				type = MessageType.UNKNOWN;
// 			} finally {
// 				result.setType(type);
// 			}
// 		}

 
//         JsonNode payload = node.get("payload");
// 		if (payload != null && typeObj != null) {
// 			Class<?> clazz = MessageVerifier.getCorrectClass(result.getType());
// 			result.setPayload(mapper.readValue(payload.asText(), clazz));		
// 		}

//         return result;
//     }
// }