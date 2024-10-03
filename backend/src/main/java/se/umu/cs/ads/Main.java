// package se.umu.cs.ads;

// import se.umu.cs.ads.metrics.SystemMetric;
// import se.umu.cs.ads.containerengine.ContainerEngine;
// import se.umu.cs.ads.service.RESTManager;

// import java.util.List;

// import org.springframework.boot.SpringApplication;

// public class Main {
//     public static void main(String[] args) throws Exception {
//         ContainerEngine engine = new ContainerEngine();

//         if (args.length < 2) {
//             System.err.println("Usage: <img name> <container name>");
//         }

// 		SpringApplication.run(RESTManager.class, args);

//         SystemMetric metric = new SystemMetric();
//         System.out.println("Current cpu load: " + metric.getCPULoad());
//         System.out.println("Current free mem: " + metric.getFreeMemory());
//         int imax = 8;
//         for (int i = 0; i <= imax; i++) {
//             engine.createContainer("cpuload:latest", "load-" + i);
//             engine.runContainer("load-" + i);
//         }

//         Thread.sleep(20_000);

//         System.out.println("Current cpu load: " + metric.getCPULoad());
//         System.out.println("Current free mem: " + metric.getFreeMemory());

//         for (int i = 0; i <= imax; i++) {
//             engine.stopContainer("load-" + i);
//         }


//     }
// }
