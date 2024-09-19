package se.umu.cs.ads.podengine;

public class Pod {
    private final String id;
    private String name;
    private String image;
    private int[] externalPorts;
    private int[] internalPorts;
    public String getName() {
        return name;
    }

    public Pod setName(String name) {
        this.name = name;
        return this;
    }

    public String getImage() {
        return image;
    }

    public Pod setImage(String image) {
        this.image = image;
        return this;
    }

    public Pod setPorts(int[] external, int[] internal) {
        this.externalPorts = external;
        this.internalPorts = internal;
        return this;
    }

    public String getId() {
        return id;
    }

    public Pod(String id) {
        this.id = id;
    }
}
