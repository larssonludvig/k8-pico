package se.umu.cs.ads.types;

import java.io.Serializable;

public class Performance implements Serializable {
    private double cpuLoad;
    private double memLoad;

    public Performance(double cpuLoad, double memLoad) {
        this.cpuLoad = cpuLoad;
        this.memLoad = memLoad;
    }

    public double getCPULoad() {
        return this.cpuLoad;
    }

    public void setCPULoad(double cpuLoad) {
        this.cpuLoad = cpuLoad;
    }

    public double getMemLoad() {
        return this.memLoad;
    }

    public void setMemLoad(double memLoad) {
        this.memLoad = memLoad;
    }
}