package se.umu.cs.ads.arguments;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class CommandLineArguments {
	public static int grpcPort;
	public static int webPort;
	public static String initialMember = "";
	public static final ExecutorService pool = Executors.newCachedThreadPool();
	public static final ScheduledExecutorService scheduledPool = Executors.newScheduledThreadPool(2);
}
