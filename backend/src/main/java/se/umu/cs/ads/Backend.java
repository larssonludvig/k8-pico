package se.umu.cs.ads;


import se.umu.cs.ads.arguments.CommandLineArguments;
import se.umu.cs.ads.exception.PicoException;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;

import se.umu.cs.ads.service.RESTManager;
import se.umu.cs.ads.types.PicoAddress;

import java.util.Collections;
import java.util.Properties;

import org.apache.commons.cli.*;
import org.apache.commons.validator.routines.*;

public class Backend {
    public static void main(String[] args) {
		Options options = new Options();
		options.addOption("ip", true, "the ip address of a member in the cluster");
		options.addOption("p", "port", true, "port for the gRPC server");
		options.addOption("web", true, "the port to use for the web interface");

		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		CommandLine cmd = null;

		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			formatter.printHelp("utility-name", options);
			System.exit(-1);
		}

		if (cmd.hasOption("web")) 
			CommandLineArguments.webPort = Integer.parseInt(cmd.getOptionValue("web"));
		 else 
			CommandLineArguments.webPort = 8080;
		

		if (cmd.hasOption("ip"))
			CommandLineArguments.initialMember = parseAddress(cmd.getOptionValue("ip"));

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
        } catch (Exception e) {
			e.printStackTrace();
        }
    }

	private static String parseAddress(String target) {
	
		InetAddressValidator validator = InetAddressValidator.getInstance();
		if (!validator.isValidInet4Address(target))
			throw new IllegalArgumentException("Port must be number");
	
		return target;
	}
}
