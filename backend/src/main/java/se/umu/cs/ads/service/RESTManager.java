package se.umu.cs.ads.service;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class RESTManager {
	@Bean
	public CommandLineRunner CommandLineRunner(ApplicationContext ctx) {
		return args -> {
			System.out.println("Service API is running!");
		};
	}
}