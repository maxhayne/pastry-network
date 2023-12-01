package cs555.system.wireformats;

/**
 * Interface for holding all message numbered message types which are encoded
 * into byte values to save space.
 *
 * @author hayne
 */
public interface Protocol {
  byte REGISTER = 0;
  byte DEREGISTER = 1;
  byte ID_COLLISION = 2;
  byte SELECT_REQUEST = 3;
  byte SELECT_RESPONSE = 4;
  byte SPECIAL_JOIN = 5;
  byte NO_PEERS = 6;
  byte PEER_BROADCAST = 7;
  byte LEAVE = 8;
}
