package se.umu.cs.ads.types;

import java.io.Serializable;

/**
 * Class for the Performance object
 */
public class Performance implements Serializable {
    private double cpuLoad;
    private double memLoad;

    /**
     * Constructor for the Performance object
     * @param cpuLoad double object
     * @param memLoad double object
     */
    public Performance(double cpuLoad, double memLoad) {
        this.cpuLoad = cpuLoad;
        this.memLoad = memLoad;
    }

    /**
     * Gets the CPU load of the node
     * @return double object
     */
    public double getCPULoad() {
        return this.cpuLoad;
    }

    /**
     * Sets the CPU load of the node
     * @param cpuLoad double object
     */
    public void setCPULoad(double cpuLoad) {
        this.cpuLoad = cpuLoad;
    }

    /**
     * Gets the memory load of the node
     * @return double object
     */
    public double getMemLoad() {
        return this.memLoad;
    }

    /**
     * Sets the memory load of the node
     * @param memLoad double object
     */
    public void setMemLoad(double memLoad) {
        this.memLoad = memLoad;
    }
}