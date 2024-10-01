using System.Collections.Generic;

namespace Types {
    public class Node {
        public string Name      { get; set; } = "";
        public string? Address  { get; set; } = null;
        public string? Port     { get; set; } = null;
        public string Cluster   { get; set; } = "";
        public List<Pod> pods   { get; set; } = new List<Pod>();
    }
}