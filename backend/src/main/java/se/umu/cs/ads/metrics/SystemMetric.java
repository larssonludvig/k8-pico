package se.umu.cs.ads.metrics;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
public class SystemMetric {
    private final OperatingSystemMXBean bean;

    public SystemMetric() {
        bean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    }


    /**
     * Get the avaialble, free memory on Megabytes MB
     * @return the free memory
     */
    public long getFreeMemory() {
      long bytes = bean.getFreeMemorySize();
      return bytes / (1_000_000); //megabytes
    }

    /**
     * Returns the memoery "load" on a system between a value [0,1]
     * @return the memory load
     */
    public double getMemoryLoad() {
        double total = bean.getTotalMemorySize();
        double current = bean.getFreeMemorySize();
        return current / total;
    }
    /**
     * Returns the average CPU load of all cors in the range [0, 1]
     * @return the cpu load
     */
    public double getCPULoad() {
        return bean.getCpuLoad();
    }



}
