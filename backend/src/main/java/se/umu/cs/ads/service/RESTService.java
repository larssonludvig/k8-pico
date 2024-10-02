package se.umu.cs.ads.service;

import org.springframework.stereotype.Service;
import se.umu.cs.ads.controller.Controller;

@Service
public class RESTService {
    private static Controller controller = new Controller();

    public Controller getController() {
        return controller;
    }
}
