package se.umu.cs.ads.types;

public enum MessageType {
    // Fetches
    FETCH_NODE,
    FETCH_NODES,
    FETCH_CONTAINER_NAMES,

    // Creates
    CREATE_CONTAINER,
	CONTAINER_ELECTION_START,
	CONTAINER_ELECTION_END,

    EVALUATE_CONTAINER_REQUEST,
	
    //Responses
	CONTAINER_LIST,
    
    // Empty as dummy
    EMPTY
}
