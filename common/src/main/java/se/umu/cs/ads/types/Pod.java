package se.umu.cs.ads.types;

/**
 * A class representing a pod.
 * 
 * In K8-pico, a pod can only contain one container. Thus, the Pod class can 
 * be used to represent a container as well.
 */
public class Pod {
    private String name;
    private String cluster;
    private String image;
    private PodStatus status;

    public Pod() {
        this.name = "";
        this.cluster = "";
        this.image = "";
        this.status = PodStatus.UNKNOWN;
    }

    public Pod(String name, String cluster, String image, PodStatus status) {
        this.name = name;
        this.cluster = cluster;
        this.image = image;
        this.status = status;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public String getCluster() {
        return this.cluster;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getImage() {
        return this.image;
    }

    public void setStatus(PodStatus status) {
        this.status = status;
    }

    public PodStatus getStatus() {
        return this.status;
    }

    @Override
    public String toString() {
        return "Pod [name=" + name + ", cluster=" + cluster + ", image=" + image + ", status=" + status + "]";
    }

    @Override
    public boolean equals(Object obj) {
        // As the name of a pod is unique, we can use it to compare two pods
        if (obj instanceof Pod) {
            Pod pod = (Pod) obj;
            return this.name.equals(pod.getName());
        }
        return false;
    }
}
