package se.umu.cs.ads.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.dockerjava.api.model.ContainerPort;

public class Util {
	

	public static Map<Integer, Integer> containerPortsToInt(ContainerPort[] containerPorts) {
		Map<Integer, Integer> ports = new HashMap<>();

		for (int i = 0; i < containerPorts.length; i++) {
			try {
				Integer publicPort = containerPorts[i].getPublicPort();
				Integer internalPort = containerPorts[i].getPrivatePort();
				if (publicPort == null || internalPort == null)
					continue;
			
				ports.put(publicPort, internalPort);
			} catch (NullPointerException e) {
				continue;
			}
		}
		return ports;
	}

	public static String parseContainerName(String name) {
		String res = name;
		if (name.startsWith("/"))
			res = name.substring(1);
		return res;
	}
}
