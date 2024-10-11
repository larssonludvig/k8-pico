package se.umu.cs.ads.types;

public enum MessageType {
    // Fetches
    FETCH_NODE,
    FETCH_NODES,
    FETCH_CONTAINER_NAMES,
    FETCH_NODE_PERFORMANCE,

    // Creates
    CREATE_CONTAINER,
	CONTAINER_ELECTION_START,
	CONTAINER_ELECTION_END,

    EVALUATE_CONTAINER_REQUEST,
	
    //Responses
	CONTAINER_LIST,

    JOIN_REQUEST,
	LEAVE_REQUEST,

    
    // Empty as dummy
    EMPTY,

	ERROR,
    UNKNOWN
}
