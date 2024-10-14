namespace Types {
    public class Node {
        public PicoAddress? Address             { get; set; } = null;
        public string Cluster                   { get; set; } = "";
        public List<PicoContainer> Containers   { get; set; } = new List<PicoContainer>();
    }

    public class PicoAddress {
        public string Ip    { get; set; } = "";
        public int Port     { get; set; } = 0;
    }
}