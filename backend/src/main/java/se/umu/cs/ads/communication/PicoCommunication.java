package se.umu.cs.ads.communication;

import java.util.*;

import se.umu.cs.ads.types.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PicoCommunication {
    private static final Logger logger = LogManager.getLogger(PicoCommunication.class);
    private final PicoServer server;
    private final String ip;
    private final int port;

    public PicoCommunication(String ip, int port) {
        this.ip = ip;
        this.port = port;
        this.server = new PicoServer(this, this.port);
        
        try {
            this.server.start();
        } catch (Exception e) {
            logger.error("Failed to rpc start server", e);
        }
    }

    public JMessage send(String ip, int port, JMessage msg) {
        try {
            msg.setSender(this.ip + ":" + this.port);

            PicoClient client = new PicoClient(ip, port);
            String res = client.send(msg);
            
            return JMessage.fromJson(res);
        } catch (Exception e) {
            logger.error("Failed to send message", e);
            return null;
        }
    }

    public List<JMessage> broadcast(List<String> ips, List<Integer> ports, JMessage msg) {
        try {
            if (ips.size() != ports.size())
                throw new IllegalArgumentException("Number of ips and ports are not the same.");

            List<JMessage> res = new ArrayList<>();
            for (int i = 0; i < ips.size(); i++) {
                res.add(send(ips.get(i), ports.get(i), msg));
            }
            return res;
        } catch (IllegalArgumentException e) {
            logger.error("Failed to broadcast", e);
            return null;
        }
        
    }
}
