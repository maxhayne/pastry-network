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
  byte SEEK = 9;
  byte ACCEPT_STORAGE = 10;
  byte DENY_STORAGE = 11;
  byte RELAY_FILE = 12;
  byte WRITE_SUCCESS = 13;
  byte WRITE_FAIL = 14;
  byte SERVE_FILE = 15;
  byte REPLACE_LEAF = 16;
}
