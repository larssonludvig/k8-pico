package se.umu.cs.ads;


import se.umu.cs.ads.arguments.CommandLineArguments;
import se.umu.cs.ads.exception.PicoException;

import org.springframework.boot.*;

import se.umu.cs.ads.service.RESTManager;

import java.util.Properties;

import org.apache.commons.cli.*;
import org.apache.commons.validator.routines.*;

public class Backend {
    public static void main(String[] args) {
		Options options = new Options();
		options.addOption("ip", true, "the ip address of a member in the cluster. Format ip:port");
		options.addOption("p", "port", true, "port for the gRPC server");
		options.addOption("web", true, "the port to use for the web interface");
		options.addOption("h", "help", false, "Display this help message");
		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		CommandLine cmd = null;

		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			formatter.printHelp("utility-name", options);
			System.exit(-1);
		}

		if (cmd.hasOption("help")) {
			formatter.printHelp("utility-name", options);
			System.exit(-1);
		}

		if (cmd.hasOption("web")) 
			CommandLineArguments.webPort = Integer.parseInt(cmd.getOptionValue("web"));
		 else 
			CommandLineArguments.webPort = 8080;
		

		if (cmd.hasOption("ip")) {
			CommandLineArguments.initialMember = validateIP(cmd.getOptionValue("ip"));
		}

		if (cmd.hasOption("port")) 
			CommandLineArguments.grpcPort = Integer.parseInt(cmd.getOptionValue("port"));
		 else 
			CommandLineArguments.grpcPort = 9000;
		
        try {
			SpringApplication app = new SpringApplication(RESTManager.class);
			Properties properties = new Properties();
			properties.put("server.port", CommandLineArguments.webPort);
			app.setDefaultProperties(properties);
			app.run(args);
        } catch (PicoException e) {
			System.err.println(e.getMessage());
        } catch (Exception e) {
			// e.printStackTrace();
		}
    }

	private static String validateIP(String target) {
	
		InetAddressValidator validator = InetAddressValidator.getInstance();
		if (!target.contains(":"))
			throw new IllegalArgumentException("Format must be ip:port");
		
		String[] ipPort = target.split(":");
		if (ipPort.length != 2)
			throw new IllegalArgumentException("Format must be ip:port");

		try {
			Integer.parseInt(ipPort[1]);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Port must be a number");
		}
 
		if (!validator.isValidInet4Address(ipPort[0]))
			throw new IllegalArgumentException("IP is not valid");
	
		return target;
	}
}
