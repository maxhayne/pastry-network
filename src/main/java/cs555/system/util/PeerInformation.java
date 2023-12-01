package cs555.system.util;

import java.util.Random;

/**
 * Holds information about a Peer in the network.
 *
 * @author hayne
 */
public class PeerInformation {
  private final String host;
  private final int port;
  private String identifier;

  /**
   * Default constructor.
   *
   * @param identifier peer identity
   * @param host peer host
   * @param port peer port
   */
  public PeerInformation(String identifier, String host, int port) {
    this.identifier = identifier;
    this.host = host;
    this.port = port;
  }

  /**
   * Generates a new, randomized identifier based on the current time.
   */
  public static String generateIdentifier() {
    byte[] identifierBytes = new byte[2];
    new Random(System.nanoTime()).nextBytes(identifierBytes);
    return HexUtilities.convertBytesToHex(identifierBytes);
  }

  public String getIdentifier() {
    return identifier;
  }

  public void setIdentifier(String identifier) {
    this.identifier = identifier;
  }

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public String getAddress() {
    return host + ":" + port;
  }

  public String toString() {
    return identifier + " <- " + host + ":" + port;
  }

  /**
   * Gives how far to the right the passed peer's identifier is from this peer's
   * identifier.
   *
   * @param peer the peer to calculate the distance to
   * @return clockwise distance to peer
   */
  public int distanceToRight(PeerInformation peer) {
    return distanceToRight(peer.getIdentifier());
  }

  public int distanceToRight(String key) {
    int idInt = HexUtilities.convertHexToInt(identifier);
    int keyInt = HexUtilities.convertHexToInt(key);
    int difference = keyInt - idInt;
    return difference < 0 ? difference + 65536 : difference;
  }

  public int distanceToLeft(PeerInformation peer) {
    return (65536 - distanceToRight(peer))%65536;
  }

  public int distanceToLeft(String key) {
    return (65536 - distanceToRight(key))%65536;
  }

  public int distanceTo(String key) {
    return Math.min(distanceToLeft(key), distanceToRight(key));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null) {
      return false;
    }
    if (!(o instanceof PeerInformation peer)) {
      return false;
    }
    return identifier.equals(peer.identifier);
  }

  @Override
  public int hashCode() {
    return Integer.decode("0x" + identifier); // converts hex to int
  }
}
