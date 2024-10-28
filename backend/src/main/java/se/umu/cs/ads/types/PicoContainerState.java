package se.umu.cs.ads.types;

/**
 * Enum for the state of a PicoContainer
 */
public enum PicoContainerState {
    RUNNING,
    STOPPED,
    RESTARTING,
    NAME_CONFLICT,
    PORT_CONFLICT,
    UNKNOWN
}
