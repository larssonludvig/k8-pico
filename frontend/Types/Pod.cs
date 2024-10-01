using System.Collections.Generic;

namespace Types {
    public class Pod {
        public string Id = "";
        public string Name = "";
        public string Image = "";
        public Dictionary<int, int> Ports = new Dictionary<int, int>();
        public List<String> Env = new List<String>();
    }
}