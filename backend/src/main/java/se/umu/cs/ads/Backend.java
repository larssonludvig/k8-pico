package se.umu.cs.ads;


import se.umu.cs.ads.arguments.CommandLineArguments;
import org.springframework.boot.SpringApplication;
import se.umu.cs.ads.service.RESTManager;
import java.net.InetSocketAddress;

public class Backend {
    public static void main(String[] args) {
        try {

			if (args.length > 0) 
				CommandLineArguments.initialMember = parseAddress(args[0]);
			
			SpringApplication.run(RESTManager.class, args);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

	private static InetSocketAddress parseAddress(String target) {
		if (!target.contains(":"))
			throw new IllegalArgumentException("Initial address must be ip:port");
		

		String buf[] = target.split(":");
		if (buf.length != 2)
			throw new IllegalArgumentException("Initial address must be ip:port");

		String ip = buf[0];
		int port;
		
		try {
			port = Integer.parseInt(buf[1]);
		} catch(NumberFormatException e) {
			throw new IllegalArgumentException("Port must be number");
		}

		return new InetSocketAddress(ip, port);
	}
}
