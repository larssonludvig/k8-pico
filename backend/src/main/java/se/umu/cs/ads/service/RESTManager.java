package se.umu.cs.ads.service;

import org.springframework.boot.autoconfigure.SpringBootApplication;

public class RESTManager {
	private final RESTController controller;
	public RESTManager() {
		controller = new RESTController();
	}
}