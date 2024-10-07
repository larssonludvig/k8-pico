using System.Collections.Generic;

namespace Types {
    public class Pod {
        public string Name      { get; set; } = "";
        public string Image     { get; set; } = "";
        public string State    { get; set; } = "";
		public List<String>? Ports {get; set;} = new List<String>();
        public List<String>? Env  { get; set; } = new List<String>();
    }
}